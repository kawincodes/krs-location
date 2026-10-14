# KRS Location

A small Android app that overrides the device's live location using Android's
built-in **mock location** mechanism. It is meant for testing location-aware
apps (delivery, ride, fitness, geofencing, and so on) on a real device without
having to physically move.

The app registers itself as a test provider for the `gps`, `network` and
`fused` providers and publishes your chosen coordinate once per second from a
foreground service, so every app on the device that reads location sees the
mocked position.

## Features

- Pick a position by tapping or dragging the marker on an OpenStreetMap map,
  or by typing latitude / longitude.
- Start / stop mocking with a persistent notification (stop button included).
- **Walk mode**: when enabled, a new position is reached at a configurable
  speed (default 1.4 m/s) instead of teleporting, which keeps apps that reject
  sudden jumps happy. Speed and bearing are reported in the location fix.
- Direction pad to nudge the position north / south / east / west by a
  configurable number of metres while mocking is running.
- **Route simulation**: tap waypoints on the map, then play the route at a
  chosen speed with pause, resume, loop and live progress on the map and in
  the notification.
- **Favorites**: save named places and jump back to them from a bottom sheet.
- **Address search** through OpenStreetMap's Nominatim (no API key).
- **Share and paste**: share the current position as a map link, paste
  "lat, lng" or Google Maps / OSM / Apple Maps links, and open `geo:` links or
  shared text directly in the app.
- **Quick Settings tile and home screen widget** to start or stop mocking at
  the last position without opening the app.
- **In-app updater** that checks GitHub Releases and installs new versions.
- Developer info screen with version, contact and update controls.
- Remembers your last position, route and settings.
- No API keys, no accounts, no root.

## UI stack

- **Jetpack Compose** for the whole UI (no XML layouts).
- **shadcn/ui style components** written in Compose: Button, Card, Input,
  Switch, Badge, Alert, Separator and Dialog, using shadcn's default "zinc"
  colour tokens with light and dark themes. See
  `app/src/main/java/com/krstechsolutions/krslocation/ui/components` and
  `ui/theme`.
- **Lucide icons** via `com.composables:icons-lucide`, used as Compose
  `ImageVector`s (`Lucide.MapPin`, `Lucide.Play`, ...).
- **osmdroid** map embedded with `AndroidView`; tiles are colour-inverted in
  dark mode.

## Requirements

- Android 8.0 (API 26) or newer.
- The device must have **Developer options** enabled and this app selected as
  the **mock location app**. Android only allows one app at a time to do this
  and it cannot be set programmatically.

## Setup on the device

1. Install the APK.
2. Open **Settings › About phone** and tap **Build number** seven times to
   enable Developer options.
3. Open **Settings › System › Developer options › Select mock location app**
   and choose **KRS Location**. The app's "Developer options" button opens
   this screen for you.
4. Launch the app, pick a position, grant the location and notification
   permissions when asked, and tap **Start mocking**.

The status line at the top of the controls turns green once the app is the
selected mock location app.

## Releases and signing

Pushing a tag such as `v1.2.0` runs `.github/workflows/release.yml`, which
builds a **signed** release APK, verifies the signature and publishes it as a
GitHub Release with the APK attached. The app's About screen checks the latest
release and can download and install it (the system installer still asks for
confirmation).

One-time setup:

1. Create a keystore (keep it safe; losing it means users must uninstall to update):

   ```bash
   keytool -genkeypair -v -keystore krs-release.jks -alias krs \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Add these repository secrets (Settings › Secrets and variables › Actions):

   | Secret              | Value                                   |
   |---------------------|-----------------------------------------|
   | `KEYSTORE_BASE64`   | `base64 -w0 krs-release.jks`            |
   | `KEYSTORE_PASSWORD` | keystore password                       |
   | `KEY_ALIAS`         | `krs` (or the alias you chose)          |
   | `KEY_PASSWORD`      | key password                            |

3. Release: `git tag v1.2.0 && git push origin v1.2.0`, or run the
   "Release" workflow manually with a version number.

For local signed builds put the same values in a `keystore.properties` file at
the repository root (ignored by git) with `KEYSTORE_FILE=/path/to/krs-release.jks`.
Without a keystore, `assembleRelease` signs with the debug key so the APK stays
installable for testing.

## Building

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleRelease        # needs a signing config
```

Android Studio Ladybug or newer, JDK 17+, and the Android SDK with platform
35 installed. Point `local.properties` at your SDK (`sdk.dir=/path/to/sdk`).

## Project layout

```
app/src/main/java/com/krstechsolutions/krslocation/
  MainActivity.kt          Hosts the Compose UI and configures osmdroid
  MainViewModel.kt         Screen state and actions (target, inputs, permissions)
  MockLocationService.kt   Foreground service that registers test providers
                           and publishes the position once a second
  MockLocationUtils.kt     "Is this the selected mock app?" and settings intent
  LocationPrefs.kt         Persisted position and movement settings
  GeoMath.kt               Distance / bearing / destination helpers
  ui/MainScreen.kt         The screen: top bar, map, status, inputs, nudge pad
  ui/map/OsmMap.kt         osmdroid MapView wrapped for Compose
  ui/components/           shadcn-style Compose primitives
  ui/theme/                shadcn colour and typography tokens
```

## Notes and limitations

- Apps that use Google Play services location on Android 12+ read the
  `fused` provider, which is why it is mocked alongside `gps` and `network`.
  A few OEM builds refuse the `fused` test provider; the app simply skips it.
- Some apps detect mocked locations through `Location.isMock()` and may
  refuse to work. That is by design on Android and cannot be hidden by a
  normal (non-root) app.
- Keep the device's own location services turned on. If GPS is disabled the
  mocked `gps` provider may be ignored by some apps.
- Stop mocking before relying on real navigation.

## Intended use

Testing and development only. Do not use it to misrepresent your location to
services that depend on it (attendance, delivery, games, banking, etc.);
doing so can violate their terms and local law.
