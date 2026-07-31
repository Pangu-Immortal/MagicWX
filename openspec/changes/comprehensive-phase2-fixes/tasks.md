## 1. Planning And Scope

- [x] 1.1 Record proposal for API compatibility, readiness, tokenizer, backup, documentation, and tests.
- [x] 1.2 Record technical design and requirements for model readiness and prototype quality gates.

## 2. Android Runtime Fixes

- [x] 2.1 Replace API 35-only prompt token removal with API 24-compatible logic.
- [x] 2.2 Strengthen `ModelDownloader` readiness checks for ONNX and tokenizer assets.
- [x] 2.3 Remove Transformer fallback to `RWKVTokenizer` and surface a clear loading error.
- [x] 2.4 Disable Android backup and device-transfer copying of app-private data.

## 3. Documentation And Product Wording

- [x] 3.1 Rewrite README to describe the current verified prototype and remove unrelated service claims.
- [x] 3.2 Add the missing human-facing design and development documents under `doc/`.
- [x] 3.3 Update product wording where needed so candidate models are not described as GA support.

## 4. Tests And Verification

- [x] 4.1 Add JVM tests for model registry invariants.
- [x] 4.2 Add JVM tests for readiness decisions using temporary model directories.
- [x] 4.3 Run `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, and `./gradlew lintDebug`.
- [x] 4.4 Run反空扫描 and final semantic review.
