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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.media3.common.MimeTypes
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


@UnstableApi
class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<Player?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Radio_verticalTheme {
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
    
    val radioStations = RadioData.stations
    
    val savedIndex = remember { prefs.getInt("last_station_index", 0) }
    var visMode by remember { mutableIntStateOf(prefs.getInt("last_vis_mode", 0)) }
    var isAluminumMode by remember { mutableStateOf(prefs.getBoolean("is_aluminum", false)) }
    var isOscillatorMode by remember { mutableStateOf(prefs.getBoolean("is_oscillator", false)) }

    val metadata by radioViewModel.metadata.collectAsState()
    val initialPage = (Int.MAX_VALUE / 2 / radioStations.size) * radioStations.size + savedIndex
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { Int.MAX_VALUE })
    
    var isPlayingState by remember { mutableStateOf(true) }
    var countdownProgress by remember { mutableFloatStateOf(0f) }
    var isCountdownActive by remember { mutableStateOf(false) }
    var isStartingActive by remember { mutableStateOf(false) } 
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
            updateInfo = updateManager.checkForUpdates(currentVersion)
        } catch (e: Exception) {
            Log.e("RadioApp", "Update check failed: ${e.message}")
        }
    }

    // SINCRONIZACIÓN DE NAVEGACIÓN BLUETOOTH (AVRCP NEXT/PREVIOUS)
    LaunchedEffect(player) {
        PlaybackService.currentStationIndexFlow.collect { index ->
            val total = radioStations.size
            if (total > 0) {
                // Sincronizar el Pager con el índice del servicio
                val currentPage = pagerState.currentPage
                val currentActualIndex = ((currentPage % total) + total) % total
                if (currentActualIndex != index) {
                    val diff = index - currentActualIndex
                    // Ajustamos para que el scroll sea hacia el lado más corto o natural
                    pagerState.animateScrollToPage(currentPage + diff)
                }
            }
        }
    }

    // SINCRONIZACIÓN DE AUDIO Y VISUALES (Compensación de Latencia de 140ms)
    var delayedEnergyL by remember { mutableFloatStateOf(0f) }
    var delayedEnergyR by remember { mutableFloatStateOf(0f) }
    
    val currentBpm by PlaybackService.currentBpm.collectAsState()
    val isMagnetActive by PlaybackService.isMagnetActive.collectAsState()
    val isCalibrated by PlaybackService.isCalibrated.collectAsState()
    val calibrationCountdown by PlaybackService.calibrationCountdown.collectAsState()

    // Sistema de Buffer para sincronizar visuales (SINCRO REAL: 2ms)
    LaunchedEffect(Unit) {
        val historyL = mutableListOf<Pair<Long, Float>>()
        val historyR = mutableListOf<Pair<Long, Float>>()
        while (true) {
            val now = System.currentTimeMillis()
            historyL.add(now to PlaybackService.currentEnergyL.value)
            historyR.add(now to PlaybackService.currentEnergyR.value)
            
            // Bajamos a 2ms: Latencia prácticamente nula, respuesta eléctrica pura
            while (historyL.isNotEmpty() && now - historyL.first().first > 2) {
                delayedEnergyL = historyL.removeAt(0).second
            }
            while (historyR.isNotEmpty() && now - historyR.first().first > 2) {
                delayedEnergyR = historyR.removeAt(0).second
            }
            delay(1) // Muestreo máximo
        }
    }

    LaunchedEffect(visMode) {
        prefs.edit().putInt("last_vis_mode", visMode).apply()
    }

    LaunchedEffect(pagerState.currentPage) {
        val actualIndex = ((pagerState.currentPage % radioStations.size) + radioStations.size) % radioStations.size
        prefs.edit().putInt("last_station_index", actualIndex).apply()
        vibratePhone(context, 30) 
    }

    LaunchedEffect(isAluminumMode) { prefs.edit().putBoolean("is_aluminum", isAluminumMode).apply() }
    // GlobalSettings ya no es necesario aquí ponrque visMode se maneja localmente

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { 
                isPlayingState = isPlaying 
                if (!isPlaying) isStartingActive = false
                Log.d("RadioApp", "Playback state: $isPlaying")
            }
            override fun onPlayerError(error: PlaybackException) { 
                Log.e("RadioApp", "Player Error: ${error.message}", error)
            }
            override fun onPlaybackStateChanged(state: Int) {
                val stateName = when(state) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.d("RadioApp", "ExoPlayer State: $stateName")
            }
        }
        player?.addListener(listener)
        isPlayingState = player?.isPlaying ?: false
        onDispose { player?.removeListener(listener) }
    }

    LaunchedEffect(isCountdownActive) {
        if (isCountdownActive) {
            val totalSteps = 300
            for (i in totalSteps downTo 1) {
                if (!isCountdownActive) break
                val speed = (i.toFloat() / totalSteps).coerceIn(0.1f, 1.0f)
                player?.playbackParameters = PlaybackParameters(speed)
                countdownProgress = speed
                delay(10)
            }
            if (isCountdownActive) {
                player?.pause()
                player?.playbackParameters = PlaybackParameters(1.0f)
                isCountdownActive = false
            }
        } else {
            if (!PlaybackService.stutterProcessor.isScratching && !isStartingActive) {
                player?.playbackParameters = PlaybackParameters(1.0f)
            }
        }
    }

    LaunchedEffect(isStartingActive) {
        if (isStartingActive) {
            isCountdownActive = false
            player?.playbackParameters = PlaybackParameters(0.1f)
            player?.play()
            val totalSteps = 150
            for (i in 1..totalSteps) {
                if (!isStartingActive) break
                val speed = (0.1f + (i.toFloat() / totalSteps) * 0.9f).coerceIn(0.1f, 1.0f)
                player?.playbackParameters = PlaybackParameters(speed)
                delay(8)
            }
            player?.playbackParameters = PlaybackParameters(1.0f)
            isStartingActive = false
        }
    }

    LaunchedEffect(pagerState.currentPage, player) {
        val currentPlayer = player ?: return@LaunchedEffect
        val actualIndex = ((pagerState.currentPage % radioStations.size) + radioStations.size) % radioStations.size
        val station = radioStations[actualIndex]
        
        // Sincronizar el índice del servicio cuando el usuario hace scroll manual
        PlaybackService.updateInternalIndex(actualIndex)
        
        Log.e("RadioApp", "VER: 6.5-LOCAL - Selected: ${station.name} - API: ${station.apiUrl}")
        radioViewModel.startPolling(station.apiUrl, station.shortcode, station.name)
        val currentUri = currentPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri == station.url && currentPlayer.playbackState != Player.STATE_IDLE) return@LaunchedEffect
        currentPlayer.stop()
        currentPlayer.clearMediaItems()
        if (station.url.isNotEmpty()) {
            val mimeType = if (station.url.contains("m3u8")) MimeTypes.APPLICATION_M3U8 else null
            val mediaItem = MediaItem.Builder()
                .setUri(station.url)
                .setMimeType(mimeType)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(station.name)
                        .setArtist(station.name)
                        .build()
                ).build()
            currentPlayer.setMediaItem(mediaItem)
            currentPlayer.prepare()
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
                        .setTitle(displayTitle).setArtist(displayArtist).setDisplayTitle(displayTitle)
                        .setArtworkUri(metadata.artworkUrl?.toUri()).build()
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
                title = if (pagerState.currentPage == page) metadata.title else "Cargando v6.4...", 
                artist = if (pagerState.currentPage == page) metadata.artist else station.name, 
                artworkUrl = if (pagerState.currentPage == page) (metadata.artworkUrl ?: station.logoUrl) else station.logoUrl, 
                isActive = pagerState.currentPage == page, 
                isPlaying = isPlayingState, 
                isCountdownActive = isCountdownActive, 
                onPauseRequest = { isCountdownActive = true }, 
                bpm = currentBpm, 
                realEnergyL = if (isPlayingState || isCountdownActive) delayedEnergyL * (player?.playbackParameters?.speed ?: 1f) else 0f, 
                realEnergyR = if (isPlayingState || isCountdownActive) delayedEnergyR * (player?.playbackParameters?.speed ?: 1f) else 0f, 
                isMagnetActive = isMagnetActive, 
                isCalibrated = isCalibrated, 
                calibrationCountdown = calibrationCountdown, 
                player = player, 
                onScratchStart = { isCountdownActive = false }, 
                onScratchEnd = { if (!it) { player?.play(); player?.playbackParameters = PlaybackParameters(1.0f) } }, 
                isAluminum = isAluminumMode,
                onToggleAluminum = { isAluminumMode = !isAluminumMode },
                onToggleOscillator = { isOscillatorMode = !isOscillatorMode },
                visMode = visMode,
                onModeChange = { visMode = it },
                audioQuality = audioQuality
            )
        }

        // LEFT LOCK BUTTON
        Box(modifier = Modifier.padding(bottom = 48.dp, start = 24.dp).align(Alignment.BottomStart).size(64.dp).scale(if (isLockPressed) 1.25f else 1f).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape).pointerInput(Unit) {
            detectTapGestures(onPress = { isLockPressed = true; vibratePhone(context, 50); try { awaitRelease() } finally { isLockPressed = false } }, onTap = { isLocked = !isLocked })
        }, contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(28.dp)) {
                val w = size.width; val h = size.height
                val lockColor = if (isLocked) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.3f)
                drawRoundRect(color = lockColor, topLeft = Offset(0f, h * 0.4f), size = Size(w, h * 0.6f), cornerRadius = CornerRadius(4.dp.toPx()))
                if (isLocked) drawArc(lockColor, 180f, 180f, false, Offset(w * 0.2f, h * 0.1f), Size(w * 0.6f, h * 0.6f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                else drawArc(lockColor, 180f, 180f, false, Offset(w * 0.2f, -h * 0.15f), Size(w * 0.6f, h * 0.6f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        // RIGHT PAUSE/PLAY BUTTON
        Box(modifier = Modifier.padding(bottom = 48.dp, end = 24.dp).align(Alignment.BottomEnd).size(64.dp).scale(if (isPausePressed) 1.25f else 1f).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape).pointerInput(Unit) {
            detectTapGestures(onPress = { isPausePressed = true; vibratePhone(context, 50); try { awaitRelease() } finally { isPausePressed = false } }, onTap = { if (isPlayingState) isCountdownActive = true else isStartingActive = true })
        }, contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                val iconColor = if (!isPlayingState) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.3f)
                if (isPlayingState) { val barW = w * 0.3f; drawRect(iconColor, Offset(0f, 0f), Size(barW, h)); drawRect(iconColor, Offset(w - barW, 0f), Size(barW, h)) }
                else { val path = Path().apply { moveTo(0f, 0f); lineTo(w, h / 2f); lineTo(0f, h); close() }; drawPath(path, iconColor) }
            }
        }

        // CENTER SIGNATURE: * IAIO *
        Row(modifier = Modifier.padding(bottom = 68.dp).align(Alignment.BottomCenter).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) { awaitFirstDown(); isShowInfo = true; vibratePhone(context, 20); while (true) { val event = awaitPointerEvent(); if (event.changes.any { !it.pressed }) { isShowInfo = false; break } } }
            }
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val pulse = if (currentBpm > 0) 60000 / currentBpm else 800
            val anim = rememberInfiniteTransition(label = "iaioLiveAnim")
            val iaioLiveAlpha by anim.animateFloat(0.3f, 1f, infiniteRepeatable(tween(pulse / 2), RepeatMode.Reverse), label = "alpha")
            val signatureColor = if (isMagnetActive) Color.Cyan else Color.White.copy(alpha = iaioLiveAlpha)
            Text(text = "*", color = signatureColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(text = if (isShowInfo) "IAIO RADIO v6.3 (vCode 73) • MEJORAS: Fix Play FM (HLS Support) • Control Bluetooth AVRCP (Next/Prev) • bdozuniga@gmail.com..... " else "IAIO", color = if (isMagnetActive) Color.Cyan else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.alpha(0.8f).widthIn(max = 200.dp).basicMarquee(iterations = Int.MAX_VALUE, velocity = if (isShowInfo) 80.dp else 0.dp, spacing = MarqueeSpacing(48.dp)))
            Text(text = "*", color = signatureColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }

        updateInfo?.let { info ->
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.padding(32.dp).background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp)).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "¡MAMBO NUEVO! 🚀", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(text = "IAIO ha lanzado la Versión ${info.versionName}", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center)
                    if (info.releaseNotes.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text(text = info.releaseNotes, color = Color.Cyan.copy(alpha = 0.8f), fontSize = 12.sp, textAlign = TextAlign.Center) }
                    Spacer(Modifier.height(24.dp))
                    if (isDownloadingUpdate) { CircularProgressIndicator(progress = { downloadProgress }, color = Color.Cyan, strokeWidth = 4.dp); Spacer(Modifier.height(8.dp)); Text(text = "${(downloadProgress * 100).toInt()}%", color = Color.White, fontSize = 12.sp) }
                    else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(text = "LUEGO", color = Color.White.copy(alpha = 0.4f), modifier = Modifier.pointerInput(Unit) { detectTapGestures { updateInfo = null } }.padding(8.dp), fontWeight = FontWeight.Bold)
                            Text(text = "ACTUALIZAR", color = Color.Cyan, modifier = Modifier.background(Color.Cyan.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).border(1.dp, Color.Cyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).pointerInput(Unit) {
                                detectTapGestures { isDownloadingUpdate = true; scope.launch { updateManager.downloadAndInstallApk(info) { downloadProgress = it } } }
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
        if (referentialUrl != null) AsyncImage(model = referentialUrl, contentDescription = null, modifier = Modifier.fillMaxSize().alpha(0.5f), contentScale = ContentScale.Crop)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            drawCircle(brush = Brush.sweepGradient(0.0f to brushColor, 0.2f to Color.Transparent, 0.5f to brushColor, 0.7f to Color.Transparent, 1.0f to brushColor), radius = radius)
            for (i in 1..25) drawCircle(color = grooveColor, radius = radius * (0.35f + (i / 25f) * 0.65f), style = Stroke(width = 0.5.dp.toPx()))
            drawCircle(color = labelColor, radius = radius * 0.35f)
            drawCircle(color = brushColor, radius = radius * 0.35f, style = Stroke(width = 1.dp.toPx()))
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun RadioScreen(station: RadioStation, title: String, artist: String, artworkUrl: String?, isActive: Boolean, isPlaying: Boolean, isCountdownActive: Boolean, onPauseRequest: () -> Unit, bpm: Int, realEnergyL: Float, realEnergyR: Float, isMagnetActive: Boolean, isCalibrated: Boolean, calibrationCountdown: Int, player: Player?, onScratchStart: () -> Unit, onScratchEnd: (Boolean) -> Unit, isAluminum: Boolean, onToggleAluminum: () -> Unit, onToggleOscillator: () -> Unit, visMode: Int, onModeChange: (Int) -> Unit, audioQuality: String) {
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
                        if (!isTouching) { val speed = if (isPlaying) player?.playbackParameters?.speed ?: 1.0f else 0.0f; currentRotation = (currentRotation + 120f * delta * speed) % 360f }
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
                        if (player?.isPlaying == false) { val up = waitForUpOrCancellation(); if (up != null) player.play() }
                        else {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) { onPauseRequest(); while (true) { if (awaitPointerEvent().changes.none { it.pressed }) break }; break }
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
                if (artist.isNotEmpty()) Text(text = "ARTISTA : $artist", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.alpha(0.9f).fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, velocity = 35.dp))
            }
            Spacer(modifier = Modifier.height(48.dp))
            Box(modifier = Modifier.pointerInput(Unit) { detectTapGestures(onDoubleTap = { onToggleOscillator() }) }) { 
                if (isActive) SpectrumVisualizer(isPlaying = isPlaying, energyL = realEnergyL, energyR = realEnergyR, currentMode = visMode, onModeChange = onModeChange) 
            }
            Spacer(modifier = Modifier.height(36.dp))
            val beatDuration = if (bpm > 0) 60000 / bpm else 500
            val infiniteBeat = rememberInfiniteTransition(label = "heartBeat")
            val beatPulse by infiniteBeat.animateFloat(initialValue = 1f, targetValue = 1.6f, animationSpec = infiniteRepeatable(tween(beatDuration / 2, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
            val energyFactor = ((realEnergyL + realEnergyR) / 2f).coerceIn(0.5f, 1.2f)
            val finalScale = beatPulse * energyFactor
            Box(modifier = Modifier.size(360.dp).pointerInput(player) {
                detectTapGestures(onDoubleTap = { onToggleAluminum() }, onPress = { 
                    isTouching = true; var initialAngle = Math.toDegrees(atan2((it.y - 180.dp.toPx()).toDouble(), (it.x - 180.dp.toPx()).toDouble())).toFloat()
                    var isDragging = false; PlaybackService.stutterProcessor.isScratching = true
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(); vibratePhone(context, 5); val pointer = event.changes.firstOrNull { it.pressed }
                                if (pointer == null) { isTouching = false; PlaybackService.stutterProcessor.isScratching = false; onScratchEnd(!isPlaying); break }
                                val currentAngle = Math.toDegrees(atan2((pointer.position.y - 180.dp.toPx()).toDouble(), (pointer.position.x - 180.dp.toPx()).toDouble())).toFloat()
                                var delta = currentAngle - initialAngle
                                if (delta > 180) delta -= 360 else if (delta < -180) delta += 360
                                if (Math.abs(delta) > 0.5f || isDragging) { if (!isDragging) { isDragging = true; onScratchStart() }; currentRotation = (currentRotation + delta) % 360f; PlaybackService.stutterProcessor.scratchSpeed = (delta / (120f * 0.016f)).coerceIn(-4f, 4f); initialAngle = currentAngle }
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
                        drawArc(color = Color(0xFF00FF41).copy(alpha = 0.4f), startAngle = -5f, sweepAngle = 40f, useCenter = false, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(color = Color(0xFF00FF41), startAngle = 0f, sweepAngle = 30f, useCenter = false, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                Box(modifier = Modifier.size(10.dp).scale(if (isPlaying) finalScale else 1f).clip(CircleShape).background(if (isPlaying) Color.Red else Color.Gray.copy(alpha = 0.5f)).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape))
            }
            Spacer(Modifier.height(56.dp))
        }
    }
}
