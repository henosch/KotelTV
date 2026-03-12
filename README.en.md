# KotelTV

KotelTV is an Android TV app for live streaming the three Kotel cameras:
`Wilson's Arch`, `Prayer Plaza`, `Western Wall`.

**Features**
- Remote-friendly navigation
- Fast zapping with the D-pad
- Default stream is Cam 2

**Build & Install**
- `gradle installDebug`
- Launch: `adb shell am start -n de.henosch.koteltv/.MainActivity`

**Tech**
- Jetpack Compose TV
- Media3 / ExoPlayer
- HLS streams
