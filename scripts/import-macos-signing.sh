#!/usr/bin/env bash
set -euo pipefail
[[ ${SIGNED_BUILD:-false} == true ]] || exit 0
: "${MACOS_CERTIFICATE_P12_BASE64:?Missing Developer ID certificate}"
: "${MACOS_NOTARY_KEY_BASE64:?Missing notarization API key}"
: "${MACOS_NOTARY_KEY_ID:?Missing notarization API key ID}"
: "${MACOS_NOTARY_ISSUER_ID:?Missing notarization API issuer ID}"
directory=$(mktemp -d "$RUNNER_TEMP/loongsim-signing.XXXXXX")
keychain="$directory/signing.keychain-db"
keychain_password=$(openssl rand -hex 24)
printf '::add-mask::%s\n' "$keychain_password"
printf '%s' "$MACOS_CERTIFICATE_P12_BASE64" | base64 --decode > "$directory/developer-id.p12"
printf '%s' "$MACOS_NOTARY_KEY_BASE64" | base64 --decode > "$directory/AuthKey.p8"
chmod 600 "$directory/developer-id.p12" "$directory/AuthKey.p8"
security create-keychain -p "$keychain_password" "$keychain"
security set-keychain-settings -lut 21600 "$keychain"
security unlock-keychain -p "$keychain_password" "$keychain"
security import "$directory/developer-id.p12" -k "$keychain" -P "${MACOS_CERTIFICATE_PASSWORD:-}" -T /usr/bin/codesign -T /usr/bin/security
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$keychain_password" "$keychain" >/dev/null
identity=$(security find-identity -v -p codesigning "$keychain" | sed -n 's/.*"\(Developer ID Application:.*\)".*/\1/p' | head -1)
[[ -n $identity ]] || { echo 'Valid Developer ID Application identity not found' >&2; exit 1; }
{
  printf 'MACOS_SIGNING_KEYCHAIN=%s\n' "$keychain"
  printf 'MACOS_SIGNING_IDENTITY=%s\n' "$identity"
  printf 'MACOS_NOTARY_KEY_PATH=%s\n' "$directory/AuthKey.p8"
} >> "$GITHUB_ENV"
