package com.example.radio_vertical

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

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
        private val _audioSessionId = MutableStateFlow(0)
        val audioSessionId: StateFlow<Int> = _audioSessionId

        val stutterProcessor = StutterAudioProcessor()
        
        val currentBpm: StateFlow<Int> = stutterProcessor.bpmFlow
        val currentEnergyL: StateFlow<Float> = stutterProcessor.energyPeakLFlow
        val currentEnergyR: StateFlow<Float> = stutterProcessor.energyPeakRFlow
        val isMagnetActive: StateFlow<Boolean> = stutterProcessor.magnetActiveFlow
        val isCalibrated: StateFlow<Boolean> = stutterProcessor.isCalibratedFlow
        val calibrationCountdown: StateFlow<Int> = stutterProcessor.calibrationCountdownFlow
        val currentWaveform: StateFlow<FloatArray> = stutterProcessor.waveformFlow
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(stutterProcessor))
                    .build()
            }
        }

        // Configuración de DataSource optimizada para evitar cortes en Redirects
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("ia-radio-engine/1.0") 
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false // Seguimos manual para controlar el Ducking sutilmente
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) requestManualFocus()
                else abandonManualFocus()
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

        mediaSession = MediaSession.Builder(this, player!!)
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        abandonManualFocus()
        mediaSession?.run {
            release()
            mediaSession = null
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
