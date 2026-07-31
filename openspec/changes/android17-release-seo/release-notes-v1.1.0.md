# MagicWX v1.1.0 Android 17 Prototype

This release publishes a verifiable Android 17 prototype APK for local LLM inference experiments.

## Highlights

- Targets Android 17 / API 37 with `versionName=1.1.0` and `versionCode=2`.
- Migrates the build to AGP 9.3.1, Gradle 9.6.1, and AGP built-in Kotlin support.
- Keeps the public scope accurate: MagicWX is an Android local LLM prototype using Jetpack Compose and ONNX Runtime Android.
- Adds model package readiness checks so partial downloads, zero-byte files, and missing Transformer tokenizers are not treated as usable models.
- Disables Android backup/data extraction for app-private runtime data until explicit export/import behavior is designed.
- Updates README and repository wording for Android local LLM, offline AI, ONNX Runtime Android, Jetpack Compose, Android 17, and GEO/SEO discovery.

## Verification

- `openspec validate android17-release-seo`
- `./gradlew help`
- `./gradlew build --dry-run`
- `./gradlew testDebugUnitTest assembleDebug lintDebug assembleRelease`
- `apksigner verify --verbose --print-certs MagicWX-v1.1.0-android17-prototype-debugsigned.apk`
- `aapt dump badging MagicWX-v1.1.0-android17-prototype-debugsigned.apk`

## Known limitations

- The APK attached here is a debug-signed prototype artifact for GitHub validation, not a Play Store production-signed build.
- No model weights are included in the APK.
- Transformer model entries remain experimental until each package has tokenizer parity, ONNX input/output binding, fixed-input dry-run, and device verification evidence.
- Large-screen, foldable, tablet, and real Android 17 device behavior still require dedicated runtime validation.
