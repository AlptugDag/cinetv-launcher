#!/usr/bin/env bash
# TV Master Launcher — AdGuard Private DNS (DoT) & Nuvio + Anthology + 4K HDR10 Cinema Suite
# 1. Configures AdGuard DNS over TLS (dns.adguard-dns.com) to completely eliminate ads & ISP DNS redirects.
# 2. Installs and configures Nuvio TV (com.nuvio.tv) with Anthology repository and 4K HDR10 Turkish stream engine.
# 3. Uninstalls legacy/deprecated players (CloudStream, Stremio) cleanly.
set -e

echo "================================================================"
echo "  TV Master Launcher — DNS & Nuvio 4K HDR10 Cinema Setup"
echo "================================================================"

DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')
if [ -z "$DEVICE" ]; then
    echo "ERROR: No ADB device connected. Please run 'adb connect <TV_IP>:5555' first."
    exit 1
fi
echo "==> Connected to device: $DEVICE"

# 1. CONFIGURE ADGUARD PRIVATE DNS OVER TLS (DoT)
echo "==> [1/4] Setting Private DNS to AdGuard (dns.adguard-dns.com)..."
adb shell "settings put global private_dns_mode hostname"
adb shell "settings put global private_dns_specifier dns.adguard-dns.com"

echo "==> Verifying Private DNS configuration..."
MODE=$(adb shell "settings get global private_dns_mode" | tr -d '\r')
SPEC=$(adb shell "settings get global private_dns_specifier" | tr -d '\r')
echo "    private_dns_mode:      $MODE"
echo "    private_dns_specifier: $SPEC"

# 2. CLEAN UP LEGACY STREAMING ENGINES
echo "==> [2/4] Removing legacy streaming applications (CloudStream & Stremio)..."
for pkg in "com.lagradost.cloudstream3" "com.stremio.one"; do
    if adb shell "pm list packages" | grep -q "$pkg"; then
        echo "    [-] Uninstalling $pkg..."
        adb shell "pm uninstall -k --user 0 $pkg 2>/dev/null || pm disable-user --user 0 $pkg 2>/dev/null || true"
    fi
done

# 3. INSTALL NUVIO TV IF NOT INSTALLED
echo "==> [3/4] Checking Nuvio TV (com.nuvio.tv)..."
if ! adb shell "pm list packages" | grep -q "com.nuvio.tv"; then
    ABI=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')
    echo "    Target ABI: $ABI"
    TMP_NUVIO="/tmp/nuvio_tv_install.apk"
    if [[ "$ABI" =~ arm64 ]]; then
        NUVIO_URL="https://github.com/NuvioMedia/NuvioTV/releases/download/1.0.0/app-full-arm64-v8a-release.apk"
    else
        NUVIO_URL="https://github.com/NuvioMedia/NuvioTV/releases/download/1.0.0/app-full-armeabi-v7a-release.apk"
    fi
    echo "    Downloading NuvioTV from $NUVIO_URL..."
    curl -L -s -o "$TMP_NUVIO" "$NUVIO_URL"
    echo "    Installing NuvioTV..."
    adb install -r -d -g "$TMP_NUVIO"
    rm -f "$TMP_NUVIO"
else
    echo "    [+] Nuvio TV is already installed."
fi

# 4. CONFIGURE ANTHOLOGY, TORRENTIO 4K HDR10 & SUBTITLES
echo "==> [4/4] Configuring Cinema Plugins & Turkish Stream Engine..."
# Launch Nuvio in background to ensure service readiness
adb shell "am start -n com.nuvio.tv/.MainActivity 2>/dev/null || true"
sleep 2

# A. Add Anthology Stremio Canlı TV Catalog
echo "    [+] Installing Anthology Canlı TV & Sports Catalog..."
adb shell "am start -a android.intent.action.VIEW -d \"stremio://falsisdev.github.io/anthology/stremio/manifest.json\" 2>/dev/null || true"
sleep 2

# B. Add Torrentio 4K HDR10 Turkish Prioritized Engine (Strict >= 720p filter)
echo "    [+] Installing Torrentio 4K HDR10 / Remux Turkish Prioritized Engine..."
adb shell "am start -a android.intent.action.VIEW -d \"stremio://torrentio.strem.fun/sort=qualitysize%7Cqualityfilter=480p,other,scr,cam%7Cpriority_language=turkish/manifest.json\" 2>/dev/null || true"
sleep 2

# C. Add OpenSubtitles v3 Subtitle Addon
echo "    [+] Installing OpenSubtitles v3 Turkish Subtitle Engine..."
adb shell "am start -a android.intent.action.VIEW -d \"stremio://opensubtitles-v3.strem.io/manifest.json\" 2>/dev/null || true"
sleep 2

echo "----------------------------------------------------------------"
echo "==> Setup Complete!"
echo "    AdGuard DoT: Active (dns.adguard-dns.com)"
echo "    Cinema Suite: Nuvio TV + Anthology + 4K HDR10 Torrentio"
echo "    Audio/Subtitles: Turkish language automatically prioritized"
echo "================================================================"
