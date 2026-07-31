## Validation Levels

Each model is evaluated at five levels:

1. UI selectable: the model appears in the running app and opens a detail/download screen.
2. Endpoint reachable: model and tokenizer URLs respond to HTTP range or HEAD probes.
3. Package ready: local file gate accepts only complete required assets.
4. Loadable: ONNX Runtime can create a session on device.
5. Callable: a fixed prompt can produce a non-empty response without crash.

## Device Strategy

Use the newest available local Android device first. If NUC Cuttlefish is unreachable, record it as an environment blocker and use connected USB/redroid devices.

## Reporting Rule

Do not claim a model is fully working unless levels 1-5 pass with evidence. Models that fail endpoint, package, load, or inference checks must remain experimental or blocked in README.

## Built-in Experience Model

The app will register one lightweight built-in experience model as the default entry. It is a local deterministic text-generation engine bundled in code, not an ONNX weight file. This keeps the APK and git repository small while giving first-time users an immediate chat path. README must describe it as an experience model and must not imply that it is a verified production LLM.

## Background Download Service

Large ONNX model downloads will run in a user-started `dataSync` foreground service. The service owns the network transfer, posts a progress notification, and emits in-process progress events to the ViewModel. The download screen can return to model selection without cancelling the service, allowing the user to keep using an already available model.

Android 13+ notification permission will be requested from the download action context. Android 14+ foreground service type requirements are satisfied by manifest declarations for `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC`. Android 15+ `dataSync` foreground service timeout risk is accepted for prototype downloads and documented as a release limitation.
