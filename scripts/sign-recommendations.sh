#!/usr/bin/env bash
# Signs recommendations/recommendations.json for the Smart Advisor.
#
# The launcher downloads recommendations.json and recommendations.json.sig from the
# repository's main branch and only uses the file if the Ed25519 signature verifies
# against the public key in RecommendationsRepository.PUBLIC_KEY.
#
# Usage: scripts/sign-recommendations.sh /path/to/recommendations-ed25519.pem
# Keep the private key OUT of the repository.
set -euo pipefail
key="${1:?usage: $0 <ed25519-private-key.pem>}"
dir="$(cd "$(dirname "$0")/.." && pwd)/recommendations"
json="$dir/recommendations.json"
[ -f "$json" ] || { echo "missing $json" >&2; exit 1; }
python3 -I -c 'import json,sys; d=json.load(open(sys.argv[1])); assert d["format"]=="zalith-recommendations" and d["version"]==1' "$json"
openssl pkeyutl -sign -inkey "$key" -rawin -in "$json" | base64 -w0 > "$json.sig"
# verify with the same public key the app embeds
pub="$(openssl pkey -in "$key" -pubout -outform DER | tail -c 32 | base64)"
echo "signed $json"
echo "public key (must match RecommendationsRepository.PUBLIC_KEY): $pub"
