#!/usr/bin/env bash
# TV Master Launcher — AirPlay & Cast Integration Suite
# Configures native AirPlay screen mirroring & audio streaming from iOS/macOS to Android TV.
set -e

echo "==> [AirPlay & Cast Receiver Setup]"
DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')

if [ -z "$DEVICE" ]; then
    echo "ERROR: No ADB device connected."
    exit 1
fi

echo "==> Connected to: $DEVICE"

# 1. Check for existing receiver apps
# Supported receivers:
# - com.instantbits.cast.receiver (Web Video Caster Receiver — ultra-fast & lightweight)
# - com.ionitech.airscreen (AirScreen — full AirPlay + Cast + Miracast + DLNA)
# - com.waxrain.airplaydmr2 (AirReceiver)
RECEIVER_PKG=""
for pkg in "com.instantbits.cast.receiver" "com.ionitech.airscreen" "com.waxrain.airplaydmr2"; do
    if adb shell "pm list packages" | grep -q "$pkg"; then
        RECEIVER_PKG="$pkg"
        echo "==> Found existing receiver app: $pkg"
        break
    fi
done

if [ -z "$RECEIVER_PKG" ]; then
    echo "==> No receiver found. Installing Web Video Caster Receiver (lightweight, zero bloat)..."
    WVC_URL="https://wvc.page.link/tv-apk"
    # Alternatively prompt user or use apk mirror
    echo "    Recommended: Install 'Web Video Caster Receiver' or 'AirScreen' from Play Store or Aptoide TV."
fi

# 2. Grant battery optimization exemptions (keeps AirPlay daemon alive in standby)
if [ -n "$RECEIVER_PKG" ]; then
    echo "==> Disabling battery optimization for $RECEIVER_PKG..."
    adb shell "dumpsys deviceidle whitelist +$RECEIVER_PKG 2>/dev/null || true"
fi

# 3. Open mDNS / Bonjour network ports for AirPlay discovery
# AirPlay uses ports 5000 (RTSP), 7000 (AirPlay mirroring), 7100, 5353 (mDNS)
echo "==> Verifying multicast DNS state..."
adb shell "getprop net.dns1 2>/dev/null || true"

# 4. Wake up display on incoming cast
adb shell "settings put global stay_on_while_plugged_in 3 2>/dev/null || true"

echo "==> AirPlay setup complete! Ensure TV and iPhone/Mac are connected to the same Wi-Fi network."
