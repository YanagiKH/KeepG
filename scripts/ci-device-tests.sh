#!/usr/bin/env bash
# Run as one shell: emulator-runner executes separate script lines independently.
# Evidence collection must never turn a failed test into a successful job.
set -uo pipefail
status=0
mkdir -p ci-logs
gradle :app:connectedFullDebugAndroidTest :app:connectedLiteDebugAndroidTest \
  --continue --stacktrace --no-daemon --max-workers=2 2>&1 | tee ci-logs/instrumentation.log || status=$?
mkdir -p qa-screenshots
adb pull /sdcard/Android/data/com.yanagikh.keepg/files/qa-screenshots qa-screenshots/full || true
adb pull /sdcard/Android/data/com.yanagikh.keepg.lite/files/qa-screenshots qa-screenshots/lite || true
exit "$status"
