#!/usr/bin/env bash
# CineTV — Smart RAM & Storage-to-RAM Optimizer
# Optimizes zRAM, Low Memory Killer (LMK), and cache thresholds for smooth 4K TV operation.
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

# 2. Expand virtual RAM / zRAM swap space
echo "==> Configuring zRAM swap compression (lz4)..."
adb shell "echo 1 > /sys/block/zram0/reset 2>/dev/null || true"
adb shell "echo lz4 > /sys/block/zram0/comp_algorithm 2>/dev/null || true"
# 1024MB zRAM swap disk size
adb shell "echo 1073741824 > /sys/block/zram0/disksize 2>/dev/null || true"
adb shell "mkswap /dev/block/zram0 2>/dev/null || true"
adb shell "swapon /dev/block/zram0 2>/dev/null || true"

# 3. Optimize Swappiness (aggressive memory swapping to keep UI responsive)
echo "==> Tuning swappiness to 80..."
adb shell "sysctl -w vm.swappiness=80 2>/dev/null || true"
adb shell "sysctl -w vm.vfs_cache_pressure=100 2>/dev/null || true"

# 4. Storage-to-RAM expansion hint on supported Android 11+ vendors
echo "==> Enabling RAM expansion features..."
adb shell "settings put global ram_expand_size_list 2048 2>/dev/null || true"
adb shell "setprop persist.sys.ram_expand.size 2048 2>/dev/null || true"

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
