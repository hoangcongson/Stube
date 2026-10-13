#!/bin/bash
set -euo pipefail

VERSION="${1:-}"
if [[ -z "$VERSION" || ! "$VERSION" =~ ^[0-9]+\.[0-9]+$ ]]; then
  echo "Usage: $0 1.1"
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION_CODE="$(python3 - "$VERSION" <<'PY'
import sys
major, minor = map(int, sys.argv[1].split('.'))
print(major * 10000 + minor * 100)
PY
)"

python3 - "$VERSION" "$VERSION_CODE" <<'PY'
from pathlib import Path
import re, sys
v, code = sys.argv[1:]
p = Path('smarttubetv/build.gradle')
s = p.read_text()
pat = r'(productFlavors\s*\{\s*stmobile\s*\{.*?versionCode\s+)\d+(\s*\n\s*versionName\s+")([^"]+)(")'
s2 = re.sub(pat, rf'\g<1>{code}\g<2>{v}\g<4>', s, count=1, flags=re.S)
if s2 == s:
    raise SystemExit('Could not update stmobile version in smarttubetv/build.gradle')
p.write_text(s2)
PY

./gradlew clean :smarttubetv:assembleStmobileRelease

APKSIGNER="${APKSIGNER:-$HOME/Library/Android/sdk/build-tools/36.0.0/apksigner}"
KEYSTORE="${STUBE_KEYSTORE:-$HOME/stube-release.keystore}"
PROPS="$ROOT/keystore.properties"
if [[ ! -f "$PROPS" ]]; then
  echo "Missing $PROPS"
  exit 1
fi
STORE_PASSWORD="$(sed -n 's/^storePassword=//p' "$PROPS")"
KEY_ALIAS="$(sed -n 's/^keyAlias=//p' "$PROPS")"
KEY_PASSWORD="$(sed -n 's/^keyPassword=//p' "$PROPS")"
[[ -x "$APKSIGNER" ]] || { echo "apksigner not found: $APKSIGNER"; exit 1; }
[[ -f "$KEYSTORE" ]] || { echo "keystore not found: $KEYSTORE"; exit 1; }

DIST="$ROOT/dist"
rm -rf "$DIST"
mkdir -p "$DIST"
SRC="$ROOT/smarttubetv/build/outputs/renamed_apks/stmobileRelease"
for abi in arm64-v8a armeabi-v7a x86 universal; do
  in="$SRC/Stube_${VERSION}_${abi}.apk"
  out="$DIST/Stube_${VERSION}_${abi}.apk"
  [[ -f "$in" ]] || { echo "Missing $in"; exit 1; }
  "$APKSIGNER" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$STORE_PASSWORD" --key-pass "pass:$KEY_PASSWORD" \
    --out "$out" "$in"
  "$APKSIGNER" verify --verbose "$out" >/dev/null
  echo "Signed: $(basename "$out")"
done

python3 tools/update_manifest.py --tag "v$VERSION" --dist "$DIST" --out "$DIST/stube.json"

if ! gh auth status >/dev/null 2>&1; then
  echo "GitHub CLI is not logged in. Run: gh auth login"
  exit 1
fi

git add smarttubetv/build.gradle tools/publish_release_local.sh
if ! git diff --cached --quiet; then
  git commit -m "Prepare Stube $VERSION release"
fi
git push origin main

gh release create "v$VERSION" "$DIST"/* \
  --title "Stube $VERSION" \
  --notes "Stube $VERSION release" \
  --latest

echo
echo "Release v$VERSION published. Update manifest:"
echo "https://github.com/hoangcongson/Stube/releases/latest/download/stube.json"
