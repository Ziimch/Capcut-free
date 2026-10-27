# Free Video Editor (foundation, v0.1)

Kotlin + Jetpack Compose + Media3. No ads, watermark, account, or paywall.

## Build the APK on GitHub
1. Create a GitHub repo, push this folder to `main`.
2. Open the **Actions** tab, run "Build APK" (it also runs on every push).
3. Download `FreeVideoEditor-debug-apk` from the run's **Artifacts**, unzip, install the .apk.

Local: `gradle assembleDebug` (Gradle 8.7, JDK 17, Android SDK 34).

## Implemented (Stage 1-4 + basic Stage 7)
Home + persistent projects (JSON, atomic save), multi-video import (SAF), live preview (ExoPlayer),
single-track timeline with zoom, split, trim, duplicate, delete, reorder, undo/redo,
real H.264/AAC MP4 export (Media3 Transformer) with progress + cancel.

## Not yet implemented
Multitrack/overlays, text, audio tracks, filters/effects, transitions, keyframes, canvas ratios,
export settings UI, waveforms/thumbnails, relink missing media, crash-recovery of exports.
Exports are saved to the app's Movies folder (Android/data/com.freeeditor.app/files/Movies).

## Licenses
Media3 (Apache 2.0), Jetpack Compose (Apache 2.0). No FFmpeg bundled.

## Status
Code was written without a compile check; if the first GitHub build shows errors, send me the log.
