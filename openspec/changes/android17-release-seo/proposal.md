## Why

MagicWX needs a release-ready Android 17 prototype package and repository metadata that no longer overstates current model support. The release must be verifiable locally before any GitHub push or Release upload.

## What Changes

- Upgrade the Android app release identity to `1.1.3` with `versionCode=5`.
- Target Android 17 / API 37 where the local SDK and Gradle toolchain allow it.
- Update README and repository-facing wording for prototype accuracy, SEO, and GEO search intent.
- Add code-level model capability, asset, visibility, and runtime adapter metadata so future LLM, ASR, VAD, TTS, Vision, VLM, and ImageGen models can be gated before public exposure.
- Produce a GitHub release APK artifact with release notes.
- Push committed changes and upload the release artifact when local verification passes and GitHub permissions are available.

## Capabilities

### New Capabilities

- `android17-release`: Defines Android 17 targeting, release versioning, and release artifact expectations.
- `repository-discovery`: Defines README, GitHub description, SEO, and GEO wording expectations.

### Modified Capabilities

- None. Existing OpenSpec capabilities are scoped to prior phase-two quality gates.

## Impact

- `app/build.gradle.kts` and Gradle version catalog.
- README, human-facing docs under `doc/`, and OpenSpec release artifacts.
- Git commit history, remote GitHub repository metadata, and GitHub Releases.
