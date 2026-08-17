package com.example.radio_vertical

import android.Manifest
import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.radio_vertical.ui.theme.Radio_verticalTheme
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class RadioStation(
    val name: String,
    val url: String,
    val backgroundColor: Color,
    val logoUrl: String? = null,
    val apiUrl: String? = null,
    val shortcode: String? = null
)

@UnstableApi
class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<Player?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Radio_verticalTheme {
                // Background is solid Color.Black
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    RadioApp(player = mediaController)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
    }
}

fun vibratePhone(context: Context, duration: Long) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(duration)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun RadioApp(radioViewModel: RadioViewModel = viewModel(), player: Player?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ia_radio_prefs", Context.MODE_PRIVATE) }
    
    val radioStations = remember {
        listOf(
            RadioStation("LIMBIK FRECUENCIES", "https://limbikfreq.com/listen/limbik_frequencies/128.mp3", Color.Black, "https://limbikfreq.com/static/uploads/limbik_frequencies/logo.png", "https://limbikfreq.com/api/nowplaying/limbik_frequencies", "limbik_frequencies"),
            RadioStation("ISEKOI RADIO", "https://public.isekoi-radio.com/listen/isekoi/radio.mp3", Color.Black, "https://public.isekoi-radio.com/static/uploads/isekoi/logo.png", "https://public.isekoi-radio.com/api/nowplaying/isekoi", "isekoi"),
            RadioStation(
                name = "MINIMAL TECHNO",
                url = "https://uzic.ice.infomaniak.ch/uzic-128.aac",
                backgroundColor = Color.Black,
                logoUrl = "https://images.unsplash.com/photo-1594623121614-290b3991bf1f?w=600&auto=format&fit=crop",
                apiUrl = "https://uzic.ch/api/now_playing.php"
            ),
            RadioStation(
                name = "DEEP TECHNO",
                url = "https://strm112.1.fm/deeptech_mobile_mp3",
                backgroundColor = Color.Black,
                logoUrl = "https://images.unsplash.com/photo-1571266028243-e4733b0f0bb1?w=600&auto=format&fit=crop",
                apiUrl = "https://www.1.fm/api/station/nowplaying?station=deeptech"
            ),
            RadioStation(
                name = "DEEP HOUSE",
                url = "https://strm112.1.fm/deephouse_mobile_mp3",
                backgroundColor = Color.Black,
                logoUrl = "https://images.unsplash.com/photo-1514525253361-bee8a48790c7?w=600&auto=format&fit=crop",
                apiUrl = "https://www.1.fm/api/station/nowplaying?station=deephouse"
            ),
            RadioStation(
                name = "ADAGIO RADIO",
                url = "https://stream.tunerplay.com/radio/8010/adagioradio.mp3",
                backgroundColor = Color.Black,
                logoUrl = "https://www.tunerplay.live/static/uploads/adagioradio/logo.png",
                apiUrl = "https://stream.tunerplay.com/api/nowplaying/adagioradio",
                shortcode = "adagioradio"
            ),
            RadioStation(
                name = "FUTURO",
                url = "https://playerservices.streamtheworld.com/api/livestream-redirect/FUTURO.mp3",
                backgroundColor = Color.Black,
                logoUrl = "https://www.futuro.cl/wp-content/uploads/2021/07/android-chrome-512x512-1.png",
                apiUrl = "https://radio-api.prisamedia.cl/v1/stations/futuro/nowplaying"
            ),
            RadioStation(
                name = "SOMAFM",
                url = "https://ice1.somafm.com/cliqhop-128-mp3",
                backgroundColor = Color.Black,
                logoUrl = "https://somafm.com/img/cliqhop400.png",
                apiUrl = "https://somafm.com/songs/cliqhop.json"
            ),
            RadioStation(
                name = "90s90s GRUNGE",
                url = "https://regiocast.streamabc.net/regc-90s90sgrunge7540920-mp3-192-4353468?sABC=671qo6n6%230%232r00710506879112po080811921p6nor%23gharva&aw_0_1st.playerid=tunein&amsparams=playerid:tunein;skey:1730000550",
                backgroundColor = Color.Black,
                logoUrl = "https://www.90s90s.de/sites/default/files/styles/station_logo/public/images/90s90s_grunge_logo.png",
                apiUrl = "https://api.90s90s.de/nowplaying/grunge"
            ),
            RadioStation(
                name = "BOB! GRUNGE",
                url = "https://regiocast.streamabc.net/regc-radiobobgrunge4112801-mp3-192-5387631?sABC=671s29qn%230%237pp438696ps9ps0nqo6038qo0q730s5o%23gharva&aw_0_1st.playerid=tunein&amsparams=playerid:tunein;skey:1730095578",
                backgroundColor = Color.Black,
                logoUrl = "https://www.radiobob.de/m/rc/branding/8b8a961d-5bd9-4246-b3f8-ce96d4d98fef/bob_grunge_1024x768.png",
                apiUrl = "https://api.radiobob.de/api/nowplaying/bob-grunge"
            ),
            RadioStation(
                name = "DELTA GRUNGE",
                url = "https://deltaradio.streamabc.net/regc-deltagrunge-mp3-192-7205779?sABC=671s26oo%230%237pp438696ps9ps0nqo6038qo0q730s5o%23gharva&aw_0_1st.playerid=tunein&amsparams=playerid:tunein;skey:1730094779",
                backgroundColor = Color.Black,
                logoUrl = "https://www.deltaradio.de/sites/default/files/styles/logo/public/delta-radio-grunge.png",
                apiUrl = "https://api.radioplay.de/metadata/v1/nowplaying/delta-grunge"
            ),
            RadioStation(
                name = "SONAR FM",
                url = "https://mdstrm.com/audio/5c915724519bce27671c4d15/icecast.audio",
                backgroundColor = Color.Black,
                logoUrl = "https://myradioonline.cl/public/uploads/radio_img/sonar-fm/play_250_250.webp",
                apiUrl = "https://api.rdfmedia.cl/nowplaying/sonar"
            ),
            RadioStation(
                name = "90s90s ROCK",
                url = "https://regiocast.streamabc.net/regc-90s90srock1436287-mp3-192-2191420?sABC=671rr92q%231%23730168p5ron6405p8q8817q3rrs5o615%23ubzrcntr&mode=preroll&aw_0_1st.skey=1730078977&cb=863839065&listenerid=730168c5eba6405c8d8817d3eef5b615&aw_0_1st.playerid=homepage&amsparams=playerid:homepage;skey:1730079021",
                backgroundColor = Color.Black,
                logoUrl = "https://www.90s90s.de/sites/default/files/styles/station_logo/public/images/90s90s_rock_logo.png",
                apiUrl = "https://api.90s90s.de/nowplaying/rock"
            ),
            RadioStation(
                name = "LABGATE ALT",
                url = "https://s2.ssl-stream.com/listen/labgate_alt_rock_grunge/radio.mp3",
                backgroundColor = Color.Black,
                logoUrl = "https://labgateradio.com/wp-content/uploads/2021/04/Labgate-Radio-Alternative-Rock-and-Grunge.png",
                apiUrl = "https://s2.ssl-stream.com/api/nowplaying/labgate_alt_rock_grunge"
            ),
            RadioStation(
                name = "LA ROCKA 80",
                url = "https://audiopanel.com.ar:8000/radio.aac",
                backgroundColor = Color.Black,
                logoUrl = "https://static.mytuner.mobi/media/tvos_radios/807/la-rocka-80.59df80ba.png",
                apiUrl = "http://audiopanel.com.ar:8000/status-json.xsl"
            ),
            RadioStation(
                name = "CHRONIX AGRESSION",
                url = "http://usa19.fastcast4u.com:5720/",
                backgroundColor = Color.Black,
                logoUrl = "https://chronixradio.com/img/aggression.png"
            ),
            RadioStation(
                name = "CHRONIX GRIT",
                url = "https://usa19.fastcast4u.com:5950/;?type=http&nocache=1720495255",
                backgroundColor = Color.Black,
                logoUrl = "https://chronixradio.com/img/grit.png"
            ),
            RadioStation(
                name = "CHRONIX METAL",
                url = "https://usa19.fastcast4u.com:4730/;?type=http&nocache=1715759286",
                backgroundColor = Color.Black,
                logoUrl = "https://chronixradio.com/img/metalcore.png"
            ),
            RadioStation(
                name = "REAL PUNK RADIO",
                url = "https://stream.rcast.net/63875",
                backgroundColor = Color.Black,
                logoUrl = "https://realpunkradio.com/wp-content/uploads/2019/11/RPR_Logo_Header.png",
                apiUrl = "http://s2.nexuscast.com:8080/stats?sid=1&json=1"
            ),
            RadioStation(
                name = "INDUSTRIAL",
                url = "https://terahertzwellen.stream.laut.fm/terahertzwellen?ref=radiode&t302=2024-10-28_01-55-33&uuid=82d132d8-23c0-48c3-8fd3-9fd73315bbf1",
                backgroundColor = Color.Black,
                logoUrl = "https://api.laut.fm/station/terahertzwellen/images/station_640x640",
                apiUrl = "https://api.laut.fm/station/terahertzwellen/current_song"
            ),
            RadioStation(
                name = "DARKSTAR GOTHIC",
                url = "https://radio-darkstar.stream.laut.fm/radio-darkstar?ref=radiode&t302=2024-10-28_02-06-42&uuid=df0d8a20-a03d-48e3-a0fe-3918096d4bfb",
                backgroundColor = Color.Black,
                logoUrl = "https://api.laut.fm/station/radio-darkstar/images/station_640x640",
                apiUrl = "https://api.laut.fm/station/radio-darkstar/current_song"
            ),
            RadioStation(
                name = "UNDERGROUND.FM",
                url = "https://eu7.fastcast4u.com/proxy/underground1?mp=/stream",
                backgroundColor = Color.Black,
                logoUrl = "https://underground.fm/wp-content/uploads/2021/01/undgrnd-logo-clear2.png"
            )
        )
    }
    
    val savedIndex = remember { prefs.getInt("last_station_index", 0) }
    var isAluminumMode by remember { mutableStateOf(prefs.getBoolean("is_aluminum", false)) }
    var isOscillatorMode by remember { mutableStateOf(prefs.getBoolean("is_oscillator", false)) }

    val metadata by radioViewModel.metadata.collectAsState()
    val initialPage = (Int.MAX_VALUE / 2 / radioStations.size) * radioStations.size + savedIndex
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { Int.MAX_VALUE })
    
    var isPlayingState by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var isLockPressed by remember { mutableStateOf(false) }
    var isPausePressed by remember { mutableStateOf(false) }
    var isShowInfo by remember { mutableStateOf(false) } 
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // ESTADOS PARA ACTUALIZACIÓN AUTOMÁTICA (OTA)
    val updateManager = remember { UpdateManager(context) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        delay(2000)
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
            
            Log.d("RadioApp", "Checking update: Current Version Code = $currentVersion")
            updateInfo = updateManager.checkForUpdates(currentVersion)
            Log.d("RadioApp", "Update Info received: $updateInfo")
        } catch (e: Exception) {
            Log.e("RadioApp", "Update check failed: ${e.message}")
        }
    }

    // SINCRONIZACIÓN DE AUDIO Y VISUALES (Compensación de Latencia de 140ms)
    var delayedEnergyL by remember { mutableFloatStateOf(0f) }
    var delayedEnergyR by remember { mutableFloatStateOf(0f) }
    
    val currentBpm by PlaybackService.currentBpm.collectAsState()
    val isMagnetActive by PlaybackService.isMagnetActive.collectAsState()
    val isCalibrated by PlaybackService.isCalibrated.collectAsState()
    val calibrationCountdown by PlaybackService.calibrationCountdown.collectAsState()

    // Sistema de Buffer para sincronizar visuales con el audio real de los parlantes (ROBUST SYNC)
    LaunchedEffect(Unit) {
        val historyL = mutableListOf<Pair<Long, Float>>()
        val historyR = mutableListOf<Pair<Long, Float>>()
        while (true) {
            val now = System.currentTimeMillis()
            historyL.add(now to PlaybackService.currentEnergyL.value)
            historyR.add(now to PlaybackService.currentEnergyR.value)
            
            // Compensación de latencia hardware (140ms)
            while (historyL.isNotEmpty() && now - historyL.first().first > 140) {
                delayedEnergyL = historyL.removeAt(0).second
            }
            while (historyR.isNotEmpty() && now - historyR.first().first > 140) {
                delayedEnergyR = historyR.removeAt(0).second
            }
            delay(16) // ~60fps sync
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val actualIndex = ((pagerState.currentPage % radioStations.size) + radioStations.size) % radioStations.size
        prefs.edit().putInt("last_station_index", actualIndex).apply()
        vibratePhone(context, 30) 
    }

    LaunchedEffect(isAluminumMode) { prefs.edit().putBoolean("is_aluminum", isAluminumMode).apply() }
    LaunchedEffect(isOscillatorMode) { 
        GlobalSettings.isOscillatorMode = isOscillatorMode
        prefs.edit().putBoolean("is_oscillator", isOscillatorMode).apply() 
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { 
                isPlayingState = isPlaying 
            }
            override fun onPlayerError(error: PlaybackException) { Log.e("RadioApp", "Player Error: ${error.message}") }
        }
        player?.addListener(listener)
        isPlayingState = player?.isPlaying ?: false
        onDispose { player?.removeListener(listener) }
    }

    LaunchedEffect(pagerState.currentPage, player) {
        val currentPlayer = player ?: return@LaunchedEffect
        val actualIndex = ((pagerState.currentPage % radioStations.size) + radioStations.size) % radioStations.size
        val station = radioStations[actualIndex]
        radioViewModel.startPolling(station.apiUrl, station.shortcode, station.name)
        val currentUri = currentPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri == station.url && currentPlayer.playbackState != Player.STATE_IDLE) return@LaunchedEffect
        currentPlayer.stop()
        currentPlayer.clearMediaItems()
        if (station.url.isNotEmpty()) {
            val mediaItem = MediaItem.Builder().setUri(station.url).setMediaMetadata(MediaMetadata.Builder().setTitle(station.name).setArtist(station.name).build()).build()
            currentPlayer.setMediaItem(mediaItem)
            currentPlayer.prepare()
            // Direct start, no arbitrary delay
            currentPlayer.play()
        }
    }

    LaunchedEffect(metadata, player) {
        val currentPlayer = player ?: return@LaunchedEffect
        if (metadata.title.isNotEmpty() && metadata.title != "Cargando..." && metadata.title != "En vivo") {
            try {
                val currentItem = currentPlayer.currentMediaItem
                if (currentItem != null) {
                    val displayTitle = metadata.title + "          " 
                    val displayArtist = metadata.artist + "          "
                    
                    val newMetadata = MediaMetadata.Builder()
                        .setTitle(displayTitle)
                        .setArtist(displayArtist)
                        .setDisplayTitle(displayTitle)
                        .setArtworkUri(metadata.artworkUrl?.toUri())
                        .build()
                    
                    currentPlayer.setPlaylistMetadata(newMetadata)
                    val newItem = currentItem.buildUpon().setMediaMetadata(newMetadata).build()
                    currentPlayer.replaceMediaItem(currentPlayer.currentMediaItemIndex, newItem)
                }
            } catch (e: Exception) {
                Log.e("RadioApp", "Error al actualizar metadata: ${e.message}")
            }
        }
    }

    var audioQuality by remember { mutableStateOf("Detectando calidad...") }

    LaunchedEffect(pagerState.currentPage, isPlayingState) {
        val actualIndex = ((pagerState.currentPage % radioStations.size) + radioStations.size) % radioStations.size
        val station = radioStations[actualIndex]
        val bitrate = when(station.name) {
            "ADAGIO RADIO" -> "256kbps"
            "FUTURO" -> "192kbps"
            "LIMBIK FRECUENCIES" -> "128kbps"
            "LA ROCKA 80" -> "64kbps"
            else -> "160kbps"
        }
        val format = if (station.url.contains("aac")) "AAC+" else "MP3"
        audioQuality = "AUDIO: $bitrate $format STEREO • HI-FI ENGINE V78 • BUFFER: 15s • SAMPLING: 44.1kHz • "
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                lastInteractionTime = System.currentTimeMillis()
            }
        }
    }) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { it }, userScrollEnabled = !isLocked) { page ->
            val actualIndex = ((page % radioStations.size) + radioStations.size) % radioStations.size
            val station = radioStations[actualIndex]
            RadioScreen(
                station = station, 
                title = if (pagerState.currentPage == page) metadata.title else "Cargando...", 
                artist = if (pagerState.currentPage == page) metadata.artist else station.name, 
                artworkUrl = if (pagerState.currentPage == page) (metadata.artworkUrl ?: station.logoUrl) else station.logoUrl, 
                isActive = pagerState.currentPage == page, 
                isPlaying = isPlayingState, 
                isCountdownActive = false, 
                onPauseRequest = { player?.pause() }, 
                countdownProgress = 0f, 
                bpm = currentBpm, 
                realEnergyL = if (isPlayingState) delayedEnergyL else 0f, 
                realEnergyR = if (isPlayingState) delayedEnergyR else 0f, 
                isMagnetActive = isMagnetActive, 
                isCalibrated = isCalibrated, 
                calibrationCountdown = calibrationCountdown, 
                player = player, 
                onScratchStart = { }, 
                onScratchEnd = { if (!it) { player?.play() } }, 
                onBrakeStart = { player?.pause() },
                isAluminum = isAluminumMode,
                onToggleAluminum = { isAluminumMode = !isAluminumMode },
                onToggleOscillator = { isOscillatorMode = !isOscillatorMode },
                audioQuality = audioQuality
            )
        }

        // LEFT LOCK BUTTON
        Box(modifier = Modifier
            .padding(bottom = 48.dp, start = 24.dp)
            .align(Alignment.BottomStart)
            .size(64.dp)
            .scale(if (isLockPressed) 1.25f else 1f)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isLockPressed = true
                        vibratePhone(context, 50)
                        try { awaitRelease() } finally { isLockPressed = false }
                    },
                    onTap = { isLocked = !isLocked }
                )
            }, 
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(28.dp)) {
                val w = size.width
                val h = size.height
                val lockColor = if (isLocked) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.3f)
                
                drawRoundRect(
                    color = lockColor,
                    topLeft = Offset(0f, h * 0.4f),
                    size = androidx.compose.ui.geometry.Size(w, h * 0.6f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )
                
                if (isLocked) {
                    drawArc(
                        color = lockColor, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                        topLeft = Offset(w * 0.2f, h * 0.1f), size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.6f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                } else {
                    drawArc(
                        color = lockColor, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                        topLeft = Offset(w * 0.2f, -h * 0.15f), size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.6f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
            }
        }

        // RIGHT PAUSE/PLAY BUTTON
        Box(modifier = Modifier
            .padding(bottom = 48.dp, end = 24.dp)
            .align(Alignment.BottomEnd)
            .size(64.dp)
            .scale(if (isPausePressed) 1.25f else 1f)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPausePressed = true
                        vibratePhone(context, 50)
                        try { awaitRelease() } finally { isPausePressed = false }
                    },
                    onTap = {
                        if (isPlayingState) player?.pause() else player?.play()
                    }
                )
            }, 
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width
                val h = size.height
                val iconColor = if (!isPlayingState) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.3f)
                
                if (isPlayingState) {
                    val barW = w * 0.3f
                    drawRect(iconColor, Offset(0f, 0f), androidx.compose.ui.geometry.Size(barW, h))
                    drawRect(iconColor, Offset(w - barW, 0f), androidx.compose.ui.geometry.Size(barW, h))
                } else {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, 0f)
                        lineTo(w, h / 2f)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(path, iconColor)
                }
            }
        }

        // CENTER SIGNATURE: * IAIO *
        Row(
            modifier = Modifier
                .padding(bottom = 68.dp)
                .align(Alignment.BottomCenter)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitFirstDown()
                            isShowInfo = true
                            vibratePhone(context, 20)
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.any { !it.pressed }) {
                                    isShowInfo = false
                                    break
                                }
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val pulse = if (currentBpm > 0) 60000 / currentBpm else 800
            val anim = rememberInfiniteTransition(label = "iaioLiveAnim")
            val iaioLiveAlpha by anim.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(pulse / 2), RepeatMode.Reverse), label = "alpha"
            )
            val signatureColor = if (isMagnetActive) Color.Cyan else Color.White.copy(alpha = iaioLiveAlpha)

            Text(text = "*", color = signatureColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
            
            Text(
                text = if (isShowInfo) "IAIO RADIO 2026 , VERSION 2.0, bdozuniga@gmail.com..... " else "IAIO",
                color = if (isMagnetActive) Color.Cyan else Color.White,
                fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1,
                modifier = Modifier.alpha(0.8f).widthIn(max = 200.dp).basicMarquee(
                    iterations = Int.MAX_VALUE, velocity = if (isShowInfo) 80.dp else 0.dp, spacing = MarqueeSpacing(48.dp)
                )
            )

            Text(text = "*", color = signatureColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }

        // OTA UPDATE OVERLAY
        updateInfo?.let { info ->
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.padding(32.dp).background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp)).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "¡MAMBO NUEVO! 🚀", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(text = "IAIO ha lanzado la Versión ${info.versionName}", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center)
                    if (info.releaseNotes.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(text = info.releaseNotes, color = Color.Cyan.copy(alpha = 0.8f), fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(24.dp))
                    if (isDownloadingUpdate) {
                        CircularProgressIndicator(progress = { downloadProgress }, color = Color.Cyan, strokeWidth = 4.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(text = "${(downloadProgress * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(text = "LUEGO", color = Color.White.copy(alpha = 0.4f), modifier = Modifier.pointerInput(Unit) { detectTapGestures { updateInfo = null } }.padding(8.dp), fontWeight = FontWeight.Bold)
                            Text(text = "ACTUALIZAR", color = Color.Cyan, modifier = Modifier.background(Color.Cyan.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).border(1.dp, Color.Cyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).pointerInput(Unit) {
                                detectTapGestures {
                                    isDownloadingUpdate = true
                                    scope.launch { updateManager.downloadAndInstallApk(info) { downloadProgress = it } }
                                }
                            }.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DefaultVinyl(referentialUrl: String?, isAluminum: Boolean) {
    val bgColor = if (isAluminum) Color(0xFFCCCCCC) else Color.Black
    val brushColor = if (isAluminum) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f)
    val grooveColor = if (isAluminum) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.05f)
    val labelColor = if (isAluminum) Color(0xFFE0E0E0) else Color(0xFF1A1A1A)

    Box(modifier = Modifier.size(330.dp).clip(CircleShape).background(bgColor), contentAlignment = Alignment.Center) {
        if (referentialUrl != null) {
            AsyncImage(model = referentialUrl, contentDescription = null, modifier = Modifier.fillMaxSize().alpha(0.5f), contentScale = ContentScale.Crop)
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            drawCircle(brush = Brush.sweepGradient(0.0f to brushColor, 0.2f to Color.Transparent, 0.5f to brushColor, 0.7f to Color.Transparent, 1.0f to brushColor), radius = radius)
            for (i in 1..25) {
                drawCircle(color = grooveColor, radius = radius * (0.35f + (i / 25f) * 0.65f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5.dp.toPx()))
            }
            drawCircle(color = labelColor, radius = radius * 0.35f)
            drawCircle(color = brushColor, radius = radius * 0.35f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun RadioScreen(station: RadioStation, title: String, artist: String, artworkUrl: String?, isActive: Boolean, isPlaying: Boolean, isCountdownActive: Boolean, onPauseRequest: () -> Unit, countdownProgress: Float, bpm: Int, realEnergyL: Float, realEnergyR: Float, isMagnetActive: Boolean, isCalibrated: Boolean, calibrationCountdown: Int, player: Player?, onScratchStart: () -> Unit, onScratchEnd: (Boolean) -> Unit, onBrakeStart: () -> Unit, isAluminum: Boolean, onToggleAluminum: () -> Unit, onToggleOscillator: () -> Unit, audioQuality: String) {
    val context = LocalContext.current
    var currentRotation by remember { mutableStateOf(0f) }
    var isTouching by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying, isTouching) {
        if (isPlaying || isTouching) {
            var lastFrameTimeNanos = 0L
            while (true) {
                withFrameNanos { time ->
                    if (lastFrameTimeNanos == 0L) lastFrameTimeNanos = time
                    else {
                        val delta = (time - lastFrameTimeNanos) / 1_000_000_000f
                        lastFrameTimeNanos = time
                        if (!isTouching) {
                            val speed = if (isPlaying) player?.playbackParameters?.speed ?: 1.0f else 0.0f
                            currentRotation = (currentRotation + 120f * delta * speed) % 360f
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 54.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(modifier = Modifier.fillMaxWidth().pointerInput(player) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        if (player?.isPlaying == false) {
                            val up = waitForUpOrCancellation()
                            if (up != null) player.play()
                        } else {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    onPauseRequest()
                                    while (true) { if (awaitPointerEvent().changes.none { it.pressed }) break }
                                    break
                                }
                                if (event.changes.none { it.pressed }) break
                            }
                        }
                    }
                }
            }, horizontalAlignment = Alignment.Start) {
                Text(text = "RADIO : ${station.name}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.alpha(0.9f).fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, spacing = MarqueeSpacing(48.dp)))
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val pulse = if (bpm > 0) 60000 / bpm else 800
                    val anim = rememberInfiniteTransition()
                    val liveAlpha by anim.animateFloat(0.3f, 1f, infiniteRepeatable(tween(pulse / 2), RepeatMode.Reverse))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (isMagnetActive) Color.Cyan else Color.Red.copy(alpha = liveAlpha)))
                    Text(text = if (!isCalibrated) "ANALYZING (${calibrationCountdown}s)" else "LIVE", color = if (isMagnetActive) Color.Cyan else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(0.8f))
                    Spacer(Modifier.width(8.dp))
                    Text(text = audioQuality, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE).alpha(0.9f))
                }
                Spacer(Modifier.height(16.dp))
                Text(text = "CANCION : ${title.ifEmpty { "En vivo" }}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.alpha(0.9f).fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, velocity = 40.dp))
                if (artist.isNotEmpty()) {
                    Text(text = "ARTISTA : $artist", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.alpha(0.9f).fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, velocity = 35.dp))
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
            Box(modifier = Modifier.pointerInput(Unit) { detectTapGestures(onDoubleTap = { onToggleOscillator() }) }) {
                if (isActive) SpectrumVisualizer(isPlaying, realEnergyL, realEnergyR)
            }
            Spacer(modifier = Modifier.height(36.dp))
            val beatDuration = if (bpm > 0) 60000 / bpm else 500
            val infiniteBeat = rememberInfiniteTransition(label = "heartBeat")
            val beatPulse by infiniteBeat.animateFloat(initialValue = 1f, targetValue = 1.6f, animationSpec = infiniteRepeatable(tween(beatDuration / 2, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
            val energyFactor = ((realEnergyL + realEnergyR) / 2f).coerceIn(0.5f, 1.2f)
            val finalScale = beatPulse * energyFactor
            Box(modifier = Modifier.size(360.dp).pointerInput(player) {
                detectTapGestures(onDoubleTap = { onToggleAluminum() }, onPress = { 
                    isTouching = true
                    var initialAngle = Math.toDegrees(atan2((it.y - 180.dp.toPx()).toDouble(), (it.x - 180.dp.toPx()).toDouble())).toFloat()
                    var isDragging = false
                    PlaybackService.stutterProcessor.isScratching = true
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                vibratePhone(context, 5)
                                if (event.changes.count { it.pressed } >= 2) { isTouching = false; isDragging = false; PlaybackService.stutterProcessor.isScratching = false; onBrakeStart(); break }
                                val pointer = event.changes.firstOrNull { it.pressed }
                                if (pointer == null) { isTouching = false; PlaybackService.stutterProcessor.isScratching = false; onScratchEnd(!isPlaying); break }
                                val currentAngle = Math.toDegrees(atan2((pointer.position.y - 180.dp.toPx()).toDouble(), (pointer.position.x - 180.dp.toPx()).toDouble())).toFloat()
                                var delta = currentAngle - initialAngle
                                if (delta > 180) delta -= 360 else if (delta < -180) delta += 360
                                if (Math.abs(delta) > 0.5f || isDragging) {
                                    if (!isDragging) { isDragging = true; onScratchStart() }
                                    currentRotation = (currentRotation + delta) % 360f
                                    PlaybackService.stutterProcessor.scratchSpeed = (delta / (120f * 0.016f)).coerceIn(-4f, 4f)
                                    initialAngle = currentAngle
                                }
                            }
                        }
                    } finally { isTouching = false; PlaybackService.stutterProcessor.isScratching = false }
                })
            }, contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().rotate(currentRotation), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFF2B2B2B), size.minDimension / 2) }
                    Box(Modifier.size(340.dp).clip(CircleShape).background(Color.Black))
                    Box(Modifier.size(330.dp).clip(CircleShape).background(if (isAluminum) Color(0xFFCCCCCC) else Color.DarkGray), contentAlignment = Alignment.Center) {
                        SubcomposeAsyncImage(model = artworkUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, error = { DefaultVinyl(station.logoUrl, isAluminum) }, loading = { DefaultVinyl(station.logoUrl, isAluminum) })
                    }
                    Canvas(Modifier.size(328.dp)) {
                        drawArc(color = Color(0xFF00FF41).copy(alpha = 0.4f), startAngle = -5f, sweepAngle = 40f, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        drawArc(color = Color(0xFF00FF41), startAngle = 0f, sweepAngle = 30f, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                }
                Box(modifier = Modifier.size(10.dp).scale(if (isPlaying) finalScale else 1f).clip(CircleShape).background(if (isPlaying) Color.Red else Color.Gray.copy(alpha = 0.5f)).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape))
            }
            Spacer(Modifier.height(56.dp))
        }
    }
}
