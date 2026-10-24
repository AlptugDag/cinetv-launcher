#!/usr/bin/env bash
# ==============================================================================
# CineTV Launcher — Standalone Test Runner
# Compiles and executes unit and security tests in seconds.
# ==============================================================================
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

# Detect Java
if ! javac -version >/dev/null 2>&1; then
    for cand in \
        "${JAVA_HOME:-}/bin" \
        "/opt/homebrew/opt/openjdk@17/bin" \
        "/opt/homebrew/opt/openjdk/bin" \
        "/usr/local/opt/openjdk@17/bin" \
        "/usr/lib/jvm/java-17-openjdk-amd64/bin" \
        "/usr/lib/jvm/java-17-openjdk/bin" \
        "/usr/lib/jvm/default-java/bin"; do
        if [ -n "$cand" ] && [ -x "$cand/javac" ]; then
            export PATH="$cand:$PATH"
            export JAVA_HOME="$(dirname "$cand")"
            break
        fi
    done
fi

if ! javac -version >/dev/null 2>&1; then
    echo "ERROR: javac not found."
    exit 1
fi

BUILD_DIR="build/test_classes"
mkdir -p "$BUILD_DIR"
rm -rf "$BUILD_DIR"/*

echo "==> Compiling test suites with javac..."
javac -d "$BUILD_DIR" tests/*.java

echo "==> Executing SecurityTest..."
java -cp "$BUILD_DIR" com.gathin.tvhome.test.SecurityTest

echo ""
echo "==> Executing LlmTest..."
java -cp "$BUILD_DIR" com.gathin.tvhome.test.LlmTest

echo ""
echo "================================================================================"
echo "  [ALL TESTS PASSED SUCCESSFULLY]"
echo "================================================================================"
