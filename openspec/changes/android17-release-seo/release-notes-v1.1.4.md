# MagicWX v1.1.4 Release Notes

## Scope

This patch release restores RWKV as a required visible mobile model while keeping the adapter-gated multimodal model architecture from v1.1.3.

## Changes

- Upgrades the Android release identity to `versionName=1.1.4` and `versionCode=6`.
- Restores `RWKV-7 World 0.4B` to the visible model list.
- Keeps RWKV on the implemented `ONNX_TEXT_GENERATION` adapter path with the bundled RWKV vocabulary tokenizer.
- Marks RWKV as fully supported and adapter-available in `ModelRegistry`.
- Adds tests that fail if RWKV is removed from the visible model list again.
- Keeps unverified Transformer and multimodal candidates hidden behind adapter and validation gates.

## Validation

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- Samsung device smoke: fresh install/clear/start, homepage shows RWKV card as `完全支持`.

## Known Boundaries

- This patch restores RWKV visibility and packaging. Full re-download and long-form RWKV generation validation should still be run before claiming production-grade support.
