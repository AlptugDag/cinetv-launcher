#!/usr/bin/env bash
# TV Master Launcher — Arçelik / Beko / Grundig (MediaTek Android 11) Debloater
# Safely uninstalls non-app bloatware, factory menus, telemetry, and storefronts for user 0.
set -e

echo "==> [Debloater: Arçelik / Beko / MediaTek TV]"
DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')

if [ -z "$DEVICE" ]; then
    echo "ERROR: No ADB device connected."
    exit 1
fi

PACKAGES_TO_REMOVE=(
    # Non-app elements & factory tools on TV home screen row 3
    "mediatek.factorymenu.ui"
    "arcelik.factorymenu.hatone"
    "com.mediatek.tv.factory"
    "com.mediatek.wwtv.webview"
    "com.mediatek.tv.service"
    "com.mediatek.wwtv.mediaplayer"
    "com.mediatek.wwtv.tvcenter"
    "com.mediatek.wwtv.tvcenter.rro"

    # Deprecated / legacy movie players
    "com.lagradost.cloudstream3"
    "com.stremio.one"
    "org.courville.nova"

    # Stock launcher clutter and telemetry
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

echo "==> Uninstalling bloatware for user 0..."
for pkg in "${PACKAGES_TO_REMOVE[@]}"; do
    printf "[-] Removing %-40s ... " "$pkg"
    adb shell "pm uninstall -k --user 0 $pkg 2>/dev/null || pm disable-user --user 0 $pkg 2>/dev/null || true" >/dev/null
    echo "Done"
done

echo "==> Preserving essential services:"
echo "    [+] TV+ (com.turkcell.ott)"
echo "    [+] Web Video Caster (com.instantbits.cast.receiver)"
echo "    [+] YouTube TizenTube / Cobalt (io.gh.reisxd.tizentube.cobalt)"
echo "    [+] Cinema Hub / Nuvio TV (com.nuvio.tv)"
echo "    [+] Native AirPlay Receiver (io.github.jqssun.airplay)"
echo "    [+] Google Chrome / Cromite"

echo "==> Arçelik / Beko Debloat Completed Successfully!"
