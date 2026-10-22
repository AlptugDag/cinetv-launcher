# 📺 TV Master Launcher

[![Release](https://img.shields.io/badge/Release-v1.0.0-blue.svg)](release/tv-master-launcher-v1.0.0.apk)
[![Platform](https://img.shields.io/badge/Platform-Android%20TV%209%2B%20%7C%20Google%20TV-green.svg)](#supported-devices)
[![Architecture](https://img.shields.io/badge/Arch-armeabi--v7a%20%7C%20arm64--v8a-orange.svg)](#architecture)
[![Based on](https://img.shields.io/badge/Origin-CleanYourTV-blueviolet.svg)](https://github.com/Gathin23/cleanyourtv)
[![License](https://img.shields.io/badge/License-Apache%202.0-yellow.svg)](LICENSE)

<p align="center">
  <a href="#-topics--tags"><code>#android-tv</code></a> •
  <a href="#-topics--tags"><code>#google-tv</code></a> •
  <a href="#-topics--tags"><code>#tv-launcher</code></a> •
  <a href="#-topics--tags"><code>#nuvio</code></a> •
  <a href="#-topics--tags"><code>#android-tv-launcher</code></a> •
  <a href="#-topics--tags"><code>#leanback</code></a> •
  <a href="#-topics--tags"><code>#4k-hdr</code></a> •
  <a href="#-topics--tags"><code>#debloat</code></a>
</p>

> **Next-generation, bloatware-free, hardware-accelerated Android TV operating suite.**  
> Features a stunning **Liquid Glass 3D Parallax CoverFlow** vitrin with dynamic focal neon poster glow, launch-time live catalog auto-refresh, **Nuvio TV + Anthology** 4K HDR10/Dolby Vision cinema suite (quality & seeders first), **Real-Time Live Weather**, **AdGuard Private DNS over TLS (DoT)**, **Native AirPlay receiver**, universal multi-brand debloating, and OLED burn-in safe drifting screensaver.

---

### 🏷️ Topics & Tags
`android-tv` `google-tv` `tv-launcher` `nuvio` `nuvio-tv` `androidtv-launcher` `custom-launcher` `leanback` `4k-hdr10` `dolby-vision` `debloat-android-tv` `smart-tv` `cinema-suite` `coverflow` `arcelik-beko-tv`

---

---

> [!NOTE]
> **Attribution & Origin**: This project is built upon the wonderful foundation of [**CleanYourTV**](https://github.com/Gathin23/cleanyourtv) by Gathin23. We have re-engineered and expanded it into **TV Master Launcher v1.0.0**, introducing hardware Z-order 3D CoverFlow rendering, dynamic 12-bin focal neon glow extraction, Nuvio & Anthology direct 4K playback integration, AdGuard DoT security, multi-vendor memory optimization, an interactive personalized setup wizard, and zero security vulnerabilities.

---

## 🌟 Visual Showcase

### Dynamic Liquid Glass Ambient Glow in Motion
Focus movement smoothly shifts depth scaling, 3D elevation, and poster-aware focal neon chromatic blooms in real time:

![Liquid Glass Glow Animation](docs/screenshots/liquid_glass_glow.gif)

---

### Diversified System Views

| 🎴 3D Parallax CoverFlow Vitrin | ⚙️ In-Video Cinema HUD Overlay |
| :---: | :---: |
| ![Hero CoverFlow](docs/screenshots/hero_coverflow_3d.png) | ![Cinema HUD Overlay](docs/screenshots/cinema_hud_overlay.png) |
| **🚀 Liquid Glass Dock (Nuvio Wordmark)** | **🌙 Modern OLED Drifting Clock Screensaver** |
| ![Dock Showcase](docs/screenshots/dock_nuvio_showcase.png) | ![Modern Screensaver](docs/screenshots/modern_screensaver.png) |

---

### Focal Neon Accent Extraction (Poster-Aware Chromatic Bloom)
| Crimson Neon | Warm Amber Neon | Electric Cyan Neon |
| :---: | :---: | :---: |
| ![Crimson Neon](docs/screenshots/focal_neon_crimson.png) | ![Warm Amber Neon](docs/screenshots/focal_neon_amber.png) | ![Electric Cyan Neon](docs/screenshots/focal_neon_cyan.png) |

---

## ⚡ Key Highlights & Architecture

### 1. 🎴 Liquid Glass 3D Parallax CoverFlow Vitrin
- **Hardware Z-Ordering**: Employs Android RenderNode `elevation` (`50f`) and `translationZ` (`65f`) rather than modifying ViewGroup hierarchy, guaranteeing smooth 60 FPS D-pad navigation.
- **Dynamic Depth Scaling**: Focused poster scales to **1.30x** with a `-22f` vertical lift; neighboring posters smoothly recede to **0.94x**, **0.80x**, and **0.68x** with physical drop shadows and distance alpha dimming.
- **12-Bin Focal Neon Histogram**: Replaces generic dominant color math with a 12-bin chromatic histogram. Dampens skin tones by 65% to isolate pure graphic neon accents (Crimson, Cyan, Emerald, Gold, Cobalt, Violet).
- **Hue Separation Rule**: Enforces a minimum `48°` hue delta between adjacent posters, ensuring adjacent cards never share the same ambient glow.
- **Dynamic Launch-Time Feed Auto-Refresh**: Every time the TV wakes or the launcher returns to the foreground (`onResume`), the vitrin feed is checked in the background. If new movies are released on Webteizle (or Cinemeta for international setups), posters, titles, and IMDb IDs update dynamically with zero UI stutter.
- **Direct 4K Stream Playback Integration**: Clicking any poster directly initiates playback in **Nuvio TV** (`launchMode="stream"` with exact `contentId` and `name`), prioritizing 4K HDR10 streams immediately.

### 2. 🍿 Nuvio TV + Cinema Suite & Strict Seeder Sorting
- Integrates **Nuvio TV** (`com.nuvio.tv`) with the verified **Anthology** repository (41 scrapers + 138 Live TV channels for TR) or Cinemeta/CyberFlix (for international users).
- Official Nuvio wordmark tile (`ic_nuvio_wordmark`) rendered on Apple visionOS liquid glass without redundant duplicate text.
- **Dynamic 8-Genre Vitrin Rotation**: On every TV boot or launcher return, the vitrin dynamically cycles across 8 curated cinema categories (Vizyondakiler & Popüler, Aksiyon & Macera, Bilim-Kurgu, Animasyon & Aile, Gerilim, Suç & Gizem, Fantastik Dünyalar, IMDb En İyiler).
- **Strict Torrent Sorting & Filtering Hierarchy**:
  1. Quality Tier: `4K HDR10 / Dolby Vision` > `1080p HDR10` > `1080p` > `720p`.
  2. Language Priority: User-defined spoken languages prioritized for dubbing and subtitles (e.g. `turkish,english`, `german,english`, `spanish,latino`).
  3. **Torrent Seeder Hierarchy**: Within each quality tier, torrent videos are strictly sorted **first by seeder count, then by file size** (`sort=quality`).
  4. Exclusions: Formats below 720p (480p, 360p, CAM, SCR, etc.) are strictly excluded.
- **Nova Video Player Integration (`scripts/setup_nova_player.sh`)**: If Nuvio TV ever encounters a playback, sync, or codec error on heavy 4K REMUX or obscure audio codecs, users can seamlessly switch to Nova Video Player (`org.courville.nova`) as an external player directly from Nuvio settings.
- **Hardware Cinema Calibration & Auto Frame Rate (AFR) (`scripts/setup_nuvio_presets.sh`)**: Calibrates the TV display and audio pipeline to cinema standards, enabling hardware Auto Frame Rate matching (`match_content_frame_rate=1`), bit-perfect digital audio passthrough (`sound_digital_output=5`), Dolby Atmos, and HDR10+ dynamic tone mapping.
- Completely replaces and retires legacy players (CloudStream, Stremio) cleanly.

### 3. 🌤️ Real-Time Live Weather & Location Awareness
- **Keyless Zero-Latency Weather**: Fetches real-time local weather and conditions using keyless `wttr.in` integration.
- **Top Bar Integration**: Displays current temperature, weather emoji, and city (e.g. `Ankara • +7°C ✨` or `London • +12°C 🌧️`) directly next to the date/clock.
- Auto-refreshes every 30 minutes in the background with zero battery or RAM overhead.
- City can be configured via installer, auto-detected from IP, or changed on the TV screen via the Settings dialog.

### 4. 🌓 Theme Customization: Dark, Auto Day/Night & Light Glass
- **Saf OLED Siyah (Deep Obsidian Dark Noir)**: Absolute pitch-black cinema canvas optimized for OLED/QLED panels with zero light bleed.
- **Otomatik Gece / Gündüz (Auto Day/Night)**: Frosted silver glass theme during daytime hours, automatically transitioning to pure OLED obsidian at dusk.
- **Aydınlık Buzlu Cam (Modern Frosted Light Glass)**: High-contrast modern platinum silver palette with deep obsidian typography.
- Changeable on the fly from the TV remote or installation wizard.

### 5. 🎮 Native Remote Control Layout Preservation
- **Zero Remote Key Tampering**: TV Master Launcher strictly preserves your TV's original hardware remote control keycodes, branded buttons, and behaviors.
- Does not hijack, intercept, or overwrite any dedicated hardware remote buttons (Netflix, Prime Video, YouTube, TV+, etc.).
- Picture, sound, and quick settings remain seamlessly accessible on-screen via the top-bar Quick Settings gear icon without requiring background accessibility services.

### 6. 🔒 AdGuard Private DNS over TLS (DoT)
- Configures `dns.adguard-dns.com` system-wide over TLS.
- Blocks ads, telemetry, and tracking at the network level, while bypassing ISP DNS tampering/poisoning.

### 7. 📲 Native AirPlay & Wireless Casting
- Built-in support for **AirPlay** audio and video mirroring directly from iOS, iPadOS, and macOS devices.
- Battery-idle exemptions and persistent daemon management keep AirPlay discovery active 24/7.

### 8. 🚀 Smart RAM & Storage-to-RAM Expansion
- **1024 MB LZ4 zRAM Swap**: Creates an in-kernel compressed memory disk on `/dev/block/zram0`.
- **Swappiness & Cache Tuning**: Sets `vm.swappiness=80` and `vfs_cache_pressure=100`, keeping UI threads instantly responsive while moving inactive background services to zRAM.
- **Low Memory Killer (LMK) Optimization**: Sets `sys.sysctl.extra_free_kbytes=24300` and enables `trim_empty_apps`, cutting launcher RAM usage to under **35 MB**.
- **Storage-to-RAM Virtual Allocation**: Enables Android 11+ virtual memory expansion (`ram_expand_size_list: 2048`).

### 9. 🛡️ Universal Multi-Brand Debloater & Custom App Removal
Safely removes factory menus, telemetry, and pre-installed storefront bloatware for user 0 without breaking HDMI ports, DVB-S2 satellite tuners, or picture settings:
- **Targeted Brand Debloaters**: Arçelik/Beko, TCL, Sony Bravia, Philips, Xiaomi, Hisense, and Chromecast with Google TV.
- **Custom Interactive Removal**: Inspects all user and vendor packages on the TV and allows the user to choose exact packages to remove or keep.
- **Hardened Safety Guard**: Protected hardware packages (HDMI, DVB, Audio, Bluetooth, Settings, SystemUI) can never be accidentally removed.
- **TV-Core Lockout (`scripts/lib/tv_safety.sh`)**: Every removal path (brand lists, interactive input) goes through one shared guard that refuses OEM TV-core packages such as `com.mediatek.wwtv.tvcenter` (+ `.rro`), `com.mediatek.tv.*`, `com.tcl.tvmanager`, etc. Removing these breaks HDMI inputs, picture/HDR settings and display configuration.
- **Self-Healing (`scripts/restore_tv_core.sh`)**: Re-enables / re-installs (for user 0) any TV-core package an older version of this project or another tool disabled. It runs automatically at the end of both debloaters and the wizard, and can be run manually (`adb reboot` afterwards).

### 9b. 🔬 Display & HDR Verification (`scripts/verify_display.sh`)
```bash
./scripts/verify_display.sh                  # quick health report
./scripts/verify_display.sh --playback-test  # plays a real 4K HEVC HDR10 clip and measures it
```
> **"My TV shows 1080p60 / no HDR in Nuvio or Developer options — is it broken?"**
> Usually **no**. On MediaTek TVs (Arçelik, Beko, Grundig, Vestel, ...) Android deliberately renders the *UI* at 1920×1080@60 and exposes only that one display mode (see `/vendor/etc/uhd_keeping_packages.xml`). Video is decoded at native resolution and shown on a separate hardware video plane that is scaled to the 4K panel, with HDR10/HLG signalled through the video path. The `--playback-test` is the authoritative check: it looks for a `3840x2160` video layer and the `BT2020_ITU_PQ` (HDR10) dataspace. If the quick report shows a disabled TV-core package, run `./scripts/restore_tv_core.sh` and reboot.

### 10. 🌙 OLED Burn-In Safe Drifting Screensaver
- Modern minimalist screensaver with Poppins typography and luminous radial chromatic glow.
- Continuous interpolated drift animation prevents static burn-in on OLED and QLED panels.

---

## 🚀 One-Command Universal Setup Wizard

TV Master Launcher v1.0.0 includes an interactive, bilingual setup wizard that automatically fingerprints your hardware, asks your regional preferences, and applies the complete transformation:

```bash
git clone https://github.com/AlptugDag/tv-master-launcher.git
cd tv-master-launcher
./install.sh
```

### Wizard Features:
- **Language & Regional Persona**:
  - Country selection (TR, US, DE, GB, FR, ES, IT, or custom ISO): automatically tailors cinema feeds, live TV catalogs, and vitrin.
- **Live Weather Integration**:
  - Prompts for your city (e.g. Istanbul, Ankara, Berlin, London, New York) or auto-detects from IP; displays real-time weather on the launcher top bar.
- **Personalized Audio & Subtitle Languages**:
  - Allows specifying multiple languages you speak (e.g. `turkish,english`, `german,english`, `spanish,latino`). Constructs Torrentio stream engine with seeder-first sorting for those exact languages.
- **Interface Theme Selection**:
  - Pure OLED Dark Noir, Auto Day/Night (solar sync), or Modern Frosted Light Glass.
- **Interactive Debloat & App Removal**:
  - Choose between recommended safe brand debloat, custom interactive package removal (with safety guards for core services), or skip.
- **DNS & Ad Blocking**:
  - AdGuard DoT, Cloudflare DoT, or keep existing.
- **Untouched Remote Controls**:
  - Zero interference with hardware remote control buttons.

---

## 🤖 Universal AI Auto-Installation

You can also transform your TV using any modern conversational AI (ChatGPT, Claude, Gemini, DeepSeek):

1. Connect your computer to your TV over Wi-Fi ADB:
   ```bash
   adb connect <YOUR_TV_IP>:5555
   ```
2. Open [`AI_AUTO_INSTALL_PROMPT.md`](AI_AUTO_INSTALL_PROMPT.md).
3. Copy the prompt and paste it into your AI assistant.
4. The AI will interview you, inspect hardware, select the correct debloat profile, boost RAM, install the launcher, and configure cinema streaming sources automatically.

---

## 📂 Repository Structure

```
tv-master-launcher/
├── AI_AUTO_INSTALL_PROMPT.md        # Master interactive prompt for AI models
├── LICENSE                          # Apache 2.0 License
├── README.md                        # Documentation & visual showcase
├── install.sh                       # One-command interactive setup wizard (symlink)
├── release/
│   └── tv-master-launcher-v1.0.0.apk # Pre-built, signed, production APK (v1.0.0)
├── docs/
│   └── screenshots/                 # Diversified captures & animated glow GIF
│       ├── liquid_glass_glow.gif    # Animated focal neon bloom transitions
│       ├── hero_coverflow_3d.png    # 3D Parallax vitrin with 1.30x focal card
│       ├── cinema_hud_overlay.png   # In-video picture/sound settings HUD
│       ├── dock_nuvio_showcase.png  # Apple visionOS dock with Nuvio wordmark
│       ├── modern_screensaver.png   # Minimalist OLED drifting screensaver
│       ├── focal_neon_amber.png     # Amber neon bloom extraction
│       ├── focal_neon_crimson.png   # Crimson neon bloom extraction
│       └── focal_neon_cyan.png      # Cyan neon bloom extraction
├── launcher/                        # Complete Android Studio / Gradle source code
│   ├── AndroidManifest.xml          # v1.0.0 (versionCode 100)
│   ├── build.sh                     # Fast APK compiler script
│   ├── src/                         # Java sources (CoverFlow, KeyInterceptor, Shaders)
│   ├── res/                         # Vector drawables, layouts, themes
│   └── assets/                      # High-res poster artwork & fallback database
└── scripts/                         # Shell automation scripts
    ├── install.sh                   # Master interactive setup wizard
    ├── debloat_universal.sh         # Auto-detects brand & removes bloat
    ├── debloat_arcelik_beko.sh      # Arçelik / Beko specific debloat script
    ├── restore_tv_core.sh           # Re-enable OEM TV-core packages (self-heal)
    ├── verify_display.sh            # Display / HDR / TV-core health + 4K HDR10 playback test
    ├── lib/tv_safety.sh             # Shared protected-package guard & safe helpers
    ├── optimize_ram.sh              # zRAM, swappiness, LMK & memory tuning
    ├── setup_dns_and_streaming.sh   # AdGuard DoT & Nuvio 4K HDR10 setup
    ├── setup_nuvio_presets.sh       # Nuvio addon presets & cinema hardware AFR calibration
    ├── setup_nova_player.sh         # Nova Video Player fallback integration
    ├── setup_youtube.sh             # Ad-free YouTube disguise installer
    └── setup_airplay.sh             # AirPlay receiver configuration
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

## 🔒 Security & Privacy Guarantee
- **Zero Security Vulnerabilities**: All shell scripts employ strict variable quotation, input sanitization (regex validation for IPs), safe temporary file handling with automatic exit traps, and zero arbitrary shell execution within Android components.
- **No Private Data**: Contains **NO personal credentials, accounts, tokens, proprietary logos, or private watermarks**.
- All components operate strictly through standard, public Android SDK and Leanback APIs.

---

## 📜 License & Acknowledgments
- Distributed under the **Apache 2.0 License**. See [LICENSE](LICENSE) for details.
- Special thanks to [**CleanYourTV**](https://github.com/Gathin23/cleanyourtv) by Gathin23 for the original project foundation.
