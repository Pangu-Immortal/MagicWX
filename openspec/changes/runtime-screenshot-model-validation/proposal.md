## Why

MagicWX v1.1.4 is the current Android 17 prototype candidate, and README model support claims must remain tied to runtime screenshots and device validation evidence rather than compile-only checks.

## What Changes

- Capture current App runtime screenshots from an installed Android build.
- Update README with the verified screenshot and current runtime validation status.
- Validate model selection, download confirmation, navigation, package readiness gates, and model endpoint availability.
- Attempt real model loading/inference where model assets and device capacity allow it.
- Add one built-in experience model so first-time users can open the app and chat without downloading model weights.
- Move large model downloads into a user-started foreground service with a persistent progress notification and a "background download" path.

## Impact

- README screenshot section and `screenshots/` assets.
- Runtime validation evidence for all registered models.
- Possible fixes if a model cannot be called because of app-side code defects.
- Android manifest permissions/service declarations for notification-backed downloads.
- ViewModel state handling for foreground-service download progress and completion events.
