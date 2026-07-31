## ADDED Requirements

### Requirement: Android API compatibility
The Android prototype SHALL avoid APIs that require a platform version higher than the configured `minSdk`.

#### Scenario: Lint checks API usage
- **WHEN** `./gradlew lintDebug` runs
- **THEN** source code does not use API 35-only calls for API 24 targets

### Requirement: Public wording matches verified status
Repository documentation SHALL describe the current app as a prototype unless runtime evidence proves production readiness.

#### Scenario: README describes models
- **WHEN** a contributor reads `README.md`
- **THEN** model entries are presented as registered prototype candidates rather than guaranteed production support

### Requirement: Runtime data is not backed up
The Android app SHALL prevent system backup and device transfer from copying app-private model or runtime data.

#### Scenario: Backup configuration is inspected
- **WHEN** Android manifest and backup XML are inspected
- **THEN** backup is disabled or rules exclude app-private domains

### Requirement: Focused verification exists
The change SHALL include focused JVM tests for model metadata and readiness behavior.

#### Scenario: Unit tests run
- **WHEN** `./gradlew testDebugUnitTest` runs
- **THEN** tests cover model registry invariants and local model readiness decisions
