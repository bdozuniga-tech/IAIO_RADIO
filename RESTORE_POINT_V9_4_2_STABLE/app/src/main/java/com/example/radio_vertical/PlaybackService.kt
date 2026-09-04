package com.example.radio_vertical

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import android.util.Log
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class NavigationMode { ALL, FAVORITES }

@UnstableApi
class PlaybackService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastNavTime = 0L
    private var isReconnecting = false

    private fun startReconnectionRoutine() {
        if (isReconnecting) return
        isReconnecting = true
        serviceScope.launch {
            while (isReconnecting) {
                Log.d("PlaybackService", "Anti-Túnel: Intentando reconectar...")
                player?.let { p ->
                    if (p.playbackState == Player.STATE_IDLE || p.playerError != null) {
                        p.prepare()
                        p.play()
                    }
                }
                delay(5000) // Reintentar cada 5 segundos
            }
        }
    }

    private fun emitNavEvent(direction: Int) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNavTime > 600) { 
            lastNavTime = currentTime
            
            val mode = navigationMode.value
            val allStations = RadioData.stations
            val favoritesNames = favoriteStationNames.value
            
            val currentList = if (mode == NavigationMode.FAVORITES && favoritesNames.isNotEmpty()) {
                RadioData.getFavoritesList(favoritesNames)
            } else {
                allStations
            }

            if (currentList.isEmpty()) return

            val currentStationName = currentStationNameFlow.value
            val currentIndex = currentList.indexOfFirst { it.name == currentStationName }.let { if (it == -1) 0 else it }
            
            val total = currentList.size
            val newIndex = (((currentIndex + direction) % total) + total) % total
            val station = currentList[newIndex]
            
            Log.d("PlaybackService", "[NAV] Mode=$mode Direction=${if(direction > 0) "NEXT" else "PREVIOUS"} Current=$currentStationName Next=${station.name}")
            
            player?.let { p ->
                val mimeType = if (station.url.contains("m3u8")) MimeTypes.APPLICATION_M3U8 else null
                val mediaItem = MediaItem.Builder()
                    .setMediaId(station.name)
                    .setUri(station.url)
                    .setMimeType(mimeType)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(station.name)
                            .setArtist(station.name)
                            .build()
                    ).build()
                p.setMediaItem(mediaItem)
                p.prepare()
                p.play()
                stutterProcessor.resetVisualPeaks()
            }

            serviceScope.launch { 
                _navEvent.emit(direction)
                val globalIndex = allStations.indexOfFirst { it.name == station.name }
                _currentStationIndexFlow.value = globalIndex
                _currentStationNameFlow.value = station.name
            }
        }
    }

    private val mediaSessionCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(RadioData.getRootItem(), params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId == "RADIO_ROOT") {
                return Futures.immediateFuture(
                    LibraryResult.ofItemList(RadioData.getMediaItems(), params)
                )
            }
            return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    Log.d("PlaybackService", "Comando NEXT recibido desde Session Callback")
                    emitNavEvent(1)
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    Log.d("PlaybackService", "Comando PREVIOUS recibido desde Session Callback")
                    emitNavEvent(-1)
                    return SessionResult.RESULT_SUCCESS
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Pausa total para llamadas o apps que requieren foco exclusivo (como TikTok)
                Log.d("PlaybackService", "Foco perdido ($focusChange). Pausando.")
                player?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // PARA NOTIFICACIONES: Solo bajamos el volumen (Ducking)
                Log.d("PlaybackService", "Ducking detectado ($focusChange). Bajando volumen.")
                player?.volume = 0.2f
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Recuperamos volumen y reanudamos si estaba pausado
                Log.d("PlaybackService", "Foco recuperado. Restaurando volumen.")
                player?.volume = 1.0f
                player?.play()
            }
        }
    }

    companion object {
        private val _navEvent = MutableSharedFlow<Int>()
        val navEvent: SharedFlow<Int> = _navEvent

        private val _audioSessionId = MutableStateFlow(0)
        val audioSessionId: StateFlow<Int> = _audioSessionId

        private val _currentStationIndexFlow = MutableStateFlow(-1)
        val currentStationIndexFlow: StateFlow<Int> = _currentStationIndexFlow

        private val _currentStationNameFlow = MutableStateFlow("")
        val currentStationNameFlow: StateFlow<String> = _currentStationNameFlow

        private val _navigationMode = MutableStateFlow(NavigationMode.ALL)
        val navigationMode = _navigationMode.asStateFlow()

        private val _favoriteStationNames = MutableStateFlow<List<String>>(emptyList())
        val favoriteStationNames = _favoriteStationNames.asStateFlow()

        fun updateInternalIndex(index: Int) {
            _currentStationIndexFlow.value = index
            if (index in RadioData.stations.indices) {
                _currentStationNameFlow.value = RadioData.stations[index].name
            }
        }

        fun updateNavigationMode(mode: NavigationMode) {
            if (_navigationMode.value != mode) {
                Log.d("PlaybackService", "[NAV-MODE] ${_navigationMode.value} -> $mode")
                _navigationMode.value = mode
            }
        }

        fun updateFavoriteNames(names: List<String>) {
            _favoriteStationNames.value = names
        }

        fun updateCurrentStation(name: String) {
            if (_currentStationNameFlow.value != name) {
                _currentStationNameFlow.value = name
                val globalIndex = RadioData.stations.indexOfFirst { it.name == name }
                _currentStationIndexFlow.value = globalIndex
            }
        }

        val stutterProcessor = StutterAudioProcessor()
        
        val currentBpm: StateFlow<Int> = stutterProcessor.bpmFlow
        val energyPeakLFlow: StateFlow<Float> = stutterProcessor.energyPeakLFlow
        val energyPeakRFlow: StateFlow<Float> = stutterProcessor.energyPeakRFlow
        val bandEnergyLFlow: StateFlow<FloatArray> = stutterProcessor.bandEnergyLFlow
        val bandEnergyRFlow: StateFlow<FloatArray> = stutterProcessor.bandEnergyRFlow
        val isMonoFlow: StateFlow<Boolean> = stutterProcessor.isMonoFlow
        val isMagnetActiveFlow: StateFlow<Boolean> = stutterProcessor.magnetActiveFlow
        val isCalibrated: StateFlow<Boolean> = stutterProcessor.isCalibratedFlow
        val calibrationCountdown: StateFlow<Int> = stutterProcessor.calibrationCountdownFlow
        val waveformFlow: StateFlow<FloatArray> = stutterProcessor.waveformFlow
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
        // CARGAR ÚLTIMO ÍNDICE GUARDADO PARA EVITAR RESET A RADIO 1
        val prefs = getSharedPreferences("ia_radio_prefs", Context.MODE_PRIVATE)
        val lastIndex = prefs.getInt("last_station_index", 0)
        updateInternalIndex(lastIndex)
        
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean,
            ): AudioSink {
                // RE-ACTIVAMOS EL PROCESADOR (No era el culpable)
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(stutterProcessor))
                    .build()
            }
        }

        // ACTIVAMOS GESTIÓN DE COOKIES (Crítico para Triton/Futuro)
        if (CookieHandler.getDefault() == null) {
            val cookieManager = CookieManager()
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
            CookieHandler.setDefault(cookieManager)
        }

        // User-Agent tipo VLC (El más robusto para streams chilenos)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18") 
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)
        
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false 
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when(playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.d("PlaybackService", "FUTURO LOG: Estado -> $stateName")
                
                // SI TERMINA A LOS 3 SEGUNDOS (HANDSHAKE/AD), LO FORZAMOS A VOLVER A EMPEZAR
                if (playbackState == Player.STATE_ENDED) {
                    Log.w("PlaybackService", "FUTURO: Reanudando por fin de segmento detectado.")
                    player?.prepare()
                    player?.play()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("PlaybackService", "FUTURO LOG: isPlaying -> $isPlaying")
                if (isPlaying) {
                    requestManualFocus()
                    isReconnecting = false 
                } else {
                    abandonManualFocus()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackService", "FUTURO ERROR CRÍTICO: ${error.message}")
                Log.e("PlaybackService", "FUTURO ERROR CODE: ${error.errorCodeName} (${error.errorCode})")
                error.cause?.let { Log.e("PlaybackService", "FUTURO ERROR CAUSA: ${it.message}") }
                
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player?.seekToDefaultPosition()
                    player?.prepare()
                } else {
                    startReconnectionRoutine()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val reasonName = when(reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
                    else -> "UNKNOWN"
                }
                Log.d("PlaybackService", "FUTURO LOG: Transición MediaItem ($reasonName): ${mediaItem?.mediaMetadata?.title}")
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                Log.d("PlaybackService", "FUTURO LOG: Discontinuidad de posición detectada (Motivo: $reason)")
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                Log.d("PlaybackService", "FUTURO LOG: Timeline cambiada (Motivo: $reason, Ventanas: ${timeline.windowCount})")
            }

            override fun onTracksChanged(tracks: Tracks) {
                Log.d("PlaybackService", "FUTURO LOG: Cambiaron los tracks/formato")
                tracks.groups.forEach { group ->
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        Log.d("PlaybackService", "FUTURO FORMATO: Mime=${format.sampleMimeType}, SampleRate=${format.sampleRate}, Channels=${format.channelCount}, Bitrate=${format.bitrate}")
                    }
                }
            }

            override fun onAudioSessionIdChanged(id: Int) {
                _audioSessionId.value = id
            }
        })
            
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Usamos un ForwardingPlayer para interceptar comandos de Bluetooth de forma más robusta
        val forwardingPlayer = object : ForwardingPlayer(player!!) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun seekToNext() {
                Log.d("PlaybackService", "seekToNext interceptado")
                emitNavEvent(1)
            }

            override fun seekToPrevious() {
                Log.d("PlaybackService", "seekToPrevious interceptado")
                emitNavEvent(-1)
            }

            override fun seekToNextMediaItem() {
                Log.d("PlaybackService", "seekToNextMediaItem interceptado")
                emitNavEvent(1)
            }

            override fun seekToPreviousMediaItem() {
                Log.d("PlaybackService", "seekToPreviousMediaItem interceptado")
                emitNavEvent(-1)
            }
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, forwardingPlayer, mediaSessionCallback)
            .setSessionActivity(pendingIntent)
            .build()
    }

    private fun requestManualFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonManualFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        abandonManualFocus()
        mediaLibrarySession?.run {
            release()
            mediaLibrarySession = null
        }
        player?.release()
        player = null
        _audioSessionId.value = 0
        super.onDestroy()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }
}
