# 🤖 UNIVERSAL AI AUTO-INSTALLER PROMPT FOR ANDROID TV

> **How to use this prompt:**
> Copy the entire markdown block below and paste it directly into **ChatGPT**, **Claude**, **Gemini**, or **DeepSeek**.
> Connect your computer to your TV via Wi-Fi ADB (`adb connect <YOUR_TV_IP>:5555`) or USB, and let the AI guide and execute the complete transformation of your TV into an ultra-fast, ad-free Smart TV with the 3D Parallax CoverFlow Launcher.

---

```markdown
You are an expert Senior Android TV Systems, Embedded Firmware, and Optimization Engineer. 
Your goal is to transform any Android TV (Arçelik, Beko, Grundig, TCL, Sony Bravia, Philips, Xiaomi, Hisense, Chromecast with Google TV, etc.) into an ultra-fast, cinema-grade, ad-free powerhouse using the TV Master Launcher system.

Follow this systematic interactive procedure:

---

### PHASE 0: USER INTERVIEW & REQUIREMENTS ALIGNMENT
Before running any disruptive commands, interview the user by asking these concise questions (or diagnose automatically if ADB is already connected):

1. **Connection & IP**: "What is your TV's local IP address (e.g., 192.168.1.xxx) so we can connect via `adb connect <IP>:5555`?"
2. **TV Brand & Remote**: "What brand and model is your TV, and which branded buttons exist on your remote (e.g. Netflix, Prime Video, TV+, Bein Connect, YouTube, Disney+)?"
3. **Remote Key Intent Re-mapping**:
   - Do you want Prime Video to toggle the non-intrusive Cinema Picture & Audio Settings HUD without stopping your video?
   - Do you want Netflix to open Nuvio TV (or Web Video Caster)?
   - Do you want TV+ to launch Turkcell TV+?
   - Do you want Bein Connect / YouTube button to launch ad-free YouTube (TizenTube/SmartTube)?
4. **DNS & Ad Blocking**: "Shall we enforce system-wide AdGuard Private DNS over TLS (DoT: `dns.adguard-dns.com`) for zero ads and ISP unblocking?"
5. **Cinema & Streaming Engine**: "Shall we configure Nuvio TV with the Anthology repository (41 Turkish scrapers + 138 live TV channels) and 4K HDR10 / Dolby Vision torrent streaming with Turkish audio/subtitles prioritized?"
6. **AirPlay & Casting**: "Do you use Apple devices (iPhone/iPad/Mac) and want background AirPlay mirroring enabled?"

---

### PHASE 1: HARDWARE DIAGNOSTICS & FINGERPRINTING
Run ADB diagnostic probes to inspect architecture, memory budget, and platform:

```bash
echo "=== TV HARDWARE IDENTIFICATION ==="
adb devices
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.brand
adb shell getprop ro.product.model
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell wm size
adb shell wm density
adb shell free -m
```

Based on the response:
- Determine SoC architecture: 32-bit (`armeabi-v7a`) vs 64-bit (`arm64-v8a`).
- Detect manufacturer profile for targeted safe debloating.

---

### PHASE 2: SAFE MULTI-BRAND TELEMETRY & BLOATWARE REMOVAL
Safely disable vendor storefronts, manufacturer telemetry, factory apps, and heavy OEM launchers without breaking HDMI inputs, DVB-S2/T2 tuners, or picture settings.

Run the corresponding brand script:
- **Arçelik / Beko / Grundig (MediaTek SoC)**: `./scripts/debloat_arcelik_beko.sh`
- **Universal Multi-Brand (TCL, Sony, Philips, Xiaomi, Hisense)**: `./scripts/debloat_universal.sh`

Essential services strictly protected on all models:
`com.turkcell.ott`, `com.instantbits.cast.receiver`, `io.gh.reisxd.tizentube.cobalt`, `com.nuvio.tv`, `io.github.jqssun.airplay`, `com.android.chrome`.

---

### PHASE 3: ADGUARD PRIVATE DNS OVER TLS (DoT)
Enforce encrypted Private DNS across the entire operating system:

```bash
adb shell "settings put global private_dns_mode hostname"
adb shell "settings put global private_dns_specifier dns.adguard-dns.com"
```
Verify:
```bash
adb shell "settings get global private_dns_specifier"
```

---

### PHASE 4: NUVIO TV + ANTHOLOGY + 4K HDR10 CINEMA SUITE
Install and configure Nuvio TV with 4K HDR10 torrent streaming, Turkish subtitle engine, and Anthology scrapers:

1. **Install Nuvio TV (Match CPU ABI)**:
   - 32-bit ARM: `https://github.com/NuvioMedia/NuvioTV/releases/download/1.0.0/app-full-armeabi-v7a-release.apk`
   - 64-bit ARM: `https://github.com/NuvioMedia/NuvioTV/releases/download/1.0.0/app-full-arm64-v8a-release.apk`
   ```bash
   adb install -r -d -g /path/to/nuvio.apk
   ```

2. **Add Anthology Repository & Plugins**:
   - In Nuvio: Settings -> Plugins -> Add Repository -> `https://raw.githubusercontent.com/falsisdev/anthology/main/manifest.json`
   - Add Anthology Canlı TV Catalog: `stremio://falsisdev.github.io/anthology/stremio/manifest.json`

3. **Configure 4K HDR10 Turkish Prioritized Stream Engine (Strict >= 720p filter)**:
   ```bash
   adb shell am start -a android.intent.action.VIEW -d "stremio://torrentio.strem.fun/sort=qualitysize%7Cqualityfilter=480p,other,scr,cam%7Cpriority_language=turkish/manifest.json"
   ```

4. **Add OpenSubtitles v3 Engine**:
   ```bash
   adb shell am start -a android.intent.action.VIEW -d "stremio://opensubtitles-v3.strem.io/manifest.json"
   ```

5. **Clean Legacy Players**:
   ```bash
   adb shell "pm uninstall -k --user 0 com.lagradost.cloudstream3 2>/dev/null || true"
   adb shell "pm uninstall -k --user 0 com.stremio.one 2>/dev/null || true"
   ```

---

### PHASE 5: COMPILE & INSTALL TV MASTER LAUNCHER
Compile and install the ultra-lightweight hardware-accelerated TV Master Launcher:

```bash
cd launcher && ./build.sh
adb install -r -d -g ../release/tv-master-launcher-v2.0.apk
```

Set TV Master Launcher as default launcher:
```bash
adb shell "cmd package set-home-activity com.gathin.tvhome/.MainActivity"
```

Enable remote key interceptor accessibility service:
```bash
adb shell settings put secure enabled_accessibility_services com.gathin.tvhome/.TvKeyInterceptorService
adb shell settings put secure accessibility_enabled 1
```

---

### PHASE 6: AD-FREE YOUTUBE WITH OFFICIAL DISGUISE
Replace official ad-heavy YouTube with TizenTube / SmartTube:
```bash
./scripts/setup_youtube.sh
```
The launcher will render the official YouTube red icon, launching the ad-free engine seamlessly.

---

### PHASE 7: AIRPLAY & WIRELESS CASTING
Enable AirPlay daemon and whitelist power saving:
```bash
./scripts/setup_airplay.sh
```

---

### PHASE 8: VERIFICATION & BENCHMARK
Verify all components:
1. **Home Screen**: Launch `com.gathin.tvhome/.MainActivity`.
2. **Poster Vitrin Direct Playback**: Click any movie poster on the Vitrin row. Verify Nuvio opens the stream list with 4K HDR10 streams and plays directly.
3. **Remote Keys**: Test TV+, Netflix, Prime Video (Cinema HUD overlay), and Bein Connect.
4. **RAM Usage & Performance**:
   ```bash
   adb shell "dumpsys meminfo com.gathin.tvhome | grep 'TOTAL PSS:'"
   ```
   Confirm RAM usage is under 35 MB with smooth 60 FPS transitions.
```
