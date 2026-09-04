package com.example.radio_vertical

import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes

data class RadioStation(
    val name: String,
    val url: String,
    val backgroundColor: Color,
    val logoUrl: String? = null,
    val apiUrl: String? = null,
    val shortcode: String? = null
) {
    fun toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle("LIVE — $name")
            .setArtist(name)
            .setDisplayTitle(name)
            .setArtworkUri(logoUrl?.toUri())
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()

        val mimeType = if (url.contains("m3u8")) MimeTypes.APPLICATION_M3U8 else null

        return MediaItem.Builder()
            .setMediaId(name) // ID único basado en el nombre para estabilidad absoluta
            .setUri(url)
            .setMimeType(mimeType)
            .setMediaMetadata(metadata)
            .build()
    }
}

object RadioData {
    val stations = listOf(
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
            name = "BEETHOVEN FM",
            url = "https://hls-audio-cl-1-isp.dps.live/beethovenfm/gotardis/audio/now/livestream1.m3u8",
            backgroundColor = Color.Black,
            logoUrl = "https://infiny.live/uploads/multimedia/2020/04/s_8ce69386916381d2d6e1da72280373bc0.png",
            apiUrl = "https://infiny.live/uploads/radios/beethovenfm/json/now.json"
        ),
        RadioStation(
            name = "FUTURO",
            url = "https://playerservices.streamtheworld.com/api/livestream-redirect/FUTURO.mp3",
            backgroundColor = Color.Black,
            logoUrl = "https://www.futuro.cl/wp-content/uploads/2021/07/android-chrome-512x512-1.png"
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
            url = "https://mdstrm.com/audio/5c915724519bce27671c4d15/live.m3u8",
            backgroundColor = Color.Black,
            logoUrl = "https://myradioonline.cl/public/uploads/radio_img/sonar-fm/play_250_250.webp",
            apiUrl = "https://rds.canal13.cl/nowplaying/sonarfm.json"
        ),
        RadioStation(
            name = "PLAY FM",
            url = "https://mdstrm.com/audio/5c8d6406f98fbf269f57c82c/live.m3u8",
            backgroundColor = Color(0xFFFA264D),
            logoUrl = "https://ott-assets.mdstrm.com/5c58a34e176c2c0813b22e4b/633db501b938191960de607d/assets/LOGOPLAY04.png",
            apiUrl = "https://rds.canal13.cl/nowplaying/playfm.json"
        ),
        RadioStation(
            name = "90s90s ROCK",
            url = "https://regiocast.streamabc.net/regc-90s90srock1436287-mp3-192-2191420?sABC=671rr92q%231%23730168p5ron6405p8q8817q3rrs5o615%23ubzrcntr&mode=preroll&aw_0_1st.skey=1730078977&cb=863839065&listenerid=730168c5eba6405c8d8817d3eef5b615&aw_0_1st.playerid=homepage&amsparams=playerid:homepage;skey:1730079021",
            backgroundColor = Color.Black,
            logoUrl = "https://www.90s90s.de/sites/default/files/styles/station_logo/public/images/90s90s_rock_logo.png",
            apiUrl = "https://api.90s90s.de/nowplaying/rock"
        ),
        RadioStation(
            name = "CHRONIX AGGRESSION",
            url = "http://usa19.fastcast4u.com:5720/",
            backgroundColor = Color.Black,
            logoUrl = "https://cdn-profiles.tunein.com/s14421/images/logoq.png"
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
        ),
        RadioStation(
            name = "LABGATE CLASSIC ROCK",
            url = "https://s2.ssl-stream.com/listen/labgate_classic_rock/radio.mp3",
            backgroundColor = Color.Black,
            logoUrl = "https://labgate.net/img/labgate_logo_300.png",
            apiUrl = "https://s2.ssl-stream.com/api/nowplaying/labgate_classic_rock",
            shortcode = "labgate_classic_rock"
        ),
        RadioStation(
            name = "LABGATE ALT ROCK",
            url = "https://s2.ssl-stream.com/listen/labgate_alt_rock_grunge/radio.mp3",
            backgroundColor = Color.Black,
            logoUrl = "https://labgate.net/img/labgate_logo_300.png",
            apiUrl = "https://s2.ssl-stream.com/api/nowplaying/labgate_alt_rock_grunge",
            shortcode = "labgate_alt_rock_grunge"
        ),
        RadioStation(
            name = "LABGATE PROG",
            url = "https://s2.ssl-stream.com/listen/labgate_prog/radio.mp3",
            backgroundColor = Color.Black,
            logoUrl = "https://labgate.net/img/labgate_logo_300.png",
            apiUrl = "https://s2.ssl-stream.com/api/nowplaying/labgate_prog",
            shortcode = "labgate_prog"
        ),
        RadioStation(
            name = "LABGATE P.Y.G.",
            url = "https://s2.ssl-stream.com/listen/labgate_p.y.g./radio.mp3",
            backgroundColor = Color.Black,
            logoUrl = "https://labgate.net/img/labgate_logo_300.png",
            apiUrl = "https://s2.ssl-stream.com/api/nowplaying/labgate_p.y.g.",
            shortcode = "labgate_p.y.g."
        ),
        RadioStation(
            name = "LABGATE HITWAVE",
            url = "https://s2.ssl-stream.com/listen/labgate_pop_rock/radio.mp3",
            backgroundColor = Color.Black,
            logoUrl = "https://labgate.net/img/labgate_logo_300.png",
            apiUrl = "https://s2.ssl-stream.com/api/nowplaying/labgate_pop_rock",
            shortcode = "labgate_pop_rock"
        ),
        RadioStation(
            name = "BÍO BÍO SANTIAGO",
            url = "https://unlimited3-cl.dps.live/biobiosantiago/mp3/icecast.audio",
            backgroundColor = Color.Black,
            logoUrl = "https://static.mytuner.mobi/media/tvos_radios/p16vf3v17r1m9.png"
        ),
        RadioStation(
            name = "MI RADIO LS",
            url = "https://audio3.tustreaming.cl/9020/stream",
            backgroundColor = Color.Black,
            logoUrl = "https://www.miradiols.cl/wp-content/uploads/2024/11/Asset-13.png"
        ),
        RadioStation(
            name = "LA ROCKA 80",
            url = "https://audiopanel.com.ar:8000/radio.aac",
            backgroundColor = Color.Black,
            logoUrl = "https://static.mytuner.mobi/media/tvos_radios/807/la-rocka-80.59df80ba.png",
            apiUrl = "http://audiopanel.com.ar:8000/status-json.xsl"
        ),
        RadioStation("cliqhop idm", "https://ice1.somafm.com/cliqhop-128-mp3", Color.Black, "https://somafm.com/img/cliqhop400.png", "https://somafm.com/songs/cliqhop.json"),
        RadioStation("Drone Zone", "https://ice1.somafm.com/dronezone-128-mp3", Color.Black, "https://somafm.com/img/dronezone400.png", "https://somafm.com/songs/dronezone.json"),
        RadioStation("Deep Space One", "https://ice1.somafm.com/deepspaceone-128-mp3", Color.Black, "https://somafm.com/img/deepspaceone400.png", "https://somafm.com/songs/deepspaceone.json"),
        RadioStation("Space Station Soma", "https://ice1.somafm.com/spacestation-128-mp3", Color.Black, "https://somafm.com/img/spacestation400.png", "https://somafm.com/songs/spacestation.json"),
        RadioStation("Groove Salad", "https://ice1.somafm.com/groovesalad-128-mp3", Color.Black, "https://somafm.com/img/groovesalad400.png", "https://somafm.com/songs/groovesalad.json"),
        RadioStation("Synphaera Radio", "https://ice1.somafm.com/synphaera-128-mp3", Color.Black, "https://somafm.com/img/synphaera400.png", "https://somafm.com/songs/synphaera.json"),
        RadioStation("n5MD Radio", "https://ice1.somafm.com/n5md-128-mp3", Color.Black, "https://somafm.com/img/n5md400.png", "https://somafm.com/songs/n5md.json"),
        RadioStation("Drone Zone 2", "https://ice1.somafm.com/dz2-128-mp3", Color.Black, "https://somafm.com/img/dz2400.png", "https://somafm.com/songs/dz2.json"),
        RadioStation("The Dark Zone", "https://ice1.somafm.com/darkzone-128-mp3", Color.Black, "https://somafm.com/img/darkzone400.png", "https://somafm.com/songs/darkzone.json"),
        RadioStation("Vaporwaves", "https://ice1.somafm.com/vaporwaves-128-mp3", Color.Black, "https://somafm.com/img/vaporwaves400.png", "https://somafm.com/songs/vaporwaves.json"),
        RadioStation("Beat Blender", "https://ice1.somafm.com/beatblender-128-mp3", Color.Black, "https://somafm.com/img/beatblender400.png", "https://somafm.com/songs/beatblender.json"),
        RadioStation("Fluid", "https://ice1.somafm.com/fluid-128-mp3", Color.Black, "https://somafm.com/img/fluid400.png", "https://somafm.com/songs/fluid.json"),
        RadioStation("Digitalis", "https://ice1.somafm.com/digitalis-128-mp3", Color.Black, "https://somafm.com/img/digitalis400.png", "https://somafm.com/songs/digitalis.json"),
        RadioStation("DEF CON Radio", "https://ice1.somafm.com/defcon-128-mp3", Color.Black, "https://somafm.com/img/defcon400.png", "https://somafm.com/songs/defcon.json"),
        RadioStation("The Trip", "https://ice1.somafm.com/thetrip-128-mp3", Color.Black, "https://somafm.com/img/thetrip400.png", "https://somafm.com/songs/thetrip.json"),
        RadioStation("NTS Labyrinth", "https://stream-mixtape-geo.ntslive.net/mixtape31?client=direct", Color.Black, "https://www.nts.live/static/images/nts-logo.png"),
        RadioStation("NTS Slow Focus", "https://stream-mixtape-geo.ntslive.net/mixtape?client=direct", Color.Black, "https://www.nts.live/static/images/nts-logo.png"),
        RadioStation("NTS The Tube", "https://stream-mixtape-geo.ntslive.net/mixtape26?client=direct", Color.Black, "https://www.nts.live/static/images/nts-logo.png"),
        RadioStation("Resonance FM", "https://stream.resonance.fm/resonance", Color.Black, "https://www.resonancefm.com/images/logo.png"),
        RadioStation("Resonance Extra", "https://stream.resonance.fm/extra", Color.Black, "https://www.resonancefm.com/images/logo.png"),
        RadioStation("SomaFM Indie Pop Rocks!", "https://ice5.somafm.com/indiepop-128-mp3", Color.Black, "https://api.somafm.com/logos/512/indiepop512.png", "https://somafm.com/songs/indiepop.json"),
        RadioStation("The Alternative One", "https://digitalaudiobroadcasting.net:8020/stream", Color.Black, "https://digitalaudiobroadcasting.net/logo.png"),
        RadioStation("WFMU", "http://stream0.wfmu.org/freeform-128k.mp3", Color.Black, "https://wfmu.org/img/wfmu_logo.gif"),
        RadioStation("Radio Paradise Rock Mix", "https://stream.radioparadise.com/rock-320", Color.Black, "https://radioparadise.com/graphics/logo_flat_350x100.png"),
        RadioStation("Radio Paradise Mellow Mix", "https://stream.radioparadise.com/mellow-320", Color.Black, "https://radioparadise.com/graphics/logo_flat_350x100.png"),
        RadioStation("Radio Paradise Eclectic Mix", "https://stream.radioparadise.com/eclectic-320", Color.Black, "https://radioparadise.com/graphics/logo_flat_350x100.png"),
        RadioStation("SomaFM PopTron", "https://ice5.somafm.com/poptron-128-mp3", Color.Black, "https://api.somafm.com/logos/512/poptron512.png", "https://somafm.com/songs/poptron.json"),
        RadioStation("SomaFM Lush", "https://ice5.somafm.com/lush-128-mp3", Color.Black, "https://api.somafm.com/logos/512/lush512.png", "https://somafm.com/songs/lush.json"),
        RadioStation("Radio 7 - 90er Eurodance", "https://streams.radio7.de/90ereudance/mp3-192/streams.radio7.de/", Color.Black, "https://www.radio7.de/sites/all/themes/radio7/logo.png"),
        RadioStation("90s90s - Eurodance", "https://streams.90s90s.de/eurodance/mp3-192/streams.90s90s.de/", Color.Black, "https://www.90s90s.de/sites/default/files/styles/station_logo/public/images/90s90s_eurodance_logo.png", "https://api.90s90s.de/nowplaying/eurodance"),
        RadioStation("Eurodance 90 France", "https://stream-eurodance90.fr/radio/8000/128.mp3", Color.Black, "https://eurodance90.fr/images/logo.png"),
        RadioStation("Eurodance 90's Brazil", "http://stream.zeno.fm/g1u270e2ybruv", Color.Black, "https://zeno.fm/static/img/zeno-logo.png"),
        RadioStation("Eurodance 90 Brazil", "http://stream.zeno.fm/zx9h2b61u8quv", Color.Black, "https://zeno.fm/static/img/zeno-logo.png"),
        RadioStation("SG Radio (Synthesizer)", "https://stream.laut.fm/synthesizergreatest", Color.Black, "https://api.laut.fm/station/synthesizergreatest/images/station_640x640", "https://api.laut.fm/station/synthesizergreatest/current_song"),
        RadioStation("DCS - Dance Classics", "https://stream.laut.fm/dcs", Color.Black, "https://api.laut.fm/station/dcs/images/station_640x640", "https://api.laut.fm/station/dcs/current_song"),
        RadioStation("Day Dee Eurodance", "https://stream.laut.fm/daydeeeurodance", Color.Black, "https://api.laut.fm/station/daydeeeurodance/images/station_640x640", "https://api.laut.fm/station/daydeeeurodance/current_song"),
        RadioStation("90s Radio", "https://stream.laut.fm/90s", Color.Black, "https://api.laut.fm/station/90s/images/station_640x640", "https://api.laut.fm/station/90s/current_song"),
        RadioStation("laut.fm Dance", "https://stream.laut.fm/dance", Color.Black, "https://api.laut.fm/station/dance/images/station_640x640", "https://api.laut.fm/station/dance/current_song"),
        RadioStation("ANTENNE BAYERN Eurodance", "https://stream.antenne.de/antenne-bayern-90er-eurodance/stream/mp3", Color.Black, "https://www.antenne.de/assets/images/logo.png")
    )

    fun getMediaItems(): List<MediaItem> = stations.map { it.toMediaItem() }

    fun getStationByName(name: String): RadioStation? = stations.find { it.name == name }

    fun getFavoritesList(names: List<String>): List<RadioStation> {
        return stations.filter { names.contains(it.name) }
    }
    
    fun getRootItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId("RADIO_ROOT")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Estaciones")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            ).build()
    }
}
