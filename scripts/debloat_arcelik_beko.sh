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

# Only what is bloat on every Arçelik-built set (Arçelik, Beko, Grundig; Android TV 11 and 14), as
# verified on real Grundig and Beko sets. The TV's own
# feature apps (voice assistant, Chromecast, AirScreen, YouTube Kids, Play Movies, the phone remotes)
# and every app the viewer installed stay.
PACKAGES_TO_REMOVE=(
    # Telemetry, analytics and install attribution
    "com.google.android.feedback"
    "com.android.tv.feedbackconsent"
    "com.google.android.partnersetup"
    "android.autoinstalls.config.arcelik.gx"
    "android.autoinstalls.config.arcelik.an"
    "com.android.adservices.api"
    "com.android.ondevicepersonalization.services"
    "com.android.federatedcompute.services"
    "tv.anoki.acr.controller"

    # Manufacturer ads and promo: ad-tile channels, hotel mode, help videos, first-setup leftovers
    "com.grundig.tv.channel"
    "com.beko.tv.channel"
    "com.cltv.fast"
    "com.arcelik.arserviceapp"
    "com.arcelik.tvcarehub"
    "com.google.android.syncadapters.calendar"
    "com.google.android.onetimeinitializer"
    "com.google.android.apps.nbu.smartconnect.tv"
    "com.android.printspooler"

    # The stock home and its feeds (only once CineTV is Home; the installer sets that first)
    "com.google.android.tvlauncher"
    "com.google.android.apps.tv.launcherx"
    "com.google.android.tvrecommendations"
    "com.google.android.backdrop"
)

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
echo "    [+] Remote keys, Input key, pairing, backlight and TV settings (Arçelik services)"
echo "    [+] The TV's own feature apps: voice assistant, Chromecast, AirScreen, Kids, Play Movies"
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
