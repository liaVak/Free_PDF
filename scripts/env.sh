#!/usr/bin/env bash
# Source this file from the repository root: source scripts/env.sh
PDF_READER_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="$PDF_READER_ROOT/.tools/android-studio/jbr"
export ANDROID_HOME="$PDF_READER_ROOT/.tools/android-sdk"
export GRADLE_USER_HOME="$PDF_READER_ROOT/.tools/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$PATH"
