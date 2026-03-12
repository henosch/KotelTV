# CarmenTV

CarmenTV is an Android TV app for curated live TV streams.

**Features**
- Remote-friendly navigation
- Fast zapping with the D-pad
- Default startup channel is `Bibel TV`
- `CHANNEL21` plays inside the app

**Build & Install**
- `gradle assembleDebug`
- Install: `adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk`
- Launch: `adb -s <device> shell am start -n de.henosch.carmentv/.MainActivity`

**Tech**
- Jetpack Compose TV
- Media3 / ExoPlayer
- HLS streams

**YouTube / CHANNEL21**
- `CHANNEL21` is not played through an external YouTube app.
- The earlier WebView embed was unreliable on the waipu stick because the actual `googlevideo` media requests failed there.
- CarmenTV now resolves the YouTube live stream into a real HLS URL at runtime and then hands that URL to ExoPlayer.
- Flow:
- The app first loads the regular YouTube watch page.
- It extracts cookies, `visitorData`, and `STS`.
- It then calls the YouTube player API with the `ANDROID_VR` client profile.
- `streamingData.hlsManifestUrl` returns the signed `m3u8`.
- That `m3u8` is then played by ExoPlayer like any other HLS channel.

**Note**
- The YouTube HLS URL is signed and temporary.
- Because of that, it is resolved again whenever the user switches to `CHANNEL21`.
