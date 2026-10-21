# 📺 TV Master Launcher

[![Release](https://img.shields.io/badge/Release-v2.0--Ultra-blue.svg)](release/tv-master-launcher-v2.0.apk)
[![Platform](https://img.shields.io/badge/Platform-Android%20TV%209%2B%20%7C%20Google%20TV-green.svg)](#supported-devices)
[![Architecture](https://img.shields.io/badge/Arch-armeabi--v7a%20%7C%20arm64--v8a-orange.svg)](#architecture)
[![Based on](https://img.shields.io/badge/Origin-CleanYourTV-blueviolet.svg)](https://github.com/Gathin23/cleanyourtv)
[![License](https://img.shields.io/badge/License-Apache%202.0-yellow.svg)](LICENSE)

> **Next-generation, bloatware-free, hardware-accelerated Android TV operating suite.**  
> Features a stunning **Liquid Glass 3D Parallax CoverFlow** vitrin with dynamic focal neon poster glow, daily midnight catalog auto-rotation, universal multi-brand debloating, smart zRAM / storage-to-RAM expansion, Private DNS over TLS, disguised ad-free YouTube, and integrated Turkish streaming sources.

---

> [!NOTE]
> **Attribution & Origin**: This project is built upon the wonderful foundation of [**CleanYourTV**](https://github.com/Gathin23/cleanyourtv) by Gathin23. We have re-engineered and expanded it into **TV Master Launcher**, introducing hardware Z-order 3D CoverFlow rendering, dynamic 12-bin focal neon glow extraction, 00:00 midnight auto-rotation crons, multi-vendor memory optimization, and an autonomous AI prompt suite.

---

## 🌟 Visual Showcase

### 3D Parallax CoverFlow Vitrin with Dynamic Focal Neon Glow
![Hero 3D CoverFlow](docs/screenshots/hero_coverflow_3d.png)

### Focal Neon Accent Extraction (Poster-Aware Chromatic Glow)
| Crimson Neon ("10") | Electric Cyan Neon ("The Best You Can") | Celestial Ice Blue ("Flight of the Living Dead") |
| :---: | :---: | :---: |
| ![Crimson Neon](docs/screenshots/focal_neon_crimson.png) | ![Electric Cyan Neon](docs/screenshots/focal_neon_cyan.png) | ![Celestial Ice Blue](docs/screenshots/focal_neon_iceblue.png) |

### Clean High-Performance Home Interface
![Vitrin Overview](docs/screenshots/vitrin_rest_overview.png)

---

## ⚡ Key Highlights & Architecture

### 1. 🎴 Liquid Glass 3D Parallax CoverFlow Vitrin
- **Hardware Z-Ordering**: Employs Android RenderNode `elevation` (`50f`) and `translationZ` (`65f`) rather than modifying ViewGroup hierarchy, guaranteeing smooth 60 FPS D-pad navigation.
- **Dynamic Depth Scaling**: Focused poster scales to **1.30x** with a `-22f` vertical lift; neighboring posters smoothly recede to **0.94x**, **0.80x**, and **0.68x** with physical drop shadows and distance alpha dimming.
- **12-Bin Focal Neon Histogram**: Replaces generic dominant color math (which often picks muddy face tones) with a 12-bin chromatic histogram. Dampens skin tones by 65% to isolate pure graphic neon accents (Crimson, Cyan, Emerald, Gold, Cobalt, Violet).
- **Hue Separation Rule**: Enforces a minimum `48°` hue delta between adjacent posters, ensuring adjacent cards never share the same ambient glow.
- **Midnight 00:00 Auto-Refresh**: Posters automatically re-index and rotate every calendar day at 00:00 midnight without needing app restarts.

### 2. 🚀 Smart RAM & Storage-to-RAM Expansion
- **1024 MB LZ4 zRAM Swap**: Creates an in-kernel compressed memory disk on `/dev/block/zram0`.
- **Swappiness & Cache Tuning**: Sets `vm.swappiness=80` and `vfs_cache_pressure=100`, keeping UI threads instantly responsive while moving inactive background services to zRAM.
- **Low Memory Killer (LMK) Optimization**: Sets `sys.sysctl.extra_free_kbytes=24300` and enables `trim_empty_apps`, cutting launcher RAM usage to under **35 MB**.
- **Storage-to-RAM Virtual Allocation**: Enables Android 11+ virtual memory expansion (`ram_expand_size_list: 2048`).

### 3. 🛡️ Universal Multi-Brand Debloater
Safely removes factory menus, telemetry, and pre-installed storefront bloatware for user 0 without breaking HDMI ports, DVB-S2 satellite tuners, or picture settings:
- **Arçelik / Beko / Grundig**: Eliminates MediaTek factory UI, Hatone menu, intrusive cast daemons, and carrier bloatware.
- **TCL / Thomson / FFALCON**: Removes TCL Waterfall, PartnerCustomizer, CyberUI, and AppMarket.
- **Sony Bravia**: Disables Bravia Lifehack, DemoMode, SmartHelp, and background telemetry.
- **Philips Android TV**: Cleans NetTV registration, DemoMode, and Philips TV provider bloat.
- **Xiaomi Mi TV / PatchWall**: Removes PatchWall ads, Mi TV Shop, and device statistics.
- **Hisense / Toshiba**: Strips HiCloud telemetry, retail demo, and recommendation pushers.
- **Chromecast with Google TV**: Disables heavy Google TV LauncherX and Tungsten telemetry.

### 4. 📺 Ad-Free YouTube Disguise Engine
- Pre-configured to route to high-performance ad-free clients (SmartTube / TizenTube / Cobalt) with SponsorBlock.
- Disguised with the **official YouTube red logo** and labeled simply as **YouTube**, ensuring a native, transparent experience for all household members with zero video or banner ads.

### 5. 🍿 CloudStream TurkSinema (5 Curated Providers)
- Integrated with CloudStream 3 repository for Turkish content.
- Pre-configured to use the top 5 high-speed providers:
  1. **Webteizle**
  2. **Filmmodu**
  3. **FilmMakinesi**
  4. **HDFilmCehennemi**
  5. **FullHDFilmizlesene**
- Posters in the vitrin link directly to these sources with instant one-click playback.

### 6. 🔒 Private DNS over TLS (DoT)
- Configures `dns.google` over TLS via system settings.
- Bypasses Turkish ISP DNS hijacking (e.g. `195.175.254.2`), eliminates ISP latency, and prevents ad trackers from intercepting TV requests.

### 7. 📲 Native AirPlay & Cast Receiver
- Full wireless mirroring support for iPhones, iPads, and MacBooks directly to the TV.
- Configured with battery-idle exemptions so the AirPlay receiver remains discoverable at all times.

---

## 🤖 Universal AI Auto-Installation (Recommended)

You can install and optimize your TV completely automatically using any modern AI (ChatGPT, Claude, Gemini, DeepSeek):

1. Connect your computer to your TV over Wi-Fi ADB:
   ```bash
   adb connect <YOUR_TV_IP>:5555
   ```
2. Open [`AI_AUTO_INSTALL_PROMPT.md`](AI_AUTO_INSTALL_PROMPT.md).
3. Copy the master prompt and paste it into your AI assistant.
4. The AI will inspect your TV model, select the correct debloat profile, boost RAM, install the launcher, and configure streaming sources automatically!

---

## 🛠️ Manual Installation Guide

### Prerequisites
- Android TV 9.0, 10.0, 11.0, or 12.0+ (ARMv7 32-bit or ARM64 64-bit).
- ADB installed on your computer (`brew install android-platform-tools` on macOS).
- Developer Options & USB / Wireless Debugging enabled on your TV.

### Step 1: Connect to TV
```bash
adb connect 192.168.1.XXX:5555
```

### Step 2: Run Debloater
```bash
chmod +x scripts/*.sh
./scripts/debloat_universal.sh
```

### Step 3: Apply Smart RAM & Storage Optimization
```bash
./scripts/optimize_ram.sh
```

### Step 4: Configure Private DNS & Turkish Movie Sources
```bash
./scripts/setup_dns_and_streaming.sh
```

### Step 5: Install TV Master Launcher
```bash
adb install -r -d -g release/tv-master-launcher-v2.0.apk
adb shell "cmd package set-home-activity com.cleanyourtv.launcher/.MainActivity"
```

### Step 6: Setup Ad-Free YouTube Disguise
```bash
./scripts/setup_youtube.sh
```

### Step 7: Setup AirPlay Support
```bash
./scripts/setup_airplay.sh
```

---

## 📂 Repository Structure

```
tv-master-launcher/
├── AI_AUTO_INSTALL_PROMPT.md    # Master prompt for ChatGPT / Claude / Gemini
├── LICENSE                      # Apache 2.0 License
├── README.md                    # Project documentation
├── release/
│   └── tv-master-launcher-v2.0.apk  # Pre-built, signed, production APK (818 KB)
├── docs/
│   └── screenshots/             # 1080p launcher & vitrin captures
│       ├── hero_coverflow_3d.png
│       ├── focal_neon_crimson.png
│       ├── focal_neon_cyan.png
│       ├── focal_neon_iceblue.png
│       └── vitrin_rest_overview.png
├── launcher/                    # Complete Android Studio / Gradle source code
│   ├── AndroidManifest.xml
│   ├── build.sh                 # Standalone APK compiler script
│   ├── src/                     # Java sources (CoverFlow, Vitrin, Shaders, DoT)
│   ├── res/                     # Vector drawables, layouts, themes
│   └── assets/                  # High-res poster artwork & fallback database
└── scripts/                     # Shell automation scripts
    ├── debloat_universal.sh     # Auto-detects brand & removes bloat
    ├── debloat_arcelik_beko.sh  # Arçelik / Beko specific debloat script
    ├── optimize_ram.sh          # zRAM, swappiness, LMK & memory tuning
    ├── setup_dns_and_streaming.sh # Private DNS (DoT) & CloudStream setup
    ├── setup_youtube.sh         # Ad-free YouTube disguise installer
    └── setup_airplay.sh         # AirPlay receiver configuration
```

---

## 📺 Supported Devices & Brands

| Manufacturer | Tested Devices | Optimization Profile | Status |
| :--- | :--- | :--- | :--- |
| **Arçelik / Beko / Grundig** | 4K UHD Smart TVs (MediaTek SoC) | `debloat_arcelik_beko.sh` | Verified 60 FPS |
| **TCL / Thomson / FFALCON** | C-Series, Q-Series, P-Series | `debloat_universal.sh (Profile B)` | Fully Supported |
| **Sony Bravia** | XR, X80, X90 Series | `debloat_universal.sh (Profile C)` | Fully Supported |
| **Philips Android TV** | Ambilight / OLED Series | `debloat_universal.sh (Profile D)` | Fully Supported |
| **Xiaomi** | Mi TV 4A/4S/5, TV Box S, TV Stick | `debloat_universal.sh (Profile E)` | Fully Supported |
| **Hisense / Toshiba** | A6/U6/U7 Android Editions | `debloat_universal.sh (Profile F)` | Fully Supported |
| **Google** | Chromecast with Google TV (HD/4K) | `debloat_universal.sh (Profile G)` | Fully Supported |

---

## 🔒 Privacy & Cleanliness Guarantee
This repository contains **NO personal user credentials, tokens, IP addresses, proprietary signatures, or private watermarks**. All drawables use clean open-source SVGs, and all scripts operate strictly on public system APIs.

---

## 📜 License & Acknowledgments
- Distributed under the **Apache 2.0 License**. See [LICENSE](LICENSE) for details.
- Special thanks to [**CleanYourTV**](https://github.com/Gathin23/cleanyourtv) by Gathin23 for the original project concept.
