# Gesture Control Android

Native Android hand-gesture controller designed for low latency and reliable system-wide control.

## Gestures

- ✌️ Index + middle up, ring + pinky closed → Tap/OK
- ☝️ Index only → Home
- ✊ Fist → Recent Apps
- 🖐️ Open palm + right→left → swipe left
- 🖐️ Open palm + left→right → Back / swipe right
- 🖐️ Open palm + up/down → scroll

## Architecture

CameraX captures the front camera using a single latest-frame queue. MediaPipe Hand Landmarker runs in LIVE_STREAM mode so inference is asynchronous and stale frames can be dropped. The classifier uses 21 hand landmarks, temporal stability, confidence thresholds and cooldowns. Android AccessibilityService performs global actions and touch gestures.

MediaPipe documents LIVE_STREAM as the camera-oriented asynchronous mode and notes that it may drop input frames to reduce latency. Android AccessibilityService `dispatchGesture` is the supported API for injecting touch gestures when the service declares gesture capability.

## Build

Requires JDK 17 and Android SDK 35. The Gradle build automatically downloads the MediaPipe hand model into `app/src/main/assets/` when it is missing.

The GitHub Actions workflow builds a debug APK and uploads it as an artifact.

## First run

1. Install the debug APK.
2. Grant Camera permission.
3. Open Android Accessibility settings and enable Gesture Control.
4. Return to the app and press START GESTURE.
5. Keep the persistent notification enabled. Android requires a foreground service for ongoing camera use.

## Important latency design

There is no honest way to guarantee zero milliseconds of latency. This project minimizes latency by using on-device inference, `STRATEGY_KEEP_ONLY_LATEST`, asynchronous LIVE_STREAM inference, a small camera analysis resolution, and a deterministic classifier. Diagnostics and tuning will be added before calling the controller production-ready.

## Termux

Termux is intended to control the service lifecycle after the Android command bridge is added. The first milestone uses the app UI to request the camera and Accessibility permissions because Android requires these permissions to be granted by the user.

## Roadmap

- [x] Native Android project
- [x] CameraX front-camera analysis
- [x] MediaPipe Hand Landmarker
- [x] Accessibility global actions
- [x] Tap/swipe/scroll injection
- [x] Temporal stability and cooldown
- [ ] Rotation/mirroring validation on the target device
- [ ] On-device latency/FPS diagnostics
- [ ] Termux command bridge
- [ ] Calibration screen and per-gesture sensitivity
- [ ] Battery/vendor-specific background survival tuning
