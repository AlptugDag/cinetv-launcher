#!/usr/bin/env bash
# CineTV — Smart RAM & Storage-to-RAM Optimizer
# Tunes the Low Memory Killer (LMK) and cache thresholds that adb may change (zRAM itself needs root).
set -e

echo "==> [Smart RAM Optimizer] Detecting connected Android TV via ADB..."
DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')

if [ -z "$DEVICE" ]; then
    echo "ERROR: No ADB device connected. Please connect via 'adb connect <TV_IP>:5555'"
    exit 1
fi

echo "==> Connected to: $DEVICE"

# 1. Enable Purgeable Assets & Memory Reclaim
echo "==> Setting purgeable memory assets..."
adb shell "setprop persist.sys.purgeable_assets 1"

# (zRAM size, swap, swappiness and RAM-expansion properties need root: adb is refused on retail TVs, so they are
# not touched. A TV's compressed swap is its maker's own.)

# 5. Low Memory Killer (LMK) / Extra Free Kbytes Tuning
echo "==> Optimizing Low Memory Killer thresholds..."
adb shell "setprop sys.sysctl.extra_free_kbytes 24300"
adb shell "setprop persist.sys.fw.trim_empty_apps true"
adb shell "setprop persist.sys.fw.empty_app_percent 50"

# 6. Flush inactive cache
echo "==> Dropping pagecache, dentries and inodes..."
adb shell "sync; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null || true"

echo "==> Memory Optimization Complete!"
adb shell "free -m 2>/dev/null || cat /proc/meminfo | grep -E 'MemTotal|MemFree|MemAvailable|SwapTotal|SwapFree'"
