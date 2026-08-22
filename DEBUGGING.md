# KeepG Debugging Manual

## Prerequisites

- JDK 17
- Android SDK 35 and Build Tools 35.0.0
- Gradle 8.10.2
- Android Studio with an API 35 emulator or a physical Android 8.0+ device

## Baseline verification

```bash
gradle :app:assembleDebug --stacktrace
gradle :app:testDebugUnitTest --stacktrace
gradle :app:lintDebug --stacktrace
adb devices
gradle :app:installDebug
gradle :app:connectedDebugAndroidTest --stacktrace
adb shell cmd package resolve-activity --brief com.yanagikh.keepg
adb shell am start -n com.yanagikh.keepg/.MainActivity
```

## Media permission problems

If the library is empty, incomplete, or EXIF locations are missing:

1. Open Android Settings > Apps > KeepG > Permissions.
2. Grant Photos and videos access; on Android 14+, full library access is needed for full-gallery behavior.
3. Grant media location access if location smart rules are required.
4. Return to KeepG and press Refresh.
5. Inspect permission state:

```bash
adb shell dumpsys package com.yanagikh.keepg | sed -n '/runtime permissions:/,/install permissions:/p'
```

## MediaStore indexing problems

KeepG mirrors MediaStore metadata into Room. Force a refresh after external changes. On debug devices, inspect MediaStore with Android content-provider tooling:

```bash
adb shell content query --uri content://media/external/images/media --projection _id:display_name:bucket_display_name --sort "date_taken DESC"
```

## Smart analysis problems

```bash
adb logcat -c
adb shell am force-stop com.yanagikh.keepg
adb shell am start -n com.yanagikh.keepg/.MainActivity
adb logcat | grep -E "KeepG|MLKit|FirebaseML|AndroidRuntime"
```

Common causes include revoked media access, a deleted original before refresh, an undecodable/malformed image, a face that is too small or occluded, or a heuristic person-cluster false merge/split. Compare detector output first, descriptor second, clustering third. Tune thresholds on a mixed validation set, not a single person's photos.

## Lock/authentication problems

For device authentication, verify a device PIN/pattern/password or supported biometric is configured. An emulator without a credential can report authentication as unavailable.

Password-lock unit tests cover PBKDF2 creation and verification. Never log plaintext passwords, password character arrays, derived keys, Vault keys, or decrypted media bytes.

## Vault problems

The instrumentation test `androidKeystoreVaultCipherRoundTripsBytes` validates AES-GCM round trips on Android.

If decryption fails:

1. Confirm the `.kgv` file exists in app-private storage.
2. Check for `AEADBadTagException`, which indicates corruption or a different/missing key.
3. Confirm the app was not uninstalled/reinstalled between encryption and decryption.
4. Never bypass GCM authentication or silently return corrupted plaintext.

```bash
adb shell run-as com.yanagikh.keepg find files -maxdepth 2 -type f -ls
```

## Room database inspection

Use Android Studio App Inspection for `keepg.db`. Important tables are `photos`, `locks`, `face_observations`, `person_profiles`, `smart_rules`, `vault_items`, `collections`, and `collection_items`.

## UI automation procedure

Determine tap coordinates from the UI hierarchy rather than screenshots:

```bash
adb exec-out uiautomator dump /dev/tty > /tmp/keepg-ui.xml
adb exec-out screencap -p > /tmp/keepg.png
```

Use XML node bounds to choose coordinates, re-dump after important transitions, and clear `logcat` before crash reproduction.

## CI failures

The workflow has two jobs: `build-test-lint` and `instrumentation`. Open the exact failed step and reproduce its Gradle command with `--stacktrace`. Do not fix CI by disabling lint/tests unless the check is demonstrably invalid and the rationale is documented.

## Release debugging checklist

- [ ] Clean build succeeds.
- [ ] JVM tests pass.
- [ ] Android lint passes.
- [ ] API 35 instrumentation tests pass.
- [ ] Fresh-install permission flow works.
- [ ] Photo lock with device authentication works.
- [ ] Photo lock with KeepG password works and rejects a wrong password.
- [ ] Album lock hides thumbnails until unlock.
- [ ] Vault import creates a `.kgv` file and decrypt round trip succeeds.
- [ ] Smart analysis handles photos with zero, one, and multiple faces.
- [ ] Person group rename persists.
- [ ] Person/time/location/expression smart rules can be created and deleted.
- [ ] No plaintext secret is present in logs or repository files.
