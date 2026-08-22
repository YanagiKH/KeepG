# KeepG Architecture

## Layers

- **Compose UI**: gallery, albums/collections, smart organization, Vault, and settings.
- **MainViewModel**: user actions and reactive state orchestration.
- **MediaStoreRepository**: read-only discovery/indexing of device images.
- **Room (`KeepGDatabase`)**: local metadata, lock state, smart rules, face observations, people, collections, and Vault index.
- **FaceAnalysisEngine**: ML Kit face detection + KeepG feature extraction and local similarity grouping.
- **Security**: PBKDF2 password verifier, AndroidX BiometricPrompt, and Android Keystore AES-GCM Vault encryption.

## Smart pipeline

```text
MediaStore URI
  -> decode bitmap
  -> ML Kit face detector
  -> crop + normalize face region
  -> 8x8 luminance descriptor + pose/expression signals
  -> cosine similarity against existing cluster centroids
  -> local person cluster
  -> optional user name / person smart rule
```

The descriptor is intentionally transparent and dependency-light for the initial implementation. It is a convenience classifier, not a biometric identity model. A future path can replace `FaceDescriptor` with a validated on-device face-embedding model after licensing, bias, accuracy, model-integrity, and migration requirements are addressed.

## Rule engine

`SmartRuleEvaluator` supports:

- `PERSON`: cluster match
- `TIME`: capture timestamp range
- `LOCATION`: Haversine distance around a user-defined latitude/longitude
- `EXPRESSION`: smile/neutral/eyes-closed thresholds from ML Kit classification probabilities

## Security boundaries

Room and app-private Vault files are excluded from normal Android backup. Vault keys are created inside Android Keystore and are not written to repository files or preferences. MediaStore originals remain outside KeepG's private boundary until users remove them through Android system controls.
