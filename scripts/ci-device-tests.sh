#!/usr/bin/env bash
set -uo pipefail
status=0
mkdir -p ci-logs qa-screenshots
# The runner reports sys.boot_completed before Quickstep has a focused window.
# Check the disposable emulator's HOME activity before starting app assertions.
# Never dismiss/disable ANR dialogs or suppress a KeepG crash to get a green run.
adb wait-for-device
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME > ci-logs/launcher-start.txt 2>&1 || exit 1
ready=0
for attempt in $(seq 1 30); do
  adb shell dumpsys window windows > ci-logs/boot-windows.txt || exit 1
  if grep -Eq 'mCurrentFocus=.*com\.android\.launcher3/|mCurrentFocus=.*com\.google\.android\.apps\.nexuslauncher/' ci-logs/boot-windows.txt; then
    ready=1
    break
  fi
  sleep 2
done
if [ "$ready" -ne 1 ]; then
  adb logcat -d -v threadtime > ci-logs/device-logcat.txt || true
  echo 'Emulator launcher never became focused; refusing to run UI assertions under a system dialog.' >&2
  exit 1
fi
# Full/Lite must both execute; --continue does not turn test failures into success.
gradle :app:connectedFullDebugAndroidTest :app:connectedLiteDebugAndroidTest --continue --stacktrace --no-daemon --max-workers=2 2>&1 | tee ci-logs/instrumentation.log || status=$?
# Screenshots are copied here by the test before UTP uninstalls the app and removes externalFilesDir.
adb pull /sdcard/Download/keepg-qa qa-screenshots/ || status=1
adb logcat -d -v threadtime > ci-logs/device-logcat.txt || true
for edition in com.yanagikh.keepg com.yanagikh.keepg.lite; do
  for screen in album-selection settings-editing image-editor gif-editor video-editor ai-models ai-chat; do
    test -s "qa-screenshots/keepg-qa/$edition/$screen.png" || status=1
  done
done
exit "$status"
