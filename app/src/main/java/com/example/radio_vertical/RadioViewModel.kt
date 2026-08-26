package com.example.radio_vertical

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class NowPlayingResponse(
    val station: StationInfo? = null,
    val now_playing: NowPlaying? = null
)

@Serializable
data class StationInfo(
    val name: String = "",
    val shortcode: String = ""
)

@Serializable
data class NowPlaying(
    val song: Song? = null
)

@Serializable
data class Song(
    val title: String = "",
    val artist: String = "",
    val art: String? = null
)

@Serializable
data class OneFMResponse(
    val Artist: String? = null,
    val Title: String? = null,
    @SerialName("Image") val imageUpper: String? = null,
    @SerialName("image") val imageLower: String? = null,
    val largeImage: String? = null,
    @SerialName("LargeImage") val largeImageUpper: String? = null,
    val imageUrl: String? = null
)

@Serializable
data class UzicResponse(
    val artist: String? = null,
    val title: String? = null,
    val artwork: String? = null,
    val image: String? = null,
    val img: String? = null
)

@Serializable
data class PrisaResponse(
    val now_playing: PrisaNowPlaying? = null,
    val nowPlaying: PrisaNowPlaying? = null,
    val current: PrisaNowPlaying? = null,
    val data: RDFData? = null
)

@Serializable
data class RDFData(
    val now_playing: PrisaNowPlaying? = null,
    val nowplaying: PrisaNowPlaying? = null,
    val now: PrisaNowPlaying? = null,
    val artist: String? = null,
    val title: String? = null,
    val song: String? = null,
    val artwork: String? = null,
    val image: String? = null,
    val cover: String? = null,
    val song_name: String? = null,
    val artist_name: String? = null,
    val cover_url: String? = null
)

@Serializable
data class PrisaNowPlaying(
    val title: String? = null,
    val artist: String? = null,
    val song: String? = null,
    val song_name: String? = null,
    val artist_name: String? = null,
    val artworkUrl: String? = null,
    val artwork: String? = null,
    val image: String? = null,
    val cover: String? = null,
    val cover_url: String? = null,
    val data: MediastreamData? = null,
    val track: MediastreamData? = null
)

@Serializable
data class MediastreamData(
    val title: String? = null,
    val artist: String? = null,
    val image: String? = null
)

@Serializable
data class TritonResponse(
    @SerialName("nowplaying-info-list") val infoList: List<TritonInfo>? = null
)

@Serializable
data class TritonInfo(
    val cuePoint: TritonCue? = null
)

@Serializable
data class TritonCue(
    val cueTitle: String? = null,
    val artistName: String? = null
)

@Serializable
data class SomaResponse(
    val songs: List<SomaSong>? = null
)

@Serializable
data class SomaSong(
    val artist: String? = null,
    val title: String? = null
)

@Serializable
data class RegiocastResponse(
    val title: String? = null,
    val artist: String? = null,
    val cover: String? = null
)

@Serializable
data class LautResponse(
    val title: String? = null,
    val artist: String? = null,
    val image: String? = null
)

@Serializable
data class NexusResponse(
    val songtitle: String? = null
)

@Serializable
data class IcecastResponse(
    val icestats: IcecastStats? = null
)

@Serializable
data class IcecastStats(
    val source: kotlinx.serialization.json.JsonElement? = null 
)

@Serializable
data class IcecastSource(
    val title: String? = null,
    val artist: String? = null,
    val listenurl: String? = null
)

data class RadioMetadata(
    val title: String = "En vivo",
    val artist: String = "",
    val artworkUrl: String? = null
)

class RadioViewModel : ViewModel() {
    private val client = OkHttpClient()
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
        isLenient = true
    }
    
    private val _metadata = MutableStateFlow(RadioMetadata())
    val metadata: StateFlow<RadioMetadata> = _metadata

    private var currentApiUrl: String? = null
    private var isPolling = false

    fun startPolling(apiUrl: String?, shortcode: String? = null, stationName: String = "") {
        if (apiUrl == currentApiUrl && isPolling) return
        
        currentApiUrl = apiUrl
        
        _metadata.value = RadioMetadata(
            title = if (apiUrl != null) "Cargando..." else "En vivo",
            artist = stationName,
            artworkUrl = null
        )
        
        if (apiUrl != null) {
            isPolling = true
            viewModelScope.launch(Dispatchers.IO) {
                while (isPolling && currentApiUrl == apiUrl) {
                    fetchMetadata(apiUrl, shortcode, stationName)
                    delay(15000) 
                }
            }
        } else {
            isPolling = false
        }
    }

    private suspend fun fetchMetadata(url: String, shortcode: String?, stationName: String) {
        var currentUrl = url
        try {
            val requestBuilder = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
            
            if (currentUrl.contains("sonarfm.cl") || stationName.contains("SONAR", ignoreCase = true)) {
                requestBuilder.header("Referer", "https://sonarfm.cl/")
                requestBuilder.header("Origin", "https://sonarfm.cl")
            } else if (currentUrl.contains("playfm.cl") || stationName.contains("PLAY", ignoreCase = true)) {
                requestBuilder.header("Referer", "https://playfm.cl/")
                requestBuilder.header("Origin", "https://playfm.cl")
            }

            val request = requestBuilder.build()
                
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d("RadioVM", "Fetching metadata from: $currentUrl")
                Log.d("RadioVM", "Response code: ${response.code}")
                Log.d("RadioVM", "Response Body: $body")
                
                if (response.isSuccessful && body.isNotEmpty()) {
                    withContext(Dispatchers.Default) {
                        try {
                            when {
                                currentUrl.contains("1.fm") -> {
                                    val parsed = json.decodeFromString<OneFMResponse>(body)
                                    val img = parsed.largeImage ?: parsed.largeImageUpper ?: parsed.imageUrl ?: parsed.imageUpper ?: parsed.imageLower
                                    val finalImg = if (!img.isNullOrBlank() && !img.startsWith("http")) {
                                        "https://www.1.fm/$img"
                                    } else img
                                    updateUI(parsed.Title, parsed.Artist, finalImg, stationName)
                                }
                                url.contains("uzic") -> {
                                    val parsed = json.decodeFromString<UzicResponse>(body)
                                    updateUI(parsed.title, parsed.artist, parsed.artwork ?: parsed.image ?: parsed.img, stationName)
                                }
                                url.contains("prisamedia") || url.contains("prisaradio") || url.contains("mdstrm") || url.contains("rdfmedia") || url.contains("sonarfm.cl") || url.contains("onlineradio.cl") || url.contains("emisorpodcasting.com") || url.contains("metadata.mdstrm.com") || url.contains("canal13.cl") -> {
                                    Log.d("RadioVM", "Parsing RDF/Mediastream/OnlineRadio metadata: $body")
                                    
                                    // 1. Intentar como objeto envuelto (Estructura RDF/Mediastream Pro)
                                    try {
                                        val wrapped = json.decodeFromString<PrisaResponse>(body)
                                        val np = wrapped.current ?: wrapped.nowPlaying ?: wrapped.now_playing ?: 
                                                 wrapped.data?.now_playing ?: wrapped.data?.nowplaying ?: wrapped.data?.now
                                        
                                        if (np != null) {
                                            val title = np.title ?: np.song ?: np.song_name ?: np.data?.title ?: np.track?.title
                                            val artist = np.artist ?: np.artist_name ?: np.data?.artist ?: np.track?.artist
                                            val art = np.artwork ?: np.artworkUrl ?: np.image ?: np.cover ?: np.cover_url ?: np.data?.image ?: np.track?.image
                                            if (title != null || artist != null) {
                                                updateUI(title, artist, art, stationName)
                                                return@withContext
                                            }
                                        }
                                        
                                        // 2. Intentar campos planos dentro de 'data' (Estructura OnlineRadio)
                                        wrapped.data?.let { d ->
                                            val title = d.title ?: d.song ?: d.song_name
                                            val artist = d.artist ?: d.artist_name
                                            val art = d.artwork ?: d.image ?: d.cover ?: d.cover_url
                                            if (title != null || artist != null) {
                                                updateUI(title, artist, art, stationName)
                                                return@withContext
                                            }
                                        }
                                    } catch (e: Exception) { 
                                        Log.d("RadioVM", "Wrapped parse failed, trying flat...")
                                    }

                                    // 3. Intentar como objeto plano total
                                    try {
                                        val flat = json.decodeFromString<PrisaNowPlaying>(body)
                                        val title = flat.title ?: flat.song ?: flat.song_name ?: flat.data?.title
                                        val artist = flat.artist ?: flat.artist_name ?: flat.data?.artist
                                        val art = flat.artwork ?: flat.artworkUrl ?: flat.image ?: flat.cover ?: flat.cover_url ?: flat.data?.image
                                        if (title != null || artist != null) {
                                            updateUI(title, artist, art, stationName)
                                            return@withContext
                                        }
                                    } catch (e: Exception) {
                                        Log.d("RadioVM", "Flat parse failed")
                                    }
                                    
                                    // 4. Extracción manual de emergencia (REGEX mejorado)
                                    val mArtist = Regex("\"artist\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?:
                                                 Regex("\"artist_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                                    val mTitle = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?:
                                                Regex("\"song\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?:
                                                Regex("\"song_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                                    val mArt = Regex("\"(?:image|artwork|cover|artworkUrl|cover_url)\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                                    
                                    if (mArtist != null || mTitle != null) {
                                        Log.d("RadioVM", "Match found via Emergency Regex")
                                        updateUI(mTitle, mArtist, mArt, stationName)
                                        return@withContext
                                    }
                                    
                                    updateUI(null, null, null, stationName)
                                }
                                url.contains("streamtheworld") || url.contains("tritondigital") -> {
                                    val parsed = json.decodeFromString<TritonResponse>(body)
                                    val track = parsed.infoList?.firstOrNull()?.cuePoint
                                    updateUI(track?.cueTitle, track?.artistName, null, stationName)
                                }
                                url.contains("somafm.com") -> {
                                    val parsed = json.decodeFromString<SomaResponse>(body)
                                    val track = parsed.songs?.firstOrNull()
                                    updateUI(track?.title, track?.artist, null, stationName)
                                }
                                url.contains("regiocast.de") || url.contains("90s90s.de") || url.contains("radiobob.de") || url.contains("radioplay.de") -> {
                                    val parsed = json.decodeFromString<RegiocastResponse>(body)
                                    updateUI(parsed.title, parsed.artist, parsed.cover, stationName)
                                }
                                url.contains("laut.fm") -> {
                                    val parsed = json.decodeFromString<LautResponse>(body)
                                    updateUI(parsed.title, parsed.artist, parsed.image, stationName)
                                }
                                url.contains("nexuscast.com") -> {
                                    val parsed = json.decodeFromString<NexusResponse>(body)
                                    val title = parsed.songtitle?.split(" - ")?.getOrNull(1)
                                    val artist = parsed.songtitle?.split(" - ")?.getOrNull(0)
                                    updateUI(title, artist, null, stationName)
                                }
                                url.contains("status-json.xsl") -> {
                                    val parsed = json.decodeFromString<IcecastResponse>(body)
                                    val sourceElement = parsed.icestats?.source
                                    if (sourceElement != null) {
                                        val sources = if (sourceElement is kotlinx.serialization.json.JsonArray) {
                                            json.decodeFromJsonElement<List<IcecastSource>>(sourceElement)
                                        } else {
                                            listOf(json.decodeFromJsonElement<IcecastSource>(sourceElement))
                                        }
                                        val match = sources.find { it.listenurl?.contains("radio.aac") == true } ?: sources.firstOrNull()
                                        updateUI(match?.title, match?.artist, null, stationName)
                                    }
                                }
                                else -> {
                                    if (body.trim().startsWith("[")) {
                                        val list = json.decodeFromString<List<NowPlayingResponse>>(body)
                                        val match = if (shortcode != null) {
                                            list.find { it.station?.shortcode == shortcode } ?: list.firstOrNull()
                                        } else {
                                            list.firstOrNull()
                                        }
                                        updateUI(match?.now_playing?.song?.title, match?.now_playing?.song?.artist, match?.now_playing?.song?.art, stationName)
                                    } else {
                                        val parsed = json.decodeFromString<NowPlayingResponse>(body)
                                        updateUI(parsed.now_playing?.song?.title, parsed.now_playing?.song?.artist, parsed.now_playing?.song?.art, stationName)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("RadioVM", "Parse Error for $url: ${e.message}", e)
                            // Intentar una extracción manual simple si falla el JSON
                            if (body.contains("title") || body.contains("artist") || body.contains("song")) {
                                Log.d("RadioVM", "Trying manual extraction for $url...")
                            }
                            updateUI(null, null, null, stationName)
                        }
                    }
                } else {
                    Log.e("RadioVM", "HTTP Error: ${response.code}")
                    updateUI(null, null, null, stationName)
                }
            }
        } catch (e: Exception) {
            Log.e("RadioVM", "Fetch Error: ${e.message}")
            updateUI(null, null, null, stationName)
        }
    }

    private suspend fun updateUI(title: String?, artist: String?, art: String?, stationName: String) {
        withContext(Dispatchers.Main) {
            if (!title.isNullOrEmpty() || !artist.isNullOrEmpty()) {
                _metadata.value = RadioMetadata(
                    title = title?.ifEmpty { "En vivo" } ?: "En vivo",
                    artist = if (!artist.isNullOrEmpty()) artist else stationName,
                    artworkUrl = if (!art.isNullOrBlank()) art else null
                )
            } else {
                _metadata.value = RadioMetadata(
                    title = "En vivo",
                    artist = stationName,
                    artworkUrl = null
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        isPolling = false
    }
}
