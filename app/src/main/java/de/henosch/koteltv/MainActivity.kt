package de.henosch.koteltv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import android.os.SystemClock
import java.util.Calendar
import android.widget.Toast

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KotelTVApp()
        }
    }
}

data class StreamInfo(val name: String, val url: String)

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
            StreamInfo("Wilson's Arch", "https://cdn.cast-tv.com/23595/Live_Kotel1_ABR/playlist.m3u8"),
            StreamInfo("Prayer Plaza", "https://cdn.cast-tv.com/23595/Live_Kotel2_ABR/playlist.m3u8"),
            StreamInfo("Western Wall", "https://cdn.cast-tv.com/23595/Live_Kotel3_ABR/playlist.m3u8")
        )
    }
    
    var selectedStream by remember { mutableStateOf(streams[1]) }
    var menuVisible by remember { mutableStateOf(true) }
    var lastInteractionAt by remember { mutableStateOf(SystemClock.uptimeMillis()) }
    var saOverride by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val rootFocusRequester = remember { FocusRequester() }
    val buttonFocusRequesters = remember { List(streams.size) { FocusRequester() } }
    val wilsonClicks = remember { mutableStateListOf<Long>() }
    val isQuietActive = remember(nowMillis, saOverride) {
        !saOverride && isQuietWindow(nowMillis)
    }
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(selectedStream.url)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }
    
    LaunchedEffect(selectedStream) {
        exoPlayer.setMediaItem(MediaItem.fromUri(selectedStream.url))
        exoPlayer.prepare()
        if (!isQuietActive) {
            exoPlayer.play()
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

    LaunchedEffect(menuVisible, selectedStream) {
        if (menuVisible) {
            withFrameNanos { }
            val index = streams.indexOf(selectedStream).coerceAtLeast(0)
            buttonFocusRequesters[index].requestFocus()
        } else {
            rootFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (menuVisible) {
                    Modifier
                } else {
                    Modifier.focusRequester(rootFocusRequester).focusable()
                }
            )
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    lastInteractionAt = SystemClock.uptimeMillis()
                    menuVisible = true
                }
                false
            }
    ) {
        // Video Player
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = true // Standard TV Controller anzeigen
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

        // Stream Selector Overlay (erscheint bei Knopfdruck oder Fokus)
        if (menuVisible && !isQuietActive) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    streams.forEachIndexed { index, stream ->
                        val isSelected = selectedStream == stream
                        var isFocused by remember { mutableStateOf(false) }
                        val selectedColor = Color.White
                        val focusColor = Color(0xFF80DEEA)
                        val defaultColor = Color(0xFFECEFF1)
                        val showBorder = isFocused
                        val borderColor = if (isFocused) focusColor else Color.Transparent
                        val textColor = if (isSelected) selectedColor else defaultColor
                        val leftRequester = buttonFocusRequesters[if (index == 0) streams.lastIndex else index - 1]
                        val rightRequester = buttonFocusRequesters[if (index == streams.lastIndex) 0 else index + 1]

                        Box(
                            modifier = Modifier
                                .focusRequester(buttonFocusRequesters[index])
                                .focusProperties {
                                    left = leftRequester
                                    right = rightRequester
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                        val code = event.nativeKeyEvent.keyCode
                                        if (code == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                            code == android.view.KeyEvent.KEYCODE_ENTER
                                        ) {
                                            if (stream == streams[0]) {
                                                val now = SystemClock.uptimeMillis()
                                                wilsonClicks.add(now)
                                                while (wilsonClicks.isNotEmpty() && now - wilsonClicks.first() > 3000L) {
                                                    wilsonClicks.removeAt(0)
                                                }
                                                if (wilsonClicks.size >= 7) {
                                                    val next = !saOverride
                                                    saOverride = next
                                                    wilsonClicks.clear()
                                                    val msg = if (next) {
                                                        "off"
                                                    } else {
                                                        "on"
                                                    }
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                    false
                                }
                                .onFocusChanged {
                                    isFocused = it.isFocused
                                    if (it.isFocused) {
                                        selectedStream = stream
                                    }
                                }
                                .focusable()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { selectedStream = stream }
                                .then(
                                    if (showBorder) {
                                        Modifier.border(2.dp, borderColor, RoundedCornerShape(12.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = stream.name,
                                color = textColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = TextStyle(
                                    shadow = Shadow(Color.Black, Offset(0f, 0f), 8f)
                                )
                            )
                        }
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
