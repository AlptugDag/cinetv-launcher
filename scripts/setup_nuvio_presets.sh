#!/usr/bin/env bash
# TV Master Launcher — Nuvio TV Cinema Presets & Hardware Calibration
# OPTIONAL / MODULAR SCRIPT (Not run by default; executed on demand by user or AI wizard)
# Configures cinema-grade picture, bit-perfect surround sound, AFR frame rate matching,
# and verified Turkish 4K HDR10 streaming repositories (Anthology, Torrentio, Subtitles).
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/tv_safety.sh
source "${SCRIPT_DIR}/lib/tv_safety.sh"

echo "==> [Nuvio TV Cinema Presets & Hardware Calibration]"
DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')
if [ -z "$DEVICE" ]; then
    echo "ERROR: No ADB device connected."
    exit 1
fi

echo "--------------------------------------------------------------------------------"
echo "1. DONANIM GÖRÜNTÜ, PANEL VE KARE HIZI (AFR) EŞLEME KALİBRASYONU"
echo "--------------------------------------------------------------------------------"
echo "[+] Match Content Frame Rate (AFR): Kapalı Tutuluyor (Düşük RAM/MediaTek zaman aşımını önler)..."
adb shell "settings put secure match_content_frame_rate 0"

echo "[+] HDR10+ Dinamik Ton Eşleme (Dynamic Tone Mapping) Aktif Ediliyor..."
put_setting_if_exists global tv_picture_hdr10_plus 1

echo "[+] Donanımsal Süper Çözünürlük (Hardware Super Resolution Upscaling) Açılıyor..."
put_setting_if_exists global tv_picture_video_super_resolution 1

echo "[+] HDMI 2.0 / EDID 2.0 (4K 60Hz HDR10) Modu Doğrulanıyor..."
put_setting_if_exists global tv_input_hdmi_edid_version 2
put_setting_if_exists global picture_list_hdr 1

echo ""
echo "--------------------------------------------------------------------------------"
echo "2. BİT-PERFECT SES & SİNEMA STANDARDI (DOLBY ATMOS / PASSTHROUGH)"
echo "--------------------------------------------------------------------------------"
echo "[+] Dolby Atmos & Çevresel Ses (Surround Sound) Aktif Ediliyor..."
put_setting_if_exists global sound_advanced_dolby_atmos 1
put_setting_if_exists global sound_advanced_dolby_ap 1
put_setting_if_exists global tv_sound_dolby_atmos 1
put_setting_if_exists global surround_sound_enabled 1

echo "[+] Dijital Ses Çıkışı: Passthrough / Bitstream (Bit-Perfect) Moduna Alınıyor..."
put_setting_if_exists global sound_digital_output 5

echo ""
echo "--------------------------------------------------------------------------------"
echo "3. NUVIO TV TÜRKÇE 4K HDR10, ANTHOLOGY & SİNEMA EKLENTİ EKOSİSTEMİ"
echo "--------------------------------------------------------------------------------"
echo "Aşağıdaki doğrulanmış eklenti ve scraper depoları Nuvio & Stremio üzerinde"
echo "en yüksek kalitede 4K HDR10 Türkçe Dublaj ve Altyazılı yayınları listeler:"
echo ""
echo "1) Anthology Türkçe Film, Dizi, Anime & Canlı TV Uzantı Deposu (41 Sağlayıcı, 138 Canlı Kanal):"
echo "      GitHub: https://github.com/falsisdev/anthology"
echo "      Nuvio Depo Manifest: https://raw.githubusercontent.com/falsisdev/anthology/main/manifest.json"
echo "      Stremio Canlı TV: stremio://falsisdev.github.io/anthology/stremio/manifest.json"
echo "      (Nuvio -> Ayarlar -> Pluginler / Uzantılar -> Depo Ekle)"
echo ""
echo "2) TurkSinema Türkçe Film, Dizi, Anime ve Belgesel Sağlayıcı Deposu (56+ Kaynak):"
echo "      GitHub: https://github.com/Wiojelt/TurkSinema"
echo "      (Nuvio / CloudStream eklenti deposu, kısa kod: !turksinema)"
echo ""
echo "3) Torrentio 4K HDR10 Seeder-Öncelikli Akış Motoru:"
echo "      Yapılandırma: https://torrentio.strem.fun/configure"
echo "      Öncelik: 4K REMUX HDR10 TR Dublaj > 4K HDR10 TR Altyazı > 1080p TR Dublaj"
echo ""
echo "4) AIOMetadata Türkçe Gelişmiş Metadata & Katalog Yapılandırması:"
echo "      Gist: https://gist.github.com/underd0se/0a623550205661139ee73564051f7e80#file-aiometadata-turkce-config-json"
echo "      (TMDB/TVDB/Trakt Türkçe afiş, özet ve aktör entegrasyonu)"
echo ""
echo "5) Kaptain's Collection (Özel Stüdyo & Sinema Koleksiyon Afişleri):"
echo "      Web: https://kaptain.stream"
echo "      (Nuvio, Stremio ve Plex için 4K özel tasarım stüdyo ve seri afişleri)"
echo ""
echo "6) TR Altyazı (Ortak Türkçe Altyazı Havuzu - AltyaziTR, YIFY, OpenSubtitles):"
echo "      Manifest: https://tr-altyazi.altyazi.workers.dev/manifest.json"
echo "      Tek Tıkla Ekle: stremio://tr-altyazi.altyazi.workers.dev/manifest.json"
echo ""
echo "7) Cinemeta Resmi Global Katalog & Metadata Sağlayıcısı:"
echo "      https://v3-cinemeta.strem.io/manifest.json"
echo ""
echo "--------------------------------------------------------------------------------"
echo "4. GÜÇ VE BEKLEME (STANDBY) OPTİMİZASYONU"
echo "--------------------------------------------------------------------------------"
echo "[+] İstenmeyen Harici HDMI-CEC Uyanma Sinyalleri Engelleniyor..."
adb shell "settings put global hdmi_control_auto_wakeup_enabled 0"
put_setting_if_exists secure wake_gesture_enabled 0
put_setting_if_exists secure double_tap_to_wake 0
put_setting_if_exists secure screensaver_activate_on_sleep 0
echo "[+] AirPlay Arka Plan Uyku Kilidi İhmal Ediliyor (Derin Uyku İzni)..."
adb shell "appops set io.github.jqssun.airplay WAKE_LOCK ignore 2>/dev/null || true"

echo ""
echo "==> Sinema Kalibrasyonu ve Nuvio Presetleri Başarıyla Tamamlandı!"
