#!/usr/bin/env bash
# Builds with the toolchain staged inside .build-env (no writes to $HOME required).
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="$PWD/.build-env/jdk"
export ANDROID_HOME="$PWD/.build-env/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$PWD/.build-env/gradle-home"
export ANDROID_PREFS_ROOT="$PWD/.build-env/home"
export TMPDIR="$PWD/.build-env/tmp"
mkdir -p "$TMPDIR"
exec "$PWD/.build-env/gradle/bin/gradle" "$@"
