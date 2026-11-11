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
    )
}
