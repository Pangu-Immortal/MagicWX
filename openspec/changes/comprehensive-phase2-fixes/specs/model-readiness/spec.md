## ADDED Requirements

### Requirement: Required local model assets
The system SHALL treat a local model as ready only when its required files exist and are non-empty.

#### Scenario: RWKV model has ONNX file
- **WHEN** a RWKV model directory contains a non-empty `.onnx` file
- **THEN** the model is reported as ready

#### Scenario: Transformer model misses tokenizer
- **WHEN** a Transformer model directory contains a non-empty `.onnx` file but does not contain a non-empty `tokenizer.json`
- **THEN** the model is not reported as ready

### Requirement: No incorrect tokenizer fallback
The system MUST NOT use the RWKV tokenizer for Transformer models.

#### Scenario: Transformer tokenizer missing during load
- **WHEN** a Transformer model is loaded without a valid `tokenizer.json`
- **THEN** loading fails with a clear tokenizer error before ONNX session creation

### Requirement: Downloaded model list reflects readiness
The system SHALL list only models that satisfy readiness checks in the downloaded-model set.

#### Scenario: Partial package exists
- **WHEN** a model directory contains partial or temporary download files
- **THEN** the downloaded-model set excludes that model
