## Why

Phase-one review found that MagicWX currently mixes a runnable Android prototype with public claims that exceed verified runtime behavior. The immediate goal is to make the existing prototype safer and more verifiable before any larger product rebuild.

## What Changes

- Fix Android API compatibility issues that block lint for the supported `minSdk=24` range.
- Strengthen model readiness checks so a model is not treated as usable when required assets are missing or obviously invalid.
- Remove the incorrect Transformer fallback to the RWKV tokenizer when `tokenizer.json` is missing.
- Align README wording with the product document: current model entries are prototype/experimental until runtime evidence exists.
- Harden backup rules so downloaded models and runtime data are not accidentally included in cloud/device transfer scope.
- Add focused unit tests for low-risk business logic around model metadata, tokenizers, and model file readiness.

## Capabilities

### New Capabilities

- `model-readiness`: Defines when a local model package can be considered ready for loading.
- `prototype-quality-gates`: Defines build, lint, tests, and public wording gates for the Android prototype.

### Modified Capabilities

- None. No existing OpenSpec capabilities are present in this repository.

## Impact

- Android app code under `app/src/main/java/com/qihao/open/rwkv/`.
- Android resources and backup policy under `app/src/main/res/`.
- Gradle test dependencies and unit test source sets.
- README public wording and OpenSpec change artifacts.
