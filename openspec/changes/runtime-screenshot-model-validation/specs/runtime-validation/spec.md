## ADDED Requirements

### Requirement: README Runtime Screenshot
The README SHALL show a current screenshot captured from a running installed app build.

#### Scenario: README is viewed
- **WHEN** a reader opens the README
- **THEN** it contains an App runtime screenshot that exists under `screenshots/`

### Requirement: Model Validation Matrix
The README SHALL describe model status using evidence-backed runtime levels instead of unsupported blanket claims.

#### Scenario: A model is not fully callable
- **WHEN** endpoint, package, load, or inference validation is missing or fails
- **THEN** the README does not claim that model is fully supported

### Requirement: Runtime Smoke Coverage
The app SHALL be installed and smoke-tested on an available Android device before screenshot update is considered complete.

#### Scenario: Runtime smoke test runs
- **WHEN** the app is launched on device
- **THEN** model selection, model detail, navigation, and screenshot capture are verified

### Requirement: Built-in First-Run Experience
The app SHALL include one default built-in experience model that can be opened without downloading external model files.

#### Scenario: First-time user selects the built-in model
- **WHEN** no downloaded ONNX model files exist
- **AND** the user selects the built-in experience model
- **THEN** the app opens the chat screen without showing the download confirmation screen

### Requirement: Background Model Download
The app SHALL keep large model downloads running when the user leaves the download page.

#### Scenario: User backgrounds an active download
- **WHEN** the user starts downloading an external model
- **AND** taps the background-download action on the download progress screen
- **THEN** the app returns to model selection
- **AND** the foreground service continues the download with a persistent progress notification
