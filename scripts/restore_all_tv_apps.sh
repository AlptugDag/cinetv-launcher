#!/usr/bin/env bash
# ==============================================================================
# CineTV Launcher — Complete Factory Package & OEM Launcher Restoration Engine
# Re-installs and re-enables all OEM stock apps, launchers, and services for user 0.
# 100% reversible, non-destructive safety tool.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "================================================================================"
echo "    CineTV Launcher — Full Factory Package Restoration Engine"
echo "================================================================================"

DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')
if [ -z "$DEVICE" ]; then
    echo "ERROR: No ADB device connected. Connect via 'adb connect <TV_IP>:5555'"
    exit 1
fi
echo "[+] Target Television: $DEVICE"

# First undo exactly what the CineTV debloater recorded on this TV (newest first).
JOURNAL="$(adb shell cat /sdcard/cinetv-debloat.txt 2>/dev/null | tr -d '\r' || true)"
if [ -n "$JOURNAL" ]; then
    echo "[+] Undoing the debloat journal"
    reverse() { if command -v tac >/dev/null 2>&1; then tac; else tail -r; fi; }
    printf '%s\n' "$JOURNAL" | reverse | while read -r action pkg; do
        case "$action" in
            disabled) adb shell "pm enable $pkg" >/dev/null 2>&1 && echo "    enabled $pkg" ;;
            uninstalled) adb shell "cmd package install-existing $pkg" >/dev/null 2>&1 && echo "    restored $pkg" ;;
        esac
    done
    adb shell rm -f /sdcard/cinetv-debloat.txt >/dev/null 2>&1 || true
fi

RESTORE_CANDIDATES=(
    # Google Stock TV Launchers & Storefront
    "com.google.android.tvlauncher"
    "com.google.android.apps.tv.launcherx"
    "com.google.android.tungsten.setupwraith"
    "com.google.android.feedback"
    "com.google.android.play.games"
    "com.google.android.videos"
    "com.android.providers.partnerbookmarks"
    "com.android.bookmarkprovider"
    # MediaTek / Arçelik / Beko OEM TV
    "com.mediatek.wwtv.tvcenter"
    "com.mediatek.wwtv.tvcenter.rro"
    "com.mediatek.tv.factory"
    "tr.com.bein.connect.tv"
    "io.freemytv"
    "com.arcelik.ChannelEditor"
    "com.arcelik.tv.rctest"
    # TCL OEM
    "com.tcl.useragreement"
    "com.tcl.waterfall"
    "com.tcl.messagebox"
    "com.tcl.bi"
    "com.tcl.tvmanager"
    # Xiaomi OEM
    "com.xiaomi.mitv.upgrade"
    "com.xiaomi.mitv.advertise"
    "com.mitv.tvhome"
)

echo "[+] Scanning uninstalled and disabled OEM packages on TV..."
ALL_SYS_PKGS=$(adb shell "pm list packages -u" 2>/dev/null | tr -d '\r')
DISABLED_PKGS=$(adb shell "pm list packages -d" 2>/dev/null | tr -d '\r')
ACTIVE_PKGS=$(adb shell "pm list packages" 2>/dev/null | tr -d '\r')

RESTORED_COUNT=0

for pkg in "${RESTORE_CANDIDATES[@]}"; do
    # Check if package exists in the system partition
    if printf '%s\n' "$ALL_SYS_PKGS" | grep -qxF "package:$pkg"; then
        # If uninstalled for user 0, restore it
        if ! printf '%s\n' "$ACTIVE_PKGS" | grep -qxF "package:$pkg"; then
            echo "  [+] Reinstalling $pkg from system image..."
            adb shell "cmd package install-existing --user 0 $pkg" >/dev/null 2>&1 || true
            RESTORED_COUNT=$((RESTORED_COUNT + 1))
        fi
        # If disabled, enable it
        if printf '%s\n' "$DISABLED_PKGS" | grep -qxF "package:$pkg"; then
            echo "  [+] Re-enabling $pkg..."
            adb shell "pm enable --user 0 $pkg" >/dev/null 2>&1 || true
            RESTORED_COUNT=$((RESTORED_COUNT + 1))
        fi
    fi
done

echo "================================================================================"
if [ "$RESTORED_COUNT" -gt 0 ]; then
    echo "  [SUCCESS] Successfully restored $RESTORED_COUNT factory package(s)."
    echo "  Recommendation: Run 'adb reboot' if you wish to switch back to stock firmware."
else
    echo "  [INFO] All OEM packages are already fully installed and active."
fi
echo "================================================================================"
