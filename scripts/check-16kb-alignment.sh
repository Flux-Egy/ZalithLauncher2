#!/usr/bin/env bash
#
# Zalith Launcher 2
# Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Checks that 64-bit native libraries (arm64-v8a, x86_64) have ELF LOAD segments
# aligned to at least 16 KB, as required on Android devices that use a 16 KB page size.
#
# Usage:
#   scripts/check-16kb-alignment.sh [--allowlist FILE] [--readelf PATH] <input>...
#
# <input> may be an .apk/.aab/.zip, a directory (scanned recursively for
# */arm64-v8a/*.so and */x86_64/*.so), or a single .so file (its ABI is taken
# from the parent directory name).
#
# Allowlist: one "<abi>/<libname.so>" per line; '#' starts a comment.
# Allowlisted offenders are reported as warnings; any other offender fails the check.
# Allowlist entries that turn out to be compliant are reported as stale.
#
# Exit codes: 0 = OK, 1 = non-compliant library found, 2 = usage / tooling error.
#
# Portable: bash + coreutils + unzip + readelf/llvm-readelf. No gawk-only features
# (GitHub's ubuntu runners ship mawk).

set -euo pipefail

readonly PAGE_SIZE=16384
readonly ABIS=("arm64-v8a" "x86_64")

allowlist_file=""
readelf_bin="${READELF:-}"
inputs=()

usage() {
    sed -n '9,24p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --allowlist) allowlist_file="${2:?--allowlist needs a file}"; shift 2 ;;
        --readelf)   readelf_bin="${2:?--readelf needs a path}"; shift 2 ;;
        -h|--help)   usage ;;
        --)          shift; inputs+=("$@"); break ;;
        -*)          echo "Unknown option: $1" >&2; usage ;;
        *)           inputs+=("$1"); shift ;;
    esac
done

[[ ${#inputs[@]} -gt 0 ]] || usage

if [[ -z "$readelf_bin" ]]; then
    for candidate in llvm-readelf readelf; do
        if command -v "$candidate" >/dev/null 2>&1; then readelf_bin="$candidate"; break; fi
    done
    if [[ -z "$readelf_bin" && -n "${ANDROID_NDK_HOME:-}" ]]; then
        readelf_bin="$(find "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt" -name llvm-readelf -type f 2>/dev/null | head -n 1 || true)"
    fi
fi
[[ -n "$readelf_bin" ]] || { echo "error: readelf/llvm-readelf not found (set READELF or --readelf)" >&2; exit 2; }

in_ci() { [[ -n "${GITHUB_ACTIONS:-}" ]]; }
annotate() { # level message
    if in_ci; then echo "::$1::$2"; else echo "[$1] $2"; fi
}

declare -A allowed=()
if [[ -n "$allowlist_file" ]]; then
    [[ -f "$allowlist_file" ]] || { echo "error: allowlist not found: $allowlist_file" >&2; exit 2; }
    while IFS= read -r line || [[ -n "$line" ]]; do
        entry="${line%%#*}"
        entry="$(echo "$entry" | tr -d '[:space:]')"
        [[ -n "$entry" ]] && allowed["$entry"]=1
    done < "$allowlist_file"
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

# Collect "<abi>/<name>\t<path>" records
records="$workdir/records.tsv"
: > "$records"

add_from_dir() { # dir
    local abi so
    for abi in "${ABIS[@]}"; do
        while IFS= read -r -d '' so; do
            printf '%s/%s\t%s\n' "$abi" "$(basename "$so")" "$so" >> "$records"
        done < <(find "$1" -type f -name '*.so' -path "*/$abi/*" -print0)
    done
}

idx=0
for input in "${inputs[@]}"; do
    if [[ -d "$input" ]]; then
        add_from_dir "$input"
    elif [[ -f "$input" ]]; then
        case "$input" in
            *.apk|*.aab|*.zip|*.aar)
                idx=$((idx + 1))
                dest="$workdir/x$idx"
                mkdir -p "$dest"
                # Only 64-bit native libs are relevant; ignore archives without them.
                unzip -qq -o "$input" '*/arm64-v8a/*.so' '*/x86_64/*.so' -d "$dest" 2>/dev/null || true
                add_from_dir "$dest"
                ;;
            *.so)
                abi="$(basename "$(dirname "$input")")"
                printf '%s/%s\t%s\n' "$abi" "$(basename "$input")" "$input" >> "$records"
                ;;
            *) echo "error: unsupported input: $input" >&2; exit 2 ;;
        esac
    else
        echo "error: input not found: $input" >&2
        exit 2
    fi
done

if [[ ! -s "$records" ]]; then
    annotate error "No 64-bit native libraries found in: ${inputs[*]}"
    exit 2
fi

# Smallest LOAD p_align of an ELF file, in bytes (0 if none / unreadable)
min_load_align() {
    local min="" value hex
    while read -r hex; do
        [[ "$hex" =~ ^0x[0-9a-fA-F]+$ ]] || continue
        value=$((hex))
        if [[ -z "$min" || "$value" -lt "$min" ]]; then min="$value"; fi
    done < <("$readelf_bin" -lW "$1" 2>/dev/null | awk '$1 == "LOAD" { print $NF }')
    echo "${min:-0}"
}

failures=0
warnings=0
checked=0
declare -A seen_bad=()
declare -A seen_ok=()
summary="$workdir/summary.md"
{
    echo "### 16 KB page-size alignment"
    echo
    echo "| Library | Min LOAD align | Status |"
    echo "|---|---|---|"
} > "$summary"

while IFS=$'\t' read -r key path; do
    checked=$((checked + 1))
    align="$(min_load_align "$path")"
    printf -v align_hex '0x%x' "$align"
    if [[ "$align" -ge "$PAGE_SIZE" ]]; then
        seen_ok["$key"]=1
        continue
    fi
    seen_bad["$key"]=1
    if [[ -n "${allowed[$key]:-}" ]]; then
        warnings=$((warnings + 1))
        annotate warning "16KB: $key is $align_hex-aligned (allowlisted third-party library, needs upstream rebuild)"
        echo "| \`$key\` | $align_hex | allowlisted |" >> "$summary"
    else
        failures=$((failures + 1))
        annotate error "16KB: $key is $align_hex-aligned (< 0x4000). Rebuild with -Wl,-z,max-page-size=16384, or if it is a third-party prebuilt add '$key' to the allowlist with an upstream TODO."
        echo "| \`$key\` | $align_hex | **FAIL** |" >> "$summary"
    fi
done < <(sort -u "$records")

stale=0
for key in "${!allowed[@]}"; do
    if [[ -n "${seen_ok[$key]:-}" && -z "${seen_bad[$key]:-}" ]]; then
        stale=$((stale + 1))
        annotate notice "16KB: allowlist entry '$key' is now compliant; remove it from the allowlist."
    fi
done

{
    echo
    echo "Checked $checked library file(s): $failures failure(s), $warnings allowlisted, $stale stale allowlist entr(y/ies)."
} >> "$summary"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    cat "$summary" >> "$GITHUB_STEP_SUMMARY"
fi
tail -n 1 "$summary"

[[ "$failures" -eq 0 ]]
