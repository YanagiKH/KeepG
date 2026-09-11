#!/usr/bin/env bash
set -uo pipefail
status=0
mkdir -p ci-logs qa-screenshots
# Full/Lite must both execute; --continue does not turn test failures into success.
gradle :app:connectedFullDebugAndroidTest :app:connectedLiteDebugAndroidTest --continue --stacktrace --no-daemon --max-workers=2 2>&1 | tee ci-logs/instrumentation.log || status=$?
# Screenshots are copied here by the test before UTP uninstalls each edition.
adb pull /sdcard/Download/keepg-qa qa-screenshots/ || status=1
adb logcat -d -v threadtime > ci-logs/device-logcat.txt || true
for edition in com.yanagikh.keepg com.yanagikh.keepg.lite; do
  for screen in album-selection settings-editing image-editor gif-editor video-editor ai-models ai-chat; do
    test -s "qa-screenshots/keepg-qa/$edition/$screen.png" || status=1
  done
done
exit "$status"
