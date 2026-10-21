#!/usr/bin/env bash
# TV Master Launcher — Private DNS (DoT) & CloudStream TurkSinema Setup
# Configures dns.google over TLS to bypass Turkish ISP DNS poisoning (195.175.254.2),
# and establishes the 5 chosen movie providers.
set -e

echo "==> [DNS & Streaming Setup]"

# 1. Configure Private DNS over TLS (DoT) via dns.google
echo "==> Setting Private DNS to dns.google over TLS..."
adb shell "settings put global private_dns_mode hostname"
adb shell "settings put global private_dns_specifier dns.google"

echo "==> Verifying Private DNS configuration..."
MODE=$(adb shell "settings get global private_dns_mode" | tr -d '\r')
SPEC=$(adb shell "settings get global private_dns_specifier" | tr -d '\r')
echo "    private_dns_mode:      $MODE"
echo "    private_dns_specifier: $SPEC"

# 2. Add TurkSinema Repository to CloudStream 3
REPO_URL="cloudstreamrepo://raw.githubusercontent.com/Wiojelt/TurkSinema/main/repo.json"
echo "==> Opening TurkSinema repo in CloudStream..."
adb shell "am start -a android.intent.action.VIEW -d \"$REPO_URL\" 2>/dev/null || true"

echo "==> Target 5 Providers Configuration:"
echo "    1. Webteizle"
echo "    2. Filmmodu"
echo "    3. FilmMakinesi"
echo "    4. HDFilmCehennemi"
echo "    5. FullHDFilmizlesene"
echo "All other sources should be uninstalled from CloudStream settings."

echo "==> Setup Complete!"
