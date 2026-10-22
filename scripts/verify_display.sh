#!/usr/bin/env bash
# TV Master Launcher — Display / HDR / TV-core verification
#
#   ./scripts/verify_display.sh                 # quick report (no playback)
#   ./scripts/verify_display.sh --playback-test # also plays a real 4K HEVC HDR10 clip and measures it
#
# IMPORTANT — how to read the result on MediaTek (Arçelik/Beko/Grundig/Vestel/...) TVs:
#   The Android UI layer is rendered at 1920x1080@60 by design (see /vendor/etc/uhd_keeping_packages.xml);
#   Android only exposes that single display mode. Video is decoded at native resolution (up to 4K)
#   and shown on a separate hardware video plane that is scaled to the 4K panel, with HDR10/HLG
#   signalled through the video path. So "Display: 1080p60" in Nuvio / Developer options is NORMAL
#   on these TVs. The playback test below is the authoritative proof of 4K + HDR10.
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/tv_safety.sh
source "${SCRIPT_DIR}/lib/tv_safety.sh"

PLAYBACK_TEST=0
[ "${1:-}" = "--playback-test" ] && PLAYBACK_TEST=1

DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')
if [ -z "$DEVICE" ]; then
    echo "ERROR: No ADB device connected."
    exit 1
fi

ok()   { echo "  [OK]   $*"; }
warn() { echo "  [WARN] $*"; }
info() { echo "  [..]   $*"; }

echo "================================================================"
echo "  TV Master Launcher — Display / HDR verification"
echo "================================================================"

# --- 1. TV core health ---------------------------------------------------------------------
echo "==> 1. TV core services"
ALL_PKGS="$(adb shell "pm list packages -u" 2>/dev/null | tr -d '\r')"
DIS_PKGS="$(adb shell "pm list packages -d" 2>/dev/null | tr -d '\r')"
INST_PKGS="$(adb shell "pm list packages" 2>/dev/null | tr -d '\r')"
CORE_BAD=0
for pkg in com.mediatek.wwtv.tvcenter com.mediatek.wwtv.tvcenter.rro com.mediatek.tv.factory; do
    printf '%s\n' "$ALL_PKGS" | grep -qxF "package:$pkg" || continue
    if ! printf '%s\n' "$INST_PKGS" | grep -qxF "package:$pkg"; then
        warn "$pkg is uninstalled for user 0"; CORE_BAD=1
    elif printf '%s\n' "$DIS_PKGS" | grep -qxF "package:$pkg"; then
        warn "$pkg is DISABLED"; CORE_BAD=1
    else
        ok "$pkg enabled"
    fi
done
[ "$CORE_BAD" = "1" ] && warn "Fix: ./scripts/restore_tv_core.sh   (then: adb reboot)"

# --- 2. Android display layer --------------------------------------------------------------
echo "==> 2. Android display layer"
DISP="$(adb shell "dumpsys display" 2>/dev/null | tr -d '\r' | grep -m1 'DisplayDeviceInfo')"
PANEL="$(adb shell getprop sys.display-size 2>/dev/null | tr -d '\r')"
info "UI/compositor mode : $(echo "$DISP" | grep -oE 'supportedModes \[[^]]*\]')"
info "Panel size (sys.display-size): ${PANEL:-unknown}"
HDR_TYPES="$(echo "$DISP" | grep -oE 'mSupportedHdrTypes=\[[0-9, ]*\]')"
info "HDR types reported : ${HDR_TYPES:-none}  (1=Dolby Vision, 2=HDR10, 3=HLG, 4=HDR10+)"
echo "$HDR_TYPES" | grep -qE '\b2\b' && ok "HDR10 advertised to apps" || warn "HDR10 NOT advertised to apps"
[ "$PANEL" = "3840x2160" ] && ok "Panel is 4K UHD"

# --- 3. User-visible settings that can disable HDR / AFR -----------------------------------
echo "==> 3. HDR / frame-rate settings"
UDH="$(adb shell settings get global user_disabled_hdr_formats 2>/dev/null | tr -d '\r')"
if [ -n "$UDH" ] && [ "$UDH" != "null" ]; then
    warn "user_disabled_hdr_formats=$UDH (some HDR formats are disabled by the user)"
else
    ok "no HDR format disabled by user"
fi
info "match_content_frame_rate = $(adb shell settings get secure match_content_frame_rate 2>/dev/null | tr -d '\r')  (0=never 1=seamless 2=always)"
info "hdr_conversion_mode      = $(adb shell settings get global hdr_conversion_mode 2>/dev/null | tr -d '\r')  (1=passthrough)"
OVR="$(adb shell wm size 2>/dev/null | tr -d '\r' | grep -i override)"
[ -n "$OVR" ] && warn "wm size override active: $OVR  (fix: adb shell wm size reset)" || ok "no wm size override"
DOVR="$(adb shell wm density 2>/dev/null | tr -d '\r' | grep -i override)"
[ -n "$DOVR" ] && warn "wm density override active: $DOVR  (fix: adb shell wm density reset)" || ok "no wm density override"

# --- 4. Real playback test -----------------------------------------------------------------
if [ "$PLAYBACK_TEST" = "1" ]; then
    echo "==> 4. Playback test (4K HEVC HDR10, Jellyfin test clip)"
    URL='https://repo.jellyfin.org/test-videos/HDR/HDR10/HEVC/Test%20Jellyfin%204K%20HEVC%20HDR10%2040M.mp4'
    PLAYER=""
    for cand in org.courville.nova/com.archos.mediacenter.video.player.PlayerActivity; do
        pkg="${cand%%/*}"
        printf '%s\n' "$INST_PKGS" | grep -qxF "package:$pkg" && PLAYER="$cand" && break
    done
    if [ -z "$PLAYER" ]; then
        warn "No supported test player installed (Nova Video Player). Run scripts/setup_nova_player.sh first."
    else
        adb shell "am start -a android.intent.action.VIEW -d '$URL' -t video/mp4 -n $PLAYER" >/dev/null 2>&1
        sleep 25
        SF="$(adb shell dumpsys SurfaceFlinger 2>/dev/null | tr -d '\r')"
        if echo "$SF" | grep -qE 'sourceCrop=\[0\.0+ 0\.0+ 3840\.0+ 2160\.0+\]'; then
            ok "video layer is 3840x2160 (native 4K decode)"
        else
            warn "no 3840x2160 video layer seen (network too slow, or player failed)"
        fi
        if echo "$SF" | grep -qE 'dataspace=BT2020_ITU_PQ'; then
            ok "dataspace BT2020_ITU_PQ  => HDR10 signalled to the panel"
        else
            warn "HDR10 dataspace (BT2020_ITU_PQ) not seen"
        fi
        adb shell "input keyevent KEYCODE_BACK; sleep 1; am force-stop org.courville.nova; am start -n com.gathin.tvhome/.MainActivity" >/dev/null 2>&1
    fi
else
    info "Tip: run with --playback-test to prove 4K + HDR10 with a real clip."
fi

echo "================================================================"
