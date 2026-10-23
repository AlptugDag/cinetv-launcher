#!/usr/bin/env bash
# TV Master Launcher — Standalone Fast Build Script
# Compiles, dexes, packages, and signs without Gradle overhead.
set -e

if [ -d "/opt/homebrew/opt/openjdk@17/bin" ]; then
    export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
    export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
fi

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

# Detect Android SDK Build Tools
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Library/Android/sdk" ]; then
        ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [ -d "/opt/homebrew/share/android-commandlinetools" ]; then
        ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
    fi
fi

if [ -z "$BUILD_TOOLS" ]; then
    BUILD_TOOLS=$(find "$ANDROID_HOME/build-tools" -maxdepth 1 -mindepth 1 | sort -V | tail -n 1)
fi

if [ -z "$ANDROID_JAR" ]; then
    ANDROID_JAR=$(find "$ANDROID_HOME/platforms" -name "android.jar" | sort -V | tail -n 1)
fi

echo "==> Using Build Tools: $BUILD_TOOLS"
echo "==> Using Android Jar:  $ANDROID_JAR"

mkdir -p build/classes build/dex build/compiled_res build/gen
rm -rf build/classes/* build/dex/* build/compiled_res/* build/unaligned.apk build/signed.apk

echo "[1/7] Compiling resources with AAPT2..."
"$BUILD_TOOLS/aapt2" compile --dir res -o build/compiled_res/

echo "[2/7] Linking APK with AAPT2..."
"$BUILD_TOOLS/aapt2" link -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    -A assets \
    -o build/unaligned.apk \
    --java build/gen \
    build/compiled_res/*.flat

echo "[3/7] Compiling Java classes with javac..."
javac -source 11 -target 11 \
    -cp "$ANDROID_JAR" \
    -d build/classes \
    build/gen/com/gathin/tvhome/R.java \
    src/com/gathin/tvhome/*.java

echo "[4/7] Compiling DEX with D8..."
"$BUILD_TOOLS/d8" --output build/dex \
    --lib "$ANDROID_JAR" \
    build/classes/com/gathin/tvhome/*.class

echo "[5/7] Packaging classes.dex..."
cp build/unaligned.apk build/packaged.apk
cd build/dex && zip -u ../packaged.apk classes.dex && cd ../..

echo "[6/7] Aligning APK with zipalign..."
"$BUILD_TOOLS/zipalign" -f -p 4 build/packaged.apk build/aligned.apk

echo "[7/7] Signing APK with apksigner..."
"$BUILD_TOOLS/apksigner" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out ../release/cinetv-launcher-v1.0.0.apk build/aligned.apk
rm -f ../release/tv-master-launcher-v1.0.0.apk
cp ../release/cinetv-launcher-v1.0.0.apk ../release/tv-master-launcher-v1.0.0.apk
cp ../release/cinetv-launcher-v1.0.0.apk.idsig ../release/tv-master-launcher-v1.0.0.apk.idsig 2>/dev/null || true

echo "SUCCESS! Built release/cinetv-launcher-v1.0.0.apk"
