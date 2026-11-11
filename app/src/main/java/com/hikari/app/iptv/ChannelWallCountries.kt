package com.hikari.app.iptv

/**
 * Country-specific premium and popular networks for the Channel Wall.
 *
 * Kept apart from the curated global list so the wall's country picker can
 * filter them without touching the hand-written genre sections. Every entry
 * uses the same WallChannel shape as the rest of the wall, so it inherits the
 * full-bleed card art, logo fallback and matching rules.
 */
object ChannelWallCountries {

    private const val LOGO_BASE =
        "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/"

    private fun ch(
        id: String,
        name: String,
        short: String,
        category: String,
        logoPath: String?,
        top: Long,
        bottom: Long,
        vararg keywords: String,
    ): WallChannel = WallChannel(
        id = id,
        name = name,
        short = short,
        category = category,
        keywords = keywords.toList().ifEmpty { listOf(name.lowercase()) },
        exclude = emptyList(),
        gradient = listOf(top, bottom),
        iconUrl = logoPath?.let { LOGO_BASE + it },
        backdropUrl = logoPath?.let { LOGO_BASE + it },
    )

    val all: List<WallChannel> = listOf(
        // ---- Africa ---------------------------------------------------------
        ch("africa_magic", "Africa Magic", "AFRM", "Africa", "south-africa/africa-magic-za.png", 0xFFF5A623, 0xFF1A1A1A, "africa magic", "africamagic"),
        ch("mnet", "M-Net", "MNET", "Africa", "south-africa/m-net-za.png", 0xFF0A4D8C, 0xFF101010, "m-net", "mnet", "m net"),
        ch("supersport", "SuperSport", "SS", "Africa", "south-africa/supersport-1-za.png", 0xFF003B7A, 0xFFE30613, "supersport", "super sport"),
        ch("channels_tv", "Channels Television", "CHTV", "Africa", "nigeria/channels-tv-ng.png", 0xFFC8102E, 0xFF101010, "channels television", "channels tv"),
        ch("citizen_tv", "Citizen TV", "CITZ", "Africa", "kenya/citizen-tv-ke.png", 0xFF0B5FA5, 0xFF101010, "citizen tv", "citizen"),
        ch("zee_world", "Zee World", "ZEEW", "Africa", "south-africa/zee-world-za.png", 0xFFE91E63, 0xFF1A1A1A, "zee world", "zeeworld"),
        ch("k24", "K24", "K24", "Africa", null, 0xFF00843D, 0xFF101010, "k24", "k-24"),

        // ---- Jamaica --------------------------------------------------------
        ch("tvj", "Television Jamaica", "TVJ", "Jamaica", "jamaica/tvj-jm.png", 0xFF00843D, 0xFFF5C518, "television jamaica", "tvj"),
        ch("cvm", "CVM TV", "CVM", "Jamaica", "jamaica/cvm-tv-jm.png", 0xFF0B5FA5, 0xFF101010, "cvm", "cvm tv"),
        ch("rjr", "RJR 94 FM", "RJR", "Jamaica", "jamaica/rjr-94-fm-jm.png", 0xFFC8102E, 0xFF101010, "rjr", "rjr 94"),
        ch("pbcj", "PBC Jamaica", "PBCJ", "Jamaica", null, 0xFF00843D, 0xFF101010, "pbcj", "public broadcasting jamaica"),
        ch("hype_tv", "Hype TV", "HYPE", "Jamaica", null, 0xFF7B1FA2, 0xFF101010, "hype tv", "hypetv"),

        // ---- Italy ----------------------------------------------------------
        ch("rai_1", "Rai 1", "RAI1", "Italy", "italy/rai-1-it.png", 0xFF0B5FA5, 0xFF101010, "rai 1", "rai uno"),
        ch("rai_2", "Rai 2", "RAI2", "Italy", "italy/rai-2-it.png", 0xFF0B5FA5, 0xFF101010, "rai 2", "rai due"),
        ch("rai_3", "Rai 3", "RAI3", "Italy", "italy/rai-3-it.png", 0xFF0B5FA5, 0xFF101010, "rai 3", "rai tre"),
        ch("canale_5", "Canale 5", "C5", "Italy", "italy/canale-5-it.png", 0xFFE30613, 0xFF101010, "canale 5", "canale cinque"),
        ch("italia_1", "Italia 1", "IT1", "Italy", "italy/italia-1-it.png", 0xFF1E88E5, 0xFF101010, "italia 1", "italia uno"),
        ch("rete_4", "Rete 4", "R4", "Italy", "italy/rete-4-it.png", 0xFFE30613, 0xFF101010, "rete 4", "rete quattro"),
        ch("la7", "La7", "LA7", "Italy", "italy/la7-it.png", 0xFF00843D, 0xFF101010, "la7", "la 7"),
        ch("sky_sport_italia", "Sky Sport Italia", "SKY", "Italy", "italy/sky-sport-1-it.png", 0xFF0B5FA5, 0xFF101010, "sky sport", "sky sport italia"),
        ch("dazn_italia", "DAZN Italia", "DAZN", "Italy", "italy/dazn-1-it.png", 0xFF00A651, 0xFF101010, "dazn italia", "dazn serie a"),

        // ---- France ---------------------------------------------------------
        ch("tf1", "TF1", "TF1", "France", "france/tf1-fr.png", 0xFF0B5FA5, 0xFF101010, "tf1", "tf 1"),
        ch("france_2", "France 2", "F2", "France", "france/france-2-fr.png", 0xFFE30613, 0xFF101010, "france 2", "france deux"),
        ch("france_3", "France 3", "F3", "France", "france/france-3-fr.png", 0xFF1E88E5, 0xFF101010, "france 3", "france trois"),
        ch("m6", "M6", "M6", "France", "france/m6-fr.png", 0xFFE30613, 0xFF101010, "m6", "metropole television"),
        ch("canal_plus_fr", "Canal+", "C+", "France", "france/canal-plus-fr.png", 0xFF101010, 0xFFD4AF37, "canal+", "canal plus"),
        ch("bein_sports_fr", "beIN Sports France", "BEIN", "France", "france/bein-sports-1-fr.png", 0xFF7B1FA2, 0xFF101010, "bein sports", "bein sport france"),
        ch("rmc_sport", "RMC Sport", "RMC", "France", "france/rmc-sport-1-fr.png", 0xFFE30613, 0xFF101010, "rmc sport", "rmc"),
        ch("arte_fr", "Arte", "ARTE", "France", "france/arte-fr.png", 0xFFF57C00, 0xFF101010, "arte", "arte france"),
        ch("bfm_tv", "BFM TV", "BFM", "France", "france/bfm-tv-fr.png", 0xFF0B5FA5, 0xFF101010, "bfm tv", "bfmtv"),

        // ---- China ----------------------------------------------------------
        ch("cctv_1", "CCTV-1", "CCTV1", "China", "china/cctv-1-cn.png", 0xFFC8102E, 0xFF101010, "cctv 1", "cctv-1"),
        ch("cctv_5", "CCTV-5 Sports", "CCTV5", "China", "china/cctv-5-cn.png", 0xFFC8102E, 0xFF101010, "cctv 5", "cctv-5"),
        ch("cctv_6", "CCTV-6 Movies", "CCTV6", "China", "china/cctv-6-cn.png", 0xFFC8102E, 0xFF101010, "cctv 6", "cctv-6"),
        ch("hunan_tv", "Hunan TV", "HNTV", "China", "china/hunan-tv-cn.png", 0xFFF57C00, 0xFF101010, "hunan tv", "hunan"),
        ch("dragon_tv", "Dragon TV", "DRAGON", "China", "china/dragon-tv-cn.png", 0xFFC8102E, 0xFF101010, "dragon tv", "dragon"),
        ch("phoenix_cn", "Phoenix TV", "PHX", "China", "china/phoenix-chinese-cn.png", 0xFF0B5FA5, 0xFF101010, "phoenix", "phoenix chinese"),
        ch("cgtn", "CGTN", "CGTN", "China", "china/cgtn-cn.png", 0xFF0B5FA5, 0xFF101010, "cgtn", "cctv news"),

        // ---- Japan ----------------------------------------------------------
        ch("nhk_general", "NHK General", "NHK", "Japan", "japan/nhk-japan.png", 0xFF0B5FA5, 0xFF101010, "nhk", "nhk general"),
        ch("nhk_world", "NHK World", "NHKW", "Japan", "japan/nhk-world-jp.png", 0xFF0B5FA5, 0xFF101010, "nhk world", "nhk world japan"),
        ch("ntv_jp", "Nippon TV", "NTV", "Japan", "japan/ntv-jp.png", 0xFF00843D, 0xFF101010, "nippon tv", "ntv"),
        ch("tv_asahi", "TV Asahi", "ASAHI", "Japan", "japan/tv-asahi-jp.png", 0xFFE30613, 0xFF101010, "tv asahi", "asahi"),
        ch("tbs_jp", "TBS", "TBS", "Japan", "japan/tbs-jp.png", 0xFF0B5FA5, 0xFF101010, "tbs", "tbs japan"),
        ch("fuji_tv", "Fuji TV", "FUJI", "Japan", "japan/fuji-tv-jp.png", 0xFFE30613, 0xFF101010, "fuji tv", "fuji"),
        ch("tv_tokyo", "TV Tokyo", "TVTOKYO", "Japan", "japan/tv-tokyo-jp.png", 0xFF1E88E5, 0xFF101010, "tv tokyo", "tokyo tv"),
        ch("wowow", "WOWOW", "WOWOW", "Japan", "japan/wowow-jp.png", 0xFF101010, 0xFFD4AF37, "wowow", "wowow prime"),

        // ---- Cuba ----------------------------------------------------------
        ch("cubavision", "Cubavisión", "CUBA", "Cuba", "cuba/cubavision-cu.png", 0xFFC8102E, 0xFF0B5FA5, "cubavision", "cubavisión"),
        ch("tele_rebelde", "Tele Rebelde", "REBELDE", "Cuba", "cuba/tele-rebelde-cu.png", 0xFFC8102E, 0xFF101010, "tele rebelde", "rebelde"),
        ch("canal_habana", "Canal Habana", "HABANA", "Cuba", "cuba/canal-habana-cu.png", 0xFF0B5FA5, 0xFF101010, "canal habana", "habana"),
        ch("multivision_cu", "Multivisión", "MULTI", "Cuba", "cuba/multivision-cu.png", 0xFF7B1FA2, 0xFF101010, "multivision", "multivisión"),
        ch("cubaplay", "Cubaplay", "CPLAY", "Cuba", null, 0xFFC8102E, 0xFF101010, "cubaplay", "cuba play"),

        // ---- India ----------------------------------------------------------
        ch("star_plus", "Star Plus", "STAR+", "India", "india/star-plus-in.png", 0xFF0B5FA5, 0xFF101010, "star plus", "starplus"),
        ch("zee_tv", "Zee TV", "ZEE", "India", "india/zee-tv-in.png", 0xFFF57C00, 0xFF101010, "zee tv", "zeetv"),
        ch("sony_ent", "Sony Entertainment", "SONY", "India", "india/sony-entertainment-television-in.png", 0xFF101010, 0xFFD4AF37, "sony entertainment", "set india"),
        ch("colors_in", "Colors", "COLORS", "India", "india/colors-in.png", 0xFFE91E63, 0xFF101010, "colors", "colors tv"),
        ch("star_sports_in", "Star Sports", "STARSP", "India", "india/star-sports-1-in.png", 0xFF0B5FA5, 0xFF101010, "star sports", "starsports"),

        // ---- Brazil ---------------------------------------------------------
        ch("globo", "TV Globo", "GLOBO", "Brazil", "brazil/globo-br.png", 0xFF0B5FA5, 0xFF101010, "globo", "tv globo"),
        ch("sbt", "SBT", "SBT", "Brazil", "brazil/sbt-br.png", 0xFF1E88E5, 0xFF101010, "sbt", "sistema brasileiro"),
        ch("record_tv", "Record TV", "RECORD", "Brazil", "brazil/record-tv-br.png", 0xFFC8102E, 0xFF101010, "record tv", "record"),
        ch("band", "Band", "BAND", "Brazil", "brazil/band-br.png", 0xFF00843D, 0xFF101010, "band", "band tv"),
        ch("sportv", "SporTV", "SPORTV", "Brazil", "brazil/sportv-br.png", 0xFF0B5FA5, 0xFF101010, "sportv", "sport tv"),

        // ---- Mexico ---------------------------------------------------------
        ch("canal_estrellas", "Canal de las Estrellas", "ESTRELLAS", "Mexico", "mexico/canal-de-las-estrellas-mx.png", 0xFFE30613, 0xFF101010, "canal de las estrellas", "estrellas"),
        ch("tv_azteca", "TV Azteca", "AZTECA", "Mexico", "mexico/tv-azteca-mx.png", 0xFF00843D, 0xFF101010, "tv azteca", "azteca"),
        ch("televisa", "Televisa", "TELEVISA", "Mexico", "mexico/televisa-mx.png", 0xFF0B5FA5, 0xFF101010, "televisa", "televisa mexico"),
        ch("fox_sports_mx", "Fox Sports Mexico", "FOXMX", "Mexico", "mexico/fox-sports-mx.png", 0xFF0B5FA5, 0xFF101010, "fox sports", "fox sports mexico"),

        // ---- Germany --------------------------------------------------------
        ch("ard", "ARD", "ARD", "Germany", "germany/ard-de.png", 0xFF0B5FA5, 0xFF101010, "ard", "das erste"),
        ch("zdf", "ZDF", "ZDF", "Germany", "germany/zdf-de.png", 0xFFF57C00, 0xFF101010, "zdf", "zdf hd"),
        ch("rtl_de", "RTL", "RTL", "Germany", "germany/rtl-de.png", 0xFFE30613, 0xFF101010, "rtl", "rtl germany"),
        ch("sat1_de", "Sat.1", "SAT1", "Germany", "germany/sat-1-de.png", 0xFF1E88E5, 0xFF101010, "sat 1", "sat.1"),
        ch("sky_sport_de", "Sky Sport Germany", "SKYDE", "Germany", "germany/sky-sport-de.png", 0xFF0B5FA5, 0xFF101010, "sky sport", "sky sport germany"),

        // ---- Spain ----------------------------------------------------------
        ch("tve", "TVE", "TVE", "Spain", "spain/tve-es.png", 0xFF0B5FA5, 0xFF101010, "tve", "television espanola"),
        ch("la_1", "La 1", "LA1", "Spain", "spain/la-1-es.png", 0xFFC8102E, 0xFF101010, "la 1", "la uno"),
        ch("antena_3", "Antena 3", "A3", "Spain", "spain/antena-3-es.png", 0xFFF57C00, 0xFF101010, "antena 3", "antena tres"),
        ch("telecinco", "Telecinco", "T5", "Spain", "spain/telecinco-es.png", 0xFF1E88E5, 0xFF101010, "telecinco", "tele 5"),
        ch("movistar_plus", "Movistar Plus+", "M+", "Spain", "spain/movistar-plus-es.png", 0xFF0B5FA5, 0xFF101010, "movistar plus", "movistar+"),

        // ---- South Korea ----------------------------------------------------
        ch("kbs1", "KBS 1", "KBS1", "South Korea", "south-korea/kbs-1-kr.png", 0xFF0B5FA5, 0xFF101010, "kbs 1", "kbs1"),
        ch("mbc_kr", "MBC", "MBC", "South Korea", "south-korea/mbc-kr.png", 0xFF00843D, 0xFF101010, "mbc", "mbc korea"),
        ch("sbs_kr", "SBS", "SBS", "South Korea", "south-korea/sbs-kr.png", 0xFFE30613, 0xFF101010, "sbs", "sbs korea"),
        ch("jtbc", "JTBC", "JTBC", "South Korea", "south-korea/jtbc-kr.png", 0xFF1E88E5, 0xFF101010, "jtbc", "jtbc korea"),
        ch("tvn_kr", "tvN", "TVN", "South Korea", "south-korea/tvn-kr.png", 0xFF101010, 0xFFE91E63, "tvn", "tv n"),

        // ---- Philippines ----------------------------------------------------
        ch("abs_cbn", "ABS-CBN", "ABSCBN", "Philippines", "philippines/abs-cbn-ph.png", 0xFF0B5FA5, 0xFFE30613, "abs-cbn", "abs cbn"),
        ch("gma", "GMA", "GMA", "Philippines", "philippines/gma-ph.png", 0xFF00843D, 0xFF101010, "gma", "gma network"),
        ch("tv5_ph", "TV5", "TV5", "Philippines", "philippines/tv5-ph.png", 0xFF0B5FA5, 0xFF101010, "tv5", "tv 5 philippines"),
        // ---- More Africa ----------------------------------------------------
        ch("mzansi_magic", "Mzansi Magic", "MZANSI", "Africa", "south-africa/mzansi-magic-za.png", 0xFF7B1FA2, 0xFF101010, "mzansi magic", "mzansi"),
        ch("mnet_movies", "M-Net Movies", "MNETM", "Africa", "south-africa/m-net-movies-za.png", 0xFF0A4D8C, 0xFF101010, "m-net movies", "mnet movies"),
        ch("ktn", "KTN", "KTN", "Africa", "kenya/ktn-ke.png", 0xFFC8102E, 0xFF101010, "ktn", "kenya television"),
        ch("ntv_kenya", "NTV Kenya", "NTVKE", "Africa", "kenya/ntv-ke.png", 0xFF0B5FA5, 0xFF101010, "ntv kenya", "ntv"),
        ch("tvc_nigeria", "TVC Nigeria", "TVC", "Africa", "nigeria/tvc-ng.png", 0xFF00843D, 0xFF101010, "tvc", "tvc nigeria"),
        ch("silverbird", "Silverbird TV", "SILVER", "Africa", "nigeria/silverbird-tv-ng.png", 0xFFF57C00, 0xFF101010, "silverbird", "silverbird tv"),

        // ---- More Jamaica ---------------------------------------------------
        ch("love_tv", "Love TV", "LOVE", "Jamaica", null, 0xFFE91E63, 0xFF101010, "love tv", "love"),
        ch("gospel_tv", "Gospel TV", "GOSPEL", "Jamaica", null, 0xFFD4AF37, 0xFF101010, "gospel tv", "gospel"),
        ch("jamaica_sports", "Jamaica Sports", "JSPORT", "Jamaica", null, 0xFF00843D, 0xFF101010, "jamaica sports", "sports jamaica"),

        // ---- More Italy -----------------------------------------------------
        ch("rai_movie", "Rai Movie", "RAIM", "Italy", "italy/rai-movie-it.png", 0xFF0B5FA5, 0xFF101010, "rai movie", "rai cinema"),
        ch("rai_sport", "Rai Sport", "RAIS", "Italy", "italy/rai-sport-it.png", 0xFF0B5FA5, 0xFF101010, "rai sport", "raisport"),
        ch("tv8_it", "TV8", "TV8", "Italy", "italy/tv8-it.png", 0xFF1E88E5, 0xFF101010, "tv8", "tv 8 italy"),
        ch("nove", "Nove", "NOVE", "Italy", "italy/nove-it.png", 0xFFF57C00, 0xFF101010, "nove", "canale nove"),
        ch("cielo", "Cielo", "CIELO", "Italy", "italy/cielo-it.png", 0xFF0B5FA5, 0xFF101010, "cielo", "cielo tv"),
        ch("sky_cinema_it", "Sky Cinema", "SKYC", "Italy", "italy/sky-cinema-1-it.png", 0xFF101010, 0xFFD4AF37, "sky cinema", "sky cinema italia"),

        // ---- More France ----------------------------------------------------
        ch("france_4", "France 4", "F4", "France", "france/france-4-fr.png", 0xFF7B1FA2, 0xFF101010, "france 4", "france quatre"),
        ch("france_5", "France 5", "F5", "France", "france/france-5-fr.png", 0xFF00843D, 0xFF101010, "france 5", "france cinq"),
        ch("w9", "W9", "W9", "France", "france/w9-fr.png", 0xFFE91E63, 0xFF101010, "w9", "w 9"),
        ch("tmc_fr", "TMC", "TMC", "France", "france/tmc-fr.png", 0xFF0B5FA5, 0xFF101010, "tmc", "tmc france"),
        ch("c8", "C8", "C8", "France", "france/c8-fr.png", 0xFFF57C00, 0xFF101010, "c8", "c 8"),
        ch("eurosport_fr", "Eurosport", "EURO", "France", "france/eurosport-1-fr.png", 0xFF00843D, 0xFF101010, "eurosport", "eurosport france"),

        // ---- More China -----------------------------------------------------
        ch("cctv_2", "CCTV-2 Finance", "CCTV2", "China", "china/cctv-2-cn.png", 0xFFC8102E, 0xFF101010, "cctv 2", "cctv-2"),
        ch("cctv_4", "CCTV-4 International", "CCTV4", "China", "china/cctv-4-cn.png", 0xFFC8102E, 0xFF101010, "cctv 4", "cctv-4"),
        ch("cctv_13", "CCTV-13 News", "CCTV13", "China", "china/cctv-13-cn.png", 0xFFC8102E, 0xFF101010, "cctv 13", "cctv news"),
        ch("jiangsu_tv", "Jiangsu TV", "JSTV", "China", "china/jiangsu-tv-cn.png", 0xFF1E88E5, 0xFF101010, "jiangsu tv", "jiangsu"),
        ch("zhejiang_tv", "Zhejiang TV", "ZJTV", "China", "china/zhejiang-tv-cn.png", 0xFF0B5FA5, 0xFF101010, "zhejiang tv", "zhejiang"),
        ch("beijing_tv", "Beijing TV", "BJTV", "China", "china/beijing-tv-cn.png", 0xFFC8102E, 0xFF101010, "beijing tv", "beijing"),

        // ---- More Japan -----------------------------------------------------
        ch("nhk_e", "NHK E", "NHKE", "Japan", "japan/nhk-e-jp.png", 0xFF00843D, 0xFF101010, "nhk e", "nhk educational"),
        ch("tbs_news", "TBS News", "TBSN", "Japan", "japan/tbs-news-jp.png", 0xFF0B5FA5, 0xFF101010, "tbs news", "tbs news japan"),
        ch("animax_jp", "Animax", "ANIMAX", "Japan", "japan/animax-jp.png", 0xFFE91E63, 0xFF101010, "animax", "animax japan"),
        ch("kids_station", "Kids Station", "KIDS", "Japan", "japan/kids-station-jp.png", 0xFFF57C00, 0xFF101010, "kids station", "kids"),

        // ---- More Cuba ------------------------------------------------------
        ch("canal_clave", "Canal Clave", "CLAVE", "Cuba", "cuba/canal-clave-cu.png", 0xFFE91E63, 0xFF101010, "canal clave", "clave"),
        ch("canal_caribe", "Canal Caribe", "CARIBE", "Cuba", "cuba/canal-caribe-cu.png", 0xFF0B5FA5, 0xFF101010, "canal caribe", "caribe"),
        ch("tele_cristal", "Tele Cristal", "CRISTAL", "Cuba", null, 0xFF00843D, 0xFF101010, "tele cristal", "cristal"),

        // ---- More India -----------------------------------------------------
        ch("star_gold", "Star Gold", "GOLD", "India", "india/star-gold-in.png", 0xFFD4AF37, 0xFF101010, "star gold", "stargold"),
        ch("zee_cinema", "Zee Cinema", "ZEEC", "India", "india/zee-cinema-in.png", 0xFFF57C00, 0xFF101010, "zee cinema", "zeecinema"),
        ch("sony_max", "Sony Max", "MAX", "India", "india/sony-max-in.png", 0xFF0B5FA5, 0xFF101010, "sony max", "sonymax"),
        ch("sab_tv", "SAB TV", "SAB", "India", "india/sab-tv-in.png", 0xFF7B1FA2, 0xFF101010, "sab tv", "sab"),
        ch("aaj_tak", "Aaj Tak", "AAJ", "India", "india/aaj-tak-in.png", 0xFFC8102E, 0xFF101010, "aaj tak", "aajtak"),
        ch("ndtv", "NDTV", "NDTV", "India", "india/ndtv-in.png", 0xFF0B5FA5, 0xFF101010, "ndtv", "ndtv india"),
        ch("star_sports_2", "Star Sports 2", "STAR2", "India", "india/star-sports-2-in.png", 0xFF0B5FA5, 0xFF101010, "star sports 2", "starsports2"),

        // ---- More Brazil ----------------------------------------------------
        ch("globo_news", "GloboNews", "GNEWS", "Brazil", "brazil/globo-news-br.png", 0xFF0B5FA5, 0xFF101010, "globonews", "globo news"),
        ch("bandsports", "BandSports", "BSPORT", "Brazil", "brazil/bandsports-br.png", 0xFF00843D, 0xFF101010, "bandsports", "band sports"),
        ch("espn_brasil", "ESPN Brasil", "ESPNBR", "Brazil", "brazil/espn-br.png", 0xFFC8102E, 0xFF101010, "espn brasil", "espn brazil"),
        ch("record_news", "Record News", "RNEWS", "Brazil", "brazil/record-news-br.png", 0xFFE30613, 0xFF101010, "record news", "recordnews"),
        ch("redetv", "RedeTV", "REDETV", "Brazil", "brazil/redetv-br.png", 0xFF7B1FA2, 0xFF101010, "redetv", "rede tv"),

        // ---- More Mexico ----------------------------------------------------
        ch("canal_5_mx", "Canal 5", "C5MX", "Mexico", "mexico/canal-5-mx.png", 0xFF0B5FA5, 0xFF101010, "canal 5", "canal cinco"),
        ch("azteca_7", "Azteca 7", "A7", "Mexico", "mexico/azteca-7-mx.png", 0xFF00843D, 0xFF101010, "azteca 7", "azteca siete"),
        ch("imagen_tv", "Imagen TV", "IMAGEN", "Mexico", "mexico/imagen-tv-mx.png", 0xFFE91E63, 0xFF101010, "imagen tv", "imagen"),
        ch("tudn", "TUDN", "TUDN", "Mexico", "mexico/tudn-mx.png", 0xFF0B5FA5, 0xFF101010, "tudn", "televisa deportes"),

        // ---- More Germany ---------------------------------------------------
        ch("prosieben", "ProSieben", "P7", "Germany", "germany/prosieben-de.png", 0xFFE91E63, 0xFF101010, "prosieben", "pro 7"),
        ch("vox_de", "VOX", "VOX", "Germany", "germany/vox-de.png", 0xFFC8102E, 0xFF101010, "vox", "vox germany"),
        ch("rtl2", "RTL II", "RTL2", "Germany", "germany/rtl-2-de.png", 0xFF0B5FA5, 0xFF101010, "rtl 2", "rtl2"),
        ch("sport1", "Sport1", "SPORT1", "Germany", "germany/sport1-de.png", 0xFF00843D, 0xFF101010, "sport1", "sport 1"),

        // ---- More Spain -----------------------------------------------------
        ch("cuatro", "Cuatro", "CUATRO", "Spain", "spain/cuatro-es.png", 0xFF0B5FA5, 0xFF101010, "cuatro", "cuatro tv"),
        ch("la_sexta", "La Sexta", "SEXTA", "Spain", "spain/la-sexta-es.png", 0xFF00843D, 0xFF101010, "la sexta", "sexta"),
        ch("movistar_deportes", "Movistar Deportes", "MDEP", "Spain", "spain/movistar-deportes-es.png", 0xFF0B5FA5, 0xFF101010, "movistar deportes", "movistar"),
        ch("dazn_laliga", "DAZN LaLiga", "DAZN", "Spain", "spain/dazn-laliga-es.png", 0xFF00A651, 0xFF101010, "dazn laliga", "dazn spain"),

        // ---- More South Korea ----------------------------------------------
        ch("kbs2", "KBS 2", "KBS2", "South Korea", "south-korea/kbs-2-kr.png", 0xFF0B5FA5, 0xFF101010, "kbs 2", "kbs2"),
        ch("sbs_sports", "SBS Sports", "SBSS", "South Korea", "south-korea/sbs-sports-kr.png", 0xFFE30613, 0xFF101010, "sbs sports", "sbs sport"),
        ch("ytn", "YTN", "YTN", "South Korea", "south-korea/ytn-kr.png", 0xFF0B5FA5, 0xFF101010, "ytn", "ytn news"),
        ch("mbc_every1", "MBC Every1", "MBCE", "South Korea", "south-korea/mbc-every1-kr.png", 0xFF00843D, 0xFF101010, "mbc every1", "every1"),

        // ---- More Philippines ----------------------------------------------
        ch("gma_news", "GMA News TV", "GMAN", "Philippines", "philippines/gma-news-tv-ph.png", 0xFF00843D, 0xFF101010, "gma news", "gma news tv"),
        ch("cnn_philippines", "CNN Philippines", "CNNPH", "Philippines", "philippines/cnn-philippines-ph.png", 0xFFC8102E, 0xFF101010, "cnn philippines", "cnn ph"),
        ch("solar_sports", "Solar Sports", "SOLAR", "Philippines", "philippines/solar-sports-ph.png", 0xFFF57C00, 0xFF101010, "solar sports", "solar"),
        ch("pba_rush", "PBA Rush", "PBA", "Philippines", "philippines/pba-rush-ph.png", 0xFF0B5FA5, 0xFF101010, "pba rush", "pba"),

        // ---- Ireland --------------------------------------------------------
        ch("rte_one", "RTÉ One", "RTE1", "Ireland", "ireland/rte-one-ie.png", 0xFF00843D, 0xFF101010, "rte one", "rte 1"),
        ch("rte_two", "RTÉ Two", "RTE2", "Ireland", "ireland/rte-two-ie.png", 0xFF0B5FA5, 0xFF101010, "rte two", "rte 2"),
        ch("virgin_media_one", "Virgin Media One", "VM1", "Ireland", "ireland/virgin-media-one-ie.png", 0xFFC8102E, 0xFF101010, "virgin media one", "virgin one"),
        ch("tg4", "TG4", "TG4", "Ireland", "ireland/tg4-ie.png", 0xFF7B1FA2, 0xFF101010, "tg4", "tg 4"),

        // ---- Australia ------------------------------------------------------
        ch("abc_au", "ABC", "ABC", "Australia", "australia/abc-au.png", 0xFF0B5FA5, 0xFF101010, "abc australia", "abc"),
        ch("seven_au", "Channel 7", "7", "Australia", "australia/seven-au.png", 0xFFC8102E, 0xFF101010, "channel 7", "seven"),
        ch("nine_au", "Channel 9", "9", "Australia", "australia/nine-au.png", 0xFF0B5FA5, 0xFF101010, "channel 9", "nine"),
        ch("ten_au", "Channel 10", "10", "Australia", "australia/ten-au.png", 0xFF1E88E5, 0xFF101010, "channel 10", "ten"),
        ch("fox_sports_au", "Fox Sports Australia", "FOXAU", "Australia", "australia/fox-sports-au.png", 0xFF0B5FA5, 0xFF101010, "fox sports australia", "fox sports au"),

        // ---- New Zealand ----------------------------------------------------
        ch("tvnz_1", "TVNZ 1", "TVNZ1", "New Zealand", "new-zealand/tvnz-1-nz.png", 0xFF0B5FA5, 0xFF101010, "tvnz 1", "tvnz one"),
        ch("tvnz_2", "TVNZ 2", "TVNZ2", "New Zealand", "new-zealand/tvnz-2-nz.png", 0xFF00843D, 0xFF101010, "tvnz 2", "tvnz two"),
        ch("three_nz", "Three", "3NZ", "New Zealand", "new-zealand/three-nz.png", 0xFFE91E63, 0xFF101010, "three new zealand", "three"),
        ch("sky_sport_nz", "Sky Sport NZ", "SKYNZ", "New Zealand", "new-zealand/sky-sport-nz.png", 0xFF0B5FA5, 0xFF101010, "sky sport nz", "sky sport new zealand"),

        // ---- Portugal -------------------------------------------------------
        ch("rtp1", "RTP1", "RTP1", "Portugal", "portugal/rtp-1-pt.png", 0xFF00843D, 0xFF101010, "rtp1", "rtp 1"),
        ch("rtp2", "RTP2", "RTP2", "Portugal", "portugal/rtp-2-pt.png", 0xFF0B5FA5, 0xFF101010, "rtp2", "rtp 2"),
        ch("sic", "SIC", "SIC", "Portugal", "portugal/sic-pt.png", 0xFFC8102E, 0xFF101010, "sic", "sic portugal"),
        ch("tvi", "TVI", "TVI", "Portugal", "portugal/tvi-pt.png", 0xFF1E88E5, 0xFF101010, "tvi", "tvi portugal"),
        ch("sporttv", "Sport TV", "SPORTTV", "Portugal", "portugal/sport-tv-1-pt.png", 0xFF0B5FA5, 0xFF101010, "sport tv", "sporttv"),

        // ---- Netherlands ----------------------------------------------------
        ch("nos", "NOS", "NOS", "Netherlands", "netherlands/nos-nl.png", 0xFFF57C00, 0xFF101010, "nos", "nos netherlands"),
        ch("rtl4", "RTL 4", "RTL4", "Netherlands", "netherlands/rtl-4-nl.png", 0xFF0B5FA5, 0xFF101010, "rtl 4", "rtl4"),
        ch("sbs6", "SBS 6", "SBS6", "Netherlands", "netherlands/sbs-6-nl.png", 0xFFE91E63, 0xFF101010, "sbs 6", "sbs6"),
        ch("ziggo_sport", "Ziggo Sport", "ZIGGO", "Netherlands", "netherlands/ziggo-sport-nl.png", 0xFF00843D, 0xFF101010, "ziggo sport", "ziggo"),

        // ---- Sweden ---------------------------------------------------------
        ch("svt1", "SVT1", "SVT1", "Sweden", "sweden/svt-1-se.png", 0xFF0B5FA5, 0xFF101010, "svt1", "svt 1"),
        ch("svt2", "SVT2", "SVT2", "Sweden", "sweden/svt-2-se.png", 0xFF00843D, 0xFF101010, "svt2", "svt 2"),
        ch("tv4_se", "TV4", "TV4", "Sweden", "sweden/tv-4-se.png", 0xFF1E88E5, 0xFF101010, "tv4", "tv 4 sweden"),
        ch("viaplay_sport", "Viaplay Sport", "VIAP", "Sweden", "sweden/viaplay-sport-se.png", 0xFFE30613, 0xFF101010, "viaplay sport", "viaplay"),

        // ---- Norway ---------------------------------------------------------
        ch("nrk1", "NRK1", "NRK1", "Norway", "norway/nrk-1-no.png", 0xFFC8102E, 0xFF101010, "nrk1", "nrk 1"),
        ch("nrk2", "NRK2", "NRK2", "Norway", "norway/nrk-2-no.png", 0xFF0B5FA5, 0xFF101010, "nrk2", "nrk 2"),
        ch("tv2_no", "TV 2", "TV2", "Norway", "norway/tv-2-no.png", 0xFF00843D, 0xFF101010, "tv2", "tv 2 norway"),
        ch("viasat_sport", "Viasat Sport", "VIASAT", "Norway", "norway/viasat-sport-no.png", 0xFF1E88E5, 0xFF101010, "viasat sport", "viasat"),

        // ---- Denmark --------------------------------------------------------
        ch("dr1", "DR1", "DR1", "Denmark", "denmark/dr-1-dk.png", 0xFFC8102E, 0xFF101010, "dr1", "dr 1"),
        ch("dr2", "DR2", "DR2", "Denmark", "denmark/dr-2-dk.png", 0xFF0B5FA5, 0xFF101010, "dr2", "dr 2"),
        ch("tv2_dk", "TV 2 Danmark", "TV2DK", "Denmark", "denmark/tv-2-dk.png", 0xFF00843D, 0xFF101010, "tv2 danmark", "tv 2 denmark"),

        // ---- Poland ---------------------------------------------------------
        ch("tvn_pl", "TVN", "TVN", "Poland", "poland/tvn-pl.png", 0xFF0B5FA5, 0xFF101010, "tvn", "tvn poland"),
        ch("polsat", "Polsat", "POLSAT", "Poland", "poland/polsat-pl.png", 0xFFF57C00, 0xFF101010, "polsat", "polsat tv"),
        ch("tvp1", "TVP1", "TVP1", "Poland", "poland/tvp-1-pl.png", 0xFFC8102E, 0xFF101010, "tvp1", "tvp 1"),
        ch("canal_sport_pl", "Canal+ Sport", "CSPORT", "Poland", "poland/canal-plus-sport-pl.png", 0xFF101010, 0xFFD4AF37, "canal sport", "canal+ sport"),

        // ---- Turkey ---------------------------------------------------------
        ch("trt1", "TRT 1", "TRT1", "Turkey", "turkey/trt-1-tr.png", 0xFF0B5FA5, 0xFF101010, "trt 1", "trt1"),
        ch("trt_haber", "TRT Haber", "TRTH", "Turkey", "turkey/trt-haber-tr.png", 0xFFC8102E, 0xFF101010, "trt haber", "trt news"),
        ch("atv_tr", "ATV", "ATV", "Turkey", "turkey/atv-tr.png", 0xFF00843D, 0xFF101010, "atv", "atv turkey"),
        ch("show_tv", "Show TV", "SHOW", "Turkey", "turkey/show-tv-tr.png", 0xFFE91E63, 0xFF101010, "show tv", "show"),
        ch("beinsports_tr", "beIN Sports Turkey", "BEINTR", "Turkey", "turkey/bein-sports-1-tr.png", 0xFF7B1FA2, 0xFF101010, "bein sports turkey", "bein tr"),

        // ---- Greece ---------------------------------------------------------
        ch("ert1", "ERT1", "ERT1", "Greece", "greece/ert-1-gr.png", 0xFF0B5FA5, 0xFF101010, "ert1", "ert 1"),
        ch("ert2", "ERT2", "ERT2", "Greece", "greece/ert-2-gr.png", 0xFF00843D, 0xFF101010, "ert2", "ert 2"),
        ch("mega_gr", "Mega", "MEGA", "Greece", "greece/mega-gr.png", 0xFFE30613, 0xFF101010, "mega", "mega greece"),
        ch("skai", "Skai", "SKAI", "Greece", "greece/skai-gr.png", 0xFFF57C00, 0xFF101010, "skai", "skai tv"),

        // ---- Pakistan -------------------------------------------------------
        ch("geo_tv", "Geo TV", "GEO", "Pakistan", "pakistan/geo-tv-pk.png", 0xFF00843D, 0xFF101010, "geo tv", "geo"),
        ch("ary_digital", "ARY Digital", "ARY", "Pakistan", "pakistan/ary-digital-pk.png", 0xFF0B5FA5, 0xFF101010, "ary digital", "ary"),
        ch("hum_tv", "Hum TV", "HUM", "Pakistan", "pakistan/hum-tv-pk.png", 0xFFE91E63, 0xFF101010, "hum tv", "hum"),
        ch("geo_super", "Geo Super", "GEOS", "Pakistan", "pakistan/geo-super-pk.png", 0xFFF57C00, 0xFF101010, "geo super", "geosuper"),

        // ---- Bangladesh -----------------------------------------------------
        ch("btv", "BTV", "BTV", "Bangladesh", "bangladesh/btv-bd.png", 0xFF00843D, 0xFF101010, "btv", "bangladesh television"),
        ch("channel_i", "Channel i", "CI", "Bangladesh", "bangladesh/channel-i-bd.png", 0xFF0B5FA5, 0xFF101010, "channel i", "channeli"),
        ch("ntv_bd", "NTV", "NTVBD", "Bangladesh", "bangladesh/ntv-bd.png", 0xFFE30613, 0xFF101010, "ntv bangladesh", "ntv bd"),
        ch("somoy_tv", "Somoy TV", "SOMOY", "Bangladesh", "bangladesh/somoy-tv-bd.png", 0xFFF57C00, 0xFF101010, "somoy tv", "somoy"),

        // ---- Indonesia ------------------------------------------------------
        ch("trans7", "Trans7", "TRANS7", "Indonesia", "indonesia/trans-7-id.png", 0xFF0B5FA5, 0xFF101010, "trans7", "trans 7"),
        ch("trans_tv", "Trans TV", "TRANSTV", "Indonesia", "indonesia/trans-tv-id.png", 0xFF00843D, 0xFF101010, "trans tv", "transtv"),
        ch("sctv", "SCTV", "SCTV", "Indonesia", "indonesia/sctv-id.png", 0xFFE30613, 0xFF101010, "sctv", "surya citra"),
        ch("mnc_tv", "MNC TV", "MNC", "Indonesia", "indonesia/mnc-tv-id.png", 0xFFF57C00, 0xFF101010, "mnc tv", "mnctv"),

        // ---- Malaysia -------------------------------------------------------
        ch("tv3_my", "TV3", "TV3", "Malaysia", "malaysia/tv-3-my.png", 0xFF0B5FA5, 0xFF101010, "tv3", "tv 3 malaysia"),
        ch("ntv7", "NTV7", "NTV7", "Malaysia", "malaysia/ntv-7-my.png", 0xFF00843D, 0xFF101010, "ntv7", "ntv 7"),
        ch("astro_awani", "Astro Awani", "AWANI", "Malaysia", "malaysia/astro-awani-my.png", 0xFFE30613, 0xFF101010, "astro awani", "awani"),
        ch("astro_supersport", "Astro SuperSport", "ASSP", "Malaysia", "malaysia/astro-supersport-my.png", 0xFFF57C00, 0xFF101010, "astro supersport", "supersport malaysia"),

        // ---- Thailand -------------------------------------------------------
        ch("thai_pbs", "Thai PBS", "TPBS", "Thailand", "thailand/thai-pbs-th.png", 0xFF0B5FA5, 0xFF101010, "thai pbs", "thaipbs"),
        ch("ch3_th", "Channel 3", "CH3", "Thailand", "thailand/channel-3-th.png", 0xFF00843D, 0xFF101010, "channel 3", "ch3"),
        ch("ch7_th", "Channel 7", "CH7", "Thailand", "thailand/channel-7-th.png", 0xFFE30613, 0xFF101010, "channel 7 thailand", "ch7"),
        ch("true_sport", "True Sport", "TRUES", "Thailand", "thailand/true-sport-th.png", 0xFFF57C00, 0xFF101010, "true sport", "truesport"),

        // ---- Vietnam --------------------------------------------------------
        ch("vtv1", "VTV1", "VTV1", "Vietnam", "vietnam/vtv-1-vn.png", 0xFFC8102E, 0xFF101010, "vtv1", "vtv 1"),
        ch("vtv3", "VTV3", "VTV3", "Vietnam", "vietnam/vtv-3-vn.png", 0xFF0B5FA5, 0xFF101010, "vtv3", "vtv 3"),
        ch("htv7", "HTV7", "HTV7", "Vietnam", "vietnam/htv-7-vn.png", 0xFF00843D, 0xFF101010, "htv7", "htv 7"),
        ch("vtv_cab", "VTV Cab", "VTVCAB", "Vietnam", "vietnam/vtv-cab-vn.png", 0xFFF57C00, 0xFF101010, "vtv cab", "vtvcab"),

        // ---- Singapore ------------------------------------------------------
        ch("mediacorp_ch5", "Channel 5", "CH5", "Singapore", "singapore/channel-5-sg.png", 0xFF0B5FA5, 0xFF101010, "channel 5 singapore", "ch5"),
        ch("channel_8", "Channel 8", "CH8", "Singapore", "singapore/channel-8-sg.png", 0xFFE30613, 0xFF101010, "channel 8", "ch8"),
        ch("suria", "Suria", "SURIA", "Singapore", "singapore/suria-sg.png", 0xFF00843D, 0xFF101010, "suria", "suria sg"),
        ch("vasantham", "Vasantham", "VAS", "Singapore", "singapore/vasantham-sg.png", 0xFFF57C00, 0xFF101010, "vasantham", "vasantham tv"),

        // ---- UAE ------------------------------------------------------------
        ch("dubai_tv", "Dubai TV", "DUBAI", "UAE", "uae/dubai-tv-ae.png", 0xFF0B5FA5, 0xFF101010, "dubai tv", "dubai"),
        ch("abu_dhabi_tv", "Abu Dhabi TV", "ADTV", "UAE", "uae/abu-dhabi-tv-ae.png", 0xFF00843D, 0xFF101010, "abu dhabi tv", "abu dhabi"),
        ch("al_arabiya", "Al Arabiya", "ARABIYA", "UAE", "uae/al-arabiya-ae.png", 0xFFC8102E, 0xFF101010, "al arabiya", "arabiya"),
        ch("mbc1", "MBC 1", "MBC1", "UAE", "uae/mbc-1-ae.png", 0xFFF57C00, 0xFF101010, "mbc1", "mbc 1"),

        // ---- Saudi Arabia ---------------------------------------------------
        ch("saudi_tv1", "Saudi TV 1", "SBC", "Saudi Arabia", "saudi-arabia/saudi-tv-1-sa.png", 0xFF00843D, 0xFF101010, "saudi tv", "sbc"),
        ch("al_ekhbariya", "Al Ekhbariya", "EKHB", "Saudi Arabia", "saudi-arabia/al-ekhbariya-sa.png", 0xFF0B5FA5, 0xFF101010, "al ekhbariya", "ekhbariya"),
        ch("mbc2", "MBC 2", "MBC2", "Saudi Arabia", "saudi-arabia/mbc-2-sa.png", 0xFFE30613, 0xFF101010, "mbc2", "mbc 2"),
        ch("ssc_sport", "SSC Sport", "SSC", "Saudi Arabia", "saudi-arabia/ssc-1-sa.png", 0xFFF57C00, 0xFF101010, "ssc sport", "ssc"),

        // ---- Israel ---------------------------------------------------------
        ch("kan_11", "Kan 11", "KAN11", "Israel", "israel/kan-11-il.png", 0xFF0B5FA5, 0xFF101010, "kan 11", "kan"),
        ch("keshet_12", "Keshet 12", "K12", "Israel", "israel/keshet-12-il.png", 0xFF00843D, 0xFF101010, "keshet 12", "channel 12"),
        ch("reshet_13", "Reshet 13", "R13", "Israel", "israel/reshet-13-il.png", 0xFFE30613, 0xFF101010, "reshet 13", "channel 13"),
        ch("sport5", "Sport5", "SPORT5", "Israel", "israel/sport-5-il.png", 0xFFF57C00, 0xFF101010, "sport5", "sport 5"),

        // ---- Argentina ------------------------------------------------------
        ch("eltrece", "eltrece", "ELTRECE", "Argentina", "argentina/eltrece-ar.png", 0xFF0B5FA5, 0xFF101010, "eltrece", "el trece"),
        ch("telefe", "Telefe", "TELEFE", "Argentina", "argentina/telefe-ar.png", 0xFF00843D, 0xFF101010, "telefe", "telefe argentina"),
        ch("tv_publica_ar", "TV Pública", "TVP", "Argentina", "argentina/tv-publica-ar.png", 0xFFE30613, 0xFF101010, "tv publica", "tv publica argentina"),
        ch("tyc_sports", "TyC Sports", "TYC", "Argentina", "argentina/tyc-sports-ar.png", 0xFFF57C00, 0xFF101010, "tyc sports", "tyc"),

        // ---- Colombia -------------------------------------------------------
        ch("caracol", "Caracol TV", "CARACOL", "Colombia", "colombia/caracol-tv-co.png", 0xFF0B5FA5, 0xFF101010, "caracol", "caracol tv"),
        ch("rcn", "RCN TV", "RCN", "Colombia", "colombia/rcn-tv-co.png", 0xFF00843D, 0xFF101010, "rcn", "rcn tv"),
        ch("canal_1", "Canal 1", "C1", "Colombia", "colombia/canal-1-co.png", 0xFFE30613, 0xFF101010, "canal 1", "canal uno"),
        ch("win_sports", "Win Sports", "WIN", "Colombia", "colombia/win-sports-co.png", 0xFFF57C00, 0xFF101010, "win sports", "winsports"),

        // ---- Chile ----------------------------------------------------------
        ch("tvn_cl", "TVN", "TVN", "Chile", "chile/tvn-cl.png", 0xFF0B5FA5, 0xFF101010, "tvn chile", "tvn"),
        ch("canal_13", "Canal 13", "C13", "Chile", "chile/canal-13-cl.png", 0xFF00843D, 0xFF101010, "canal 13", "canal trece"),
        ch("mega_cl", "Mega", "MEGA", "Chile", "chile/mega-cl.png", 0xFFE30613, 0xFF101010, "mega chile", "mega"),
        ch("tnt_sports_cl", "TNT Sports", "TNT", "Chile", "chile/tnt-sports-cl.png", 0xFFF57C00, 0xFF101010, "tnt sports", "tnt sports chile"),

        // ---- Peru -----------------------------------------------------------
        ch("america_tv", "América TV", "AMERICA", "Peru", "peru/america-tv-pe.png", 0xFF0B5FA5, 0xFF101010, "america tv", "america television"),
        ch("latina", "Latina", "LATINA", "Peru", "peru/latina-pe.png", 0xFFE30613, 0xFF101010, "latina", "latina tv"),
        ch("tv_peru", "TV Perú", "TVPE", "Peru", "peru/tv-peru-pe.png", 0xFF00843D, 0xFF101010, "tv peru", "tv perú"),

        // ---- Venezuela ------------------------------------------------------
        ch("venevision", "Venevisión", "VENEV", "Venezuela", "venezuela/venevision-ve.png", 0xFF0B5FA5, 0xFF101010, "venevision", "venevición"),
        ch("televen", "Televen", "TELEVEN", "Venezuela", "venezuela/televen-ve.png", 0xFF00843D, 0xFF101010, "televen", "televen tv"),
        ch("vtv_ve", "VTV", "VTVVE", "Venezuela", "venezuela/vtv-ve.png", 0xFFE30613, 0xFF101010, "vtv venezuela", "venezolana de television"),

        // ---- Dominican Republic --------------------------------------------
        ch("color_vision", "Color Visión", "CV", "Dominican Republic", "dominican-republic/color-vision-do.png", 0xFF0B5FA5, 0xFF101010, "color vision", "colorvision"),
        ch("telemicro", "Telemicro", "TELEMICRO", "Dominican Republic", "dominican-republic/telemicro-do.png", 0xFF00843D, 0xFF101010, "telemicro", "tele micro"),
        ch("teleantillas", "Teleantillas", "TANT", "Dominican Republic", "dominican-republic/teleantillas-do.png", 0xFFE30613, 0xFF101010, "teleantillas", "tele antillas"),

    )
}
