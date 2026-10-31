# Inkdot

A minimal, ad-free manga reader for Android and iOS. Nothing-inspired monochrome UI with
dot-matrix headings and a single red accent, built with Jetpack Compose.

- **Reader:** left-to-right, right-to-left (manga) and vertical webtoon modes;
  glass page turn; two-page spreads in landscape; pinch and double-tap zoom; tap zones; page prefetch;
  immersive full screen; resumes where you left off.
- **Extensions:** add any GitHub repo that has an `index.json` (see
  [`extensions-repo/`](extensions-repo/README.md)). Sources are declarative JSON,
  so nothing gets downloaded and run as code. A MangaDex example is included.
- **Local:** open a CBZ/ZIP file or a folder of images or chapters.
- Library, continue-reading card, read markers, dark/light/AMOLED themes.

Minimum Android 8.0 (API 26). Tuned for a OnePlus 5T (Snapdragon 835).

## Build

```bash
./gradlew :app:assembleRelease
```

The APK lands in `app/build/outputs/apk/release/app-release.apk`. It's signed
with the debug key so it can be sideloaded straight away.

## Using the sample extensions

Push this repo to GitHub, then in the app open **Extensions** and add
`<your-user>/<repo>/tree/main/extensions-repo`.

## iOS

A native SwiftUI version lives in [`ios/`](ios/). It uses the same extension
repos and adds real iOS 26+ Liquid Glass for the page turn and reader controls.

```bash
cd ios && xcodegen generate && open Inkdot.xcodeproj
```

Pick your Apple ID team under Signing & Capabilities, plug in your iPhone and
press Run. With a free Apple ID the install lasts 7 days.
