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

The app runs offline with local Room storage. To enable live Google Maps and Places details, add API keys to `local.properties`:

```properties
MAPS_API_KEY=...
PLACES_API_KEY=...
```
