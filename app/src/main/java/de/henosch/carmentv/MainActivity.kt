package de.henosch.carmentv

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val YOUTUBE_WATCH_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"
private const val YOUTUBE_VR_USER_AGENT =
    "com.google.android.apps.youtube.vr.oculus/1.71.26 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
private const val YOUTUBE_VR_CLIENT_NAME = "28"
private const val YOUTUBE_VR_CLIENT_VERSION = "1.71.26"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CarmenTVApp()
        }
    }
}

sealed interface StreamSource {
    data class Hls(val url: String) : StreamSource
    data class YouTube(val videoId: String) : StreamSource
}

data class StreamInfo(val name: String, val source: StreamSource)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CarmenTVApp() {
    val context = LocalContext.current
    val streams = remember {
        listOf(
            StreamInfo(
                "QVC ZWEI",
                StreamSource.Hls("https://qvcde-voslive.akamaized.net/Content/HLS/Live/channel(a4433f54-ff00-9393-429b-9eac7a9fdd29)/variant.m3u8")
            ),
            StreamInfo(
                "QVC Style",
                StreamSource.Hls("https://qvcde-voslive.akamaized.net/Content/HLS/Live/channel(a76ed561-562f-455c-ef74-e20c46a090ef)/variant.m3u8")
            ),
            StreamInfo(
                "QVC",
                StreamSource.Hls("https://qvcde-live.akamaized.net/hls/live/2097104/qvc/master.m3u8")
            ),
            StreamInfo(
                "HSE24",
                StreamSource.Hls("https://hse24.akamaized.net/hls/live/2006663/hse24/master_576p25.m3u8")
            ),
            StreamInfo(
                "HSE24 Extra",
                StreamSource.Hls("https://hse24extra.akamaized.net/hls/live/2006596/hse24extra/master_576p25.m3u8")
            ),
            StreamInfo(
                "HSE24 Trend",
                StreamSource.Hls("https://hse24trend.akamaized.net/hls/live/2006597/hse24trend/master_432p25.m3u8")
            ),
            StreamInfo(
                "CHANNEL21",
                StreamSource.YouTube("0Vz3bK6K0f4")
            ),
            StreamInfo(
                "ERF",
                StreamSource.Hls("https://fastly.live.brightcove.com/6384599780112/eu-central-1/6194387526001/eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJob3N0IjoiaXNjdXhqLmVncmVzcy5haGc3NmwiLCJhY2NvdW50X2lkIjoiNjE5NDM4NzUyNjAwMSIsImVobiI6ImZhc3RseS5saXZlLmJyaWdodGNvdmUuY29tIiwiaXNzIjoiYmxpdmUtcGxheWJhY2stc291cmNlLWFwaSIsInN1YiI6InBhdGhtYXB0b2tlbiIsImF1ZCI6WyI2MTk0Mzg3NTI2MDAxIl0sImp0aSI6IjYzODQ1OTk3ODAxMTIifQ.TkzX0_PkN3akZ9Q3MrUxjUHkXr003ak95Gz-H8SdDkI/playlist-hls.m3u8")
            ),
            StreamInfo(
                "Bibel TV",
                StreamSource.Hls("https://bibeltv01.iptv-playoutcenter.de/bibeltv01/bibeltv01.stream_all/playlist.m3u8")
            ),
            StreamInfo(
                "Bibel Impulse",
                StreamSource.Hls("https://bibeltv02.iptv-playoutcenter.de/bibeltv02/bibeltv02.stream_all/playlist.m3u8")
            ),
            StreamInfo(
                "EchtJetzt",
                StreamSource.Hls("https://bibeltv03.iptv-playoutcenter.de/bibeltv03/bibeltv03.stream_all/playlist.m3u8")
            ),
            StreamInfo(
                "HopeTV",
                StreamSource.Hls("https://cdn-de-fra.b-cdn.net/hcorg_Lmro4tmzOp1/s/jsl_AQfP7b5HoTM/W7Vfg/480p/main.m3u8")
            ),
            StreamInfo(
                "MB classic",
                StreamSource.Hls("https://dash2.antik.sk/live/mb_classic/index.m3u8")
            ),
            StreamInfo(
                "Musik 70er",
                StreamSource.Hls("https://lightning-now70s-samsungnz.amagi.tv/playlist.m3u8")
            ),
            StreamInfo(
                "Musik 80er",
                StreamSource.Hls("https://lightning-now80s-samsungnz.amagi.tv/playlist.m3u8")
            ),
            StreamInfo(
                "Musik 90er",
                StreamSource.Hls("https://lightning-now90s-samsungnz.amagi.tv/playlist.m3u8")
            ),
            StreamInfo(
                "XITE Hits",
                StreamSource.Hls("https://jmp2.uk/stvp-DEBA2200007V6")
            ),
            StreamInfo(
                "XITE 80s",
                StreamSource.Hls("https://d1n314cytqn9r3.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-bklagqy82v0pr/XITE_80s_Flashback.m3u8")
            ),
            StreamInfo(
                "XITE 90s",
                StreamSource.Hls("https://d284aawtm5vi48.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-fjdfi2br1jtq7/XITE_90s_Throwback.m3u8")
            ),
            StreamInfo(
                "XITE Rock",
                StreamSource.Hls("https://d198ro05q94rc4.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-c5xdq9qwilrd2/XITE_Rock_On.m3u8")
            ),
            StreamInfo(
                "New KPOP",
                StreamSource.Hls("https://newidco-newkid-1-eu.xiaomi.wurl.tv/playlist.m3u8")
            ),
            StreamInfo(
                "Welt News",
                StreamSource.Hls("https://welt.personalstream.tv/v1/master.m3u8")
            )
        )
    }

    val defaultIndex = remember {
        streams.indexOfFirst { it.name == "Bibel TV" }.takeIf { it >= 0 } ?: 0
    }
    var selectedIndex by remember { mutableIntStateOf(defaultIndex) }
    val selectedStream = streams[selectedIndex]
    var menuVisible by remember { mutableStateOf(true) }
    var lastInteractionAt by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val rootFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val itemWidth = 160.dp
    var playerLoadingMessage by remember { mutableStateOf<String?>(null) }
    var playerErrorMessage by remember { mutableStateOf<String?>(null) }

    fun buildHlsMediaItem(url: String): MediaItem {
        return MediaItem.Builder()
            .setUri(url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
    }

    fun buildMediaItem(stream: StreamInfo): MediaItem? {
        val source = stream.source as? StreamSource.Hls ?: return null
        return buildHlsMediaItem(source.url)
    }

    suspend fun resolveYouTubeHlsUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val watchUrl =
            "https://www.youtube.com/watch?v=${URLEncoder.encode(videoId, "UTF-8")}&bpctr=9999999999&has_verified=1"
        val watchConnection = (URL(watchUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", YOUTUBE_WATCH_USER_AGENT)
            setRequestProperty("Accept-Language", "en-us,en;q=0.5")
            setRequestProperty("Cookie", "PREF=hl=en&tz=UTC; SOCS=CAI")
        }

        try {
            val html = watchConnection.inputStream.bufferedReader().use { it.readText() }
            val visitorData =
                Regex("\"visitorData\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            val signatureTimestamp =
                Regex("\"STS\":(\\d+)").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 20521
            val setCookies = watchConnection.headerFields["Set-Cookie"]
                .orEmpty()
                .map { it.substringBefore(';') }
                .filter { it.isNotBlank() }

            val cookieHeader = buildList {
                add("PREF=hl=en&tz=UTC")
                add("SOCS=CAI")
                addAll(setCookies)
            }.joinToString("; ")

            val requestBody = JSONObject().apply {
                put(
                    "context",
                    JSONObject().apply {
                        put(
                            "client",
                            JSONObject().apply {
                                put("clientName", "ANDROID_VR")
                                put("clientVersion", YOUTUBE_VR_CLIENT_VERSION)
                                put("deviceMake", "Oculus")
                                put("deviceModel", "Quest 3")
                                put("androidSdkVersion", 32)
                                put("userAgent", YOUTUBE_VR_USER_AGENT)
                                put("osName", "Android")
                                put("osVersion", "12L")
                                put("hl", "en")
                                put("timeZone", "UTC")
                                put("utcOffsetMinutes", 0)
                            }
                        )
                    }
                )
                put("videoId", videoId)
                put(
                    "playbackContext",
                    JSONObject().apply {
                        put(
                            "contentPlaybackContext",
                            JSONObject().apply {
                                put("html5Preference", "HTML5_PREF_WANTS")
                                put("signatureTimestamp", signatureTimestamp)
                            }
                        )
                    }
                )
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }.toString()

            val playerConnection =
                (URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 15_000
                        readTimeout = 15_000
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("User-Agent", YOUTUBE_VR_USER_AGENT)
                        setRequestProperty("X-YouTube-Client-Name", YOUTUBE_VR_CLIENT_NAME)
                        setRequestProperty("X-YouTube-Client-Version", YOUTUBE_VR_CLIENT_VERSION)
                        setRequestProperty("Origin", "https://www.youtube.com")
                        setRequestProperty("Cookie", cookieHeader)
                        visitorData?.let {
                            setRequestProperty("X-Goog-Visitor-Id", it)
                        }
                    }

            try {
                playerConnection.outputStream.use { output ->
                    output.write(requestBody.toByteArray())
                }

                val responseText = playerConnection.inputStream.bufferedReader().use { it.readText() }
                val response = JSONObject(responseText)
                val hlsManifestUrl = response
                    .optJSONObject("streamingData")
                    ?.optString("hlsManifestUrl")
                    .orEmpty()
                    .takeIf { it.isNotBlank() }

                if (hlsManifestUrl == null) {
                    Log.e(
                        "CarmenTV",
                        "YouTube resolver failed: ${response.optJSONObject("playabilityStatus")}"
                    )
                }

                hlsManifestUrl
            } finally {
                playerConnection.disconnect()
            }
        } catch (error: Exception) {
            Log.e("CarmenTV", "YouTube resolver exception", error)
            null
        } finally {
            watchConnection.disconnect()
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            buildMediaItem(selectedStream)?.let { mediaItem ->
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
        }
    }

    LaunchedEffect(selectedIndex) {
        when (selectedStream.source) {
            is StreamSource.Hls -> {
                playerLoadingMessage = null
                playerErrorMessage = null
                buildMediaItem(selectedStream)?.let { mediaItem ->
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }

            is StreamSource.YouTube -> {
                val source = selectedStream.source
                playerErrorMessage = null
                playerLoadingMessage = "${selectedStream.name} wird geladen..."
                exoPlayer.pause()
                exoPlayer.clearMediaItems()
                val resolvedUrl = resolveYouTubeHlsUrl(source.videoId)
                if (resolvedUrl != null) {
                    exoPlayer.setMediaItem(buildHlsMediaItem(resolvedUrl))
                    exoPlayer.prepare()
                    exoPlayer.play()
                    playerLoadingMessage = null
                } else {
                    playerLoadingMessage = null
                    playerErrorMessage = "${selectedStream.name} konnte nicht geladen werden"
                }
            }
        }
    }

    LaunchedEffect(lastInteractionAt) {
        menuVisible = true
        delay(10_000)
        if (SystemClock.uptimeMillis() - lastInteractionAt >= 10_000) {
            menuVisible = false
        }
    }

    LaunchedEffect(menuVisible, selectedIndex) {
        if (menuVisible) {
            listState.scrollToItem(selectedIndex)
        }
        rootFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    lastInteractionAt = SystemClock.uptimeMillis()
                    menuVisible = true
                    when (event.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            selectedIndex = if (selectedIndex == 0) streams.lastIndex else selectedIndex - 1
                            scope.launch {
                                listState.animateScrollToItem(selectedIndex)
                            }
                            return@onPreviewKeyEvent true
                        }

                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            selectedIndex = if (selectedIndex == streams.lastIndex) 0 else selectedIndex + 1
                            scope.launch {
                                listState.animateScrollToItem(selectedIndex)
                            }
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            }
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
            },
            update = { view ->
                view.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        playerLoadingMessage?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        playerErrorMessage?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC220000), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (menuVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp)
            ) {
                BoxWithConstraints {
                    val centerPadding = ((maxWidth - itemWidth) / 2).coerceAtLeast(0.dp)
                    val itemHeight = 48.dp
                    val focusColor = Color(0xFF80DEEA)
                    val backgroundColor = Color(0xAA000000)
                    Box {
                        LazyRow(
                            state = listState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = centerPadding)
                        ) {
                            items(streams) { stream ->
                                Box(
                                    modifier = Modifier
                                        .width(itemWidth)
                                        .height(itemHeight)
                                        .background(backgroundColor, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stream.name,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(
                                            shadow = Shadow(Color.Black, Offset(0f, 0f), 8f)
                                        )
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .width(itemWidth)
                                .height(itemHeight)
                                .border(4.dp, focusColor, RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
}
