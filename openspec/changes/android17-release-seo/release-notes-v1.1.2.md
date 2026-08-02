# MagicWX v1.1.2 Android 17 Prototype

This release refreshes the Android 17 prototype after real-device model validation and expands the documented mobile model roadmap beyond text-only LLMs.

## Highlights

- Targets Android 17 / API 37 with `versionName=1.1.2` and `versionCode=4`.
- Keeps the visible app model list limited to the models that passed Samsung device validation: MagicWX built-in, Qwen3 0.6B, Qwen2.5 0.5B, and SmolLM2 360M.
- Corrects Qwen3 0.6B displayed package size to `544 MB` based on the verified downloaded ONNX file.
- Adds a mobile model candidate pool covering LLM, VLM, ASR, VAD, TTS, vision, and image-generation model families.
- Documents that non-LLM modalities require dedicated runtime adapters before they can be exposed in the app.

## Verification

- `./gradlew testDebugUnitTest assembleDebug`
- Clean-data Samsung device validation for built-in, Qwen3 0.6B, Qwen2.5 0.5B, and SmolLM2 360M.
- `adb logcat AndroidRuntime:E` crash scan.
- `./gradlew testDebugUnitTest assembleRelease`

## Known limitations

- The release APK is an unsigned prototype artifact unless explicitly signed for distribution.
- No external model weights are bundled in the APK.
- Qwen2 0.5B downloads and loads, but remains hidden because its `hello` output produced garbled tokens on device.
- ASR, TTS, image, VLM, and diffusion candidates are researched but not implemented in the current app runtime.
- Large-screen, foldable, tablet, and real Android 17 device behavior still require dedicated runtime validation.
