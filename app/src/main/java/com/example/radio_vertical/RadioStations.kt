package com.example.radio_vertical

import androidx.compose.ui.graphics.Color

data class RadioStation(
    val name: String,
    val url: String,
    val backgroundColor: Color,
    val logoUrl: String? = null,
    val apiUrl: String? = null,
    val shortcode: String? = null
)

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
        ),
        RadioStation(
            name = "LABGATE ALT",
            url = "https://shoutcast.labgate.net:443/alt-rock",
            backgroundColor = Color.Black,
            logoUrl = "https://labgate.net/img/labgate_logo_300.png"
        ),
        RadioStation(
            name = "LA ROCKA 80",
            url = "https://stream.zeno.fm/u2q7u560vzzuv",
            backgroundColor = Color.Black,
            logoUrl = "https://zeno.fm/_next/image/?url=https%3A%2F%2Fimages.zeno.fm%2Fstations%2Farchive%2F38f9b9646b%2Fimage.jpg&w=640&q=75"
        )
    )
}
