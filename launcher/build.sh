#!/usr/bin/env bash
# ==============================================================================
# CineTV Launcher — Portable Standalone Build Engine
# Cross-platform compilation across Linux, macOS, WSL, and GitHub Actions CI.
# Compiles resources (AAPT2), Java (javac), bytecode (D8), packages, aligns,
# and signs with production/debug keystores without heavy Gradle overhead.
# ==============================================================================
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

# 1. Java Compiler (JDK 11-17+) Detection
if ! javac -version >/dev/null 2>&1; then
    for cand in \
        "${JAVA_HOME:-}/bin" \
        "/opt/homebrew/opt/openjdk@17/bin" \
        "/opt/homebrew/opt/openjdk/bin" \
        "/usr/local/opt/openjdk@17/bin" \
        "/usr/lib/jvm/java-17-openjdk-amd64/bin" \
        "/usr/lib/jvm/java-17-openjdk/bin" \
        "/usr/lib/jvm/default-java/bin"; do
        if [ -n "$cand" ] && [ -x "$cand/javac" ]; then
            export PATH="$cand:$PATH"
            export JAVA_HOME="$(dirname "$cand")"
            break
        fi
    done
fi

if ! javac -version >/dev/null 2>&1; then
    echo "ERROR: javac not functional or not found in standard JDK locations."
    echo "Please install OpenJDK 11 or 17 (e.g., sudo apt install openjdk-17-jdk or brew install openjdk@17)."
    exit 1
fi

# 2. Android SDK Root Detection
if [ -z "${ANDROID_HOME:-}" ] && [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    ANDROID_HOME="$ANDROID_SDK_ROOT"
fi

if [ -z "${ANDROID_HOME:-}" ]; then
    for cand in \
        "$HOME/Library/Android/sdk" \
        "$HOME/Android/Sdk" \
        "/usr/lib/android-sdk" \
        "/opt/android-sdk" \
        "/opt/homebrew/share/android-commandlinetools"; do
        if [ -d "$cand" ]; then
            ANDROID_HOME="$cand"
            break
        fi
    done
fi

if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "$ANDROID_HOME" ]; then
    echo "ERROR: ANDROID_HOME is not set and no standard Android SDK was found."
    echo "Set ANDROID_HOME to your Android SDK directory (e.g., export ANDROID_HOME=~/Android/Sdk)."
    exit 1
fi

# 3. Build Tools & Platform JAR Detection
if [ -z "${BUILD_TOOLS:-}" ]; then
    if [ -d "$ANDROID_HOME/build-tools" ]; then
        BUILD_TOOLS=$(find "$ANDROID_HOME/build-tools" -maxdepth 1 -mindepth 1 | sort -V | tail -n 1)
    fi
fi

if [ -z "${BUILD_TOOLS:-}" ] || [ ! -x "${BUILD_TOOLS}/aapt2" ]; then
    echo "ERROR: Android build-tools not found under $ANDROID_HOME/build-tools."
    echo "Install build-tools via sdkmanager (e.g., sdkmanager 'build-tools;34.0.0')."
    exit 1
fi

if [ -z "${ANDROID_JAR:-}" ]; then
    if [ -d "$ANDROID_HOME/platforms" ]; then
        ANDROID_JAR=$(find "$ANDROID_HOME/platforms" -name "android.jar" | sort -V | tail -n 1)
    fi
fi

if [ -z "${ANDROID_JAR:-}" ] || [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: android.jar not found under $ANDROID_HOME/platforms."
    echo "Install platform via sdkmanager (e.g., sdkmanager 'platforms;android-34')."
    exit 1
fi

echo "================================================================================"
echo "  CineTV Launcher — Portable Build Engine"
echo "================================================================================"
echo "==> JDK Version:   $(javac -version 2>&1)"
echo "==> Build Tools:   $BUILD_TOOLS"
echo "==> Android Jar:   $ANDROID_JAR"
echo "================================================================================"

mkdir -p build/classes build/dex build/compiled_res build/gen ../release
rm -rf build/classes/* build/dex/* build/compiled_res/* build/gen/* build/unaligned.apk build/packaged.apk build/aligned.apk

echo "[1/7] Compiling Android resources with AAPT2..."
"$BUILD_TOOLS/aapt2" compile --dir res -o build/compiled_res/

echo "[2/7] Linking APK with AAPT2..."
"$BUILD_TOOLS/aapt2" link -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    -A assets \
    -o build/unaligned.apk \
    --java build/gen \
    build/compiled_res/*.flat

echo "[3/7] Compiling Java classes with javac (Target 11)..."
javac -source 11 -target 11 \
    -cp "$ANDROID_JAR" \
    -d build/classes \
    build/gen/com/gathin/tvhome/R.java \
    src/com/gathin/tvhome/*.java

echo "[4/7] Compiling DEX bytecode with D8..."
"$BUILD_TOOLS/d8" --output build/dex \
    --lib "$ANDROID_JAR" \
    build/classes/com/gathin/tvhome/*.class

echo "[5/7] Packaging classes.dex into APK..."
cp build/unaligned.apk build/packaged.apk
(cd build/dex && zip -u ../packaged.apk classes.dex)

echo "[6/7] 4-Byte Page Aligning APK with zipalign..."
"$BUILD_TOOLS/zipalign" -f -p 4 build/packaged.apk build/aligned.apk

# Keystore & Signature Scheme Management
KEYSTORE_TEMP=""
trap 'if [ -n "$KEYSTORE_TEMP" ] && [ -f "$KEYSTORE_TEMP" ]; then rm -f "$KEYSTORE_TEMP"; fi' EXIT

if [ -n "${CINETV_RELEASE_KEYSTORE_BASE64:-}" ]; then
    echo "[7/7] Signing APK with Production Key from CI Base64 Secret..."
    KEYSTORE_TEMP="$(mktemp -t cinetv_release_XXXXXX.keystore)"
    echo "$CINETV_RELEASE_KEYSTORE_BASE64" | base64 --decode > "$KEYSTORE_TEMP"
    KEYSTORE="$KEYSTORE_TEMP"
    KEYSTORE_PASS="${CINETV_RELEASE_KEYSTORE_PASSWORD:-}"
    KEY_ALIAS="${CINETV_RELEASE_KEY_ALIAS:-}"
    KEY_PASS="${CINETV_RELEASE_KEY_PASSWORD:-$KEYSTORE_PASS}"
    "$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KEY_PASS" --out ../release/cinetv-launcher-v1.0.0.apk build/aligned.apk
elif [ -n "${CINETV_RELEASE_KEYSTORE:-}" ] && [ -f "$CINETV_RELEASE_KEYSTORE" ]; then
    echo "[7/7] Signing APK with Production Keystore ($CINETV_RELEASE_KEYSTORE)..."
    KEYSTORE="$CINETV_RELEASE_KEYSTORE"
    KEYSTORE_PASS="${CINETV_RELEASE_KEYSTORE_PASSWORD:-}"
    KEY_ALIAS="${CINETV_RELEASE_KEY_ALIAS:-}"
    KEY_PASS="${CINETV_RELEASE_KEY_PASSWORD:-$KEYSTORE_PASS}"
    "$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KEY_PASS" --out ../release/cinetv-launcher-v1.0.0.apk build/aligned.apk
else
    KEYSTORE="${CINETV_KS:-debug.keystore}"
    KEYSTORE_PASS="${CINETV_KS_PASS:-pass:android}"
    KEY_PASS="${CINETV_KEY_PASS:-$KEYSTORE_PASS}"
    echo "[7/7] Signing APK with Development Keystore ($KEYSTORE)..."
    echo "      [Note: For official production distribution, set CINETV_RELEASE_KEYSTORE_BASE64 in CI]"
    "$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass "$KEYSTORE_PASS" --key-pass "$KEY_PASS" --out ../release/cinetv-launcher-v1.0.0.apk build/aligned.apk
fi

cp ../release/cinetv-launcher-v1.0.0.apk ../release/tv-master-launcher-v1.0.0.apk
cp ../release/cinetv-launcher-v1.0.0.apk.idsig ../release/tv-master-launcher-v1.0.0.apk.idsig 2>/dev/null || true

echo "================================================================================"
echo "  [SUCCESS] Built signed artifacts:"
echo "    -> release/cinetv-launcher-v1.0.0.apk"
echo "    -> release/tv-master-launcher-v1.0.0.apk"
echo "================================================================================"
