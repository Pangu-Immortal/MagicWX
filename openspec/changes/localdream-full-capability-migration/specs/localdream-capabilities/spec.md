# localdream-capabilities

## ADDED Requirements

### Requirement: LocalDream model catalog

MagicWX SHALL register LocalDream image models as a separate image-model catalog covering SD1.5 CPU, SD1.5 NPU, SDXL NPU, Anima QNN, and upscaler packages.

#### Scenario: CPU image tab lists LocalDream CPU models

- **WHEN** the user opens the CPU 生图 tab
- **THEN** the app shows LocalDream SD1.5 CPU model cards
- **AND** models that are not generation-ready show a migration/runtime-blocked state
- **AND** MiniSD/MediaPipe and non-LocalDream SD1.5/MNN candidates are not shown in the main image-generation entry

#### Scenario: NPU image tab lists LocalDream NPU models

- **WHEN** the user opens the NPU 生图 tab
- **THEN** the app shows LocalDream SD1.5 NPU, SDXL NPU, Anima QNN, and upscaler cards
- **AND** generation stays blocked until QNN native runtime passes device validation
- **AND** non-LocalDream image candidates are not shown in the NPU main entry

### Requirement: Download-before-runtime support

MagicWX SHALL allow LocalDream image packages to be downloaded and checked even when the native runtime adapter is still blocked.

#### Scenario: LocalDream archive readiness requires fixed runtime files

- **WHEN** a LocalDream ZIP archive has been extracted
- **THEN** readiness is true only if every required runtime file for that backend type exists and is non-empty
- **AND** a partially extracted archive never appears as ready

### Requirement: LocalDream generation request protocol

MagicWX SHALL represent LocalDream-style generation options in a typed request object before they are sent to any native image backend.

#### Scenario: Text-to-image request includes complete generation parameters

- **WHEN** MagicWX builds a backend `/generate` request
- **THEN** the request includes prompt, negative prompt, steps, cfg, seed, width, height, scheduler, mode, preview format, output format, backend type, and memory mode
- **AND** prompt text and file paths are JSON escaped

#### Scenario: Image modes validate required inputs

- **WHEN** a request uses img2img, inpaint, or ultrafix mode
- **THEN** MagicWX validates the required source image and mask fields before contacting the native backend

### Requirement: LocalDream generation workspace UI

MagicWX SHALL expose LocalDream-style image generation controls in the image model run screen so users can prepare every supported request shape, monitor progress, review results, and reuse parameters before the native backend is complete.

#### Scenario: Prompt page exposes full generation controls

- **WHEN** the user opens an image model run screen
- **THEN** the screen provides prompt, negative prompt, mode, steps, CFG, seed, size, aspect ratio, scheduler, denoise, batch count, preview, preview stride, output format, and UltraFix controls
- **AND** the Generate action sends a typed LocalDream generation request instead of only a prompt string

#### Scenario: User monitors generation progress

- **WHEN** generation starts
- **THEN** the run screen shows backend status, progress percentage, elapsed time, and background-notification wording

#### Scenario: Result and history pages preserve generation workflow

- **WHEN** a generation completes or fails
- **THEN** the run screen keeps result, retry, parameter detail, copy-parameter, upscale, UltraFix, and session-history entry points visible
- **AND** unfinished native capabilities remain visible as explicit gated actions rather than disappearing from the workflow
