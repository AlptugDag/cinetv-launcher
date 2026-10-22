#!/usr/bin/env bash
# TV Master Launcher — Restore TV core services
#
# Re-enables (or re-installs for user 0) the OEM TV-core packages that must never be removed.
# Safe to run any time; it is a no-op when everything is already healthy.
#
#   ./scripts/restore_tv_core.sh           # verbose
#   ./scripts/restore_tv_core.sh --quiet   # only print when something was repaired
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/tv_safety.sh
source "${SCRIPT_DIR}/lib/tv_safety.sh"

QUIET=0
[ "${1:-}" = "--quiet" ] && QUIET=1
say() { [ "$QUIET" = "1" ] || echo "$@"; }

DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')
if [ -z "$DEVICE" ]; then
    echo "ERROR: No ADB device connected."
    exit 1
fi

# Core packages per platform. Only packages that exist on the firmware are touched.
CORE_PACKAGES=(
    "com.mediatek.wwtv.tvcenter"        # MediaTek TV app (HDMI / picture / HDR / inputs)
    "com.mediatek.wwtv.tvcenter.rro"    # its resource overlay
    "com.mediatek.tv.factory"           # persistent factory command service
)

ALL_PKGS="$(adb shell "pm list packages -u" 2>/dev/null | tr -d '\r')"
INST_PKGS="$(adb shell "pm list packages" 2>/dev/null | tr -d '\r')"
DIS_PKGS="$(adb shell "pm list packages -d" 2>/dev/null | tr -d '\r')"

FIXED=0
for pkg in "${CORE_PACKAGES[@]}"; do
    printf '%s\n' "$ALL_PKGS" | grep -qxF "package:$pkg" || continue   # not part of this firmware

    if ! printf '%s\n' "$INST_PKGS" | grep -qxF "package:$pkg"; then
        echo "[!] $pkg was uninstalled for user 0 — restoring from system image..."
        adb shell "cmd package install-existing --user 0 $pkg" >/dev/null 2>&1 || true
        FIXED=$((FIXED + 1))
    fi
    if printf '%s\n' "$DIS_PKGS" | grep -qxF "package:$pkg"; then
        echo "[!] $pkg was disabled — re-enabling..."
        adb shell "pm enable --user 0 $pkg" >/dev/null 2>&1 || true
        FIXED=$((FIXED + 1))
    fi
done

if [ "$FIXED" -gt 0 ]; then
    echo "==> Restored $FIXED TV-core component(s). A reboot is recommended: adb reboot"
else
    say "==> TV core is healthy (nothing to restore)."
fi
exit 0
