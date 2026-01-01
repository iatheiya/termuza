#!/usr/bin/env bash
set -e -o pipefail

rm -rf emulator_logs
mkdir -p emulator_logs

command -v adb >/dev/null 2>&1 && adb --version 2>&1 | sed -n '1,20p' > emulator_logs/adb_version.txt || echo "adb_missing" > emulator_logs/adb_version.txt
command -v aapt2 >/dev/null 2>&1 && aapt2 version 2>&1 | sed -n '1,20p' > emulator_logs/aapt2_version.txt || echo "aapt2_missing" > emulator_logs/aapt2_version.txt
command -v aapt >/dev/null 2>&1 && aapt dump badging --version 2>&1 | sed -n '1,20p' > emulator_logs/aapt_version.txt || echo "aapt_missing" > emulator_logs/aapt_version.txt
command -v sdkmanager >/dev/null 2>&1 && sdkmanager --version 2>&1 > emulator_logs/sdkmanager_version.txt || echo "sdkmanager_missing" > emulator_logs/sdkmanager_version.txt

adb devices -l > emulator_logs/adb_devices_before.txt || true

adb wait-for-device

if [ -z "${APK_PATH:-}" ]; then
  echo "APK_PATH_NOT_SET" > emulator_logs/install_log.txt
  exit 0
fi

timeout 300s adb install -r "$APK_PATH" > emulator_logs/install_log.txt 2>&1 || true

AAPT2="$(command -v aapt2 || true)"
AAPT="$(command -v aapt || true)"
PKG=""
MAINACT=""

if [ -n "$AAPT2" ]; then
  PKG="$($AAPT2 dump badging "$APK_PATH" 2>/dev/null | awk -F"'" '/package: name=/{print $2; exit}' || true)"
  MAINACT="$($AAPT2 dump badging "$APK_PATH" 2>/dev/null | awk -F"'" '/launchable-activity: name=/{print $2; exit}' || true)"
elif [ -n "$AAPT" ]; then
  PKG="$($AAPT dump badging "$APK_PATH" 2>/dev/null | awk -F"'" '/package: name=/{print $2; exit}' || true)"
  MAINACT="$($AAPT dump badging "$APK_PATH" 2>/dev/null | awk -F"'" '/launchable-activity: name=/{print $2; exit}' || true)"
fi

echo "PKG=${PKG}" > emulator_logs/pkg.txt
echo "MAINACT=${MAINACT}" >> emulator_logs/pkg.txt

if [ -n "$PKG" ]; then
  if [ -n "$MAINACT" ]; then
    adb shell am start -n "${PKG}/${MAINACT}" > emulator_logs/launch_log.txt 2>&1 || true
  else
    adb shell monkey -p "${PKG}" -c android.intent.category.LAUNCHER 1 > emulator_logs/launch_log.txt 2>&1 || true
  fi

  sleep 5
  adb logcat -d > emulator_logs/logcat.txt || true
else
  echo "PKG_NOT_DETECTED" > emulator_logs/pkg_error.txt
fi

adb devices -l > emulator_logs/adb_devices_after.txt || true

exit 0
