# Changelog

## 0.6.0 - 2026-08-28

### Added

- On-device Latin, Chinese, Japanese, and Korean image-text indexing with album-scoped search.
- Photo and video capture with selectable quality, optional audio, pause/resume, timer, focus, zoom, exposure, flash, torch, lens switching, and a composition grid.
- Configurable grid columns, tile proportions, thumbnail framing, preview framing, badges, and interface animations.
- Direct manipulation for crop and text layers, image color controls, and arbitrary-range video trimming.
- Collection rename, clear, delete, and individual-item removal actions.
- Complete English, Traditional Chinese, Japanese, and Korean UI/widget localization coverage tests.

### Fixed

- Full-screen navigation now stays within the active album, Collection, Favorites list, or filtered result order.
- Partial MediaStore query failures no longer erase the cached library.
- MediaStore output publishing and video/image temporary files are cleaned up atomically after failures or cancellation.
- Large image analysis, OCR, sensitive-content classification, and video samples use bounded memory.
- Orphaned photo locks, face observations, OCR records, and Collection links are pruned after a verified complete scan.
- Camera and media-player lifecycle handling prevents background work after the screen stops.
- Batch selection and favorite operations avoid repeated full-state rewrites.

### Verification

- Full and Lite lint, JVM tests, assembly, and Android instrumentation run in GitHub Actions.
- Instrumentation covers Android API 26 and API 35.
- Releases are created only from the current `main` commit after same-repository push CI succeeds.
