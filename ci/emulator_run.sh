#!/usr/bin/env bash
set -e -o pipefail

LOG_DIR="emulator_logs"
mkdir -p "$LOG_DIR"

command -v adb >/dev/null 2>&1 && adb --version 2>&1 | sed -n '1,20p' > "$LOG_DIR/adb_version.txt" || echo "adb_missing" > "$LOG_DIR/adb_version.txt"
command -v aapt2 >/dev/null 2>&1 && aapt2 version 2>&1 | sed -n '1,20p' > "$LOG_DIR/aapt2_version.txt" || echo "aapt2_missing" > "$LOG_DIR/aapt2_version.txt"

adb wait-for-device

boot_completed=""
max_retries=300
retry_count=0

while [ "$retry_count" -lt "$max_retries" ]; do
  boot_completed=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  if [ "$boot_completed" == "1" ]; then
    break
  fi
  sleep 1
  retry_count=$((retry_count + 1))
done

if [ "$boot_completed" != "1" ]; then
  echo "BOOT_TIMEOUT" > "$LOG_DIR/boot_error.txt"
  exit 1
fi

adb devices -l > "$LOG_DIR/adb_devices_before.txt" || true

if [ -z "${APK_PATH:-}" ]; then
  echo "APK_PATH_NOT_SET" > "$LOG_DIR/install_log.txt"
  exit 1
fi

adb install -r "$APK_PATH" > "$LOG_DIR/install_log.txt" 2>&1 || true

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

echo "PKG=${PKG}" > "$LOG_DIR/pkg.txt"
echo "MAINACT=${MAINACT}" >> "$LOG_DIR/pkg.txt"

if [ -n "$PKG" ]; then
  if [ -n "$MAINACT" ]; then
    adb shell am start -n "${PKG}/${MAINACT}" > "$LOG_DIR/launch_log.txt" 2>&1 || true
  else
    adb shell monkey -p "${PKG}" -c android.intent.category.LAUNCHER 1 > "$LOG_DIR/launch_log.txt" 2>&1 || true
  fi

  sleep 10
  adb shell screencap -p /sdcard/screen_light.png
  adb pull /sdcard/screen_light.png "$LOG_DIR/screen_light.png" || true

  adb shell settings put secure ui_night_mode 2
  sleep 5
  adb shell screencap -p /sdcard/screen_dark.png
  adb pull /sdcard/screen_dark.png "$LOG_DIR/screen_dark.png" || true

  for i in {1..30}; do
    if ! adb shell pidof "$PKG" > /dev/null; then
      echo "CRASH_DETECTED_AT_SECOND_$i" >> "$LOG_DIR/crash_status.txt"
      break
    fi
    sleep 1
  done

  adb logcat -d > "$LOG_DIR/logcat.txt" || true
  adb shell dumpsys activity "${PKG}" > "$LOG_DIR/activity_dump.txt" || true
else
  echo "PKG_NOT_DETECTED" > "$LOG_DIR/pkg_error.txt"
fi

adb devices -l > "$LOG_DIR/adb_devices_after.txt" || true

exit 0
