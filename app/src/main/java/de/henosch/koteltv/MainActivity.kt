package de.henosch.koteltv

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val YOUTUBE_WATCH_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"
private const val YOUTUBE_VR_USER_AGENT =
    "com.google.android.apps.youtube.vr.oculus/1.71.26 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
private const val YOUTUBE_VR_CLIENT_NAME = "28"
private const val YOUTUBE_VR_CLIENT_VERSION = "1.71.26"
private val YOUTUBE_VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KotelTVApp()
        }
    }
}

sealed interface StreamSource {
    data class Hls(val url: String) : StreamSource
    data class YouTube(val videoId: String) : StreamSource
}

data class StreamInfo(val name: String, val source: StreamSource)

fun extractYouTubeVideoId(input: String): String? {
    val value = input.trim()
    if (value.isBlank()) {
        return null
    }

    if (YOUTUBE_VIDEO_ID_REGEX.matches(value)) {
        return value
    }

    val normalized = if (value.startsWith("http://") || value.startsWith("https://")) {
        value
    } else {
        "https://$value"
    }

    return try {
        val uri = URI(normalized)
        val host = (uri.host ?: "").lowercase()
        val pathSegments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val queryParams = uri.rawQuery
            .orEmpty()
            .split('&')
            .mapNotNull { part ->
                if (part.isBlank()) {
                    null
                } else {
                    val pieces = part.split('=', limit = 2)
                    pieces[0] to pieces.getOrElse(1) { "" }
                }
            }
            .toMap()

        val candidate = when {
            host == "youtu.be" -> pathSegments.firstOrNull()
            host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") -> {
                when {
                    queryParams["v"] != null -> queryParams["v"]
                    pathSegments.firstOrNull() in setOf("embed", "shorts", "live", "watch") ->
                        pathSegments.getOrNull(1)
                    else -> null
                }
            }

            else -> null
        }

        candidate?.takeIf { YOUTUBE_VIDEO_ID_REGEX.matches(it) }
    } catch (_: Exception) {
        null
    }
}

private fun isQuietWindow(nowMillis: Long): Boolean {
    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMillis
    val day = cal.get(Calendar.DAY_OF_WEEK)
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    return (day == Calendar.FRIDAY && hour >= 15) || (day == Calendar.SATURDAY && hour < 18)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KotelTVApp() {
    val context = LocalContext.current
    val streams = remember {
        listOf(
            StreamInfo("Wilson's Arch", StreamSource.Hls("https://cdn.cast-tv.com/23595/Live_Kotel1_ABR/playlist.m3u8")),
            StreamInfo("Prayer Plaza", StreamSource.Hls("https://cdn.cast-tv.com/23595/Live_Kotel2_ABR/playlist.m3u8")),
            StreamInfo("Western Wall", StreamSource.Hls("https://cdn.cast-tv.com/23595/Live_Kotel3_ABR/playlist.m3u8")),
            StreamInfo("EarthCam", StreamSource.YouTube("https://www.youtube.com/watch?v=77akujLn4k8")),
            StreamInfo("Archaeological Park", StreamSource.YouTube("https://www.youtube.com/watch?v=zp6LNSoq000")),
            StreamInfo("Golden Gate", StreamSource.YouTube("https://www.youtube.com/watch?v=kXOjSHqo8dE")),
            StreamInfo("kotel nah", StreamSource.YouTube("https://www.youtube.com/watch?v=AKGqd20ik_A"))
        )
    }
    
    val defaultIndex = remember {
        streams.indexOfFirst { it.name == "Western Wall" }.takeIf { it >= 0 } ?: 0
    }
    var selectedIndex by remember { mutableIntStateOf(defaultIndex) }
    val selectedStream = streams[selectedIndex]
    var menuVisible by remember { mutableStateOf(true) }
    var lastInteractionAt by remember { mutableStateOf(SystemClock.uptimeMillis()) }
    var saOverride by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val rootFocusRequester = remember { FocusRequester() }
    val streamListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val itemWidth = 160.dp
    val sabbathToggleClicks = remember { mutableStateListOf<Long>() }
    val isQuietActive = remember(nowMillis, saOverride) {
        !saOverride && isQuietWindow(nowMillis)
    }
    var playerLoadingMessage by remember { mutableStateOf<String?>(null) }
    var playerErrorMessage by remember { mutableStateOf<String?>(null) }

    fun showPlayerError(message: String, error: Throwable? = null) {
        playerLoadingMessage = null
        playerErrorMessage = message
        if (error != null) {
            Log.e("KotelTV", message, error)
        } else {
            Log.e("KotelTV", message)
        }
    }

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

    suspend fun resolveYouTubeHlsUrl(videoIdOrUrl: String): String? = withContext(Dispatchers.IO) {
        val videoId = extractYouTubeVideoId(videoIdOrUrl)
        if (videoId == null) {
            Log.e("KotelTV", "YouTube resolver failed: invalid video id or URL: $videoIdOrUrl")
            return@withContext null
        }

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
                        "KotelTV",
                        "YouTube resolver failed: ${response.optJSONObject("playabilityStatus")}"
                    )
                }

                hlsManifestUrl
            } finally {
                playerConnection.disconnect()
            }
        } catch (error: Exception) {
            Log.e("KotelTV", "YouTube resolver exception", error)
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

    DisposableEffect(exoPlayer, selectedStream.name) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                showPlayerError("${selectedStream.name} konnte nicht geladen werden", error)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }
    
    LaunchedEffect(selectedStream) {
        when (val source = selectedStream.source) {
            is StreamSource.Hls -> {
                playerLoadingMessage = null
                playerErrorMessage = null
                exoPlayer.setMediaItem(buildHlsMediaItem(source.url))
                exoPlayer.prepare()
                if (!isQuietActive) {
                    exoPlayer.play()
                }
            }

            is StreamSource.YouTube -> {
                playerErrorMessage = null
                playerLoadingMessage = "${selectedStream.name} wird geladen..."
                exoPlayer.pause()
                exoPlayer.clearMediaItems()
                val resolvedUrl = resolveYouTubeHlsUrl(source.videoId)
                if (resolvedUrl != null) {
                    exoPlayer.setMediaItem(buildHlsMediaItem(resolvedUrl))
                    exoPlayer.prepare()
                    if (!isQuietActive) {
                        exoPlayer.play()
                    }
                    playerLoadingMessage = null
                } else {
                    showPlayerError("${selectedStream.name} konnte nicht geladen werden")
                }
            }
        }
    }

    LaunchedEffect(isQuietActive) {
        if (isQuietActive) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    LaunchedEffect(lastInteractionAt) {
        menuVisible = true
        delay(10_000)
        if (SystemClock.uptimeMillis() - lastInteractionAt >= 10_000) {
            menuVisible = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000)
        }
    }

    LaunchedEffect(menuVisible, selectedIndex) {
        if (menuVisible) {
            streamListState.scrollToItem(selectedIndex)
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
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            val now = SystemClock.uptimeMillis()
                            sabbathToggleClicks.add(now)
                            while (
                                sabbathToggleClicks.isNotEmpty() &&
                                now - sabbathToggleClicks.first() > 3000L
                            ) {
                                sabbathToggleClicks.removeAt(0)
                            }
                            if (sabbathToggleClicks.size >= 7) {
                                val next = !saOverride
                                saOverride = next
                                sabbathToggleClicks.clear()
                                val msg = if (next) "off" else "on"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                            return@onPreviewKeyEvent true
                        }

                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            selectedIndex = if (selectedIndex == 0) streams.lastIndex else selectedIndex - 1
                            coroutineScope.launch {
                                streamListState.animateScrollToItem(selectedIndex)
                            }
                            return@onPreviewKeyEvent true
                        }

                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            selectedIndex = if (selectedIndex == streams.lastIndex) 0 else selectedIndex + 1
                            coroutineScope.launch {
                                streamListState.animateScrollToItem(selectedIndex)
                            }
                            return@onPreviewKeyEvent true
                        }

                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER -> {
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            }
    ) {
        // Video Player
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isQuietActive) {
            Image(
                painter = painterResource(id = R.drawable.sabbath),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (!isQuietActive) {
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
        }

        // Stream Selector Overlay (erscheint bei Knopfdruck oder Fokus)
        if (menuVisible && !isQuietActive) {
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
                            state = streamListState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = centerPadding)
                        ) {
                            itemsIndexed(streams) { _, stream ->
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
