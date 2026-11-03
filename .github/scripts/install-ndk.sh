#!/usr/bin/env bash
# Installs the NDK app/build.gradle.kts pins, before Gradle runs. Left to Gradle, the Android
# plugin downloads it once mid-configuration and a dropped connection ("Premature EOF") fails the
# whole build; here the download is tried again.
set -euo pipefail

version=$(sed -n 's/^ *ndkVersion = "\(.*\)"$/\1/p' app/build.gradle.kts)
if [ -z "$version" ]; then
  echo "::error::No ndkVersion in app/build.gradle.kts"
  exit 1
fi

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ]; then
  echo "::error::Neither ANDROID_HOME nor ANDROID_SDK_ROOT is set"
  exit 1
fi
if [ -f "$sdk/ndk/$version/source.properties" ]; then
  echo "NDK $version is already installed"
  exit 0
fi

sdkmanager="$sdk/cmdline-tools/latest/bin/sdkmanager"
for attempt in 1 2 3 4; do
  # A fixed run of "y" for the licence prompts: `yes` would die of SIGPIPE and fail the pipe.
  if printf 'y\n%.0s' {1..20} | "$sdkmanager" --install "ndk;$version" > /dev/null; then
    echo "NDK $version installed"
    exit 0
  fi
  echo "::warning::NDK download failed (attempt $attempt), retrying"
  sleep $((attempt * 10))
done
echo "::error::Could not install NDK $version"
exit 1
