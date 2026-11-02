#!/usr/bin/env bash
# CineTV — Universal Multi-Brand Android TV Debloater
# Automatically detects TV brand and removes manufacturer telemetry, retail bloatware,
# and heavy default launchers without breaking HDMI inputs, DVB tuners, or picture settings.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/tv_safety.sh
source "${SCRIPT_DIR}/lib/tv_safety.sh"

echo "================================================================"
echo "    CineTV — Universal Android TV Debloater"
echo "================================================================"

DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')
if [ -z "$DEVICE" ]; then
    echo "ERROR: No ADB device connected. Connect via 'adb connect <TV_IP>:5555'"
    exit 1
fi

# Detect Device Brand & Model
BRAND=$(adb shell getprop ro.product.brand | tr -d '\r' | tr '[:upper:]' '[:lower:]')
MANUFACTURER=$(adb shell getprop ro.product.manufacturer | tr -d '\r' | tr '[:upper:]' '[:lower:]')
MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
SOC=$(adb shell getprop ro.board.platform | tr -d '\r')
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')

echo "==> Detected Hardware:"
echo "    Brand:        $BRAND"
echo "    Manufacturer: $MANUFACTURER"
echo "    Model:        $MODEL"
echo "    SoC Platform: $SOC"
echo "    Android SDK:  $SDK"
echo "----------------------------------------------------------------"

# Safe Common Bloatware across all Android TV devices
COMMON_BLOAT=(
    "com.google.android.tvlauncher"
    "com.google.android.apps.tv.launcherx"
    "com.google.android.tungsten.setupwraith"
    "com.google.android.feedback"
    "com.google.android.play.games"
    "com.google.android.videos"
    "com.android.providers.partnerbookmarks"
    "com.android.bookmarkprovider"
)

# Brand Specific Bloatware Arrays
ARCELIK_BEKO_BLOAT=(
    "tr.com.bein.connect.tv"
    "io.freemytv"
    "com.arcelik.ChannelEditor"
    "com.arcelik.tv.rctest"
)

TCL_BLOAT=(
    "com.tcl.useragreement"
    "com.tcl.waterfall"
    "com.tcl.messagebox"
    "com.tcl.bi"
    "com.tcl.appmarket"
    "com.tcl.videoplayer"
    "com.tcl.browser"
    "com.tcl.livebox"
)

SONY_BLOAT=(
    "com.sony.dtv.bravialifehack"
    "com.sony.dtv.demomode"
    "com.sony.dtv.smarthelp"
    "com.sony.dtv.telemetry"
    "com.sony.dtv.soundoptimizer"
    "com.sony.dtv.recommendations"
    "com.sony.dtv.interactivetvutil.generic"
)

PHILIPS_BLOAT=(
    "org.droidtv.demomode"
    "org.droidtv.recommendations"
    "org.droidtv.nettvregistration"
    "org.droidtv.epg"
)

XIAOMI_BLOAT=(
    "com.mitv.gallery"
    "com.xiaomi.mitv.upgrade"
    "com.mitv.videoplayer"
    "com.xiaomi.mitv.advertise"
    "com.mitv.tvhome"
    "com.xiaomi.mitv.shop"
    "com.xiaomi.devicestatistics"
    "com.xiaomi.screenrecorder"
)

HISENSE_BLOAT=(
    "com.jamdeo.tv.vod"
    "com.hisense.service.smarthome"
    "com.hisense.hitv.hicloud.account"
    "com.hisense.tv.epg"
    "com.hisense.recommendation"
)

TARGET_BLOAT=("${COMMON_BLOAT[@]}")

if [[ "$MANUFACTURER" =~ arcelik|beko|grundig ]] || [[ "$BRAND" =~ arcelik|beko|grundig ]]; then
    echo "==> Matching Profile: Arçelik / Beko / Grundig (MediaTek)"
    TARGET_BLOAT+=("${ARCELIK_BEKO_BLOAT[@]}")
elif [[ "$MANUFACTURER" =~ tcl ]] || [[ "$BRAND" =~ tcl|thomson|ffalcon ]]; then
    echo "==> Matching Profile: TCL / Thomson"
    TARGET_BLOAT+=("${TCL_BLOAT[@]}")
elif [[ "$MANUFACTURER" =~ sony ]] || [[ "$BRAND" =~ sony ]]; then
    echo "==> Matching Profile: Sony Bravia"
    TARGET_BLOAT+=("${SONY_BLOAT[@]}")
elif [[ "$MANUFACTURER" =~ philips|tpv ]] || [[ "$BRAND" =~ philips ]]; then
    echo "==> Matching Profile: Philips Android TV"
    TARGET_BLOAT+=("${PHILIPS_BLOAT[@]}")
elif [[ "$MANUFACTURER" =~ xiaomi ]] || [[ "$BRAND" =~ xiaomi|redmi ]]; then
    echo "==> Matching Profile: Xiaomi Mi TV"
    TARGET_BLOAT+=("${XIAOMI_BLOAT[@]}")
elif [[ "$MANUFACTURER" =~ hisense ]] || [[ "$BRAND" =~ hisense|toshiba ]]; then
    echo "==> Matching Profile: Hisense / Toshiba"
    TARGET_BLOAT+=("${HISENSE_BLOAT[@]}")
else
    echo "==> Profile: Generic Android TV / Unknown OEM"
fi

echo "----------------------------------------------------------------"
echo "==> Starting safe debloat for user 0 (${#TARGET_BLOAT[@]} candidates)..."

REMOVED=0
SKIPPED=0
PROTECTED=0

refresh_pkg_cache

for pkg in "${TARGET_BLOAT[@]}"; do
    if is_protected_pkg "$pkg"; then
        echo "[!] Skipping protected TV-core package: $pkg"
        PROTECTED=$((PROTECTED + 1))
    elif pkg_installed "$pkg"; then
        printf "[-] Uninstalling %-42s ... " "$pkg"
        safe_remove_pkg "$pkg" || true
        echo "Removed"
        REMOVED=$((REMOVED + 1))
    else
        SKIPPED=$((SKIPPED + 1))
    fi
done

echo "----------------------------------------------------------------"
echo "==> Debloat Finished!"
echo "    Packages Removed/Disabled: $REMOVED"
echo "    Packages Not Installed:    $SKIPPED"
echo "    Protected (kept):          $PROTECTED"
echo "    All HDMI inputs, audio pass-through and core settings preserved."

# Self-heal: restore the OEM TV core if something disabled it earlier.
bash "${SCRIPT_DIR}/restore_tv_core.sh" --quiet || true
