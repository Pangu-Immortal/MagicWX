## ADDED Requirements

### Requirement: Isolated Image Backend Process

MagicWX SHALL run local image generation through an isolated backend process instead of invoking unstable native diffusion code directly from the app process.

#### Scenario: Backend health check

- **WHEN** the app starts a downloaded CPU image model
- **THEN** the backend process SHALL expose a local `/health` endpoint
- **AND** the Android service SHALL publish a ready or error state.

#### Scenario: Streaming image generation

- **WHEN** the user starts image generation
- **THEN** the generation service SHALL call the local `/generate` endpoint
- **AND** progress SHALL be surfaced to UI and notification state.

#### Scenario: User cancellation

- **WHEN** the user stops generation
- **THEN** the active HTTP call SHALL be cancelled
- **AND** the foreground notification SHALL stop.

### Requirement: Device Safety Gate

MagicWX SHALL preserve device runtime gates for known-broken image backends.

#### Scenario: Known broken Samsung runtime

- **WHEN** the device matches the known s5e8855/a56x MNN SD1.5 failure profile
- **THEN** the app SHALL block MNN SD1.5 generation before starting the backend process.
