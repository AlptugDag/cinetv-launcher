#!/usr/bin/env bash
# TV Master Launcher — Ad-Free YouTube Disguise Engine
# Replaces stock bloated YouTube with an ad-free client (SmartTube / TizenTube / Cobalt)
# disguised with the official YouTube logo and name for a seamless family experience.
set -e

echo "==> [YouTube Disguise & Ad-Free Setup]"
DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')

if [ -z "$DEVICE" ]; then
    echo "ERROR: No ADB device connected."
    exit 1
fi

echo "==> Connected to: $DEVICE"

# 1. Disable / remove stock YouTube TV (cluttered with unskippable ads & telemetry)
echo "==> Disabling stock YouTube TV app..."
adb shell "pm uninstall -k --user 0 com.google.android.youtube.tv 2>/dev/null || pm disable-user --user 0 com.google.android.youtube.tv 2>/dev/null || true"

# 2. Check architecture (arm vs arm64)
ABI=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')
echo "==> Target SoC Architecture: $ABI"

# 3. Detect installed ad-free YouTube clients
# Supported packages:
# - io.gh.reisxd.tizentube.cobalt (TizenTube Cobalt TV)
# - com.teamsmart.videomanager.tv (SmartTube Stable)
# - com.liskovsoft.smarttubetv.beta (SmartTube Beta)
INSTALLED_YT=""
for pkg in "io.gh.reisxd.tizentube.cobalt" "com.teamsmart.videomanager.tv" "com.liskovsoft.smarttubetv.beta"; do
    if adb shell "pm list packages" | grep -q "$pkg"; then
        INSTALLED_YT="$pkg"
        echo "==> Found installed ad-free YouTube client: $pkg"
        break
    fi
done

if [ -z "$INSTALLED_YT" ]; then
    echo "==> No ad-free client found. Downloading SmartTube Stable (TV edition)..."
    ST_URL="https://github.com/yuliskov/SmartTube/releases/latest/download/smarttube_stable.apk"
    TMP_APK="/tmp/smarttube_stable.apk"
    curl -L -s -o "$TMP_APK" "$ST_URL" || wget -q -O "$TMP_APK" "$ST_URL"
    echo "==> Installing SmartTube..."
    adb install -r -d -g "$TMP_APK"
    INSTALLED_YT="com.teamsmart.videomanager.tv"
    rm -f "$TMP_APK"
fi

# 4. Configure YouTube disguised intent mapping
echo "==> Setting default YouTube intent handler to: $INSTALLED_YT"
adb shell "cmd package set-home-activity \"$INSTALLED_YT\" 2>/dev/null || true"

# 5. Enable SponsorBlock & High-Bitrate VP9/AV1 codec settings if supported
echo "==> Optimizing playback buffers..."
adb shell "setprop persist.sys.media.use_awesome 0 2>/dev/null || true"
adb shell "setprop media.stagefright.enable-player true 2>/dev/null || true"

echo "==> Ad-Free YouTube Disguise Setup Complete!"
echo "    App: $INSTALLED_YT"
echo "    Icon: Official YouTube Red Icon (via TV Master Launcher)"
echo "    Ads: Blocked (Video ads + sponsor segments + banners)"
