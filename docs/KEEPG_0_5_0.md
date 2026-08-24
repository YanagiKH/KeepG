# KeepG 0.5.0 — Viewer, Camera, Trash, and Widget Update

KeepG 0.5.0 focuses on media-viewer correctness and faster gallery workflows.

## Media preview reliability

- Inline and full-screen video surfaces preserve the decoded video's aspect ratio instead of stretching to the device or grid cell dimensions.
- Video playback position, play/pause intent, and playback speed survive ordinary activity recreation such as screen rotation.
- A single tap toggles video chrome. Controls and progress UI also hide automatically after an idle period.
- The five-item preview action bar uses equal-width cells and system navigation-bar padding, preventing labels from overflowing narrow screens.
- Optional left/right swipe navigation moves between adjacent items in the current visible library order. Swipe navigation is disabled while media is zoomed to avoid conflicts with pan gestures.

## Gallery interaction

- Pinch-to-change-grid-density now remains active for the full gesture. A continuous pinch can move through multiple column counts from 2 through 8.
- Inline video playback is detached while the grid is transforming, then safely resumed after the gesture.

## MediaStore Trash

Android 11 and newer expose a Trash entry in the Albums tab. KeepG can list MediaStore trashed images/videos, restore individual/all items, or request permanent deletion. Restore and permanent-delete operations use Android's system confirmation UI.

## KeepG Camera

The app bar now opens an in-app CameraX camera with:

- rear/front camera switching;
- fit-center preview without aspect-ratio stretching;
- tap-to-focus and metering;
- zoom control;
- exposure compensation when supported;
- torch control and photo flash off/auto/on;
- maximum-quality or low-latency capture mode;
- 3-second and 10-second self timers;
- optional rule-of-thirds composition grid;
- non-destructive JPEG output to `Pictures/KeepG` through MediaStore.

Camera permission is requested only when the camera is opened. The manifest marks camera hardware as optional so devices without a camera can still install KeepG.

## Launch animation

A short KeepG logo animation is shown on a fresh app launch. Saveable state prevents an ordinary screen rotation from replaying it as though the app had been reopened.

## Home-screen widget

KeepG registers a resizable home-screen widget through Android's standard AppWidget framework. Compatible launchers expose it through their widget picker. The widget provides quick actions for Photos, Albums, Camera, and Vault.

## Verification gates

Before merge, the release candidate must pass:

- Full and Lite Android lint;
- JVM unit tests, including aspect-ratio, swipe-threshold, zoom-bound, and continuous-grid helpers;
- Full and Lite APK assembly;
- API 35 Full and Lite instrumentation tests;
- manifest checks for Camera permission and widget-provider registration;
- persistence verification for preview swipe navigation;
- existing Room and Android Keystore Vault round-trip tests.

After merge, the existing release workflow only publishes from a successful `main` Android CI run and packages Full/Lite APKs, Full/Lite AABs, and `SHA256SUMS.txt` into the corresponding GitHub prerelease.
