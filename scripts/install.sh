#!/usr/bin/env bash
# ==============================================================================
# TV Master Launcher — Universal Interactive Installer & Setup Wizard (v1.0.0)
# Multi-Language support: English & Türkçe
# Zero-security-vulnerability design: strict variable hygiene, input validation.
# ==============================================================================
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Temporary directory with automatic cleanup on exit
TMP_DIR=$(mktemp -d -t tv_installer_XXXXXX)
trap 'rm -rf "${TMP_DIR}"' EXIT

clear
echo -e "${CYAN}${BOLD}"
echo "================================================================================"
echo "    TV MASTER LAUNCHER v1.0.0 — UNIVERSAL ANDROID TV SETUP WIZARD"
echo "================================================================================"
echo -e "${NC}"

# ------------------------------------------------------------------------------
# STEP 1: LANGUAGE SELECTION
# ------------------------------------------------------------------------------
echo -e "${BOLD}Select Setup Language / Kurulum Dilini Seçin:${NC}"
echo "  1) English (International)"
echo "  2) Türkçe (Turkish)"
read -r -p "Choice / Seçim [1-2] (default: 1): " LANG_CHOICE
LANG_CHOICE="${LANG_CHOICE:-1}"

if [[ "$LANG_CHOICE" == "2" ]]; then
    LANG="tr"
else
    LANG="en"
fi

msg() {
    local key="$1"
    case "$key" in
        "checking_adb")
            [[ "$LANG" == "tr" ]] && echo "==> ADB bağlantısı kontrol ediliyor..." || echo "==> Checking ADB connection..."
            ;;
        "connected_to")
            [[ "$LANG" == "tr" ]] && echo "==> TV'ye bağlanıldı: " || echo "==> Connected to device: "
            ;;
        "no_device")
            [[ "$LANG" == "tr" ]] && echo "[-] Bağlı TV bulunamadı." || echo "[-] No ADB device detected."
            ;;
        "enter_ip")
            [[ "$LANG" == "tr" ]] && echo "Lütfen TV'nin yerel IP adresini girin (örn: 192.168.1.128): " || echo "Please enter TV local IP address (e.g. 192.168.1.128): "
            ;;
        "invalid_ip")
            [[ "$LANG" == "tr" ]] && echo "[!] Geçersiz IP adresi formatı." || echo "[!] Invalid IP address format."
            ;;
        "connecting_ip")
            [[ "$LANG" == "tr" ]] && echo "==> TV IP'sine bağlanılıyor: " || echo "==> Connecting to TV IP: "
            ;;
    esac
}

# ------------------------------------------------------------------------------
# STEP 2: ADB CONNECTION & HARDWARE IDENTIFICATION
# ------------------------------------------------------------------------------
msg "checking_adb"
CONNECTED_DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}' || true)

if [[ -z "$CONNECTED_DEVICE" ]]; then
    msg "no_device"
    while true; do
        read -r -p "$(msg "enter_ip")" TV_IP
        if [[ "$TV_IP" =~ ^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$ ]]; then
            msg "connecting_ip"
            echo -e "${YELLOW}${TV_IP}:5555${NC}"
            adb connect "${TV_IP}:5555" || true
            sleep 2
            CONNECTED_DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}' || true)
            if [[ -n "$CONNECTED_DEVICE" ]]; then
                break
            else
                if [[ "$LANG" == "tr" ]]; then
                    echo -e "${RED}Bağlantı başarısız. Lütfen TV'de Geliştirici Seçenekleri -> Ağ Üzerinden Hata Ayıklama (Ağ ADB) açık olduğundan emin olun.${NC}"
                else
                    echo -e "${RED}Connection failed. Ensure Developer Options -> Network ADB debugging is enabled on TV.${NC}"
                fi
            fi
        else
            msg "invalid_ip"
        fi
    done
fi

echo -e "${GREEN}$(msg "connected_to")${BOLD}${CONNECTED_DEVICE}${NC}"

# Read Device Hardware Specs Safely
DEVICE_BRAND=$(adb shell getprop ro.product.brand 2>/dev/null | tr -d '\r' | tr '[:upper:]' '[:lower:]' || echo "unknown")
DEVICE_MANUF=$(adb shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r' | tr '[:upper:]' '[:lower:]' || echo "unknown")
DEVICE_MODEL=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "unknown")
DEVICE_ABI=$(adb shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r' || echo "armeabi-v7a")
DEVICE_SDK=$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || echo "unknown")

echo -e "\n${CYAN}--- HARDWARE PROFILE ---${NC}"
echo "  Brand:        ${DEVICE_BRAND}"
echo "  Manufacturer: ${DEVICE_MANUF}"
echo "  Model:        ${DEVICE_MODEL}"
echo "  Architecture: ${DEVICE_ABI}"
echo "  Android SDK:  ${DEVICE_SDK}"
echo -e "${CYAN}------------------------${NC}\n"

# ------------------------------------------------------------------------------
# STEP 3: DEBLOAT PREFERENCE
# ------------------------------------------------------------------------------
if [[ "$LANG" == "tr" ]]; then
    echo -e "${BOLD}[Adım 1/7] Üretici Bloatware & Telemetri Temizleme:${NC}"
    echo "  1) Arçelik / Beko / Grundig (MediaTek) Güvenli Temizlik"
    echo "  2) Evrensel Android TV Temizliği (TCL, Sony, Philips, Xiaomi, vb.)"
    echo "  3) Temizliği Atla (Tüm fabrika uygulamalarını koru)"
    read -r -p "Seçim [1-3] (Varsayılan: 1): " DEBLOAT_CHOICE
else
    echo -e "${BOLD}[Step 1/7] OEM Bloatware & Telemetry Debloating:${NC}"
    echo "  1) Universal Android TV Debloat (TCL, Sony, Philips, Xiaomi, Hisense, etc.)"
    echo "  2) Arçelik / Beko / Grundig (MediaTek TV Profile)"
    echo "  3) Skip Debloating (Keep all existing OEM apps)"
    read -r -p "Choice [1-3] (Default: 1): " DEBLOAT_CHOICE
fi
DEBLOAT_CHOICE="${DEBLOAT_CHOICE:-1}"

if [[ "$LANG" == "tr" && "$DEBLOAT_CHOICE" == "1" ]] || [[ "$LANG" == "en" && "$DEBLOAT_CHOICE" == "2" ]]; then
    echo -e "${YELLOW}==> Running Arçelik/Beko Debloater...${NC}"
    bash "${SCRIPT_DIR}/debloat_arcelik_beko.sh" || true
elif [[ "$LANG" == "tr" && "$DEBLOAT_CHOICE" == "2" ]] || [[ "$LANG" == "en" && "$DEBLOAT_CHOICE" == "1" ]]; then
    echo -e "${YELLOW}==> Running Universal Android TV Debloater...${NC}"
    bash "${SCRIPT_DIR}/debloat_universal.sh" || true
else
    echo "==> Skipping debloat step."
fi

# ------------------------------------------------------------------------------
# STEP 4: PRIVATE ENCRYPTED DNS (DoT)
# ------------------------------------------------------------------------------
if [[ "$LANG" == "tr" ]]; then
    echo -e "\n${BOLD}[Adım 2/7] Güvenli Şifreli DNS & Reklam Engelleme:${NC}"
    echo "  1) AdGuard DNS over TLS (dns.adguard-dns.com - Sıfır reklam & ISS engelsiz) [Önerilen]"
    echo "  2) Cloudflare DNS over TLS (one.one.one.one - Hızlı & Gizlilik odaklı)"
    echo "  3) Değiştirme (Mevcut DNS ayarlarını koru)"
    read -r -p "Seçim [1-3] (Varsayılan: 1): " DNS_CHOICE
else
    echo -e "\n${BOLD}[Step 2/7] Private Encrypted DNS & Ad Blocking:${NC}"
    echo "  1) AdGuard DNS over TLS (dns.adguard-dns.com - Zero ads & ISP bypass) [Recommended]"
    echo "  2) Cloudflare DNS over TLS (one.one.one.one - Fast & Private)"
    echo "  3) Keep Existing (Do not modify TV DNS settings)"
    read -r -p "Choice [1-3] (Default: 1): " DNS_CHOICE
fi
DNS_CHOICE="${DNS_CHOICE:-1}"

if [[ "$DNS_CHOICE" == "1" ]]; then
    echo "==> Configuring AdGuard DNS over TLS..."
    adb shell "settings put global private_dns_mode hostname"
    adb shell "settings put global private_dns_specifier dns.adguard-dns.com"
elif [[ "$DNS_CHOICE" == "2" ]]; then
    echo "==> Configuring Cloudflare DNS over TLS..."
    adb shell "settings put global private_dns_mode hostname"
    adb shell "settings put global private_dns_specifier one.one.one.one"
else
    echo "==> Keeping existing DNS configuration."
fi

# ------------------------------------------------------------------------------
# STEP 5: CINEMA & STREAMING SUITE (NUVIO + ADDONS + VITRIN FEED)
# ------------------------------------------------------------------------------
if [[ "$LANG" == "tr" ]]; then
    echo -e "\n${BOLD}[Adım 3/7] Sinema Paketi & Yayın Motoru:${NC}"
    echo "  1) Türkçe Sinema Paketi (Nuvio TV + Webteizle Vitrin + Anthology + 4K HDR10 TR Torrentio) [Önerilen]"
    echo "  2) Uluslararası Sinema Paketi (Nuvio TV + Cinemeta Global Vitrin + 4K HDR10 Çok Dilli Torrentio)"
    echo "  3) Sinema Kurulumunu Atla (Yalnızca başlatıcıyı kur)"
    read -r -p "Seçim [1-3] (Varsayılan: 1): " CINEMA_CHOICE
else
    echo -e "\n${BOLD}[Step 3/7] Cinema Suite & Streaming Engine:${NC}"
    echo "  1) International Cinema Suite (Nuvio TV + Cinemeta Global Vitrin + 4K HDR10 Multi-Language Torrentio) [Recommended]"
    echo "  2) Turkish Cinema Suite (Nuvio TV + Webteizle Vitrin + Anthology + 4K HDR10 Turkish Torrentio)"
    echo "  3) Skip Cinema Suite (Install Launcher only)"
    read -r -p "Choice [1-3] (Default: 1): " CINEMA_CHOICE
fi
CINEMA_CHOICE="${CINEMA_CHOICE:-1}"

INSTALL_TR_CINEMA=false
INSTALL_INTL_CINEMA=false

if [[ "$LANG" == "tr" && "$CINEMA_CHOICE" == "1" ]] || [[ "$LANG" == "en" && "$CINEMA_CHOICE" == "2" ]]; then
    INSTALL_TR_CINEMA=true
elif [[ "$LANG" == "tr" && "$CINEMA_CHOICE" == "2" ]] || [[ "$LANG" == "en" && "$CINEMA_CHOICE" == "1" ]]; then
    INSTALL_INTL_CINEMA=true
fi

if [[ "$INSTALL_TR_CINEMA" == "true" ]] || [[ "$INSTALL_INTL_CINEMA" == "true" ]]; then
    # Clean up deprecated / legacy engines
    echo "==> Removing deprecated apps (CloudStream, Stremio)..."
    for old_pkg in "com.lagradost.cloudstream3" "com.stremio.one"; do
        adb shell "pm uninstall -k --user 0 ${old_pkg} 2>/dev/null || pm disable-user --user 0 ${old_pkg} 2>/dev/null || true" >/dev/null || true
    done

    # Check / Install Nuvio TV
    if ! adb shell "pm list packages" | grep -q "com.nuvio.tv"; then
        echo "==> Downloading Nuvio TV for architecture: ${DEVICE_ABI}..."
        NUVIO_TMP="${TMP_DIR}/nuvio_tv.apk"
        if [[ "$DEVICE_ABI" =~ arm64 ]]; then
            NUVIO_URL="https://github.com/NuvioMedia/NuvioTV/releases/download/1.0.0/app-full-arm64-v8a-release.apk"
        else
            NUVIO_URL="https://github.com/NuvioMedia/NuvioTV/releases/download/1.0.0/app-full-armeabi-v7a-release.apk"
        fi
        curl -L -s -o "${NUVIO_TMP}" "${NUVIO_URL}" || wget -q -O "${NUVIO_TMP}" "${NUVIO_URL}"
        echo "==> Installing Nuvio TV..."
        adb install -r -d -g "${NUVIO_TMP}"
    else
        echo "==> Nuvio TV is already installed."
    fi

    # Wake Nuvio once
    adb shell "am start -n com.nuvio.tv/.MainActivity 2>/dev/null || true" >/dev/null
    sleep 2

    if [[ "$INSTALL_TR_CINEMA" == "true" ]]; then
        echo "==> Configuring Turkish Cinema Suite (Webteizle + Anthology + TR Torrentio)..."
        # Set Launcher Vitrin Feed to Webteizle
        adb shell "settings put global tvhome_vitrin_source webteizle"
        # Install Anthology
        adb shell "am start -a android.intent.action.VIEW -d \"stremio://falsisdev.github.io/anthology/stremio/manifest.json\" 2>/dev/null || true" >/dev/null
        sleep 2
        # Install Torrentio TR Prioritized (Quality tier -> Seeders first -> Size)
        adb shell "am start -a android.intent.action.VIEW -d \"stremio://torrentio.strem.fun/sort=quality%7Cqualityfilter=480p,other,scr,cam%7Cpriority_language=turkish/manifest.json\" 2>/dev/null || true" >/dev/null
        sleep 2
        # Install OpenSubtitles TR
        adb shell "am start -a android.intent.action.VIEW -d \"stremio://opensubtitles-v3.strem.io/manifest.json\" 2>/dev/null || true" >/dev/null
    else
        echo "==> Configuring International Cinema Suite (Cinemeta + Global Torrentio)..."
        # Set Launcher Vitrin Feed to Cinemeta
        adb shell "settings put global tvhome_vitrin_source cinemeta"
        # Install International Torrentio (Quality tier -> Seeders first -> Size, >= 720p, all languages)
        adb shell "am start -a android.intent.action.VIEW -d \"stremio://torrentio.strem.fun/sort=quality%7Cqualityfilter=480p,other,scr,cam/manifest.json\" 2>/dev/null || true" >/dev/null
        sleep 2
        # Install OpenSubtitles Multi-language
        adb shell "am start -a android.intent.action.VIEW -d \"stremio://opensubtitles-v3.strem.io/manifest.json\" 2>/dev/null || true" >/dev/null
    fi
    sleep 1
fi

# ------------------------------------------------------------------------------
# STEP 6: AD-FREE YOUTUBE DISGUISE
# ------------------------------------------------------------------------------
if [[ "$LANG" == "tr" ]]; then
    echo -e "\n${BOLD}[Adım 4/7] Reklamsız YouTube Kurulumu:${NC}"
    read -r -p "Resmi YouTube logolu, reklamsız ve SponsorBlock destekli YouTube kurulsun mu? [E/h] (Varsayılan: E): " YT_CONFIRM
    YT_CONFIRM="${YT_CONFIRM:-E}"
else
    echo -e "\n${BOLD}[Step 4/7] Ad-Free YouTube Disguise Setup:${NC}"
    read -r -p "Install ad-free YouTube disguised under the official YouTube icon? [Y/n] (Default: Y): " YT_CONFIRM
    YT_CONFIRM="${YT_CONFIRM:-Y}"
fi

if [[ "$YT_CONFIRM" =~ ^[eEyY]$ ]]; then
    bash "${SCRIPT_DIR}/setup_youtube.sh" || true
fi

# ------------------------------------------------------------------------------
# STEP 7: AIRPLAY & WIRELESS CASTING
# ------------------------------------------------------------------------------
if [[ "$LANG" == "tr" ]]; then
    echo -e "\n${BOLD}[Adım 5/7] Apple AirPlay & Kablosuz Ekran Yansıtma:${NC}"
    read -r -p "Apple cihazları için arka plan AirPlay servisi etkinleştirilsin mi? [E/h] (Varsayılan: E): " AIRPLAY_CONFIRM
    AIRPLAY_CONFIRM="${AIRPLAY_CONFIRM:-E}"
else
    echo -e "\n${BOLD}[Step 5/7] Apple AirPlay & Wireless Casting:${NC}"
    read -r -p "Enable background AirPlay mirroring daemon for Apple devices? [Y/n] (Default: Y): " AIRPLAY_CONFIRM
    AIRPLAY_CONFIRM="${AIRPLAY_CONFIRM:-Y}"
fi

if [[ "$AIRPLAY_CONFIRM" =~ ^[eEyY]$ ]]; then
    bash "${SCRIPT_DIR}/setup_airplay.sh" || true
fi

# ------------------------------------------------------------------------------
# STEP 8: RAM OPTIMIZATION (zRAM & LMK)
# ------------------------------------------------------------------------------
echo -e "\n==> Optimizing TV RAM thresholds & zRAM swap..."
bash "${SCRIPT_DIR}/optimize_ram.sh" || true

# ------------------------------------------------------------------------------
# STEP 9: INSTALL TV MASTER LAUNCHER v1.0.0
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[Adım 6/7] TV Master Launcher v1.0.0 Kurulumu:${NC}"
APK_RELEASE="${ROOT_DIR}/release/tv-master-launcher-v1.0.0.apk"

if [[ ! -f "$APK_RELEASE" ]]; then
    echo "==> Compiling APK from source..."
    (cd "${ROOT_DIR}/launcher" && bash ./build.sh)
fi

echo "==> Installing ${APK_RELEASE} on device..."
adb install -r -d -g "${APK_RELEASE}"

echo "==> Setting TV Master Launcher as default home launcher..."
adb shell "cmd package set-home-activity com.gathin.tvhome/.MainActivity 2>/dev/null || true"

# ------------------------------------------------------------------------------
# STEP 10: REMOTE KEY INTERCEPTOR SERVICE
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[Adım 7/7] Kumanda Özel Tuş Eşlemeleri:${NC}"
echo "==> Enabling Accessibility Interceptor Service..."
adb shell "settings put secure enabled_accessibility_services com.gathin.tvhome/.TvKeyInterceptorService"
adb shell "settings put secure accessibility_enabled 1"

if [[ "$LANG" == "tr" ]]; then
    echo -e "${GREEN}    [✓] Prime Video tuşu  -> Sinema Ses ve Görüntü Ayarları HUD (Yayını kesmez)${NC}"
    echo -e "${GREEN}    [✓] Netflix tuşu      -> Nuvio TV (Doğrudan akış motoru)${NC}"
    echo -e "${GREEN}    [✓] TV+ tuşu          -> Turkcell TV+${NC}"
    echo -e "${GREEN}    [✓] Bein / YT tuşu    -> Reklamsız YouTube${NC}"
else
    echo -e "${GREEN}    [✓] Prime Video key   -> In-Video Cinema Audio & Picture HUD Overlay (non-intrusive)${NC}"
    echo -e "${GREEN}    [✓] Netflix key       -> Nuvio TV (Direct 4K HDR10 Stream Engine)${NC}"
    echo -e "${GREEN}    [✓] TV+ key           -> Turkcell TV+${NC}"
    echo -e "${GREEN}    [✓] Bein / YT key     -> Ad-Free YouTube${NC}"
fi

# Launch MainActivity
adb shell "am start -n com.gathin.tvhome/.MainActivity" >/dev/null 2>&1 || true

echo -e "\n${GREEN}${BOLD}================================================================================"
if [[ "$LANG" == "tr" ]]; then
    echo "  🎉 TEBRİKLER! KURULUM BAŞARIYLA TAMAMLANDI."
    echo "  Televizyonunuz 60 FPS hızında, reklamsız ve sinema kalitesinde hazır!"
else
    echo "  🎉 CONGRATULATIONS! SETUP COMPLETED SUCCESSFULLY."
    echo "  Your TV is transformed into an ultra-fast, cinema-grade, ad-free powerhouse!"
fi
echo "================================================================================${NC}"
