# KotelTV

KotelTV ist eine Android TV App zum Live-Streaming der drei Kotel-Kameras:
`Wilson's Arch`, `Prayer Plaza`, `Western Wall`.

**Features**
- Navigation per Fernbedienung
- Schnelles Umschalten per D-Pad
- Standard-Stream ist Cam 2

**Build & Install**
- `gradle installDebug`
- Start: `adb shell am start -n de.henosch.koteltv/.MainActivity`

**Technik**
- Jetpack Compose TV
- Media3 / ExoPlayer
- HLS-Streams
