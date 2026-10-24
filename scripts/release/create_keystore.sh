#!/usr/bin/env bash
# Creates the CineTV release signing key, once, outside the repository.
#
#   scripts/release/create_keystore.sh
#
# Writes ~/.cinetv/release/cinetv-release.jks and keystore.properties (owner-only permissions),
# prints the certificate fingerprint to pin in release-signing/cert-sha256.txt, and prints the
# GitHub Actions secrets to add. Back the folder up somewhere safe: an app signed with a lost key
# can never be updated in place again.
set -euo pipefail

DIR="${CINETV_KEYSTORE_DIR:-$HOME/.cinetv/release}"
KS="$DIR/cinetv-release.jks"
PROPS="$DIR/keystore.properties"
ALIAS="cinetv"

command -v keytool >/dev/null || { echo "keytool not found: install JDK 17" >&2; exit 1; }
if [ -f "$KS" ]; then
    echo "A release keystore already exists at $KS; not overwriting it." >&2
    exit 1
fi

umask 077
mkdir -p "$DIR"
PASS="$(openssl rand -base64 32 | tr -d '/+=' | cut -c1-32)"

keytool -genkeypair -v -keystore "$KS" -storetype PKCS12 -alias "$ALIAS" \
    -keyalg RSA -keysize 4096 -validity 10950 \
    -storepass "$PASS" -keypass "$PASS" \
    -dname "CN=CineTV, O=CineTV, C=TR" >/dev/null 2>&1

cat > "$PROPS" <<EOF
storeFile=cinetv-release.jks
storePassword=$PASS
keyAlias=$ALIAS
keyPassword=$PASS
EOF
chmod 600 "$KS" "$PROPS"

SHA=$(keytool -list -v -keystore "$KS" -storepass "$PASS" -alias "$ALIAS" | awk -F': ' '/SHA256:/{print $2}' | tr -d ':' | tr 'A-F' 'a-f')
echo "Keystore:     $KS"
echo "Properties:   $PROPS"
echo "Certificate SHA-256: $SHA"
echo
echo "1) Pin the fingerprint (public, safe to commit):"
echo "     echo $SHA > release-signing/cert-sha256.txt"
echo
echo "2) Add these GitHub Actions secrets (Settings › Secrets and variables › Actions), e.g. with gh:"
echo "     base64 < \"$KS\" | gh secret set CINETV_RELEASE_KEYSTORE_BASE64"
echo "     gh secret set CINETV_RELEASE_KEYSTORE_PASSWORD   # paste the storePassword from $PROPS"
echo "     gh secret set CINETV_RELEASE_KEY_ALIAS --body $ALIAS"
echo "     gh secret set CINETV_RELEASE_KEY_PASSWORD        # same value as the store password"
echo
echo "3) Back up $DIR (password manager or an encrypted drive). Never commit it."
