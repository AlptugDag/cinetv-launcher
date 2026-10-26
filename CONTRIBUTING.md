# Contributing to CineTV

Thanks for helping make TVs feel like a cinema. This page is everything you need to build, test
and send a change.

## Build

Requirements: JDK 17 and the Android SDK (platform 34, build-tools 34). Android Studio works, so
does the command line:

```bash
./gradlew :launcher:assembleDebug        # debug APK, signed with the shared debug key
./gradlew :launcher:testDebugUnitTest    # unit tests
./install.sh --apk launcher/build/outputs/apk/debug/launcher-debug.apk --tv 192.168.1.50
```

Release builds need your own signing key; create one with `scripts/release/create_keystore.sh` and check builds with `scripts/release/verify_apk.sh`.

## Where things live

The app is split into layers; [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) has the full map.

| Package | Owns |
|---|---|
| `ui/` | Everything drawn: home screen, DVD shelf, glass panels, screensaver |
| `service/` | Apps, films (TMDB / Cinemeta), themes, weather, encrypted secrets |
| `ai/` | Search understanding, AI curation, the Gemini / Claude client |
| `remote/` | The phone companion: HTTP server, pairing, web app |
| `device/` | Remote buttons, picture calibration, memory, performance tiers |

`MainActivity` only wires these together. If a change makes it grow, the logic belongs in a layer.

## Rules that keep TVs safe and fast

- **Measure on a weak TV.** CineTV must stay smooth on a 1 GB, 4-core TV. New visuals go through
  `PerformanceProfile` and must not allocate in `onDraw`. Check with
  `adb shell dumpsys gfxinfo com.cinetv.launcher` before and after.
- **Never uninstall system packages.** Scripts disable with `pm disable-user`, which the TV's own
  settings can undo. `scripts/lib/tv_safety.sh` refuses packages a TV cannot boot without; do not
  bypass it.
- **No secrets in the repo.** API keys, keystores, `keystore.properties`, pairing tokens and personal
  branding never go into git. CI runs a secret scan and fails on findings.
- **Every user-facing string goes in `res/values/strings.xml`** (English) with a Turkish
  translation in `values-tr`. Screensaver lines live in `quotes.xml` in every language.

## Pull requests

CineTV is source-available ([LICENSE](LICENSE)), not open for forks: suggestions and fixes are welcome as
issues or pull requests to this repository, and a merged change becomes part of CineTV under its terms. Do not
publish changed copies or forks.

1. One topic per pull request, with a short description of what changed and why.
2. Include a screenshot or a short screen recording for anything visual, taken on a real TV
   (`adb exec-out screencap -p > shot.png`).
3. `./gradlew :launcher:testDebugUnitTest :launcher:assembleDebug` passes.
4. Mention the TVs you tested on in the description.
