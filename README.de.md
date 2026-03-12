# CarmenTV

CarmenTV ist eine Android TV App zum Live-Streaming kuratierter Sender.

**Features**
- Navigation per Fernbedienung
- Schnelles Umschalten per D-Pad
- Startsender ist `Bibel TV`
- `CHANNEL21` läuft direkt in der App

**Build & Install**
- `gradle assembleDebug`
- Install: `adb -s <geraet> install -r app/build/outputs/apk/debug/app-debug.apk`
- Start: `adb -s <geraet> shell am start -n de.henosch.carmentv/.MainActivity`

**Technik**
- Jetpack Compose TV
- Media3 / ExoPlayer
- HLS-Streams

**YouTube / CHANNEL21**
- `CHANNEL21` wird nicht ueber eine externe YouTube-App abgespielt.
- Das fruehere WebView-Embed war auf dem waipu-Stick unzuverlaessig, weil die eigentlichen `googlevideo`-Requests dort fehlschlugen.
- Deshalb loest `CarmenTV` den YouTube-Livestream zur Laufzeit in einen echten HLS-Link auf und gibt diesen dann an ExoPlayer.
- Ablauf:
- Die App laedt zuerst die normale YouTube-Watch-Seite.
- Daraus werden Cookies, `visitorData` und `STS` gelesen.
- Danach wird die YouTube-Player-API mit dem `ANDROID_VR`-Client aufgerufen.
- Aus `streamingData.hlsManifestUrl` kommt die signierte `m3u8`.
- Diese `m3u8` wird anschliessend wie jeder andere HLS-Sender in ExoPlayer abgespielt.

**Hinweis**
- Die YouTube-HLS-URL ist signiert und zeitlich begrenzt.
- Deshalb wird sie beim Umschalten auf `CHANNEL21` jeweils frisch aufgeloest.
