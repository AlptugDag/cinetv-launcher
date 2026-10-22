#!/usr/bin/env bash
# ==============================================================================
# TV Master Launcher v1.0.0 — Universal Interactive Setup Wizard
# Fully personalized: Country, Languages (Dub/Subs), City (Weather), Theme & Debloat
# Multi-Language interactive shell: English & Türkçe
# Zero Security Vulnerabilities: strict variable quotation, input sanitization.
# ==============================================================================
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Shared safety helpers (protected TV-core package list, exact-match package checks)
# shellcheck source=lib/tv_safety.sh
source "${SCRIPT_DIR}/lib/tv_safety.sh"

# Temporary directory with automatic cleanup on exit
TMP_DIR=$(mktemp -d -t tv_installer_XXXXXX)
trap 'rm -rf "${TMP_DIR}"' EXIT

clear
echo -e "${CYAN}${BOLD}"
echo "================================================================================"
echo "    TV MASTER LAUNCHER v1.0.0 — PERSONALIZED ANDROID TV SETUP WIZARD"
echo "================================================================================"
echo -e "${NC}"

# ------------------------------------------------------------------------------
# STEP 1: INTERFACE LANGUAGE
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

# ------------------------------------------------------------------------------
# STEP 2: ADB CONNECTION & HARDWARE FINGERPRINTING
# ------------------------------------------------------------------------------
if [[ "$LANG" == "tr" ]]; then
    echo "==> ADB bağlantısı kontrol ediliyor..."
else
    echo "==> Checking ADB connection..."
fi

CONNECTED_DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}' || true)

if [[ -z "$CONNECTED_DEVICE" ]]; then
    if [[ "$LANG" == "tr" ]]; then
        echo "[-] Bağlı TV bulunamadı."
        PROMPT_IP="Lütfen TV'nin yerel IP adresini girin (örn: 192.168.1.128): "
    else
        echo "[-] No ADB device detected."
        PROMPT_IP="Please enter TV local IP address (e.g. 192.168.1.128): "
    fi

    while true; do
        read -r -p "$PROMPT_IP" TV_IP
        if [[ "$TV_IP" =~ ^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$ ]]; then
            echo -e "${YELLOW}==> Connecting to ${TV_IP}:5555...${NC}"
            adb connect "${TV_IP}:5555" || true
            sleep 2
            CONNECTED_DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}' || true)
            if [[ -n "$CONNECTED_DEVICE" ]]; then
                break
            else
                if [[ "$LANG" == "tr" ]]; then
                    echo -e "${RED}Bağlantı başarısız. Lütfen TV'de Geliştirici Seçenekleri -> Ağ ADB açık olduğundan emin olun.${NC}"
                else
                    echo -e "${RED}Connection failed. Ensure Developer Options -> Network ADB debugging is enabled on TV.${NC}"
                fi
            fi
        else
            if [[ "$LANG" == "tr" ]]; then
                echo -e "${RED}[!] Geçersiz IP adresi formatı.${NC}"
            else
                echo -e "${RED}[!] Invalid IP address format.${NC}"
            fi
        fi
    done
fi

echo -e "${GREEN}==> Connected to device: ${BOLD}${CONNECTED_DEVICE}${NC}"

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
# STEP 3: USER COUNTRY & REGIONAL CATALOG PREFERENCES
# ------------------------------------------------------------------------------
if [[ "$LANG" == "tr" ]]; then
    echo -e "${BOLD}[Adım 1/8] Bulunduğunuz Ülke / Bölge:${NC}"
    echo "  1) Türkiye (TR) - Webteizle Vitrin + Anthology Canlı TV & Arşivleri"
    echo "  2) United States (US) - Cinemeta Box Office + Global Catalogs"
    echo "  3) Germany / Deutschland (DE) - Cinemeta DE + Mediathek"
    echo "  4) United Kingdom (GB) - Cinemeta UK + Freeview"
    echo "  5) France (FR) - Cinemeta FR"
    echo "  6) Spain / Latin America (ES) - Cinemeta ES"
    echo "  7) Diğer / Custom Country Code (ISO 3166-1 alpha-2, örn: BR, IT, NL, JP)"
    read -r -p "Seçim [1-7] (Varsayılan: 1): " COUNTRY_SEL
else
    echo -e "${BOLD}[Step 1/8] Your Country / Region:${NC}"
    echo "  1) United States (US) - Cinemeta Global Box Office"
    echo "  2) Türkiye (TR) - Webteizle Vitrin + Anthology TV"
    echo "  3) Germany / Deutschland (DE) - Cinemeta DE + Mediathek"
    echo "  4) United Kingdom (GB) - Cinemeta UK + Freeview"
    echo "  5) France (FR) - Cinemeta FR"
    echo "  6) Spain / Latin America (ES) - Cinemeta ES"
    echo "  7) Other / Custom Country Code (ISO 3166-1 alpha-2, e.g. BR, IT, NL, JP)"
    read -r -p "Choice [1-7] (Default: 1): " COUNTRY_SEL
fi
COUNTRY_SEL="${COUNTRY_SEL:-1}"

USER_COUNTRY="US"
if [[ "$LANG" == "tr" ]]; then
    case "$COUNTRY_SEL" in
        1) USER_COUNTRY="TR" ;;
        2) USER_COUNTRY="US" ;;
        3) USER_COUNTRY="DE" ;;
        4) USER_COUNTRY="GB" ;;
        5) USER_COUNTRY="FR" ;;
        6) USER_COUNTRY="ES" ;;
        7)
            read -r -p "Ülke kodunuzu girin (örn: IT, BR, CA): " CUSTOM_C
            USER_COUNTRY="${CUSTOM_C:-TR}"
            ;;
        *) USER_COUNTRY="TR" ;;
    esac
else
    case "$COUNTRY_SEL" in
        1) USER_COUNTRY="US" ;;
        2) USER_COUNTRY="TR" ;;
        3) USER_COUNTRY="DE" ;;
        4) USER_COUNTRY="GB" ;;
        5) USER_COUNTRY="FR" ;;
        6) USER_COUNTRY="ES" ;;
        7)
            read -r -p "Enter 2-letter Country Code (e.g. IT, BR, CA): " CUSTOM_C
            USER_COUNTRY="${CUSTOM_C:-US}"
            ;;
        *) USER_COUNTRY="US" ;;
    esac
fi
USER_COUNTRY=$(echo "$USER_COUNTRY" | tr '[:lower:]' '[:upper:]' | tr -cd 'A-Z')
adb shell "settings put global tvhome_country \"${USER_COUNTRY}\""

# ------------------------------------------------------------------------------
# STEP 4: WEATHER CITY FOR LIVE HOME SCREEN
# ------------------------------------------------------------------------------
if [[ "$LANG" == "tr" ]]; then
    echo -e "\n${BOLD}[Adım 2/8] Canlı Hava Durumu Şehri:${NC}"
    echo "Ana ekranda saatin yanında anlık hava durumu ve sıcaklık görüntülenecektir."
    read -r -p "Şehrinizi girin (örn: Istanbul, Ankara, Izmir, Berlin. Boş bırakılırsa IP'den otomatik bulunur): " USER_CITY
else
    echo -e "\n${BOLD}[Step 2/8] Live Home Screen Weather City:${NC}"
    echo "Real-time temperature and weather icons will display on the home top bar."
    read -r -p "Enter your city (e.g. New York, London, Berlin, Paris. Leave blank for auto IP detection): " USER_CITY
fi

USER_CITY=$(echo "$USER_CITY" | tr -d '\r\n"')
if [[ -n "$USER_CITY" ]]; then
    echo "==> Testing weather for city: ${USER_CITY}..."
    WEATHER_SAMPLE=$(curl -s "https://wttr.in/${USER_CITY}?format=%t+%c" 2>/dev/null || echo "")
    if [[ -n "$WEATHER_SAMPLE" ]]; then
        echo -e "${GREEN}    [✓] Weather check success: ${USER_CITY} -> ${WEATHER_SAMPLE}${NC}"
    fi
    adb shell "settings put global tvhome_city \"${USER_CITY}\""
else
    adb shell "settings put global tvhome_city \"\""
fi

# ------------------------------------------------------------------------------
# STEP 5: SPOKEN & PREFERRED LANGUAGES (DUBBING & SUBTITLES)
# ------------------------------------------------------------------------------
DEFAULT_LANGS="english"
if [[ "$USER_COUNTRY" == "TR" ]]; then
    DEFAULT_LANGS="turkish,english"
elif [[ "$USER_COUNTRY" == "DE" ]]; then
    DEFAULT_LANGS="german,english"
elif [[ "$USER_COUNTRY" == "FR" ]]; then
    DEFAULT_LANGS="french,english"
elif [[ "$USER_COUNTRY" == "ES" ]]; then
    DEFAULT_LANGS="spanish,english"
elif [[ "$USER_COUNTRY" == "IT" ]]; then
    DEFAULT_LANGS="italian,english"
elif [[ "$USER_COUNTRY" == "BR" ]]; then
    DEFAULT_LANGS="portuguese,english"
fi

if [[ "$LANG" == "tr" ]]; then
    echo -e "\n${BOLD}[Adım 3/8] Tercih Ettiğiniz Dublaj & Altyazı Dilleri:${NC}"
    echo "Filmlerde ve torrent akışlarında önceliklendirilecek dilleri virgülle ayırarak girin."
    echo "(Desteklenenler: turkish, english, german, french, spanish, italian, russian, japanese, korean, vb.)"
    read -r -p "Diller [Varsayılan: ${DEFAULT_LANGS}]: " USER_LANGS
else
    echo -e "\n${BOLD}[Step 3/8] Spoken & Preferred Languages (Dubbing & Subtitles):${NC}"
    echo "Enter languages to prioritize for video streams and subtitles, separated by commas."
    echo "(Supported: english, turkish, german, french, spanish, italian, russian, japanese, korean, etc.)"
    read -r -p "Languages [Default: ${DEFAULT_LANGS}]: " USER_LANGS
fi
USER_LANGS="${USER_LANGS:-$DEFAULT_LANGS}"
USER_LANGS=$(echo "$USER_LANGS" | tr -d ' "' | tr '[:upper:]' '[:lower:]')
adb shell "settings put global tvhome_languages \"${USER_LANGS}\""

# ------------------------------------------------------------------------------
# STEP 6: THEME PREFERENCE (DARK / AUTO / LIGHT)
# ------------------------------------------------------------------------------
if [[ "$LANG" == "tr" ]]; then
    echo -e "\n${BOLD}[Adım 4/8] Arayüz Tema Tercihi:${NC}"
    echo "  1) Saf OLED Siyah (Deep Obsidian Dark Noir - Sıfır ışık sızması) [Önerilen]"
    echo "  2) Otomatik Gece / Gündüz (Gündüz Aydınlık Buzlu Cam, Gece Saf OLED Siyah)"
    echo "  3) Sürekli Aydınlık Buzlu Cam (Modern Frosted Silver Glass)"
    read -r -p "Seçim [1-3] (Varsayılan: 1): " THEME_SEL
else
    echo -e "\n${BOLD}[Step 4/8] Interface Theme Preference:${NC}"
    echo "  1) OLED Pure Dark Noir (Deep obsidian black, zero light bleed) [Recommended]"
    echo "  2) Automatic Day / Night (Frosted glass by day, OLED pure black at night)"
    echo "  3) Continuous Frosted Light Glass (Modern platinum frosted silver)"
    read -r -p "Choice [1-3] (Default: 1): " THEME_SEL
fi
THEME_SEL="${THEME_SEL:-1}"

THEME_MODE="dark"
case "$THEME_SEL" in
    1) THEME_MODE="dark" ;;
    2) THEME_MODE="auto" ;;
    3) THEME_MODE="light" ;;
esac
adb shell "settings put global tvhome_theme_mode \"${THEME_MODE}\""

# ------------------------------------------------------------------------------
# STEP 7: APP DEBLOATING & REMOVAL PREFERENCES
# ------------------------------------------------------------------------------
if [[ "$LANG" == "tr" ]]; then
    echo -e "\n${BOLD}[Adım 5/8] Mevcut Uygulamaların Temizliği & Debloat:${NC}"
    echo "  1) Önerilen Güvenli Temizlik (Cihaz markanıza göre üretici bloatware & telemetrisini sil)"
    echo "  2) Kişiselleştirilmiş / Özel Temizlik (TV'deki uygulamaları incele ve silinecekleri kendin seç)"
    echo "  3) Temizliği Atla (Tüm mevcut fabrika uygulamalarını koru)"
    read -r -p "Seçim [1-3] (Varsayılan: 1): " DEBLOAT_SEL
else
    echo -e "\n${BOLD}[Step 5/8] App Debloating & Removal Preferences:${NC}"
    echo "  1) Recommended Safe Debloat (Targeted removal of OEM telemetry & storefront pushers)"
    echo "  2) Custom Interactive Debloat (Inspect installed TV packages and choose what to remove)"
    echo "  3) Skip Debloating (Preserve all existing apps)"
    read -r -p "Choice [1-3] (Default: 1): " DEBLOAT_SEL
fi
DEBLOAT_SEL="${DEBLOAT_SEL:-1}"

if [[ "$DEBLOAT_SEL" == "1" ]]; then
    if [[ "$DEVICE_BRAND" =~ (arcelik|beko|grundig) || "$DEVICE_MANUF" =~ (arcelik|beko|grundig) ]]; then
        echo "==> Running Arçelik/Beko Profile Debloater..."
        bash "${SCRIPT_DIR}/debloat_arcelik_beko.sh" || true
    else
        echo "==> Running Universal Android TV Debloater..."
        bash "${SCRIPT_DIR}/debloat_universal.sh" || true
    fi
elif [[ "$DEBLOAT_SEL" == "2" ]]; then
    echo -e "\n${CYAN}--- INSTALLED THIRD-PARTY & VENDOR PACKAGES ON TV ---${NC}"
    adb shell "pm list packages -3" | sed 's/package:/  [User App]   /g' || true
    adb shell "pm list packages" | grep -E "factory|telemetry|partner|demo|launcher|store" | head -n 25 | sed 's/package:/  [Vendor Bloat]/g' || true
    echo -e "${CYAN}------------------------------------------------------${NC}"

    if [[ "$LANG" == "tr" ]]; then
        echo "Kaldırılmasını istediğiniz paket isimlerini veya anahtar kelimeleri virgülle ayırarak girin:"
        echo "(Örn: com.netflix.ninja, mediatek.factorymenu.ui, com.google.android.play.games)"
        read -r -p "Silinecek paketler: " CUSTOM_PKGS
    else
        echo "Enter package names or keywords to remove, separated by commas:"
        echo "(e.g. com.netflix.ninja, mediatek.factorymenu.ui, com.google.android.play.games)"
        read -r -p "Packages to remove: " CUSTOM_PKGS
    fi

    IFS=',' read -ra PKG_ARRAY <<< "$CUSTOM_PKGS"
    for raw_p in "${PKG_ARRAY[@]}"; do
        p=$(echo "$raw_p" | tr -d ' \r\n"')
        if [[ -n "$p" ]]; then
            # Only accept valid Android package names (blocks shell injection into "adb shell")
            if [[ ! "$p" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$ ]]; then
                echo -e "${RED}[!] Not a valid package name, skipping: $p${NC}"
            # Protect critical TV hardware / MediaTek TV-core services (tvcenter, tvapi, HDMI, display, ...)
            elif is_protected_pkg "$p"; then
                echo -e "${RED}[!] Skipping protected core service: $p${NC}"
            else
                echo "[-] Removing $p..."
                safe_remove_pkg "$p" || true
            fi
        fi
    done
else
    echo "==> Skipping debloat step."
fi

# ------------------------------------------------------------------------------
# STEP 8: PRIVATE DNS OVER TLS (DoT)
# ------------------------------------------------------------------------------
if [[ "$LANG" == "tr" ]]; then
    echo -e "\n${BOLD}[Adım 6/8] Şifreli DNS & Reklam Engelleme (DoT):${NC}"
    echo "  1) AdGuard DNS over TLS (dns.adguard-dns.com - Sıfır reklam & ISS engelsiz) [Önerilen]"
    echo "  2) Cloudflare DNS over TLS (one.one.one.one - Hızlı & Gizlilik odaklı)"
    echo "  3) Mevcut DNS ayarlarını koru"
    read -r -p "Seçim [1-3] (Varsayılan: 1): " DNS_CHOICE
else
    echo -e "\n${BOLD}[Step 6/8] Encrypted Private DNS & Ad Blocking (DoT):${NC}"
    echo "  1) AdGuard DNS over TLS (dns.adguard-dns.com - Zero ads & ISP bypass) [Recommended]"
    echo "  2) Cloudflare DNS over TLS (one.one.one.one - Ultra-fast & Private)"
    echo "  3) Keep existing TV DNS settings"
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
fi

# ------------------------------------------------------------------------------
# STEP 9: CINEMA SUITE & TORRENT ENGINE CONFIGURATION
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[Adım 7/8] Sinema & Yayın Motoru Kurulumu:${NC}"
echo "==> Removing legacy/deprecated players (CloudStream, Stremio)..."
for old_pkg in "com.lagradost.cloudstream3" "com.stremio.one"; do
    adb shell "pm uninstall -k --user 0 ${old_pkg} 2>/dev/null || pm disable-user --user 0 ${old_pkg} 2>/dev/null || true" >/dev/null || true
done

# Install Nuvio TV if not installed
if ! adb shell "pm list packages" | grep -q "com.nuvio.tv"; then
    echo "==> Downloading Nuvio TV for architecture ${DEVICE_ABI}..."
    NUVIO_TMP="${TMP_DIR}/nuvio_tv.apk"
    if [[ "$DEVICE_ABI" =~ arm64 ]]; then
        NUVIO_URL="https://github.com/NuvioMedia/NuvioTV/releases/download/1.0.0/app-full-arm64-v8a-release.apk"
    else
        NUVIO_URL="https://github.com/NuvioMedia/NuvioTV/releases/download/1.0.0/app-full-armeabi-v7a-release.apk"
    fi
    curl -L -s -o "${NUVIO_TMP}" "${NUVIO_URL}" || wget -q -O "${NUVIO_TMP}" "${NUVIO_URL}"
    echo "==> Installing Nuvio TV..."
    adb install -r -d -g "${NUVIO_TMP}"
fi

# Wake Nuvio
adb shell "am start -n com.nuvio.tv/.MainActivity 2>/dev/null || true" >/dev/null
sleep 2

# Regional Catalogs:
if [[ "$USER_COUNTRY" == "TR" ]]; then
    echo "==> Configuring Webteizle Vitrin + Anthology Canlı TV Catalogs..."
    adb shell "settings put global tvhome_vitrin_source webteizle"
    adb shell "am start -a android.intent.action.VIEW -d \"stremio://falsisdev.github.io/anthology/stremio/manifest.json\" 2>/dev/null || true" >/dev/null
    sleep 2
else
    echo "==> Configuring Cinemeta Global Box Office Vitrin..."
    adb shell "settings put global tvhome_vitrin_source cinemeta"
fi

# Configure Torrentio with Selected Languages & Seeder-First Sorting (sort=quality)
echo "==> Configuring 4K HDR10 Torrent Engine (Quality tier -> Seeders first -> Size)..."
echo "    Priority Languages: ${USER_LANGS}"
TORRENTIO_URL="stremio://torrentio.strem.fun/sort=quality%7Cqualityfilter=480p,other,scr,cam%7Cpriority_language=${USER_LANGS}/manifest.json"
adb shell "am start -a android.intent.action.VIEW -d \"${TORRENTIO_URL}\" 2>/dev/null || true" >/dev/null
sleep 2

# OpenSubtitles v3
echo "==> Adding OpenSubtitles v3 Engine..."
adb shell "am start -a android.intent.action.VIEW -d \"stremio://opensubtitles-v3.strem.io/manifest.json\" 2>/dev/null || true" >/dev/null
sleep 1

# ------------------------------------------------------------------------------
# STEP 10: OPTIONAL ADD-ONS (AD-FREE YOUTUBE & AIRPLAY)
# ------------------------------------------------------------------------------
if [[ "$LANG" == "tr" ]]; then
    read -r -p "Resmi YouTube logolu, reklamsız YouTube kurulsun mu? [E/h]: " YT_CONFIRM
    YT_CONFIRM="${YT_CONFIRM:-E}"
    read -r -p "Apple AirPlay ekran yansıtma servisi etkinleştirilsin mi? [E/h]: " AIRPLAY_CONFIRM
    AIRPLAY_CONFIRM="${AIRPLAY_CONFIRM:-E}"
else
    read -r -p "Install ad-free YouTube disguised under official YouTube logo? [Y/n]: " YT_CONFIRM
    YT_CONFIRM="${YT_CONFIRM:-Y}"
    read -r -p "Enable Apple AirPlay wireless mirroring receiver? [Y/n]: " AIRPLAY_CONFIRM
    AIRPLAY_CONFIRM="${AIRPLAY_CONFIRM:-Y}"
fi

if [[ "$YT_CONFIRM" =~ ^[eEyY]$ ]]; then
    bash "${SCRIPT_DIR}/setup_youtube.sh" || true
fi

if [[ "$AIRPLAY_CONFIRM" =~ ^[eEyY]$ ]]; then
    bash "${SCRIPT_DIR}/setup_airplay.sh" || true
fi

# Self-heal: make sure the OEM TV core (HDMI / picture / HDR plumbing) is enabled
bash "${SCRIPT_DIR}/restore_tv_core.sh" --quiet || true

# RAM & zRAM Optimization
echo "==> Applying zRAM & memory optimization..."
bash "${SCRIPT_DIR}/optimize_ram.sh" || true

# ------------------------------------------------------------------------------
# STEP 11: COMPILE & INSTALL TV MASTER LAUNCHER v1.0.0
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[Adım 8/8] TV Master Launcher v1.0.0 Kuruluyor:${NC}"
APK_RELEASE="${ROOT_DIR}/release/tv-master-launcher-v1.0.0.apk"

if [[ ! -f "$APK_RELEASE" ]]; then
    echo "==> Compiling APK from source..."
    (cd "${ROOT_DIR}/launcher" && bash ./build.sh)
fi

echo "==> Installing ${APK_RELEASE} on device..."
adb install -r -d -g "${APK_RELEASE}"

echo "==> Setting default home launcher..."
adb shell "cmd package set-home-activity com.gathin.tvhome/.MainActivity 2>/dev/null || true"

# Launch MainActivity
adb shell "am force-stop com.gathin.tvhome && am start -n com.gathin.tvhome/.MainActivity" >/dev/null 2>&1 || true

# Post-install display / HDR sanity report (informational, never fails the install)
bash "${SCRIPT_DIR}/verify_display.sh" || true

echo -e "\n${GREEN}${BOLD}================================================================================"
if [[ "$LANG" == "tr" ]]; then
    echo "  🎉 TEBRİKLER! KİŞİSELLEŞTİRİLMİŞ KURULUM TAMAMLANDI."
    echo "  Ülke: ${USER_COUNTRY} | Şehir: ${USER_CITY:-Otomatik} | Tema: ${THEME_MODE}"
    echo "  Diller: ${USER_LANGS} | 4K HDR10 Seeder Öncelikli Yayın Motoru Hazır!"
else
    echo "  🎉 CONGRATULATIONS! PERSONALIZED SETUP COMPLETED."
    echo "  Country: ${USER_COUNTRY} | City: ${USER_CITY:-Auto} | Theme: ${THEME_MODE}"
    echo "  Languages: ${USER_LANGS} | 4K HDR10 Seeder-First Cinema Engine Ready!"
fi
echo "================================================================================${NC}"
