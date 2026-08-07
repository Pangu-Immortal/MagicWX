# MagicWX

MagicWX is an Android local AI app built with Kotlin, Jetpack Compose, and Material3. It combines fully offline text chat (RWKV / ONNX / MediaPipe runtimes) with on-device Stable Diffusion image generation (SD1.5 CPU pipeline via a native MNN backend), covering offline model selection, verified package checks, runtime gating, chat, and a complete image-generation workspace (txt2img / img2img / inpaint, mask painting, cropping, and persistent history).

Keywords: Android local LLM, Android 17 AI app, Jetpack Compose AI chat, ONNX Runtime Android, MediaPipe LLM Android, LiteRT LLM, offline LLM, RWKV Android, Stable Diffusion Android, local image generation, MNN diffusion, 端侧大模型, 安卓离线 AI, 本地大模型推理, 安卓本地生图.

## Current Status

- Android app: available.
- Latest release: `v1.1.5`.
- Android target: API 37 / Android 17.
- **Local image generation (GA on CPU)**: SD1.5 CPU pipeline verified end-to-end on Samsung SM-A566E — txt2img / img2img / inpaint all produce images; ~10-14s per 512×512 step (MNN, `-O3`, UNet fp16); prompt workspace with album import + cropping, mask painting, parameter panel, auto result page, and Room-persisted history. NPU/QNN pipelines (SD1.5 NPU, SDXL, Anima, upscaler) are registered and will open after QNN runtime integration.
- Verified chat models: built-in MagicWX experience model (no download), RWKV-7 World 0.4B and other registered language models pass device validation.
- Runtime adapters: `BUILTIN_TEXT`, `ONNX_TEXT_GENERATION`, `LITERT_LM` (MediaPipe), and the isolated native image backend (`libmagicwx_image_backend.so`, foreground-service-managed process on localhost:18081 with SSE streaming).
- Background downloads: user-started foreground service with persistent progress notification and model-card progress state.
- Multimodal scope: ASR, VAD, TTS, image understanding, and VLM remain separate roadmap items.

The repository intentionally distinguishes registered model candidates from verified GA support. Do not describe a model as supported until it has a complete package manifest, required assets, successful load, fixed-input dry-run, and device verification evidence.

## App Runtime Screenshot

<img src="screenshots/home-storage.png" width="320" alt="MagicWX model selection screen with storage badge and download-ready model cards" />
<img src="screenshots/chat-actions.png" width="320" alt="MagicWX chat screen with elapsed time, copy, and retry actions" />

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
│   ├── ModelDownloader.kt    # Download and local package checks
│   └── adapter/
│       ├── ModelRuntimeAdapter.kt # Runtime adapter dispatch and load gates
│       └── MediaPipeLlmAdapter.kt # MediaPipe/LiteRT .task text runtime
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

The app currently exposes only models that passed device validation. The built-in model is a deterministic local experience engine, not a bundled large model weight. External models stay in the app only after they pass download, tokenizer, ONNX load, basic chat, and device-log checks.

| Model | Architecture | Download status | Runtime status |
|---|---|---|---|
| MagicWX built-in experience | Built-in | Bundled in code | Verified on device for first-run chat |
| RWKV-7 World 0.4B | RWKV | Verified full download on Samsung test device via HF mirror fallback | Verified ONNX load and `hello` chat response |
| Qwen3 0.6B | Transformer | Verified full download on Samsung test device | Verified `hello` chat response |
| Qwen2.5 0.5B | Transformer | Verified full download on Samsung test device | Verified `hello` chat response |
| SmolLM2 360M | Transformer | Verified full download on Samsung test device | Verified `hello` chat response |
| TinyLlama 1.1B Chat LiteRT | Transformer | Verified 1.1GB `.task` download on Samsung test device via HF mirror fallback | Verified MediaPipe/LiteRT load and `hello` response in 4.8s |

DeepSeek-R1, Gemma 3 ONNX, Phi-3, Llama 3.2 ONNX, TinyLlama ONNX, StableLM 2, MiniCPM, Qwen2 0.5B, SmolLM2 135M, and SmolLM2 135M MHA are intentionally hidden until they can pass the same validation bar.

## Mobile Model Candidate Pool

This catalog is a research and implementation queue, not a support claim. Only the registered models above are visible in the app today. MagicWX now stores capability, runtime adapter, visibility, and asset metadata for candidates in code, but candidates stay hidden until the adapter and device tests pass. `Direct` means the current ONNX text-generation path can be tested with limited extra work; `Adapter` means MagicWX needs a new runtime layer such as LiteRT-LM, whisper.cpp, sherpa-onnx, Piper, MediaPipe Tasks, NCNN, MNN, or a diffusion pipeline.

| Modality | Candidate | Mobile runtime / format | MagicWX status |
|---|---|---|---|
| LLM | Qwen3 0.6B ONNX | ONNX q4f16 + tokenizer | Verified |
| LLM | RWKV-7 World 0.4B | ONNX + bundled RWKV vocab | Verified; required visible model |
| LLM | Qwen2.5 0.5B ONNX | ONNX q4f16 + tokenizer | Verified |
| LLM | SmolLM2 360M ONNX | ONNX q4f16 + tokenizer | Verified |
| LLM | TinyLlama 1.1B Chat LiteRT | MediaPipe/LiteRT `.task` | Verified download, load, and `hello` response |
| LLM | Qwen2 0.5B ONNX | ONNX q4f16 + tokenizer | Hidden: garbled output |
| LLM | SmolLM2 135M ONNX | ONNX q4f16 + tokenizer | Hidden: abnormal output |
| LLM | SmolLM2 135M MHA ONNX | ONNX q4f16 + tokenizer | Hidden: ORT shape mismatch |
| LLM | MobileLLM 125M / 350M | ONNX, custom-code lineage | 125M hidden for webpage-fragment output; 350M remains candidate |
| LLM | Gemma 3 270M / 1B ONNX | ONNX + external data assets | 270M hidden for repeated-fragment output; 1B remains candidate |
| LLM | Llama 3.2 1B ONNX | ONNX + tokenizer | Direct candidate; license/runtime validation required |
| LLM | Llama 3.2 1B GGUF | llama.cpp / GGUF | Candidate; needs native llama.cpp adapter |
| LLM | Llama 3.2 1B Uncensored GGUF | llama.cpp / GGUF | Candidate; public non-gated source checked; test only with normal `hello` |
| LLM | Creative Writing RP 1B GGUF | llama.cpp / GGUF | Candidate; public non-gated source checked; test only with normal `hello` |
| LLM | Triangulum 1B Roleplay GGUF | llama.cpp / GGUF | Candidate; public non-gated source checked; test only with normal `hello` |
| LLM | Uyara Companion 1.5B GGUF | llama.cpp / GGUF | Candidate; public non-gated source checked; emotional-companion direction |
| LLM | TinyLlama 1.1B Chat MNN | MNN-LLM | Candidate; requires MNN-LLM runtime and multi-file asset manifest |
| LLM | DeepSeek-R1 Distill Qwen 1.5B ONNX | ONNX + tokenizer | Direct candidate; larger memory budget |
| LLM | Phi-3 Mini 4K ONNX | ONNX + tokenizer | Adapter/package manifest required |
| LLM | Gemma3-1B-IT `.task` | Google AI Edge LiteRT-LM | Adapter required |
| LLM | Qwen2.5-1.5B `.task` | Google AI Edge LiteRT-LM | Adapter required |
| LLM/VLM | Gemma-3n E2B / E4B `.task` | Google AI Edge LiteRT-LM | Adapter required; high peak memory |
| VLM | SmolVLM 256M Instruct | ONNX image-text-to-text | Adapter required |
| ASR | Whisper tiny / tiny.en | whisper.cpp ggml or ONNX encoder/decoder | Adapter required |
| ASR | Whisper base | whisper.cpp ggml or ONNX encoder/decoder | Adapter required |
| ASR | Moonshine tiny ONNX | ONNX ASR encoder/decoder | Adapter required |
| ASR | Vosk small-en / small-cn | Vosk Android runtime | Adapter required |
| ASR | sherpa-onnx Zipformer / Paraformer | sherpa-onnx Android | Adapter required |
| VAD | Silero VAD ONNX | ONNX Runtime | Adapter required |
| TTS | Kokoro 82M ONNX | ONNX + voices | Adapter required |
| TTS | Piper lessac / amy / ryanspeech voices | Piper ONNX runtime | Adapter required |
| TTS | sherpa-onnx VITS / Matcha | sherpa-onnx Android | Adapter required |
| Vision | MobileNetV2 / MobileNetV3 | TFLite / ONNX | Adapter required |
| Vision | EfficientNet-Lite | TFLite | Adapter required |
| Vision | CLIP ViT-B/32 ONNX | ONNX text/image encoders | Adapter required |
| Vision | YOLOv8n / YOLO11n | TFLite / NCNN / ONNX | Adapter required |
| Vision | NanoDet / YOLOX-Nano | NCNN / ONNX | Adapter required |
| Vision | MediaPipe Image Segmenter | TFLite task model | Adapter required |
| Vision | MoveNet Lightning | TFLite | Adapter required |
| Vision | BlazeFace / Face Mesh | MediaPipe Tasks | Adapter required |
| Vision | MobileSAM / FastSAM-s | ONNX / NCNN | Adapter required |
| Image generation | Stable Diffusion mobile variants | MNN / NCNN / TFLite / Qualcomm AI Hub | Adapter required; large memory budget |
| Image generation | Tiny-SD / LCM distilled SD | ONNX / MNN / NCNN | Adapter required |

Primary sources used for this queue include Hugging Face model APIs, Google AI Edge Gallery allowlist, Google LiteRT/MediaPipe docs, whisper.cpp, Piper, sherpa-onnx, Ultralytics export docs, and MediaPipe Tasks. Every candidate still needs a frozen URL, full asset manifest, license check, Android memory budget, and a real-device golden-output test before it can move into `ModelRegistry`.

## Download Behavior

External model downloads run in a user-started `dataSync` foreground service. Users can tap “后台下载，返回模型选择” to leave the download page; the active model card shows “下载中 xx%” and a progress bar while the service continues. The downloader keeps `.downloading` temporary files for resume, validates short reads before renaming, and requires tokenizer and `_data` assets when the model package needs them. RWKV uses ordered fallback sources: Hugging Face official URL, hf-mirror, then the GitHub Release asset.

## Android 17 Notes

The `v1.1.5` prototype targets Android 17 / API 37. Current code does not use local-network discovery, SMS/OTP APIs, custom notifications, or fixed-orientation constraints, so the Android 17 adaptation is focused on SDK targeting, backup safety, edge-to-edge Compose screens, adapter-gated model loading, and release verification.

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
