#!/usr/bin/env bash
set -euo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
jar=${1:?Pass the built Shadow JAR path}
version=${APP_VERSION:-1.0.1}
signed=${SIGNED_BUILD:-false}
[[ $(uname -s) == Darwin ]] || { echo 'macOS packaging must run on macOS' >&2; exit 1; }
case $(uname -m) in
  arm64) arch=arm64 ;;
  x86_64) arch=x64 ;;
  *) echo 'Unsupported Mac architecture' >&2; exit 1 ;;
esac
mkdir -p "$repo/local" "$repo/dist"
work=$(mktemp -d "$repo/local/macos-package.XXXXXX")
mkdir -p "$work/input" "$work/image" "$work/installer"
cp "$jar" "$work/input/"
jar_name=$(basename "$jar")
sign_options=()
if [[ $signed == true ]]; then
  : "${MACOS_SIGNING_IDENTITY:?Developer ID Application identity is required}"
  : "${MACOS_SIGNING_KEYCHAIN:?Signing keychain is required}"
  : "${MACOS_NOTARY_KEY_PATH:?Notary API key file is required}"
  : "${MACOS_NOTARY_KEY_ID:?Notary API key ID is required}"
  : "${MACOS_NOTARY_ISSUER_ID:?Notary API issuer ID is required}"
  signing_name=${MACOS_SIGNING_IDENTITY#Developer ID Application: }
  sign_options=(--mac-sign --mac-signing-key-user-name "$signing_name" --mac-signing-keychain "$MACOS_SIGNING_KEYCHAIN" --mac-entitlements "$repo/scripts/macos-entitlements.plist")
fi
"$JAVA_HOME/bin/jpackage" --type app-image --name LoongSim --app-version "$version" --vendor LoongSim \
  --input "$work/input" --main-jar "$jar_name" --main-class com.cburch.logisim.Main \
  --icon "$repo/logisim/src/main/resources/resources/logisim/img/LoongSim.icns" \
  --mac-package-identifier com.loongson.loongsim --mac-app-category public.app-category.education \
  --dest "$work/image" "${sign_options[@]}"
app="$work/image/LoongSim.app"

notarize() {
  local input=$1 report=$2
  xcrun notarytool submit "$input" --key "$MACOS_NOTARY_KEY_PATH" --key-id "$MACOS_NOTARY_KEY_ID" \
    --issuer "$MACOS_NOTARY_ISSUER_ID" --wait --output-format json > "$report"
  python3 - "$report" <<'PY'
import json, sys
result = json.load(open(sys.argv[1]))
print('Notarization:', result.get('status'), result.get('id'))
if result.get('status') != 'Accepted':
    raise SystemExit('Apple notarization was not accepted')
PY
}

if [[ $signed == true ]]; then
  codesign --verify --deep --strict --verbose=2 "$app"
  ditto -c -k --keepParent "$app" "$work/notarize-app.zip"
  notarize "$work/notarize-app.zip" "$work/app-notarization.json"
  xcrun stapler staple "$app"
  xcrun stapler validate "$app"
  spctl --assess --type execute --verbose=2 "$app"
fi
"$app/Contents/MacOS/LoongSim" -version
"$JAVA_HOME/bin/jpackage" --type dmg --name LoongSim --app-version "$version" --app-image "$app" \
  --dest "$work/installer" --icon "$repo/logisim/src/main/resources/resources/logisim/img/LoongSim.icns"
dmg=$(find "$work/installer" -maxdepth 1 -name '*.dmg' -print -quit)
[[ -n $dmg ]] || { echo 'DMG was not generated' >&2; exit 1; }
if [[ $signed == true ]]; then
  codesign --force --timestamp --sign "$MACOS_SIGNING_IDENTITY" --keychain "$MACOS_SIGNING_KEYCHAIN" "$dmg"
  codesign --verify --verbose=2 "$dmg"
  notarize "$dmg" "$work/dmg-notarization.json"
  xcrun stapler staple "$dmg"
  xcrun stapler validate "$dmg"
  suffix=''
else
  suffix=-unsigned
fi
prefix="LoongSim-$version-logisim-only-macos-$arch$suffix"
output="$repo/dist/$prefix.dmg"
cp "$dmg" "$output"
(cd "$repo/dist" && shasum -a 256 "$prefix.dmg" > "$prefix.dmg.sha256")
python3 - "$output" "$jar" "$signed" "$arch" "$version" "$(git -C "$repo" rev-parse HEAD)" <<'PY'
import hashlib, json, pathlib, sys
output, jar, signed, arch, version, sha = sys.argv[1:]
info = dict(platform='macos-' + arch, version=version, sourceCommit=sha,
            jarSha256=hashlib.sha256(pathlib.Path(jar).read_bytes()).hexdigest(),
            signed=signed == 'true', notarized=signed == 'true', installer=pathlib.Path(output).name)
pathlib.Path(output.removesuffix('.dmg') + '-build.json').write_text(json.dumps(info, indent=2) + '\n')
PY
echo "$output"
