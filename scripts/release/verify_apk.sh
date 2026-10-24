#!/usr/bin/env bash
# Verifies a release APK before it is published or installed.
#
#   scripts/release/verify_apk.sh path/to/cinetv.apk
#
# Checks: APK Signature Scheme v2/v3 verifies, the signing certificate matches the pinned
# fingerprint in release-signing/cert-sha256.txt, the APK is not debuggable, it is 4-byte
# zip-aligned, the package id and version are what Gradle declares, and no secrets are bundled.
set -euo pipefail

APK="${1:?usage: verify_apk.sh <apk>}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PIN_FILE="$ROOT/release-signing/cert-sha256.txt"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
[ -x "$BT/apksigner" ] || { echo "apksigner not found under $SDK/build-tools" >&2; exit 1; }

fail=0
pass() { echo "PASS  $*"; }
bad()  { echo "FAIL  $*"; fail=1; }

OUT="$("$BT/apksigner" verify --verbose --print-certs "$APK" 2>&1)" || { echo "$OUT"; bad "signature does not verify"; exit 1; }
echo "$OUT" | grep -q "Verified using v2 scheme (APK Signature Scheme v2): true" && pass "APK Signature Scheme v2" || bad "v2 signature missing"
echo "$OUT" | grep -q "Verified using v3 scheme (APK Signature Scheme v3): true" && pass "APK Signature Scheme v3" || bad "v3 signature missing"

SHA="$(echo "$OUT" | awk -F': ' '/Signer #1 certificate SHA-256 digest/{print $2}' | tr 'A-F' 'a-f')"
if [ -f "$PIN_FILE" ]; then
    PIN="$(tr -d ' \n' < "$PIN_FILE" | tr 'A-F' 'a-f')"
    [ "$SHA" = "$PIN" ] && pass "certificate matches pinned fingerprint" || bad "certificate $SHA does not match pin $PIN"
else
    bad "no pinned fingerprint at release-signing/cert-sha256.txt"
fi
DEBUG_SHA="$(keytool -list -v -keystore "$ROOT/launcher/debug.keystore" -storepass android -alias androiddebugkey 2>/dev/null | awk -F': ' '/SHA256:/{print $2}' | tr -d ':' | tr 'A-F' 'a-f')"
[ -n "$DEBUG_SHA" ] && [ "$SHA" = "$DEBUG_SHA" ] && bad "signed with the public debug key"

BADGING="$("$BT/aapt2" dump badging "$APK" 2>/dev/null)"
echo "$BADGING" | grep -q "application-debuggable" && bad "APK is debuggable" || pass "not debuggable"
PKG="$(echo "$BADGING" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
VER="$(echo "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")"
[ "$PKG" = "com.cinetv.launcher" ] && pass "package $PKG $VER" || bad "unexpected package $PKG"

"$BT/zipalign" -c -p 4 "$APK" >/dev/null 2>&1 && pass "zip-aligned" || bad "not zip-aligned"

if unzip -l "$APK" | grep -qiE "keystore|\.jks|keystore\.properties|\.env"; then
    bad "APK contains a keystore or env file"
else
    pass "no keystores or env files inside"
fi
if unzip -l "$APK" | grep -q "assets/signature_logo.png"; then
    # Personal builds may carry the owner's mark (CINETV_ALLOW_PRIVATE_BRANDING=1); public releases never do.
    [ "${CINETV_ALLOW_PRIVATE_BRANDING:-0}" = 1 ] && echo "NOTE  personal branding included (allowed for this build)" || bad "private branding included in a public build"
else
    pass "no private branding"
fi
if unzip -p "$APK" 'classes*.dex' 'assets/*' 2>/dev/null | LC_ALL=C grep -aqE "AIza[0-9A-Za-z_-]{30,}|sk-ant-[0-9A-Za-z_-]{20,}"; then
    bad "an API key is embedded in the APK"
else
    pass "no embedded API keys"
fi

SUM="$( (sha256sum "$APK" 2>/dev/null || shasum -a 256 "$APK") | awk '{print $1}')"
echo "SHA-256 $SUM  $(basename "$APK")"
exit $fail
