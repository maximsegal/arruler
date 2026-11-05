# AR Ruler (Android)

Measure real-world distances with your phone using ARCore.

## Use
1. Point the camera at a surface and move slowly until the reticle turns green.
2. Tap **Add point** to drop a point; add more to measure along a path (segments + total shown).
3. **Undo** removes the last point, **Clear** removes all, the unit button toggles cm/m <-> in/ft.

Requires an [ARCore-supported device](https://developers.google.com/ar/devices). Accuracy is typically
within a few percent; it's best on textured, well-lit surfaces (depth-capable phones are better).

## Build
- Android Studio: open this folder and run the `app` configuration, or
- CLI (needs Android SDK + Gradle 8.9+): `gradle :app:assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`
- GitHub Actions builds a debug APK on every push (artifact: `ar-ruler-debug-apk`).
