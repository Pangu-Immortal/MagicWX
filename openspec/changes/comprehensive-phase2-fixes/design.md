## Context

MagicWX is currently a single-module Android Compose prototype. Phase-one evidence showed that it can build, but lint is blocked by an API 35-only Kotlin collection call, model readiness is too loose, Transformer tokenization can silently fall back to the wrong tokenizer, backup rules include app runtime state, and public README wording overstates verified support.

## Goals / Non-Goals

**Goals:**
- Keep the current prototype architecture intact while removing known false-ready and false-support states.
- Make model readiness depend on required package files, not only the presence of any `.onnx` file.
- Fail Transformer loading clearly when `tokenizer.json` is missing or invalid.
- Add focused JVM tests for model registry and readiness logic.
- Align public documentation with current verified behavior.

**Non-Goals:**
- Do not replace ONNX Runtime, implement a production model package manager, or add new remote services.
- Do not claim Transformer models are production-ready without dry-run and golden-output evidence.
- Do not fix unrelated dependency upgrade warnings in this change.

## Decisions

- Readiness stays in `ModelDownloader` because that class owns local model directories and file naming. This keeps UI state checks and downloaded-model discovery using one source of truth.
- Transformer `tokenizer.json` becomes a required asset. Falling back to `RWKVTokenizer` hides an invalid model package and creates incorrect outputs; a clear loading error is safer.
- ONNX validation remains lightweight. The app will check file existence and non-zero length now, while full session dry-run and checksums stay future work because they require model-specific fixtures and large assets.
- Backup is disabled at the manifest level and XML rules exclude app files/shared preferences. This avoids accidental cloud or device-transfer copy of downloaded models and user runtime data.
- README is rewritten as prototype documentation. The long commercial service block is removed because it is unrelated to the repository code and conflicts with the current product document.

## Risks / Trade-offs

- Requiring `tokenizer.json` means some previously downloaded Transformer directories become not ready until the tokenizer is downloaded. Mitigation: the UI returns to download/error flow instead of loading a misleading tokenizer.
- Lightweight readiness still cannot detect corrupt ONNX graphs. Mitigation: keep this documented as a remaining gate and cover it with future dry-run work.
- Disabling backup may prevent convenient migration of benign preferences. Mitigation: prioritize privacy and model/runtime safety until data classification is complete.

## Migration Plan

1. Add readiness helpers and tests without changing model storage layout.
2. Replace API 35-only collection usage with API 24-compatible logic.
3. Disable backup and update README/doc wording.
4. Run JVM tests, build, and lint. Any remaining lint warnings are classified with evidence.

## Open Questions

- Which exact models should graduate from experimental to verified after device dry-run and golden-output tests?
- Should future model packages include signed manifests with SHA-256 for all required assets?
