# Repository Guidelines

## Project Structure & Module Organization

MagicWX is a single-module Android application. Root Gradle configuration lives in `build.gradle.kts`, `settings.gradle.kts`, and `gradle/libs.versions.toml`; app-specific configuration is in `app/build.gradle.kts`. Kotlin sources are under `app/src/main/java/com/qihao/open/rwkv/`, grouped into `model/`, `viewmodel/`, and `ui/theme/`. Android resources live in `app/src/main/res/`, while the bundled tokenizer vocabulary is in `app/src/main/assets/model/`. Keep README images in `screenshots/`. Generated `build/` directories and downloaded model binaries must not be committed.

## Build, Test, and Development Commands

- `./gradlew assembleDebug` builds a debuggable APK.
- `./gradlew lintDebug` runs Android lint against the debug variant.
- `./gradlew testDebugUnitTest` runs local JVM tests when present.
- `./gradlew connectedDebugAndroidTest` runs instrumentation tests on a connected device or emulator.
- `./test_automation.sh` exercises the installed app through ADB; it expects ADB at `~/Library/Android/sdk/platform-tools/adb` and package `com.qihao.open.rwkv`.

Use JDK 17 and the checked-in Gradle wrapper. Open the project in Android Studio for Compose previews and device deployment.

## Coding Style & Naming Conventions

Use Kotlin with four-space indentation and standard Kotlin formatting. Name classes and composables in `PascalCase`, functions and properties in `camelCase`, and constants in `UPPER_SNAKE_CASE`. Keep packages lowercase. Preserve the existing MVVM separation: UI state and actions belong in `MainViewModel`, inference and download behavior in `model/`, and composables in the UI layer. Add concise Simplified Chinese file headers and comments for non-obvious logic, and retain detailed `Log.d`/`Log.e` tracing around downloads, model loading, and inference failures.

## Testing Guidelines

The repository currently has no committed `src/test` or `src/androidTest` suites. Add JVM tests under `app/src/test/...` and device tests under `app/src/androidTest/...`, mirroring production packages. Name test classes `*Test` and test methods by behavior, such as `selectModel_requiresDownload_whenFilesMissing`. For UI changes, run the ADB automation script and manually verify the affected Compose state on API 24 and a recent target device when practical.

## Commit & Pull Request Guidelines

Recent history uses concise prefixes such as `feat:`, `fix:`, `docs:`, and `style:`; follow that pattern and keep each commit focused. Pull requests should explain the user-visible change, list verification commands and device/API details, and link related issues. Include screenshots for Compose UI changes and relevant logs for model download or inference changes.

## Security & Configuration Tips

Never commit credentials, signing keys, local SDK paths, downloaded models, or user-generated chat data. Keep dependency versions centralized in `gradle/libs.versions.toml` and review external model URLs before merging changes.
