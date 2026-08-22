# KeepG Debugging Manual

## Prerequisites

- JDK 17
- Android SDK 35 and Build Tools 35.0.0
- Gradle 8.10.2
- Android Studio with an API 35 emulator or a physical Android 8.0+ device

## Baseline verification

KeepG has two product flavors: `full` and `lite`.

```bash
gradle :app:lintFullDebug :app:lintLiteDebug --stacktrace
gradle :app:testFullDebugUnitTest :app:testLiteDebugUnitTest --stacktrace
gradle :app:assembleFullDebug :app:assembleLiteDebug --stacktrace
adb devices
gradle :app:installFullDebug
adb shell cmd package resolve-activity --brief com.yanagikh.keepg
adb shell am start -n com.yanagikh.keepg/.MainActivity
gradle :app:connectedFullDebugAndroidTest :app:connectedLiteDebugAndroidTest --stacktrace
```

Lite uses application ID `com.yanagikh.keepg.lite`, so Full and Lite can be installed together.

## In-app debug logging

Open **Settings > Debug logging**. The switch enables a rotating app-private text log. The active file is `files/debug/keepg.log`; when it exceeds 2 MiB KeepG rotates it to `keepg.log.1`.

- **View log** displays the newest lines inside KeepG.
- **Export log** uses Android's document picker; KeepG never silently uploads logs.
- **Clear** truncates the current KeepG log.
- Plaintext passwords, derived password keys, Android Keystore keys and decrypted Vault bytes must never be logged.

ADB equivalents:

```bash
adb logcat -c
adb logcat | grep -E "KeepG/|AndroidRuntime|MLKit"
adb shell run-as com.yanagikh.keepg cat files/debug/keepg.log
```

## Media permission problems

If the library is empty or incomplete:

1. Open Android Settings > Apps > KeepG > Permissions.
2. Grant **Photos and videos** access. On Android 14+, selected-only access intentionally limits what KeepG can index.
3. Grant media location access if location smart rules are required.
4. Return to KeepG and press Refresh.

```bash
adb shell dumpsys package com.yanagikh.keepg | sed -n '/runtime permissions:/,/install permissions:/p'
```

KeepG requests `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` on Android 13+, selected-media access on Android 14+, and legacy storage read access only on Android 12L and older.

## MediaStore and format problems

KeepG scans both Android image and video MediaStore collections. Format recognition includes JPEG/JFIF, PNG, WebP, GIF, BMP, HEIC/HEIF, AVIF, DNG, TIFF, ICO, SVG, MP4/MOV/3GP/MKV/WebM/AVI/MPEG/TS/OGV when Android's MediaStore/provider exposes the item. Recognition does not guarantee that every OEM ships a decoder for every codec.

```bash
adb shell content query --uri content://media/external/images/media --projection _id:display_name:mime_type --sort "date_taken DESC"
adb shell content query --uri content://media/external/video/media --projection _id:display_name:mime_type --sort "date_taken DESC"
```

If metadata is malformed but pixels/tracks remain decodable, use **Repair**. KeepG writes a recovered copy instead of destructively rewriting the source. If the source cannot be decoded or remuxed, recovery cannot be guaranteed.

## Image, GIF and background-removal debugging

Full edition image operations are non-destructive and export a new item under `Pictures/KeepG`:

- rotate 90 degrees;
- horizontal flip;
- grayscale;
- center square crop;
- bundled ML Kit automatic person/background segmentation;
- corner-color manual removal with adjustable tolerance;
- first-frame GIF extraction to PNG.

For automatic background removal, inspect `KeepG/Editor`, ML Kit errors and memory pressure. Very large images can exceed device memory even when the file itself is valid. GIF animation is not re-encoded as an animated GIF; the explicit GIF command extracts the first decoded frame.

## Video editing debugging

Full edition video editing uses Android `MediaExtractor` + `MediaMuxer` without transcoding. Current actions are first-five-seconds trimming and audio-track removal. Output is an MP4 copy under `Movies/KeepG`.

A container/codec can be viewable on a device but still be incompatible with MP4 remuxing. In that case KeepG reports the failure and leaves the original untouched.

## QR, barcode and URL detection

Long-press an unlocked image thumbnail or use **Links** in media detail. KeepG scans the image using bundled ML Kit barcode scanning plus Latin text recognition. Only normalized `http://` and `https://` targets are exposed as external links; custom schemes are rejected.

If detection fails, test a high-resolution image with a clear QR code first. Rotation, blur, tiny codes and low contrast can reduce detection accuracy.

## Smart analysis problems

```bash
adb logcat -c
adb shell am force-stop com.yanagikh.keepg
adb shell am start -n com.yanagikh.keepg/.MainActivity
adb logcat | grep -E "KeepG|MLKit|AndroidRuntime"
```

Smart face analysis only runs on images in Full edition. Common causes include revoked media access, an undecodable image, a face that is too small/occluded, or a heuristic person-cluster false merge/split.

## Lock/authentication problems

For device authentication, verify a device PIN/pattern/password or supported biometric is configured. An emulator without a credential can report authentication as unavailable. Password-lock unit tests cover PBKDF2 creation and verification.

## Vault problems

The instrumentation test `androidKeystoreVaultCipherRoundTripsBytes` validates AES-GCM round trips on Android. If decryption fails, confirm the `.kgv` file exists, look for `AEADBadTagException`, and confirm the app was not uninstalled/reinstalled between encryption and decryption.

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

## CI and Releases

`.github/workflows/android.yml` compiles/lints/tests both Full and Lite and runs both instrumentation variants on API 35. `.github/workflows/release.yml` only auto-publishes after a successful Android CI run on `main` (or explicit manual dispatch).

Automatic prerelease APKs are debug-key signed and intended for direct testing. They are installable, but the debug signing identity is not a production upgrade identity. AAB files are also attached for a production signing/store pipeline.

## Release debugging checklist

- [ ] Full and Lite lint pass.
- [ ] Full and Lite JVM tests pass.
- [ ] Full and Lite APKs assemble.
- [ ] API 35 instrumentation passes for both flavors.
- [ ] Image and video MediaStore items appear after permission grant.
- [ ] Long-press QR/URL scan only exposes HTTP(S) links.
- [ ] Image edits create new copies without changing originals.
- [ ] Automatic and adjustable manual background removal create alpha PNG output.
- [ ] GIF frame extraction creates a PNG copy.
- [ ] Video trim and mute produce playable MP4 output for supported input codecs.
- [ ] Repair creates a recovered copy and handles invalid dates safely.
- [ ] Debug log enable/view/export/clear works and contains no secrets.
- [ ] Full photo/album locks and Vault still work.
- [ ] Automatic GitHub prerelease contains Full/Lite APK variants, AABs and `SHA256SUMS.txt`.
