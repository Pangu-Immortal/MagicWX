# MagicWX

MagicWX is an Android 17 local LLM inference prototype built with Kotlin, Jetpack Compose, Material3, and ONNX Runtime Android. It focuses on offline AI model selection, verified local model package checks, and a simple on-device chat flow.

Keywords: Android local LLM, Android 17 AI app, Jetpack Compose AI chat, ONNX Runtime Android, offline LLM prototype, RWKV Android, local AI inference, 端侧大模型, 安卓离线 AI, 本地大模型推理.

## Current Status

- Android app prototype: available.
- Latest prototype release: `v1.1.1`.
- Android target: API 37 / Android 17.
- Verified production-ready models: none yet.
- Verified first-run path: built-in MagicWX experience model, no external download required.
- ONNX path in code: RWKV-style local text generation prototype plus experimental Transformer support.
- Background downloads: user-started foreground service with persistent progress notification and model-card progress state.
- Transformer entries: experimental candidates that require model-specific tokenizer, input/output, dry-run, and golden-output validation before public support claims.

The repository intentionally distinguishes registered model candidates from verified GA support. Do not describe a model as supported until it has a complete package manifest, required assets, successful load, fixed-input dry-run, and device verification evidence.

## App Runtime Screenshot

<img src="screenshots/home.png" width="320" alt="MagicWX Android 17 model selection screen with built-in experience model" />

## Project Structure

```text
com.qihao.open.rwkv/
├── App.kt                    # Application entry
├── MainActivity.kt           # Jetpack Compose UI states
├── model/
│   ├── ITokenizer.kt         # Tokenizer interface
│   ├── RWKVTokenizer.kt      # RWKV vocabulary tokenizer
│   ├── HFTokenizer.kt        # Experimental tokenizer.json reader
│   ├── BuiltinExperienceModel.kt # Built-in no-download demo engine
│   ├── TextGenerationEngine.kt # Shared generation interface
│   ├── RWKVModel.kt          # ONNX Runtime inference wrapper
│   ├── ModelInfo.kt          # Registered model metadata
│   └── ModelDownloader.kt    # Download and local package checks
├── service/
│   ├── ModelDownloadService.kt # Foreground model download service
│   └── ModelDownloadEvents.kt  # In-process download progress events
├── viewmodel/
│   └── MainViewModel.kt      # MVVM state and user actions
└── ui/theme/
    └── Theme.kt              # Material3 theme
```

Human-facing product, design, and development documents live under `doc/`. AI-facing change plans live under `openspec/`.

## Registered Models

The app registers one built-in experience model for first-run use and 10 external ONNX candidates for download-flow and inference validation. The built-in model is a deterministic local experience engine, not a bundled large model weight. Transformer entries remain experimental until each package passes tokenizer, ONNX input/output, dry-run, and device tests.

| Model | Architecture | Download status | Runtime status |
|---|---|---|---|
| MagicWX built-in experience | Built-in | Bundled in code | Verified on device for first-run chat |
| RWKV-7 World 0.4B | RWKV | Endpoint reachable | Prototype path, needs full download/load test |
| DeepSeek-R1 1.5B | Transformer | Model/tokenizer endpoints reachable | Experimental |
| Qwen3 0.6B | Transformer | Model/tokenizer endpoints reachable | Experimental; background download UI verified |
| Gemma 3 1B | Transformer | Model/tokenizer and `_data` endpoints reachable | Experimental; requires external data package |
| Phi-3 Mini 4K | Transformer | Model/tokenizer and `_data` endpoints reachable | Experimental; requires external data package |
| Llama 3.2 1B | Transformer | Model/tokenizer endpoints reachable | Experimental |
| SmolLM2 360M | Transformer | Model/tokenizer endpoints reachable | Experimental |
| TinyLlama 1.1B | Transformer | Endpoint returned HTTP 401 in validation | Blocked until URL is replaced |
| StableLM 2 1.6B | Transformer | Endpoint returned HTTP 401 in validation | Blocked until URL is replaced |
| MiniCPM 2B | Transformer | Endpoint returned HTTP 401 in validation | Blocked until URL is replaced |

## Download Behavior

External model downloads run in a user-started `dataSync` foreground service. Users can tap “后台下载，返回模型选择” to leave the download page; the active model card shows “下载中 xx%” and a progress bar while the service continues. The downloader keeps `.downloading` temporary files for resume, validates short reads before renaming, and requires tokenizer and `_data` assets when the model package needs them.

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
