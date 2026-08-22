# KeepG Architecture

## Product editions

KeepG is one Android application module with an `edition` flavor dimension:

- `full`: complete smart/security/editor/repair/QR feature set, application ID `com.yanagikh.keepg`;
- `lite`: basic image/video gallery, albums, Collections and diagnostics, application ID `com.yanagikh.keepg.lite`.

`AdvancedFeatureTools` is the flavor boundary. `src/full` provides the ML/media implementation; `src/lite` provides a no-op implementation without the new barcode/OCR/segmentation dependencies. Shared gallery/storage code remains in `src/main`.

## Layers

- **Compose UI**: photo/video grid, albums/Collections, Full-only smart organization, Vault, editor/repair actions, and settings.
- **MainViewModel**: user actions, reactive state, flavor feature gates, editor/repair/link orchestration and debug controls.
- **MediaStoreRepository**: discovery/indexing of Android image and video MediaStore collections.
- **MediaFormatRegistry**: normalizes broad extension/MIME coverage independently from platform decoder availability.
- **Room (`KeepGDatabase`)**: local metadata, lock state, smart rules, face observations, people, Collections, and Vault index.
- **FaceAnalysisEngine**: ML Kit face detection + KeepG feature extraction/local similarity grouping.
- **AdvancedFeatureTools (Full)**: QR/barcode + OCR URL detection, image/GIF editing, segmentation, video remux editing, and recovered-copy repair.
- **KeepGLog**: optional app-private rotating diagnostics with user-initiated document export.
- **Security**: PBKDF2 password verifier, AndroidX BiometricPrompt, and Android Keystore AES-GCM Vault encryption.

## Media pipeline

```text
Android MediaStore Images + Video
  -> MediaStoreRepository
  -> MIME/extension normalization
  -> Room photo/media index
  -> Compose gallery + album grouping
```

Media type recognition does not imply codec support. Coil and Android platform decoders determine thumbnail/view support on each device.

## Non-destructive editing pipeline

```text
MediaStore URI
  -> Full AdvancedFeatureTools
  -> image decode / ML mask / bitmap operation
     OR MediaExtractor track read / MediaMuxer remux
  -> new MediaStore item in Pictures/KeepG or Movies/KeepG
  -> MediaStore refresh
```

Source media is never overwritten by current edit/repair operations. Image outputs use PNG when alpha is required and JPEG otherwise. Video editing currently remuxes compatible tracks to MP4; it does not transcode incompatible codecs.

## Link detection boundary

```text
Unlocked image
  -> ML Kit barcode scanner
  -> ML Kit Latin OCR
  -> URL normalization
  -> allow only http/https
  -> explicit user tap
  -> Android ACTION_VIEW external browser
```

KeepG itself has no `INTERNET` permission and never fetches the target page.

## Smart pipeline

```text
MediaStore image URI
  -> decode bitmap
  -> ML Kit face detector
  -> crop + normalize face region
  -> luminance descriptor + pose/expression signals
  -> cosine similarity against existing clusters
  -> local person cluster
  -> optional user name / person smart rule
```

The descriptor is a convenience classifier, not a biometric identity model.

## Rule engine

`SmartRuleEvaluator` supports:

- `PERSON`: cluster match
- `TIME`: capture timestamp range
- `LOCATION`: Haversine distance around a user-defined latitude/longitude
- `EXPRESSION`: smile/neutral/eyes-closed thresholds from ML Kit classification probabilities

## Debug boundary

`KeepGLog` writes to app-private `files/debug/keepg.log`, rotates at 2 MiB, and exports only after the user selects a destination with Android's document picker. It must never receive plaintext authentication secrets or decrypted Vault payloads.

## Security boundaries

Room and app-private Vault files are excluded from normal Android backup. Vault keys are created inside Android Keystore and are not written to repository files or preferences. MediaStore originals remain outside KeepG's private boundary until users remove them through Android system controls. External QR/URL targets are treated as untrusted and only HTTP(S) is exposed.

## CI and release boundary

Android CI compiles/lints/tests Full and Lite and executes both instrumentation variants on API 35. The release workflow runs only after successful `main` CI (or manual dispatch), rebuilds installers/bundles, generates SHA-256 checksums, and creates a GitHub prerelease. Automatic APKs are debug-key signed test installers; production signing material is intentionally not stored in this repository.
