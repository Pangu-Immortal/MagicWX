## 1. Planning And Environment

- [x] 1.1 Create OpenSpec scope for runtime screenshot and model validation.
- [x] 1.2 Select available Android test device and record SDK/ABI/storage.
- [x] 1.3 Confirm NUC availability or record blocker.

## 2. Runtime App Testing

- [x] 2.1 Build and install the current debug APK.
- [x] 2.2 Launch app and verify model selection screen.
- [ ] 2.3 Verify model detail/download confirmation/navigation for all registered models.
- [x] 2.4 Capture current runtime screenshot into `screenshots/`.
- [x] 2.5 Verify built-in experience model opens chat without external download.
- [x] 2.6 Verify background download action returns to model selection while service remains active.
- [x] 2.7 Verify model card shows background download badge and progress bar.

## 3. Model Validation

- [x] 3.1 Probe model and tokenizer endpoints for all registered models.
- [x] 3.2 Verify local package readiness gates with tests.
- [ ] 3.3 Attempt real model load/inference where assets and device capacity allow it.
- [x] 3.4 Record any blocked models with precise evidence.
- [x] 3.5 Verify foreground notification/service declarations for Android 14+ dataSync downloads.
- [x] 3.6 Harden downloader resume behavior for short reads and interrupted transfers.

## 4. Documentation And Final QA

- [x] 4.1 Update README screenshot and validation matrix.
- [ ] 4.2 Run Gradle/OpenSpec validation after edits.
- [ ] 4.3 Commit and push scoped changes if verification is acceptable.
