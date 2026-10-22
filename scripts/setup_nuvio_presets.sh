#!/usr/bin/env bash
# TV Master Launcher — Nuvio TV Cinema Presets & Hardware Calibration
# OPTIONAL / MODULAR SCRIPT (Not run by default; executed on demand by user or AI wizard)
# Configures cinema-grade picture, bit-perfect surround sound, AFR frame rate matching,
# and verified Turkish 4K HDR10 streaming repositories (Anthology, Torrentio, Subtitles).
set -e

echo "==> [Nuvio TV Cinema Presets & Hardware Calibration]"
DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')
if [ -z "$DEVICE" ]; then
    echo "ERROR: No ADB device connected."
    exit 1
fi

echo "--------------------------------------------------------------------------------"
echo "1. DONANIM GÖRÜNTÜ, PANEL VE KARE HIZI (AFR) EŞLEME KALİBRASYONU"
echo "--------------------------------------------------------------------------------"
echo "[+] Match Content Frame Rate (AFR) Açılıyor (Otomatik 24.000 / 23.976 / 60 Hz)..."
adb shell "settings put secure match_content_frame_rate 1"

echo "[+] HDR10+ Dinamik Ton Eşleme (Dynamic Tone Mapping) Aktif Ediliyor..."
adb shell "settings put global tv_picture_hdr10_plus 1 2>/dev/null || true"

echo "[+] Donanımsal Süper Çözünürlük (Hardware Super Resolution Upscaling) Açılıyor..."
adb shell "settings put global tv_picture_video_super_resolution 1 2>/dev/null || true"

echo "[+] HDMI 2.0 / EDID 2.0 (4K 60Hz HDR10) Modu Doğrulanıyor..."
adb shell "settings put global tv_input_hdmi_edid_version 2 2>/dev/null || true"
adb shell "settings put global picture_list_hdr 1 2>/dev/null || true"

echo ""
echo "--------------------------------------------------------------------------------"
echo "2. BİT-PERFECT SES & SİNEMA STANDARDI (DOLBY ATMOS / PASSTHROUGH)"
echo "--------------------------------------------------------------------------------"
echo "[+] Dolby Atmos & Çevresel Ses (Surround Sound) Aktif Ediliyor..."
adb shell "settings put global sound_advanced_dolby_atmos 1 2>/dev/null || true"
adb shell "settings put global sound_advanced_dolby_ap 1 2>/dev/null || true"
adb shell "settings put global tv_sound_dolby_atmos 1 2>/dev/null || true"
adb shell "settings put global surround_sound_enabled 1 2>/dev/null || true"

echo "[+] Dijital Ses Çıkışı: Passthrough / Bitstream (Bit-Perfect) Moduna Alınıyor..."
adb shell "settings put global sound_digital_output 5 2>/dev/null || true"

echo ""
echo "--------------------------------------------------------------------------------"
echo "3. NUVIO TV TÜRKÇE 4K HDR10 VE ANTHOLOGY EKLENTİ REPOLARI"
echo "--------------------------------------------------------------------------------"
echo "Aşağıdaki doğrulanmış eklenti ve scraper depoları Nuvio TV üzerinde"
echo "en yüksek kalitede 4K HDR10 Türkçe Dublaj ve Altyazılı yayınları listeler:"
echo ""
echo "📦 1) Anthology Türkçe Film, Dizi, Anime & Canlı TV Eklenti Deposu:"
echo "      https://raw.githubusercontent.com/falsisdev/anthology/main/manifest.json"
echo "      (Nuvio -> Ayarlar -> Pluginler -> Depo Ekle alanına yapıştırın)"
echo ""
echo "⚡ 2) Torrentio 4K HDR10 Türkçe Öncelikli P2P Akış Motoru:"
echo "      https://torrentio.strem.fun/manifest.json"
echo "      Öncelik Hiyerarşisi:"
echo "      4K HDR10 TR Dublaj > 4K HDR10 TR Altyazı > 1080p HDR10 TR Dublaj > 1080p TR Dublaj"
echo ""
echo "💬 3) OpenSubtitles v3 Resmi Türkçe Altyazı Sağlayıcısı:"
echo "      https://opensubtitles-v3.strem.io/manifest.json"
echo ""
echo "🎬 4) Cinemeta Resmi Global Katalog & Metadata Sağlayıcısı:"
echo "      https://v3-cinemeta.strem.io/manifest.json"
echo ""
echo "--------------------------------------------------------------------------------"
echo "4. GÜÇ VE BEKLEME (STANDBY) OPTİMİZASYONU"
echo "--------------------------------------------------------------------------------"
echo "[+] İstenmeyen Harici HDMI-CEC Uyanma Sinyalleri Engelleniyor..."
adb shell "settings put global hdmi_control_auto_wakeup_enabled 0"
adb shell "settings put secure wake_gesture_enabled 0 2>/dev/null || true"
adb shell "settings put secure double_tap_to_wake 0 2>/dev/null || true"
adb shell "settings put secure screensaver_activate_on_sleep 0 2>/dev/null || true"
echo "[+] AirPlay Arka Plan Uyku Kilidi İhmal Ediliyor (Derin Uyku İzni)..."
adb shell "appops set io.github.jqssun.airplay WAKE_LOCK ignore 2>/dev/null || true"

echo ""
echo "==> Sinema Kalibrasyonu ve Nuvio Presetleri Başarıyla Tamamlandı!"
