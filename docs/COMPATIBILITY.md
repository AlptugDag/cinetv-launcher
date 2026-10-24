# CineTV Launcher: Multi-OEM Hardware & Android OS Compatibility Matrix

This document defines the formal compatibility matrix, OEM-specific architectural behavior, remote control keycode normalizations, and permission models across major smart TV manufacturers and Android TV OS versions.

---

## 1. OEM Hardware Manufacturer Matrix

| Manufacturer | Hardware Platform | Tested OS Versions | Remote Keycode Quirks | Protected Vendor Services (Must NOT be debloated) | Status |
|---|---|---|---|---|---|
| **Arçelik / Beko / Grundig** | MediaTek MT5867 / MT9638 Quad-Core ARM Cortex-A53 | Android TV 9.0, 11 | Netflix: `207`/`188`, TV+: `170`/`186`/`292` | `com.mediatek.wwtv.tvcenter`, `com.mediatek.tv.factory` (HDMI/Tuner HAL) | **Verified Live Hardware** (Daily Driver) |
| **Sony Bravia** | MediaTek / XR Cognitive Processor (ARM64) | Android TV 9, 10, Google TV (Android 11) | Netflix: `191`, YouTube: `192`, Prime: `193` | `com.sony.dtv.networkservice`, `com.sony.dtv.tvx`, `com.sony.dtv.irbservice` | **Fully Supported** |
| **TCL / Thomson** | Realtek RTD2851 / MediaTek Quad-Core | Android TV 9, 11, Google TV (Android 12) | Netflix: `222`, YouTube: `224`, Prime: `225`, TV: `226` | `com.tcl.tvmanager`, `com.tcl.sourcemanager`, `com.tcl.cyberui` | **Fully Supported** |
| **Philips (TP Vision)** | MediaTek MT5895 / MT9970 | Android TV 9, 10, 11 | Ambilight Key: `230`, Netflix: `191` | `org.droidtv.tvpowermanager`, `org.droidtv.channels`, Ambilight HAL | **Fully Supported** |
| **Xiaomi (Mi TV / TV Box S)** | Amlogic S905X / S905X4 | Android TV 9, 10, 11, 12 | PatchWall Key: `275`, Netflix: `191` | `com.xiaomi.mitv.tvservice`, `com.xiaomi.mitv.upgrade` | **Fully Supported** |
| **Google Chromecast** | Amlogic S905D3 / S905X5 (Sabrina) | Google TV (Android 12, 14) | Assistant Key: `231`, YouTube: `192` | Core Google TV Play Services | **Supported (Requires App Ops grant)** |
| **NVIDIA SHIELD TV** | NVIDIA Tegra X1+ (arm64-v8a) | Android TV 9, 11 (SHIELD Experience 9.x) | Menu Key: `82`, Netflix: `191` | `com.nvidia.shield.remote`, Tegra display services | **Fully Supported** |
| **Hisense (Android models)** | MediaTek / Novatek | Android TV 9, 10 | Live TV: `170`, Netflix: `191` | `com.hisense.tv.service`, `com.jamdeo.tv.service` | **Supported** |

---

## 2. Android OS Version Architectural Roadmap

```
+---------------------------------------------------------------------------------------+
|  Android TV 9 (Pie) / 10 (Q)                                                          |
|  - Full standard AccessibilityService support without sideload restriction.           |
|  - Non-root input injection and key interception works out of the box.                |
|  - AOT compilation: 'cmd package compile -m speed com.gathin.tvhome' supported.       |
+---------------------------------------------------------------------------------------+
|  Android TV 11 (Red Velvet Cake) / 12 (Snow Cone)                                     |
|  - Scoped storage enforced: TMDB cache stored strictly in app internal cacheDir.      |
|  - Package visibility requires explicit queries or QUERY_ALL_PACKAGES for app dock.    |
|  - Splash screen API introduced; CineTV bypasses splash via zero-wait theme.         |
+---------------------------------------------------------------------------------------+
|  Android TV 13 (Tiramisu) / 14 (Upside Down Cake)                                     |
|  - Restricted Settings Policy: Sideloaded APKs block AccessibilityService by default.  |
|  - Automatic Detection: OemCompatibilityManager detects SDK >= 33.                    |
|  - One-Time ADB Unlock Routine:                                                       |
|    adb shell appops set com.gathin.tvhome ACCESS_RESTRICTED_SETTINGS ALLOW            |
+---------------------------------------------------------------------------------------+
```

---

## 3. Remote Control Keycode Normalization

Different OEM remotes transmit different keycodes when physical branded buttons are pressed. The CineTV [OemCompatibilityManager](file:///Users/alptug/.gemini/antigravity/scratch/tv-master-launcher/launcher/src/com/gathin/tvhome/OemCompatibilityManager.java) normalizes these inputs:

| Logical Target Action | Standard Android KeyCode | Sony Bravia KeyCodes | TCL / Thomson KeyCodes | MediaTek / Arçelik KeyCodes |
|---|---|---|---|---|
| **Nuvio 4K Cinema** | `KEYCODE_NETFLIX` (`207`) | `191` | `222` | `188`, `207` |
| **Ad-Free YouTube / TizenTube** | `KEYCODE_GUIDE` / `172` | `192` | `224` | `192`, `224` |
| **Web Video Caster (WVC)** | `KEYCODE_PROG_BLUE` (`401`) | `193` | `225` | `193`, `225` |
| **Turkcell TV+ / Live TV** | `KEYCODE_TV` (`170`) | `194` | `226` | `170`, `186`, `292`, `401` |

---

## 4. Display Density & Resolution Scaling

CineTV adapts layout mathematics based on the screen density reported by the hardware:

| Resolution Category | Panel Pixels | Reported Density (DPI) | Poster Card Geometry | 3D Perspective Distance |
|---|---|---|---|---|
| **Standard Android TV (FHD)** | 1920 x 1080 | 320 dpi (`density = 2.0`) | 260 x 390 px | `5600 px` (`2800 * 2.0`) |
| **Native 4K Ultra-HD UI** | 3840 x 2160 | 640 dpi (`density = 4.0`) | 520 x 780 px | `11200 px` (`2800 * 4.0`) |
| **720p Smart Projector** | 1280 x 720 | 213 dpi (`density = 1.33`) | 173 x 260 px | `3724 px` (`2800 * 1.33`) |

> [!NOTE]
> All poster cards maintain an exact **2:3 cinematic aspect ratio** ($w / h = 0.6667 \pm 0.005$) regardless of screen density.

---

## 5. Non-Destructive Debloat Safety Policy

CineTV implements strict hardware protection rules in [tv_safety.sh](file:///Users/alptug/.gemini/antigravity/scratch/tv-master-launcher/scripts/lib/tv_safety.sh):
1. **Never Delete**: System partition binaries are never removed.
2. **Reversible**: Packages are uninstalled only for user 0 (`pm uninstall -k --user 0`).
3. **1-Click Restore**: Running `./scripts/restore_all_tv_apps.sh` immediately reinstalls all factory apps from the system image.
4. **Hardware Whitelist**: HDMI HAL, DVB tuners, CEC control, Bluetooth remotes, and vendor audio DSPs are hard-coded as untouchable.
