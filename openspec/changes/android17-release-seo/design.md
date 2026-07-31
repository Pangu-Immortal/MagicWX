## Context

The project currently builds with AGP 8.7.3, Gradle 8.11.1, Kotlin 2.1.0, `compileSdk=35`, and `targetSdk=35`. The local Android SDK contains API 37 platform packages. AGP 9 migration guidance indicates that AGP 9.0 has major DSL and built-in Kotlin changes; this single-module app has no custom variant API usage, but a full AGP 9 migration is not required unless the build rejects API 37.

## Goals / Non-Goals

**Goals:**

- Produce a verifiable Android 17 release candidate without adding unverified product features.
- Keep README/GitHub metadata truthful: MagicWX is a local LLM Android prototype, not a production multi-model GA app.
- Publish a release artifact only after Gradle verification passes.

**Non-Goals:**

- Do not implement the future multimodal product roadmap in this release.
- Do not add signing keys or commit credentials.
- Do not use force push, `--no-verify`, or destructive Git operations.

## Decisions

1. Use `versionName=1.1.0` and `versionCode=2`.
   - Rationale: existing GitHub latest release is `V1.0.1`; this is a minor release with compatibility and metadata changes.

2. Use AGP 9 built-in Kotlin for API 37 targeting.
   - Rationale: AGP 8.7.3 rejected the installed API 37 platform layout, while AGP 9.3.1 with Gradle 9.6.1 verified `compileSdk=37` and `targetSdk=37`. The app is single-module and has no custom legacy variant API usage.

3. Publish an APK artifact, not signing credentials.
   - Rationale: no release signing material is present or authorized for commit. A generated release APK can be attached to GitHub Release with its signing state disclosed.

## Risks / Trade-offs

- API 37 and AGP 9 may expose DSL or dependency compatibility issues -> mitigate by running OpenSpec validation, Gradle help, dry-run, unit tests, debug assembly, lint, release assembly, APK signing verification, and APK metadata inspection.
- Unsigned or debug-signed artifacts are not Play production artifacts -> mitigate by naming and release notes.
- GitHub repository metadata update is an external side effect -> mitigate by verifying `gh auth status` and reporting the exact metadata changed.
