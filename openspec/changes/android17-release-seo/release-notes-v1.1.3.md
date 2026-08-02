# MagicWX v1.1.3 Release Notes

## Scope

This prototype release adds the first code-level runtime adapter architecture for MagicWX while preserving the existing validation gate.

## Changes

- Upgrades the Android release identity to `versionName=1.1.3` and `versionCode=5`.
- Adds model capability metadata for text chat, ASR, VAD, TTS, image classification, object detection, segmentation, pose, face analysis, image-text, and image generation.
- Adds runtime adapter metadata for built-in text, ONNX text generation, LiteRT-LM, MediaPipe Tasks, whisper.cpp, Piper, sherpa-onnx, Vosk, NCNN, MNN, and unsupported models.
- Adds explicit model asset metadata so future model packages can declare model, tokenizer, external data, config, voice, label, task, encoder, and decoder files.
- Adds `ModelRuntimeAdapterFactory` with implemented `BUILTIN_TEXT` and `ONNX_TEXT_GENERATION` adapters.
- Keeps unsupported multimodal candidates behind `UnsupportedRuntimeAdapter` so they cannot be falsely loaded as usable models.
- Keeps the visible app model list limited to Samsung-device-verified entries: MagicWX built-in, Qwen3 0.6B, Qwen2.5 0.5B, and SmolLM2 360M.
- Updates README screenshots to show the storage badge and chat action row.

## Validation

- Required verification before release upload:
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
  - `./gradlew assembleRelease`
  - APK metadata inspection for version code/name and Android 17 target
  - Optional installed-app smoke on the Samsung test device

## Known Boundaries

- ASR, VAD, TTS, Vision, VLM, LiteRT, MediaPipe, Piper, sherpa-onnx, NCNN, MNN, and diffusion adapters are metadata-gated candidates only.
- No candidate model should be described as supported until its runtime adapter, full asset manifest, fixed-input dry-run, and device validation evidence are complete.
