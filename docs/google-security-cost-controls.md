# Google Security and Cost Controls

This app is local-first. Google services are optional and must stay limited to the dedicated Trip Planner Google Cloud project.

## Required Google Cloud Setup

- Project: `Trip Planner`
- Package name: `com.tripplanner.app`
- Debug SHA-1 for this machine:
  `F8:1B:60:5B:0F:8E:20:FC:43:44:03:CD:B5:8E:5C:8D:03:3A:23:07`
- Debug SHA-256 for this machine:
  `6A:FE:63:5F:1E:EB:6A:E2:DE:F1:3B:FD:D6:71:FE:E6:E1:10:3C:B9:CA:09:11:3D:4B:18:26:DE:43:3A:09:2D`

Before publishing a release build, add the release signing SHA-1 too. If Google Play App Signing is used, use the Play Console app-signing certificate fingerprint, not only the local upload-key fingerprint.

## API Keys

Use restricted Android keys only.

Preferred setup:

- `MAPS_API_KEY`
  - Application restriction: Android apps
  - Allowed Android app: `com.tripplanner.app` + the active debug/release SHA-1 fingerprints
  - API restriction: Maps SDK for Android only
- `PLACES_API_KEY`
  - Application restriction: Android apps
  - Allowed Android app: `com.tripplanner.app` + the active debug/release SHA-1 fingerprints
  - API restriction: Places API (New) only

If the same key is reused for both `MAPS_API_KEY` and `PLACES_API_KEY`, restrict that key to only Maps SDK for Android and Places API (New).

Do not authorize old mock/debug package names such as `com.tripplanner.app.mock`.

## Enabled APIs

Only enable APIs that the app currently uses:

- Maps SDK for Android
- Places API (New)

Disable unused Google Maps Platform services at the project level.

## Quotas and Budget

Keep a low quota on Places API (New) methods and any Maps SDK quotas that Google exposes for the project. Quotas are the real project-wide stop for API volume.

Keep the `$5` Google Cloud budget alert on the Trip Planner project. Budget alerts are warnings only; they do not automatically stop usage or billing. If a hard stop is needed, use API quotas and/or billing automation.

The app also has local per-device monthly counters for Google API usage. Those counters are a convenience guard and status display, not a project-wide security or billing boundary.

## App Check

App Check is not enabled in the app yet. If we add it later, the expected path is:

- Add Firebase to the Trip Planner Android app.
- Add Firebase App Check dependencies.
- Use Play Integrity as the Android attestation provider.
- Test with debug tokens.
- Monitor App Check metrics first.
- Enable enforcement only after legitimate updated app traffic is verified.

App Check should be treated as an additional legitimacy signal, not a replacement for API key restrictions, API restrictions, quotas, and budget alerts.

## Build Behavior

The app still builds and runs offline when Google keys are blank.

- Places SDK initialization is skipped when `PLACES_API_KEY` is blank.
- Live Google map rendering is skipped when `MAPS_API_KEY` is blank.
- Google account linking is disabled when `GOOGLE_WEB_CLIENT_ID` is blank.
- Release builds blank Google key/client values by default.
- To build a Google-enabled release, set `ALLOW_GOOGLE_RELEASE=true`; this requires `MAPS_API_KEY`, `PLACES_API_KEY`, and `GOOGLE_WEB_CLIENT_ID`.

Root APK artifacts pushed to GitHub must match the current normal build only. Mock APK artifacts are intentionally hidden/removed.

## Official References

- Google Maps Platform API security best practices: https://developers.google.com/maps/api-security-best-practices
- Maps SDK for Android usage and billing: https://developers.google.com/maps/documentation/android-sdk/usage-and-billing
- Places SDK for Android usage and billing: https://developers.google.com/maps/documentation/places/android-sdk/usage-and-billing
- Places SDK for Android App Check: https://developers.google.com/maps/documentation/places/android-sdk/app-check
- Google Cloud budget alerts: https://cloud.google.com/billing/docs/how-to/budgets
