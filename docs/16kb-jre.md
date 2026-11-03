# Bundled JREs and 16 KB memory pages

Android 15+ devices may use a 16 KB memory page size. Shared libraries whose ELF `LOAD`
segments are aligned to less than 16 KB cannot be loaded there. The launcher's own native
code and PetLib are built with `-Wl,-z,max-page-size=16384`, and the APK is checked in CI
(`scripts/check-16kb-alignment.sh`). The bundled Java runtimes are not: they ship as prebuilt
`.tar.xz` assets (`ZalithLauncher/src/main/assets/runtimes/jre-*`) and are extracted and
`dlopen()`ed at runtime, so a non-aligned `libjvm.so` means **the game cannot start at all**
on a 16 KB-page device, whatever the renderer.

## Current state (measured)

`scripts/check-16kb-alignment.sh --abi <abi> <archive>` on the archives in this repository:

| JRE | arm64-v8a | x86_64 | Minecraft versions |
|---|---|---|---|
| Java 8  | OK (0/34 below 16 KB) | 33/33 below 16 KB | <= 1.16 |
| Java 17 | 31/31 below 16 KB | 31/31 below 16 KB | 1.17 – 1.20.4 |
| Java 21 | 32/32 below 16 KB | 32/32 below 16 KB | 1.20.5 – 1.21.x |
| Java 25 | 33/34 below 16 KB | 34/35 below 16 KB | 26.x |

Java 8 for arm64 is fine because it is linked with GNU ld, whose default maximum page size
on aarch64 is 64 KB. The others are linked with LLVM lld from NDK r27b, which defaults to 4 KB.

Confirmed on a device: the libraries extracted to
`files/games/runtimes/Internal-{17,21}` on an arm64 phone have `libjvm.so` with
`LOAD` alignment `0x1000`.

## Why this cannot be fixed in this repository

Alignment is not a header field that can be patched after the fact: segment file offsets
and virtual addresses must themselves be congruent modulo 16 KB, which requires relinking.
The fix belongs in the JRE build.

## Mitigation shipped in the app (Android 16+)

`AndroidManifest.xml` sets `android:pageSizeCompat="enabled"`. On a device with 16 KB
pages running Android 16 or newer, the package manager then runs the app in **16 KB
backcompat mode**: the dynamic linker loads ELF files whose `LOAD` segments are only
4 KB aligned, so the extracted JRE (`libjvm.so` etc.) can be loaded and the game starts.
On 4 KB-page devices (almost all phones today) the attribute has no effect.

This is a stopgap (Android documents backcompat mode as such, with some memory and
performance cost on 16 KB devices). It does not help on Android 15 16 KB devices, and
relinking the JREs (below) remains the real fix. Patching the extracted libraries is not
possible: segment offsets and addresses themselves must be congruent modulo 16 KB.

## Fix in the JRE build (upstream)

The runtimes come from the Pojav-family Android OpenJDK build scripts
([PojavLauncherTeam/android-openjdk-build-multiarch](https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch)).
`6_buildjdk.sh` passes `$LDFLAGS` to OpenJDK's `configure` through `--with-extra-ldflags`,
and the third-party libraries built in `4_buildlibs.sh` inherit `LDFLAGS` from the
environment, so one change in `setdevkitpath.sh` covers everything:

Branch `buildjre17-21` (Java 17 / 21; NDK r27b, clang + lld):

```diff
-export LDFLAGS=""
+# Android 15+ 16 KB page size support (NDK r27 lld still defaults to 4 KB)
+export LDFLAGS="-Wl,-z,max-page-size=16384"
```

Branch `buildjre8` (Java 8; NDK r10e, GCC + GNU ld) — only needed for x86_64, since
GNU ld already uses 64 KB on aarch64, but harmless everywhere:

```diff
-export LDFLAGS="-L$NDK/platforms/android-$API/arch-$TARGET_SHORT/usr/lib"
+export LDFLAGS="-L$NDK/platforms/android-$API/arch-$TARGET_SHORT/usr/lib -Wl,-z,max-page-size=16384"
```

Java 25: the branch that produces `jre-25` could not be identified; apply the same
`LDFLAGS` change there.

Alternatively, raising the NDK to r28 or newer makes 16 KB the lld default.

## Verifying rebuilt runtimes

```bash
for a in arm64:arm64-v8a x86_64:x86_64; do
  scripts/check-16kb-alignment.sh --abi "${a##*:}" ZalithLauncher/src/main/assets/runtimes/jre-21/bin-${a%%:*}.tar.xz
done
```

PR CI runs this for every JRE (`Check 16 KB alignment of bundled JREs`, report-only). Once
rebuilt runtimes are committed, remove `continue-on-error` from that step so regressions fail
the build.
