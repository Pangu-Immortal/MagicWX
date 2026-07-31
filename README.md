# MagicWX

MagicWX is an Android 17 local LLM inference prototype built with Kotlin, Jetpack Compose, Material3, and ONNX Runtime Android. It focuses on offline AI model selection, verified local model package checks, and a simple on-device chat flow.

Keywords: Android local LLM, Android 17 AI app, Jetpack Compose AI chat, ONNX Runtime Android, offline LLM prototype, RWKV Android, local AI inference, 端侧大模型, 安卓离线 AI, 本地大模型推理.

## Current Status

- Android app prototype: available.
- Latest prototype release: `v1.1.0`.
- Android target: API 37 / Android 17.
- Verified production-ready models: none yet.
- Fully implemented path in code: RWKV-style local text generation prototype.
- Transformer entries: experimental candidates that require model-specific tokenizer, input/output, dry-run, and golden-output validation before public support claims.

The repository intentionally distinguishes registered model candidates from verified GA support. Do not describe a model as supported until it has a complete package manifest, required assets, successful load, fixed-input dry-run, and device verification evidence.

## Project Structure

```text
com.qihao.open.rwkv/
├── App.kt                    # Application entry
├── MainActivity.kt           # Jetpack Compose UI states
├── model/
│   ├── ITokenizer.kt         # Tokenizer interface
│   ├── RWKVTokenizer.kt      # RWKV vocabulary tokenizer
│   ├── HFTokenizer.kt        # Experimental tokenizer.json reader
│   ├── RWKVModel.kt          # ONNX Runtime inference wrapper
│   ├── ModelInfo.kt          # Registered model metadata
│   └── ModelDownloader.kt    # Download and local package checks
├── viewmodel/
│   └── MainViewModel.kt      # MVVM state and user actions
└── ui/theme/
    └── Theme.kt              # Material3 theme
```

Human-facing product, design, and development documents live under `doc/`. AI-facing change plans live under `openspec/`.

## Registered Prototype Candidates

The app currently registers 10 candidate model entries for UI and download-flow development. Only `RWKV-7 World 0.4B` is marked as the current preferred prototype path. Transformer entries remain experimental until each package passes tokenizer, ONNX input/output, dry-run, and device tests.

| Model | Params | Quantization | Architecture | Status |
|---|---:|---|---|---|
| RWKV-7 World 0.4B | 0.4B | FP32 | RWKV | Prototype path |
| DeepSeek-R1 1.5B | 1.5B | INT4 | Transformer | Experimental |
| Qwen3 0.6B | 0.6B | Q4F16 | Transformer | Experimental |
| Gemma 3 1B | 1B | INT4 | Transformer | Experimental |
| Phi-3 Mini 4K | 3.8B | INT4 | Transformer | Experimental |
| Llama 3.2 1B | 1B | INT8 | Transformer | Experimental |
| SmolLM2 360M | 360M | Q4F16 | Transformer | Experimental |
| TinyLlama 1.1B | 1.1B | INT4 | Transformer | Experimental |
| StableLM 2 1.6B | 1.6B | INT4 | Transformer | Experimental |
| MiniCPM 2B | 2B | INT4 | Transformer | Experimental |

## Android 17 Notes

The `v1.1.0` prototype targets Android 17 / API 37. Current code does not use local-network discovery, SMS/OTP APIs, custom notifications, or fixed-orientation constraints, so the Android 17 adaptation is focused on SDK targeting, backup safety, edge-to-edge Compose screens, and release verification.

Large-screen, foldable, and tablet behavior still needs real-device validation before production claims. Model correctness also requires per-model package manifests, tokenizer parity checks, fixed-input dry-runs, and device logs.

## Build And Test

Use JDK 17 and the checked-in Gradle wrapper.

```bash
./gradlew help
./gradlew build --dry-run
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleRelease
```

For device validation, install the debug APK on API 24 and a recent API device when available, then exercise model selection, download, loading, generation, reset, stop, switch, and deletion flows. `test_automation.sh` provides an ADB-driven smoke script for installed-app checks.

## Release Scope

GitHub Releases provide APK artifacts for prototype validation. They are not Play Store production builds and do not include signing keys, model weights, or a claim that all registered model candidates are production-ready.

## Security And Data

Downloaded model files, runtime state, chat data, signing keys, local SDK paths, and credentials must not be committed. Android backup is disabled for app-private runtime data until data classification and export/import behavior are explicitly designed.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
