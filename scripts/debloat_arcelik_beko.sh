#!/usr/bin/env bash
# CineTV — Arçelik / Beko / Grundig (MediaTek Android 11) Debloater
# Safely removes non-app bloatware, telemetry, and storefronts for user 0.
#
# SAFETY: the MediaTek TV core (com.mediatek.wwtv.tvcenter + its .rro overlay, com.mediatek.tv.*,
# the factory command service, ...) is intentionally NOT in the removal list and is additionally
# blocked by scripts/lib/tv_safety.sh. Removing it breaks the TV pipeline (tuner, HDMI inputs,
# picture/HDR settings, display configuration).
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/tv_safety.sh
source "${SCRIPT_DIR}/lib/tv_safety.sh"

echo "==> [Debloater: Arçelik / Beko / MediaTek TV]"
DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')

if [ -z "$DEVICE" ]; then
    echo "ERROR: No ADB device connected."
    exit 1
fi

PACKAGES_TO_REMOVE=(
    # Deprecated / legacy movie players
    "com.lagradost.cloudstream3"
    "com.stremio.one"

    # Stock launcher clutter and telemetry (replaced by CineTV)
    "com.google.android.tvlauncher"
    "com.google.android.apps.tv.launcherx"
    "com.google.android.tungsten.setupwraith"
    "com.google.android.feedback"
    "com.google.android.play.games"

    # Operator / Manufacturer bloatware
    "tr.com.bein.connect.tv"
    "io.freemytv"
    "com.cybercat.adbappcontrol.tv"
    "com.arcelik.ChannelEditor"
    "com.arcelik.tv.rctest"
)

# NOTE (deliberately kept — do NOT add back):
#   com.mediatek.wwtv.tvcenter / .tvcenter.rro  -> core MediaTek TV app + overlay
#   com.mediatek.tv.factory                     -> persistent factory command service
#   com.mediatek.wwtv.webview / .mediaplayer    -> vendor WebView & stock USB media player
#   mediatek.factorymenu.ui / arcelik.factorymenu.hatone
#                                               -> already hidden by the firmware
#                                                  (/vendor/etc/hidden-applications-configs.xml)

refresh_pkg_cache

echo "==> Removing bloatware for user 0..."
REMOVED=0
for pkg in "${PACKAGES_TO_REMOVE[@]}"; do
    printf "[-] Removing %-40s ... " "$pkg"
    if safe_remove_pkg "$pkg"; then
        echo "Done"
        REMOVED=$((REMOVED + 1))
    else
        echo "Skipped (not installed / protected)"
    fi
done

echo "==> Preserving essential services:"
echo "    [+] MediaTek TV core (com.mediatek.wwtv.tvcenter) — required for HDMI / picture / HDR"
echo "    [+] TV+ (com.turkcell.ott)"
echo "    [+] Nova Video Player (org.courville.nova) — CineTV casting and Nuvio external player"
echo "    [+] YouTube TizenTube / Cobalt (io.gh.reisxd.tizentube.cobalt)"
echo "    [+] Cinema Hub / Nuvio TV (com.nuvio.tv)"
echo "    [+] Native AirPlay Receiver (io.github.jqssun.airplay)"
echo "    [+] Google Chrome / Cromite"

# Self-heal: if an earlier (buggy) version of this script — or any other tool — disabled the TV
# core, bring it back automatically.
bash "${SCRIPT_DIR}/restore_tv_core.sh" --quiet || true

echo "==> Arçelik / Beko Debloat Completed Successfully! (${REMOVED} package(s) removed)"
