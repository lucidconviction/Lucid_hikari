package com.hikari.app.iptv

/**
 * The 0.12.5 Channel Wall expansion.
 *
 * The 0.12.4 wall covered the big sports and news networks; this shelf adds the
 * channels people actually keep in their playlists next to them: music, anime,
 * lifestyle, premium movie feeds, the missing broadcasters, and the second-
 * tier sports networks. Kept in its own file so the original curated set stays
 * readable, and appended by [ChannelWallChannels.all] like the country list.
 *
 * Every entry is a search pattern, not a stream (see [WallChannel]), and a
 * missing logo is deliberate: [com.hikari.app.ui.screens.WallFullBleedArt]
 * paints a generated initials poster for it, so a dead logo URL can never
 * leave a blank tile.
 */
object ChannelWallExpansion {

    private const val LOGO_BASE =
        "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/"

    private fun palette(category: String): Pair<Long, Long> = when (category) {
        "Music" -> 0xFF7C3AEDL to 0xFF160B26L
        "Anime" -> 0xFFEC4899L to 0xFF1A0A14L
        "Lifestyle" -> 0xFFF59E0BL to 0xFF1A1406L
        "Black Media" -> 0xFF7C3AEDL to 0xFF120A1FL
        "Adult" -> 0xFFE11D48L to 0xFF1A060EL
        "Movies" -> 0xFFB8860BL to 0xFF12100AL
        "News" -> 0xFF0EA5E9L to 0xFF07131AL
        "Sports" -> 0xFF2563EBL to 0xFF070F1FL
        "Int. Sports" -> 0xFF16A34AL to 0xFF06140CL
        "Kids" -> 0xFFF97316L to 0xFF1A0E06L
        "Discovery" -> 0xFF0891B2L to 0xFF061418L
        "US" -> 0xFF1D4ED8L to 0xFF070C18L
        "UK" -> 0xFFDC2626L to 0xFF180707L
        "CA" -> 0xFFE11D48L to 0xFF18070AL
        "Soccer" -> 0xFF22C55EL to 0xFF06140AL
        "Combat" -> 0xFFB91C1CL to 0xFF160606L
        "Racing" -> 0xFFEAB308L to 0xFF161205L
        else -> 0xFF334155L to 0xFF0B0B10L
    }

    private fun ch(
        id: String,
        name: String,
        short: String,
        category: String,
        logo: String?,
        vararg keywords: String,
    ): WallChannel {
        val (top, bottom) = palette(category)
        return WallChannel(
            id = "x_$id",
            name = name,
            short = short,
            category = category,
            keywords = keywords.toList().ifEmpty { listOf(name.lowercase()) },
            exclude = emptyList(),
            gradient = listOf(top, bottom),
            iconUrl = logo?.let { LOGO_BASE + it },
            backdropUrl = logo?.let { LOGO_BASE + it },
        )
    }

    val all: List<WallChannel> = listOf(
        // ---- Music ----------------------------------------------------------
        ch("mtv", "MTV", "MTV", "Music", "united-states/mtv-us.png", "mtv", "music television"),
        ch("mtv2", "MTV2", "MTV2", "Music", "united-states/mtv-2-us.png", "mtv2", "mtv 2"),
        ch("mtv_classic", "MTV Classic", "MTVC", "Music", "united-states/mtv-classic-us.png", "mtv classic", "vh1 classic"),
        ch("mtv_live", "MTV Live", "MTVL", "Music", "united-states/mtv-live-us.png", "mtv live", "mtv live hd"),
        ch("vh1", "VH1", "VH1", "Music", "united-states/vh1-us.png", "vh1", "vh-1"),
        ch("vh1_classic", "VH1 Classic", "VH1C", "Music", null, "vh1 classic"),
        ch("bet", "BET", "BET", "Music", "united-states/bet-us.png", "bet", "black entertainment"),
        ch("bet_her", "BET Her", "BETH", "Music", "united-states/bet-her-us.png", "bet her", "bether"),
        ch("bet_soul", "BET Soul", "BETS", "Music", "united-states/bet-soul-us.png", "bet soul"),
        ch("bet_jams", "BET Jams", "BETJ", "Music", "united-states/bet-jams-us.png", "bet jams"),
        ch("cmt", "CMT", "CMT", "Music", "united-states/cmt-us.png", "cmt", "country music television"),
        ch("cmt_music", "CMT Music", "CMTM", "Music", "united-states/cmt-music-us.png", "cmt music"),
        ch("axs_tv", "AXS TV", "AXS", "Music", "united-states/axs-tv-us.png", "axs tv", "axs"),
        ch("fuse", "Fuse", "FUSE", "Music", "united-states/fuse-us.png", "fuse", "fuse tv"),
        ch("revolt", "Revolt", "RVLT", "Music", "united-states/revolt-us.png", "revolt", "revolt tv"),
        ch("nick_music", "Nick Music", "NICKM", "Music", "united-states/nick-music-us.png", "nick music", "nickmusic"),
        ch("much", "Much", "MUCH", "Music", "canada/much-ca.png", "much", "muchmusic", "much music"),
        ch("mtv_canada", "MTV Canada", "MTVCA", "Music", "canada/mtv-ca.png", "mtv canada", "mtv ca"),
        ch("4music", "4Music", "4MUS", "Music", null, "4music", "4 music"),
        ch("kiss_tv", "Kiss TV", "KISS", "Music", "united-kingdom/kiss-tv-uk.png", "kiss tv", "kiss"),
        ch("magic_tv", "Magic TV", "MAGC", "Music", "united-kingdom/magic-tv-uk.png", "magic tv", "magic"),
        ch("now_80s", "Now 80s", "NOW80", "Music", "united-kingdom/now-80s-uk.png", "now 80s", "now80s"),
        ch("now_90s", "Now 90s", "NOW90", "Music", "united-kingdom/now-90s-uk.png", "now 90s", "now90s"),
        ch("sky_arts", "Sky Arts", "ARTS", "Music", "united-kingdom/sky-arts-uk.png", "sky arts", "sky arts hd"),
        ch("clubland_tv", "Clubland TV", "CLUB", "Music", "united-kingdom/clubland-uk.png", "clubland", "clubland tv"),

        // ---- Anime ----------------------------------------------------------
        ch("adult_swim", "Adult Swim", "AS", "Anime", "united-states/adult-swim-us.png", "adult swim", "adultswim"),
        ch("toonami", "Toonami", "TOON", "Anime", "united-states/toonami-us.png", "toonami"),
        ch("boomerang", "Boomerang", "BOOM", "Anime", "united-states/boomerang-us.png", "boomerang"),
        ch("disney_xd", "Disney XD", "DXD", "Anime", "united-states/disney-xd-us.png", "disney xd", "disneyxd"),
        ch("nicktoons", "Nicktoons", "NTOON", "Anime", null, "nicktoons", "nick toons"),
        ch("teennick", "TeenNick", "TNICK", "Anime", "united-states/teen-nick-us.png", "teennick", "teen nick"),
        ch("animax", "Animax", "ANMX", "Anime", null, "animax"),
        ch("anime_network", "Anime Network", "ANIME", "Anime", "united-states/anime-network-us.png", "anime network", "animenetwork"),
        ch("crunchyroll", "Crunchyroll", "CRUN", "Anime", null, "crunchyroll", "crunchy roll"),
        ch("hidive", "HIDIVE", "HIDV", "Anime", null, "hidive", "hi dive"),
        ch("retro_crush", "Retro Crush", "RCRU", "Anime", null, "retro crush", "retrocrush"),
        ch("anime_central", "Anime Central", "ACEN", "Anime", null, "anime central", "animecentral"),
        ch("pop", "POP", "POP", "Anime", "united-kingdom/pop-uk.png", "pop tv", "pop"),
        ch("pop_max", "POP Max", "POPMX", "Anime", "united-kingdom/pop-max-uk.png", "pop max", "popmax"),
        ch("tiny_pop", "Tiny POP", "TPOP", "Anime", "united-kingdom/tiny-pop-uk.png", "tiny pop", "tinypop"),

        // ---- Lifestyle ------------------------------------------------------
        ch("food_network", "Food Network", "FOOD", "Lifestyle", "united-states/food-network-us.png", "food network", "foodnetwork"),
        ch("hgtv", "HGTV", "HGTV", "Lifestyle", "united-states/hgtv-us.png", "hgtv", "home garden"),
        ch("tlc", "TLC", "TLC", "Lifestyle", "united-states/tlc-us.png", "tlc", "the learning channel"),
        ch("travel_channel", "Travel Channel", "TRVL", "Lifestyle", "united-states/travel-channel-us.png", "travel channel", "travelchannel"),
        ch("cooking_channel", "Cooking Channel", "COOK", "Lifestyle", "united-states/cooking-channel-us.png", "cooking channel", "cookingchannel"),
        ch("magnolia_network", "Magnolia Network", "MAGN", "Lifestyle", "united-states/magnolia-network-us.png", "magnolia network", "magnolia"),
        ch("diy_network", "DIY Network", "DIY", "Lifestyle", "canada/diy-network-ca.png", "diy network", "diy"),
        ch("fyi", "FYI", "FYI", "Lifestyle", "united-states/fyi-us.png", "fyi", "fyi network"),
        ch("destination_america", "Destination America", "DEST", "Lifestyle", "united-states/destination-america-us.png", "destination america"),
        ch("own", "OWN", "OWN", "Lifestyle", null, "own", "oprah winfrey network"),
        ch("bravo", "Bravo", "BRAVO", "Lifestyle", "united-states/bravo-us.png", "bravo", "bravo tv"),
        ch("e_entertainment", "E! Entertainment", "E", "Lifestyle", "united-states/e-entertainment-us.png", "e entertainment", "e! entertainment", "e! tv"),
        ch("hgtv_ca", "HGTV Canada", "HGTVC", "Lifestyle", "canada/hgtv-ca.png", "hgtv canada", "hgtv ca"),
        ch("food_network_ca", "Food Network Canada", "FOODC", "Lifestyle", "canada/food-network-ca.png", "food network canada", "food ca"),
        ch("tlc_ca", "TLC Canada", "TLCC", "Lifestyle", "canada/tlc-ca.png", "tlc canada", "tlc ca"),
        ch("slice", "Slice", "SLICE", "Lifestyle", "canada/slice-ca.png", "slice", "slice tv"),
        ch("cottage_life", "Cottage Life", "COTG", "Lifestyle", "canada/cottage-life-ca.png", "cottage life"),
        ch("food_network_uk", "Food Network UK", "FOODU", "Lifestyle", "united-kingdom/food-network-uk.png", "food network uk", "food network hd"),
        ch("hgtv_uk", "HGTV UK", "HGTU", "Lifestyle", "united-kingdom/hgtv-uk.png", "hgtv uk"),
        ch("really", "Really", "REAL", "Lifestyle", "united-kingdom/really-uk.png", "really", "really tv"),
        ch("dmax_uk", "DMAX", "DMAX", "Lifestyle", "united-kingdom/dmax-uk.png", "dmax", "d-max"),
        ch("quest", "Quest", "QUEST", "Lifestyle", "united-kingdom/quest-uk.png", "quest", "quest tv"),

        // ---- Movies ---------------------------------------------------------
        ch("starz_encore", "Starz Encore", "ENC", "Movies", "united-states/starz-encore-us.png", "starz encore", "encore"),
        ch("starz_black", "Starz Black", "STZB", "Movies", "united-states/starz-black-us.png", "starz black", "starz in black"),
        ch("starz_comedy", "Starz Comedy", "STZC", "Movies", "united-states/starz-comedy-us.png", "starz comedy"),
        ch("showtime2", "Showtime 2", "SHO2", "Movies", "united-states/showtime-2-us.png", "showtime 2", "showtime two"),
        ch("showtime_showcase", "Showtime Showcase", "SHOS", "Movies", "united-states/showtime-showcase-us.png", "showtime showcase"),
        ch("hbo2", "HBO 2", "HBO2", "Movies", "united-states/hd/hbo-2-hd-us.png", "hbo 2", "hbo two"),
        ch("hbo_comedy", "HBO Comedy", "HBOC", "Movies", "united-states/hbo-comedy-us.png", "hbo comedy"),
        ch("hbo_family", "HBO Family", "HBOF", "Movies", "united-states/hbo-family-us.png", "hbo family"),
        ch("movieplex", "MoviePlex", "MPLX", "Movies", "united-states/movie-plex-us.png", "movieplex", "movie plex"),
        ch("tcm", "Turner Classic Movies", "TCM", "Movies", "united-states/tcm-us.png", "turner classic", "tcm"),
        ch("ifc", "IFC", "IFC", "Movies", "united-states/ifc-us.png", "ifc", "independent film"),
        ch("sundance_tv", "Sundance TV", "SUND", "Movies", "united-states/sundance-tv-us.png", "sundance", "sundance tv"),
        ch("epix", "EPIX", "EPIX", "Movies", "united-states/epix-us.png", "epix"),
        ch("film4", "Film4", "FILM4", "Movies", null, "film4", "film 4"),
        ch("sky_cinema_premiere", "Sky Cinema Premiere", "SKYP", "Movies", "united-kingdom/sky-cinema-premiere-uk.png", "sky cinema premiere"),
        ch("sky_cinema_family", "Sky Cinema Family", "SKYF", "Movies", "united-kingdom/sky-cinema-family-uk.png", "sky cinema family"),

        // ---- News -----------------------------------------------------------
        ch("abc_news_us", "ABC News", "ABCN", "News", "united-states/abc-news-us.png", "abc news", "abc news live"),
        ch("cbs_news_us", "CBS News", "CBSN", "News", "united-states/cbs-news-us.png", "cbs news", "cbs news 24"),
        ch("nbc_news_now", "NBC News Now", "NBCN", "News", "united-states/nbc-news-now-us.png", "nbc news now", "nbc news"),
        ch("scripps_news", "Scripps News", "SCRP", "News", "united-states/scripps-news-us.png", "scripps news", "scripps"),
        ch("straight_arrow_news", "Straight Arrow News", "SAN", "News", null, "straight arrow", "san news"),
        ch("dw_english", "DW English", "DW", "News", "international/dw-english-int.png", "dw english", "deutsche welle", "dw news"),
        ch("trt_world", "TRT World", "TRT", "News", "turkey/trt-world-tr.png", "trt world", "trt"),
        ch("global_news", "Global News", "GLBN", "News", "canada/global-news-ca.png", "global news", "global news ca"),
        ch("bbc_world_news", "BBC World News", "BBCW", "News", "united-kingdom/bbc-world-news-uk.png", "bbc world news", "bbc world"),
        ch("talk_tv", "Talk TV", "TALK", "News", "united-kingdom/talk-tv-uk.png", "talk tv", "talktv"),

        // ---- Sports ---------------------------------------------------------
        ch("nfl_network", "NFL Network", "NFLN", "Sports", "united-states/nfl-network-us.png", "nfl network", "nfln"),
        ch("nba_tv", "NBA TV", "NBATV", "Sports", "united-states/nba-tv-us.png", "nba tv", "nbatv"),
        ch("mlb_network", "MLB Network", "MLBN", "Sports", "united-states/mlb-network-us.png", "mlb network", "mlbn"),
        ch("nhl_network", "NHL Network", "NHLN", "Sports", "united-states/nhl-network-us.png", "nhl network", "nhln"),
        ch("golf_channel", "Golf Channel", "GOLF", "Sports", "united-states/nbc-golf-channel-us.png", "golf channel", "golf"),
        ch("tennis_channel", "Tennis Channel", "TENN", "Sports", "united-states/tennis-channel-us.png", "tennis channel", "tennis"),
        ch("olympic_channel", "Olympic Channel", "OLYM", "Sports", "united-states/olympic-channel-us.png", "olympic channel", "olympics"),
        ch("cbs_sports_network", "CBS Sports Network", "CBSSN", "Sports", "united-states/cbs-sports-network-us.png", "cbs sports network", "cbssn"),
        ch("fox_sports_1", "Fox Sports 1", "FS1", "Sports", "united-states/fox-sports-1-us.png", "fox sports 1", "fs1"),
        ch("fox_sports_2", "Fox Sports 2", "FS2", "Sports", "united-states/fox-sports-2-us.png", "fox sports 2", "fs2"),

        // ---- International sports ------------------------------------------
        ch("sky_sports_football", "Sky Sports Football", "SSFB", "Int. Sports", "united-kingdom/sky-sports-football-uk.png", "sky sports football"),
        ch("sky_sports_cricket", "Sky Sports Cricket", "SSCR", "Int. Sports", "united-kingdom/sky-sports-cricket-uk.png", "sky sports cricket"),
        ch("sky_sports_f1", "Sky Sports F1", "SSF1", "Int. Sports", "united-kingdom/sky-sports-f1-uk.png", "sky sports f1"),
        ch("sky_sports_golf", "Sky Sports Golf", "SSGL", "Int. Sports", "united-kingdom/sky-sports-golf-uk.png", "sky sports golf"),
        ch("sky_sports_action", "Sky Sports Action", "SSAC", "Int. Sports", "united-kingdom/sky-sports-action-uk.png", "sky sports action"),
        ch("sky_sports_arena", "Sky Sports Arena", "SSAR", "Int. Sports", "united-kingdom/sky-sports-arena-uk.png", "sky sports arena"),
        ch("tnt_sports_1", "TNT Sports 1", "TNT1", "Int. Sports", "united-kingdom/tnt-sports-1-uk.png", "tnt sports 1", "bt sport 1"),
        ch("tnt_sports_2", "TNT Sports 2", "TNT2", "Int. Sports", "united-kingdom/tnt-sports-2-uk.png", "tnt sports 2", "bt sport 2"),
        ch("eurosport_1", "Eurosport 1", "EUR1", "Int. Sports", "united-kingdom/eurosport-1-uk.png", "eurosport 1", "eurosport"),
        ch("star_sports_1", "Star Sports 1", "STAR1", "Int. Sports", "india/star-sports-1-in.png", "star sports 1", "star sports"),
        ch("bein_sports_1", "beIN Sports 1", "BEIN1", "Int. Sports", "international/beinsports/old/stacked/bein-sports-1-int.png", "bein sports 1", "bein 1"),
        ch("willow", "Willow Cricket", "WILL", "Int. Sports", "united-states/willow-us.png", "willow", "willow cricket"),

        // ---- Kids -----------------------------------------------------------
        ch("disney_channel", "Disney Channel", "DISN", "Kids", "united-states/disney-channel-us.png", "disney channel", "disney"),
        ch("disney_junior", "Disney Junior", "DISJ", "Kids", "united-states/disney-jr-us.png", "disney junior", "disney jr"),
        ch("nick_jr", "Nick Jr", "NICKJ", "Kids", "united-states/nick-jr-us.png", "nick jr", "nickjr"),
        ch("cartoonito", "Cartoonito", "CTTO", "Kids", "united-states/cartoonito-us.png", "cartoonito"),
        ch("pbs_kids", "PBS Kids", "PBSK", "Kids", null, "pbs kids", "pbskids"),
        ch("cbeebies", "CBeebies", "CBEE", "Kids", "united-kingdom/bbc-cbeebies-uk.png", "cbeebies"),
        ch("citv", "CITV", "CITV", "Kids", null, "citv"),
        ch("ytv", "YTV", "YTV", "Kids", "canada/ytv-ca.png", "ytv"),
        ch("treehouse", "Treehouse", "TREE", "Kids", "canada/treehouse-ca.png", "treehouse", "treehouse tv"),
        ch("teletoon", "Teletoon", "TLTO", "Kids", "canada/teletoon-ca.png", "teletoon"),
        ch("family_jr", "Family Jr", "FAMJ", "Kids", "canada/family-jr-ca.png", "family jr", "family junior"),

        // ---- Discovery ------------------------------------------------------
        ch("history_channel", "History", "HIST", "Discovery", "united-states/history-channel-us.png", "history channel", "history"),
        ch("military_history", "Military History", "MILH", "Discovery", "united-states/military-history-us.png", "military history"),
        ch("investigation_discovery", "Investigation Discovery", "ID", "Discovery", "united-states/investigation-discovery-us.png", "investigation discovery", "id channel"),
        ch("science_channel", "Science Channel", "SCI", "Discovery", "united-states/discovery-science-us.png", "science channel", "science"),
        ch("discovery_family", "Discovery Family", "DFAM", "Discovery", "united-states/discovery-family-us.png", "discovery family"),
        ch("discovery_life", "Discovery Life", "DLIF", "Discovery", "united-states/discovery-life-us.png", "discovery life"),
        ch("discovery_turbo", "Discovery Turbo", "DTUR", "Discovery", "united-states/discovery-turbo-us.png", "discovery turbo", "turbo"),
        ch("nat_geo_wild", "Nat Geo Wild", "NGW", "Discovery", "united-states/nat-geo-wild-us.png", "nat geo wild", "national geographic wild"),
        ch("smithsonian", "Smithsonian Channel", "SMTH", "Discovery", "united-states/smithsonian-channel-us.png", "smithsonian"),

        // ---- US broadcast ---------------------------------------------------
        ch("cw", "The CW", "CW", "US", "united-states/cw-us.png", "the cw", "cw network"),
        ch("pbs", "PBS", "PBS", "US", "united-states/pbs-us.png", "pbs", "public broadcasting"),
        ch("telemundo", "Telemundo", "TLMD", "US", "united-states/telemundo-us.png", "telemundo"),
        ch("univision", "Univision", "UNIV", "US", "united-states/univision-us.png", "univision"),
        ch("ion", "ION Television", "ION", "US", "united-states/ion-television-us.png", "ion", "ion television"),
        ch("mynetwork", "MyNetworkTV", "MYTV", "US", "united-states/my-network-tv-us.png", "mynetwork", "my network tv"),

        // ---- UK -------------------------------------------------------------
        ch("itv2", "ITV2", "ITV2", "UK", "united-kingdom/itv-2-uk.png", "itv2", "itv 2"),
        ch("itv3", "ITV3", "ITV3", "UK", "united-kingdom/itv-3-uk.png", "itv3", "itv 3"),
        ch("itv4", "ITV4", "ITV4", "UK", "united-kingdom/itv-4-uk.png", "itv4", "itv 4"),
        ch("e4", "E4", "E4", "UK", null, "e4", "e4 tv"),
        ch("dave", "Dave", "DAVE", "UK", "united-kingdom/dave-uk.png", "dave", "dave tv"),
        ch("gold", "Gold", "GOLD", "UK", "united-kingdom/gold-uk.png", "gold", "gold tv"),
        ch("alibi", "Alibi", "ALIBI", "UK", "united-kingdom/alibi-uk.png", "alibi", "alibi tv"),
        ch("drama_uk", "Drama", "DRMA", "UK", "united-kingdom/drama-uk.png", "drama", "drama channel"),
        ch("5usa", "5USA", "5USA", "UK", null, "5usa", "5 usa"),
        ch("5star", "5STAR", "5STR", "UK", null, "5star", "5 star"),

        // ---- Canada ---------------------------------------------------------
        ch("ctv", "CTV", "CTV", "CA", "canada/ctv-ca.png", "ctv", "ctv television"),
        ch("citytv", "Citytv", "CITY", "CA", "canada/city-tv-ca.png", "citytv", "city tv"),
        ch("cbc", "CBC", "CBC", "CA", "canada/cbc-ca.png", "cbc", "cbc television"),
        ch("radio_canada", "Radio-Canada", "SRC", "CA", null, "radio-canada", "radio canada", "src"),
        ch("noovo", "Noovo", "NOOV", "CA", null, "noovo"),
        ch("tva", "TVA", "TVA", "CA", "canada/tva-ca.png", "tva", "tva quebec"),

        // ---- Soccer ---------------------------------------------------------
        ch("europa_league", "Europa League", "UEL", "Soccer", null, "europa league", "uefa europa"),
        ch("conference_league", "Conference League", "UECL", "Soccer", null, "conference league", "europa conference"),
        ch("fa_cup", "FA Cup", "FAC", "Soccer", null, "fa cup", "emirates fa cup"),
        ch("efl_championship", "EFL Championship", "EFL", "Soccer", null, "efl championship", "championship football"),
        ch("liga_mx", "Liga MX", "LIGAMX", "Soccer", null, "liga mx", "ligamx"),
        ch("brasileirao", "Brasileirão", "BRA", "Soccer", null, "brasileirao", "campeonato brasileiro"),
        ch("copa_sudamericana", "Copa Sudamericana", "SUDA", "Soccer", null, "copa sudamericana", "sudamericana"),
        ch("nwsl", "NWSL", "NWSL", "Soccer", null, "nwsl", "national women's soccer"),

        // ---- Combat ---------------------------------------------------------
        ch("pfl", "PFL", "PFL", "Combat", null, "pfl", "professional fighters league"),
        ch("invicta", "Invicta FC", "INV", "Combat", null, "invicta", "invicta fc"),
        ch("glory", "Glory Kickboxing", "GLORY", "Combat", null, "glory", "glory kickboxing"),
        ch("bkfc", "BKFC", "BKFC", "Combat", null, "bkfc", "bare knuckle"),
        ch("ksw", "KSW", "KSW", "Combat", null, "ksw", "konfrontacja"),

        // ---- Racing ---------------------------------------------------------
        ch("formula_e", "Formula E", "FE", "Racing", null, "formula e", "formulae"),
        ch("nhra", "NHRA", "NHRA", "Racing", null, "nhra", "drag racing"),
        ch("imsa", "IMSA", "IMSA", "Racing", null, "imsa", "weathertech sportscar"),
        ch("supercars", "Supercars", "SCAR", "Racing", null, "supercars", "v8 supercars"),
        ch("wrc_racing", "WRC", "WRC", "Racing", null, "wrc", "world rally"),
        ch("world_of_outlaws", "World of Outlaws", "WOO", "Racing", null, "world of outlaws", "outlaws"),

        // ---- Black media ----------------------------------------------------
        ch("tv_one", "TV One", "TV1", "Black Media", null, "tv one", "tvone"),
        ch("aspire_tv", "Aspire TV", "ASPIRE", "Black Media", null, "aspire", "aspire tv"),
        ch("cleo_tv", "Cleo TV", "CLEO", "Black Media", null, "cleo tv", "cleo"),
        ch("bounce_tv", "Bounce TV", "BOUNCE", "Black Media", null, "bounce", "bounce tv"),
        ch("thegrio", "TheGrio", "GRIO", "Black Media", null, "thegrio", "the grio"),
        ch("fox_soul", "Fox Soul", "SOUL", "Black Media", null, "fox soul", "foxsoul"),
        ch("allblk", "ALLBLK", "ALLBLK", "Black Media", null, "allblk", "all blk", "urban movie channel"),
        ch("brown_sugar", "Brown Sugar", "BSUGAR", "Black Media", null, "brown sugar", "brownsugar"),
        ch("kweli_tv", "KweliTV", "KWELI", "Black Media", null, "kweli", "kwelitv"),
        ch("afroland_tv", "AfroLandTV", "AFRO", "Black Media", null, "afroland", "afrolandtv"),
        ch("ebony_tv", "Ebony TV", "EBONY", "Black Media", null, "ebony tv", "ebony"),
        ch("jet_tv", "JET TV", "JET", "Black Media", null, "jet tv", "jet"),
        ch("black_star", "Black Star Network", "BSN", "Black Media", null, "black star", "blackstar"),
        ch("newsone", "NewsOne", "NEWS1", "Black Media", null, "newsone", "news one"),
        ch("revolt_tv", "Revolt", "RVLT", "Black Media", null, "revolt", "revolt tv"),
        ch("tv_one_now", "TV One Now", "TV1N", "Black Media", null, "tv one now", "tvone now"),
        ch("impact_tv", "Impact Network", "IMPACT", "Black Media", null, "impact network", "impact tv"),
        ch("soul_of_the_south", "Soul of the South", "SOTS", "Black Media", null, "soul of the south", "sots"),
        ch("black_enterprise", "Black Enterprise", "BE", "Black Media", null, "black enterprise", "blackenterprise"),
        ch("the_shade_room", "The Shade Room", "TSR", "Black Media", null, "shade room", "theshaderoom"),

        // ---- Adult premium networks (hidden when NSFW is off) ---------------
        ch("playboy_tv", "Playboy TV", "PBTV", "Adult", null, "playboy", "playboy tv"),
        ch("penthouse_tv", "Penthouse TV", "PENT", "Adult", null, "penthouse", "penthouse tv"),
        ch("hustler_tv", "Hustler TV", "HUST", "Adult", null, "hustler", "hustler tv"),
        ch("brazzers_tv", "Brazzers TV", "BRZ", "Adult", null, "brazzers", "brazzers tv"),
        ch("reality_kings", "Reality Kings", "RK", "Adult", null, "reality kings", "realitykings"),
        ch("naughty_america", "Naughty America", "NA", "Adult", null, "naughty america", "naughtyamerica"),
        ch("vivid_tv", "Vivid TV", "VIVID", "Adult", null, "vivid", "vivid tv"),
        ch("private_tv", "Private TV", "PRIV", "Adult", null, "private tv", "private"),
        ch("dorcel_tv", "Dorcel TV", "DORCEL", "Adult", null, "dorcel", "dorcel tv"),
        ch("blue_hustler", "Blue Hustler", "BHUST", "Adult", null, "blue hustler", "bluehustler"),
        ch("erotic_1", "Erotic 1", "ER1", "Adult", null, "erotic 1", "erotic1"),
        ch("erotic_2", "Erotic 2", "ER2", "Adult", null, "erotic 2", "erotic2"),
        ch("erotic_3", "Erotic 3", "ER3", "Adult", null, "erotic 3", "erotic3"),
        ch("erotic_4", "Erotic 4", "ER4", "Adult", null, "erotic 4", "erotic4"),
        ch("evil_angel", "Evil Angel", "EVIL", "Adult", null, "evil angel", "evilangel"),
        ch("jules_jordan", "Jules Jordan", "JJ", "Adult", null, "jules jordan", "julesjordan"),
        ch("digital_playground", "Digital Playground", "DP", "Adult", null, "digital playground", "digitalplayground"),
        ch("wicked_tv", "Wicked TV", "WICKED", "Adult", null, "wicked", "wicked tv"),
        ch("vixen_tv", "Vixen TV", "VIXEN", "Adult", null, "vixen", "vixen tv"),
        ch("blacked_tv", "Blacked", "BLACKED", "Adult", null, "blacked", "blacked tv"),
        ch("tushy_tv", "Tushy", "TUSHY", "Adult", null, "tushy", "tushy tv"),
        ch("deeper_tv", "Deeper", "DEEPER", "Adult", null, "deeper", "deeper tv"),
        ch("twistys_tv", "Twistys TV", "TWST", "Adult", null, "twistys", "twistys tv"),
        ch("xempire_tv", "XEmpire", "XEMP", "Adult", null, "xempire", "x empire"),
        ch("redlight_tv", "RedLight TV", "REDL", "Adult", null, "redlight", "red light tv"),
        ch("sct_tv", "SCT TV", "SCT", "Adult", null, "sct", "sct tv"),
    )
}
