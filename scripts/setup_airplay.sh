#!/usr/bin/env bash
# CineTV — AirPlay & Cast Integration Suite
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
# - io.github.jqssun.airplay (Open-Source Native AirPlay Receiver Daemon)
# - com.instantbits.cast.receiver (Web Video Caster Receiver — ultra-fast & lightweight)
# - com.ionitech.airscreen (AirScreen — AirPlay + Cast + Miracast + DLNA)
# - com.waxrain.airplaydmr2 (AirReceiver)
RECEIVER_PKG=""
for pkg in "io.github.jqssun.airplay" "com.instantbits.cast.receiver" "com.ionitech.airscreen" "com.waxrain.airplaydmr2"; do
    if adb shell "pm list packages" | grep -q "$pkg"; then
        RECEIVER_PKG="$pkg"
        echo "==> Found existing receiver app: $pkg"
        break
    fi
done

if [ -z "$RECEIVER_PKG" ]; then
    echo "==> Recommended: Install Web Video Caster Receiver or AirPlay daemon from release/ or Play Store."
fi

# 2. Grant battery optimization exemptions (keeps AirPlay daemon alive in standby)
for p in "io.github.jqssun.airplay" "com.instantbits.cast.receiver" "com.ionitech.airscreen"; do
    if adb shell "pm list packages" | grep -q "$p"; then
        echo "==> Disabling battery optimization for $p..."
        adb shell "dumpsys deviceidle whitelist +$p 2>/dev/null || true"
    fi
done

# 3. Ensure AirPlay service is running
if adb shell "pm list packages" | grep -q "io.github.jqssun.airplay"; then
    echo "==> Starting native AirPlay service..."
    adb shell "am start-foreground-service -n io.github.jqssun.airplay/.AirPlayService 2>/dev/null || am start -n io.github.jqssun.airplay/.MainActivity 2>/dev/null || true"
fi

# 4. Wake up display on incoming cast & keep network alive
adb shell "settings put global stay_on_while_plugged_in 3 2>/dev/null || true"

echo "==> AirPlay setup complete! Ensure TV and iPhone/Mac are connected to the same Wi-Fi network."
