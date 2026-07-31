## ADDED Requirements

### Requirement: Android 17 Targeting
The app SHALL declare Android 17 / API 37 as its compile and target SDK when the local Android SDK and Gradle toolchain can verify the build.

#### Scenario: Gradle verification passes
- **WHEN** the app targets API 37 and `./gradlew testDebugUnitTest assembleDebug lintDebug` runs
- **THEN** all commands complete successfully without lint errors

### Requirement: Release Version
The app SHALL increment release identity from `1.0` / `1` to `1.1.0` / `2`.

#### Scenario: APK metadata is inspected
- **WHEN** the release candidate is built
- **THEN** the APK metadata reflects `versionName=1.1.0` and `versionCode=2`

### Requirement: GitHub Release Artifact
The release SHALL publish an APK artifact and notes that describe verification status and known limitations.

#### Scenario: Release is created
- **WHEN** GitHub Release `v1.1.0` is viewed
- **THEN** it contains the APK artifact and release notes that disclose prototype scope
