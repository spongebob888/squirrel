#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

for name in ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
  if [[ -z "${!name:-}" ]]; then
    echo "Missing signing environment variable: $name" >&2
    exit 1
  fi
done
: "${ANDROID_HOME:?Enter nix develop ./nix before signing}"
build_tools="$ANDROID_HOME/build-tools/36.0.0"
apk_dir=app/build/outputs/apk/release
umask 077
signing_dir="$(mktemp -d)"
trap 'rm -rf "$signing_dir"' EXIT
printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$signing_dir/release.jks"
"$build_tools/zipalign" -P 16 -f 4 "$apk_dir/app-release-unsigned.apk" "$signing_dir/aligned.apk"
"$build_tools/apksigner" sign \
  --ks "$signing_dir/release.jks" \
  --ks-key-alias "$ANDROID_KEY_ALIAS" \
  --ks-pass env:ANDROID_KEYSTORE_PASSWORD \
  --key-pass env:ANDROID_KEY_PASSWORD \
  --out "$signing_dir/signed.apk" "$signing_dir/aligned.apk"
"$build_tools/apksigner" verify --verbose "$signing_dir/signed.apk"
"$build_tools/zipalign" -c -P 16 4 "$signing_dir/signed.apk"
cp "$signing_dir/signed.apk" "$apk_dir/app-release.apk"
