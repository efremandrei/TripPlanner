# Trip Planner

Trip Planner is a local-first Android app for planning trips with typed trip objects, offline storage, optional Google Maps display, Google Places detail fetching, light/dark skins, and a custom launcher icon.

## Build

```powershell
.\gradlew.bat :app:assembleDebug
```

The generated debug APK is also included as:

```text
TripPlanner-debug.apk
```

## Google Services

The app runs offline with local Room storage. Google services are optional and are injected at build time from `local.properties`, Gradle properties, or environment variables.

To enable Google account login, create an OAuth Web client ID in Google Cloud and add it before building:

```properties
GOOGLE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
```

For debug builds on this machine, configure the Android OAuth client with this package name:

```text
com.tripplanner.app
```

Current debug signing certificate fingerprints on this machine:

```text
SHA1: F8:1B:60:5B:0F:8E:20:FC:43:44:03:CD:B5:8E:5C:8D:03:3A:23:07
SHA256: 6A:FE:63:5F:1E:EB:6A:E2:DE:F1:3B:FD:D6:71:FE:E6:E1:10:3C:B9:CA:09:11:3D:4B:18:26:DE:43:3A:09:2D
```

To enable live Google Maps and Places details, add API keys too:

```properties
MAPS_API_KEY=...
PLACES_API_KEY=...
```

The app uses Android Credential Manager for Google sign-in. If `GOOGLE_WEB_CLIENT_ID` is missing, the APK still builds and local accounts still work, but the Google login button will show that the APK needs to be rebuilt with that value.

Release builds intentionally remove Google key values unless this is set:

```properties
ALLOW_GOOGLE_RELEASE=true
```

Only enable that flag after Android API key restrictions, API restrictions, quotas, and the release OAuth client are configured. When the flag is enabled, release builds require `MAPS_API_KEY`, `PLACES_API_KEY`, and `GOOGLE_WEB_CLIENT_ID`.

Security and cost controls for the Google Cloud project are tracked in:

```text
docs/google-security-cost-controls.md
```
