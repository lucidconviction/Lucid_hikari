package com.hikari.app.sports

import com.hikari.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every league and sport the Sports page knows. ESPN's free feed covers most of them (scores,
 * logos, team colors, US networks); sports it doesn't cover are found in your own TV guide.
 */
object SportsCatalog {

    private val US = setOf("US")
    private val UK = setOf("UK", "IE")
    private val NA = setOf("US", "CA")
    private val EVERYWHERE = League.ALL_REGIONS

    val leagues: List<League> = listOf(
        // ---- American football
        // NFL and NBA: on by default in the US and Canada only (elsewhere, add them in Leagues or follow a team).
        League("football/nfl", Sport.AMERICAN_FOOTBALL, "NFL", "NFL", "football/nfl", guideWords = listOf("NFL"),
            regions = NA, minutes = 195, color = 0xFF013369),
        League("football/college-football", Sport.AMERICAN_FOOTBALL, "College Football", "NCAAF", "football/college-football",
            guideWords = listOf("College Football", "NCAA Football"), regions = US, minutes = 195, color = 0xFF005A9C, schoolNames = true, espnQuery = "groups=80"),
        // ---- Basketball
        League("basketball/nba", Sport.BASKETBALL, "NBA", "NBA", "basketball/nba", guideWords = listOf("NBA"),
            regions = NA, minutes = 150, color = 0xFFC9082A),
        League("basketball/wnba", Sport.BASKETBALL, "WNBA", "WNBA", "basketball/wnba", guideWords = listOf("WNBA"),
            regions = US, minutes = 120, color = 0xFFEB7D29, women = true),
        League("basketball/mens-college-basketball", Sport.BASKETBALL, "College Basketball (M)", "NCAAM", "basketball/mens-college-basketball",
            guideWords = listOf("College Basketball", "NCAA Basketball"), regions = US, minutes = 120, color = 0xFF005A9C, schoolNames = true, espnQuery = "groups=50"),
        League("basketball/womens-college-basketball", Sport.BASKETBALL, "College Basketball (W)", "NCAAW", "basketball/womens-college-basketball",
            guideWords = listOf("Women's College Basketball", "NCAA Women"), regions = US, minutes = 120, color = 0xFFEB7D29, schoolNames = true, women = true, espnQuery = "groups=50"),
        // ---- Hockey
        League("hockey/nhl", Sport.HOCKEY, "NHL", "NHL", "hockey/nhl", guideWords = listOf("NHL"),
            regions = NA, minutes = 150, color = 0xFF000000),
        League("hockey/australian-ice-hockey-league", Sport.HOCKEY, "AIHL", "AIHL", "hockey/australian-ice-hockey-league",
            guideWords = listOf("AIHL"), regions = setOf("AU"), minutes = 120, color = 0xFF0033A0),
        // ---- Baseball
        League("baseball/mlb", Sport.BASEBALL, "MLB", "MLB", "baseball/mlb", guideWords = listOf("MLB"),
            regions = NA, minutes = 180, color = 0xFF003366),
        League("baseball/npb", Sport.BASEBALL, "NPB", "NPB", "baseball/npb", guideWords = listOf("NPB", "Japan Series"),
            regions = setOf("OTHER"), minutes = 180, color = 0xFF003366),
        League("baseball/kbo", Sport.BASEBALL, "KBO", "KBO", "baseball/kbo", guideWords = listOf("KBO"),
            regions = setOf("OTHER"), minutes = 180, color = 0xFF003366),
        // ---- Football (soccer)
        // Top 5 European + major international
        League("soccer/eng.1", Sport.FOOTBALL, "Premier League", "PL", "soccer/eng.1", guideWords = listOf("Premier League", "English Premier"),
            regions = EVERYWHERE, minutes = 115, color = 0xFF37003C),
        League("soccer/esp.1", Sport.FOOTBALL, "La Liga", "La Liga", "soccer/esp.1", guideWords = listOf("La Liga", "Primera Division"),
            regions = EVERYWHERE, minutes = 115, color = 0xFFC60C30),
        League("soccer/ger.1", Sport.FOOTBALL, "Bundesliga", "Bundesliga", "soccer/ger.1", guideWords = listOf("Bundesliga"),
            regions = EVERYWHERE, minutes = 115, color = 0xFFD10000),
        League("soccer/ita.1", Sport.FOOTBALL, "Serie A", "Serie A", "soccer/ita.1", guideWords = listOf("Serie A"),
            regions = EVERYWHERE, minutes = 115, color = 0xFF003D7A),
        League("soccer/fra.1", Sport.FOOTBALL, "Ligue 1", "Ligue 1", "soccer/fra.1", guideWords = listOf("Ligue 1"),
            regions = EVERYWHERE, minutes = 115, color = 0xFF002395),
        League("soccer/uefa.champions", Sport.FOOTBALL, "Champions League", "UCL", "soccer/uefa.champions", guideWords = listOf("Champions League", "UCL"),
            regions = EVERYWHERE, minutes = 125, color = 0xFF000000),
        League("soccer/uefa.europa", Sport.FOOTBALL, "Europa League", "UEL", "soccer/uefa.europa", guideWords = listOf("Europa League"),
            regions = EVERYWHERE, minutes = 125, color = 0xFFFFCD00),
        League("soccer/fifa.world", Sport.FOOTBALL, "World Cup", "WC", "soccer/fifa.world", guideWords = listOf("World Cup"),
            regions = EVERYWHERE, minutes = 125, color = 0xFF000000, national = true),
        // ---- Rugby
        League("rugby/super-rugby", Sport.RUGBY, "Super Rugby Pacific", "Super Rugby", "rugby/super-rugby", guideWords = listOf("Super Rugby"),
            regions = setOf("AU", "OTHER"), minutes = 110, color = 0xFF1B4332),
        League("rugby/united-rugby-championship", Sport.RUGBY, "United Rugby Championship", "URC", "rugby/united-rugby-championship", guideWords = listOf("United Rugby Championship", "URC"),
            regions = UK + setOf("OTHER"), minutes = 110, color = 0xFF1B4332),
        // ---- Fighting
        League("mma/ufc", Sport.FIGHTING, "UFC", "UFC", "mma/ufc", guideWords = listOf("UFC"),
            regions = EVERYWHERE, minutes = 180, color = 0xFF000000, kind = LeagueKind.EVENT),
        // ---- Racing
        League("racing/f1", Sport.RACING, "Formula 1", "F1", "racing/f1", guideWords = listOf("Formula 1", "F1", "Grand Prix"),
            regions = EVERYWHERE, minutes = 120, color = 0xFFDC143C, kind = LeagueKind.EVENT),
        League("racing/nascar", Sport.RACING, "NASCAR Cup", "NASCAR", "racing/nascar", guideWords = listOf("NASCAR", "Cup Series"),
            regions = US, minutes = 210, color = 0xFF000000, kind = LeagueKind.EVENT),
        // ---- Golf
        League("golf/pga", Sport.GOLF, "PGA Tour", "PGA", "golf/pga", guideWords = listOf("PGA Tour", "PGA Championship"),
            regions = EVERYWHERE, minutes = 360, color = 0xFF006B3F, kind = LeagueKind.EVENT),
        // ---- Tennis
        League("tennis/atp", Sport.TENNIS, "ATP Tour", "ATP", "tennis/atp", guideWords = listOf("ATP", "Masters 1000"),
            regions = EVERYWHERE, minutes = 180, color = 0xFF006B3F, kind = LeagueKind.EVENT),
        League("tennis/wta", Sport.TENNIS, "WTA Tour", "WTA", "tennis/wta", guideWords = listOf("WTA", "WTA 1000"),
            regions = EVERYWHERE, minutes = 180, color = 0xFF006B3F, kind = LeagueKind.EVENT, women = true),
        // ---- Cricket
        League("cricket/ipl", Sport.CRICKET, "IPL", "IPL", "cricket/ipl", guideWords = listOf("IPL", "Indian Premier League"),
            regions = EVERYWHERE, minutes = 210, color = 0xFF004D99),
        // ---- Other
        League("darts/pdc", Sport.DARTS, "PDC Darts", "PDC", "darts/pdc", guideWords = listOf("PDC", "World Darts", "Premier League Darts"),
            regions = UK + setOf("OTHER"), minutes = 180, color = 0xFF000000, kind = LeagueKind.EVENT, guideOnly = true, fixtureSlug = "darts"),
        League("snooker/wst", Sport.SNOOKER, "World Snooker Tour", "Snooker", "snooker/wst", guideWords = listOf("World Snooker", "Snooker", "Masters"),
            regions = UK + setOf("OTHER"), minutes = 180, color = 0xFF000000, guideOnly = true, fixtureSlug = "snooker"),
        League("wrestling/wwe", Sport.WRESTLING, "WWE", "WWE", "wrestling/wwe", guideWords = listOf("WWE", "Raw", "SmackDown", "NXT"),
            regions = EVERYWHERE, minutes = 180, color = 0xFF000000, guideOnly = true, liveByDefault = true),
        League("wrestling/aew", Sport.WRESTLING, "AEW", "AEW", "wrestling/aew", guideWords = listOf("AEW", "Dynamite", "Rampage", "Collision"),
            regions = EVERYWHERE, minutes = 180, color = 0xFF000000, guideOnly = true, liveByDefault = true),
    )

    /** Find a league by its id ("football/nfl", "soccer/eng.1"…). */
    fun league(id: String): League? = leagues.firstOrNull { it.id == id }

    /** Leagues sorted for display (active regions first, then by sport order). */
    fun ordered(settings: SportsSettings): List<League> {
        val region = settings.region.ifBlank { SportsPlatform.countryCode() }.uppercase()
        return leagues
            .filter { !settings.hiddenLeagues.contains(it.id) }
            .sortedWith(
                compareByDescending<League> { it.regions.contains(region) }
                    .thenBy { sportsOrder(settings).indexOf(it.sport).let { i -> if (i < 0) 99 else i } }
                    .thenBy { it.name }
            )
    }

    /** Sports in the order the chips appear. */
    fun sportsOrder(settings: SportsSettings): List<Sport> {
        val region = settings.region.ifBlank { SportsPlatform.countryCode() }.uppercase()
        val activeSports = leagues
            .filter { !settings.hiddenLeagues.contains(it.id) && it.regions.contains(region) }
            .map { it.sport }
            .distinct()
        return Sport.values().filter { it in activeSports }
    }

    /** The "More Sports" league (for guide-only sports without an ESPN path). */
    val OTHER_LEAGUE = League("other", Sport.OTHER, "More Sports", "More", null, guideOnly = true, kind = LeagueKind.EVENT)
    val OTHER_ID = "other"

    /** True when [league] might have a standings table (ESPN path and TEAMS kind). */
    fun mayHaveTable(league: League): Boolean = league.espnPath != null && league.kind == LeagueKind.TEAMS

    // ------------------------------------------------------------------ normalization helpers (ported from Nuvio)

    /**
     * Lowercase with accents, punctuation and "&" sorted out, padded with spaces so whole words
     * can be looked for with [hasWord].
     */
    private val SMALL = "ᴬᴮᴰᴱᴳᴴᴵᴶᴷᴸᴹᴺᴼᴾᴿᵀᵁⱽᵂᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐⁿᵒᵖʳˢᵗᵘᵛʷˣʸᶻᶦᶫᶰᶸᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘʀꜱᴛᴜᴠᴡʏᴢ⁰¹²³⁴⁵⁶⁷⁸⁹⁺"
    private val PLAIN = "abdeghijklmnoprtuvwabcdefghijklmnoprstuvwxyzilnuabcdefghijklmnoprstuvwyz0123456789+"
    private val SMALL_RUN = Regex("[$SMALL]+(?:\\s*[$SMALL]+)*")

    private fun plainOf(run: String): String = buildString {
        run.forEach { ch -> val i = SMALL.indexOf(ch); if (i >= 0) append(PLAIN[i]) else if (ch.isWhitespace()) append(' ') }
    }

    fun plainSmall(s: String): String =
        if (s.none { it in SMALL }) s else s.replace(SMALL_RUN) { m -> " " + plainOf(m.value) + " " }

    private val SMALL_MARKS = setOf("live", "new", "hd", "fhd", "uhd", "4k")

    fun withoutSmallMarks(s: String): String =
        if (s.none { it in SMALL }) s
        else s.replace(SMALL_RUN) { m -> if (plainOf(m.value).split(' ').filter { it.isNotBlank() }.all { it in SMALL_MARKS }) " " else m.value }
            .replace(Regex("\\s+"), " ").trim()

    fun norm(s: String): String {
        val plain = SportsPlatform.foldAccents(plainSmall(s))
        return " " + plain.lowercase().replace("&", " and ").replace(NOT_WORD, " ").trim().replace(SPACES, " ") + " "
    }

    private val NOT_WORD = Regex("[^a-z0-9]+")
    private val SPACES = Regex(" +")

    fun hasWord(normText: String, term: String): Boolean = term.isNotBlank() && normText.contains(" $term ")

    /** Words that make a show a replay or a highlights show rather than the game itself. */
    val NOT_LIVE_WORDS = listOf(
        "highlights", "replay", "classic", "classics", "rewind", "recap", "review", "reaction",
        "condensed", "mini match", "extended", "best of", "in 60", "in 90", "encore", "re air", "reair",
        "magazine", "preview show", "goals", "match of the day", "motd", "uncut", "repeat",
        "this week in", "bottom line", "untold", "vault", "biography",
        "hlts", "highlight", "rerun", "re run", "as live"
    )

    /** Other names guides use for teams (and the start of names that need them). */
    private val ALIASES: Map<String, List<String>> = mapOf(
        "manchester united" to listOf("man utd", "man united", "man u"),
        "manchester city" to listOf("man city"),
        "tottenham hotspur" to listOf("tottenham", "spurs"),
        "wolverhampton wanderers" to listOf("wolves", "wolverhampton"),
        "brighton and hove albion" to listOf("brighton"),
        "west ham united" to listOf("west ham"),
        "newcastle united" to listOf("newcastle"),
        "nottingham forest" to listOf("nottm forest", "nottingham f"),
        "leicester city" to listOf("leicester"),
        "leeds united" to listOf("leeds"),
        "sheffield united" to listOf("sheff utd", "sheffield utd"),
        "sheffield wednesday" to listOf("sheff wed", "sheffield wed"),
        "queens park rangers" to listOf("qpr"),
        "west bromwich albion" to listOf("west brom", "wba"),
        "afc bournemouth" to listOf("bournemouth"),
        "ipswich town" to listOf("ipswich"),
        "luton town" to listOf("luton"),
        "aston villa" to listOf("villa"),
        "crystal palace" to listOf("palace"),
        "paris saint germain" to listOf("psg", "paris sg", "paris st germain"),
        "internazionale" to listOf("inter milan", "internazionale milano"),
        "bayern munich" to listOf("bayern", "fc bayern", "bayern munchen"),
        "borussia dortmund" to listOf("dortmund", "bvb"),
        "bayer leverkusen" to listOf("leverkusen"),
        "atletico madrid" to listOf("atletico", "atleti"),
        "barcelona" to listOf("barca", "fc barcelona"),
        "celtic" to listOf("celtic fc"),
        "rangers" to listOf("rangers fc"),
        "inter miami cf" to listOf("inter miami"),
        "la galaxy" to listOf("galaxy"),
        "los angeles fc" to listOf("lafc"),
        "new york red bulls" to listOf("red bulls", "ny red bulls"),
        "new york city fc" to listOf("nycfc", "nyc fc")
    )

    private val CLUB_BITS = Regex("""^(fc|afc|cf|sc|ac|ss|ssc|as|rc|cd|ca|sv|vfb|vfl|tsg|1 fc) | (fc|afc|cf|sc)$""")

    fun teamNames(team: TeamInfo, league: League): Pair<List<String>, List<String>> {
        val strong = java.util.LinkedHashSet<String>()
        val weak = java.util.LinkedHashSet<String>()
        fun n(s: String?) = s?.let { norm(it).trim() }?.takeIf { it.length >= 3 }
        val full = n(team.fullName)
        full?.let { strong += it; strong += it.replace(CLUB_BITS, "").trim() }
        val nick = n(team.name)
        val loc = n(team.location)
        if (league.schoolNames) {
            loc?.let { strong += it }
            nick?.let { if (loc == null || nick != loc) weak += it }
        } else if (league.sport == Sport.FOOTBALL) {
            nick?.let { strong += it; strong += it.replace(CLUB_BITS, "").trim() }
            loc?.let { if (loc != nick) weak += it }
        } else {
            nick?.let { if (it != loc) strong += it }
            loc?.let { weak += it }
        }
        listOfNotNull(full, nick).forEach { k -> ALIASES[k]?.let { strong += it } }
        strong.removeAll { it.length < 3 }
        weak.removeAll(strong)
        return strong.toList() to weak.toList()
    }

    /** The "family" of a team id (same club across competitions). */
    fun teamFamily(leagueId: String): String {
        return when (leagueId) {
            "soccer/uefa.champions", "soccer/uefa.europa", "soccer/uefa.conference" -> "uefa"
            "soccer/eng.1", "soccer/eng.2", "soccer/eng.fa_cup", "soccer/eng.league_cup" -> "eng"
            "soccer/esp.1", "soccer/esp.2", "soccer/esp.copa_del_rey" -> "esp"
            "soccer/ger.1", "soccer/ger.2", "soccer/ger.dfb_pokal" -> "ger"
            "soccer/ita.1", "soccer/ita.2", "soccer/ita.coppa_italia" -> "ita"
            "soccer/fra.1", "soccer/fra.2", "soccer/fra.coupe_de_france" -> "fra"
            "soccer/usa.1", "soccer/usa.open_cup" -> "usa"
            "soccer/mex.1" -> "mex"
            "soccer/bra.1" -> "bra"
            "soccer/arg.1" -> "arg"
            "soccer/por.1" -> "por"
            "soccer/ned.1" -> "ned"
            "soccer/bel.1" -> "bel"
            "soccer/tur.1" -> "tur"
            "soccer/aut.1" -> "aut"
            "soccer/sui.1" -> "sui"
            "soccer/dnk.1" -> "dnk"
            "soccer/nor.1" -> "nor"
            "soccer/swe.1" -> "swe"
            "soccer/pol.1" -> "pol"
            "soccer/grc.1" -> "grc"
            "soccer/ukr.1" -> "ukr"
            "soccer/rou.1" -> "rou"
            "soccer/cze.1" -> "cze"
            "soccer/scot.1" -> "scot"
            "soccer/fifa.world", "soccer/fifa.worldq", "soccer/uefa.euro" -> "national"
            "football/nfl" -> "nfl"
            "football/college-football" -> "ncaaf"
            "basketball/nba" -> "nba"
            "basketball/wnba" -> "wnba"
            "basketball/mens-college-basketball" -> "ncaam"
            "basketball/womens-college-basketball" -> "ncaaw"
            "hockey/nhl" -> "nhl"
            "baseball/mlb" -> "mlb"
            "baseball/npb" -> "npb"
            "baseball/kbo" -> "kbo"
            "mma/ufc" -> "ufc"
            "racing/f1" -> "f1"
            "racing/nascar" -> "nascar"
            "golf/pga" -> "pga"
            "tennis/atp" -> "atp"
            "tennis/wta" -> "wta"
            "cricket/ipl" -> "ipl"
            "rugby/super-rugby" -> "superrugby"
            "rugby/united-rugby-championship" -> "urc"
            "darts/pdc" -> "pdc"
            "snooker/wst" -> "snooker"
            "wrestling/wwe" -> "wwe"
            "wrestling/aew" -> "aew"
            else -> leagueId
        }
    }

    /**
     * A show's name without the date some schedules put in it: "NXT - October 20th 2026" -> "NXT",
     * "Raw: 20/10/2026" -> "Raw", "SmackDown (Oct 24, 2026)" -> "SmackDown". The card shows the date
     * anyway. A name that's only a date is left as it is.
     */
    fun withoutAirDate(title: String): String {
        val month = """(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.?"""
        val day = """\d{1,2}(?:st|nd|rd|th)?"""
        val date = listOf(
            """$month\s+$day,?\s+\d{4}""",
            """$day\s+$month,?\s+\d{4}""",
            """\d{4}-\d{2}-\d{2}""",
            """\d{1,2}[/.]\d{1,2}[/.]\d{2,4}"""
        ).joinToString("|")
        val trailing = Regex("""\s*(?:[\-–—:|,]\s*)?\(?(?:$date)\)?\s*$""", RegexOption.IGNORE_CASE)
        val out = title.replace(trailing, "").trim().trimEnd('-', '–', '—', ':', '|', ',').trim()
        return out.ifBlank { title }
    }

    /**
     * A wrestling show's headline: without the promotion in front (its logo is beside it) and without
     * the episode number. A special episode's own name wins.
     */
    fun wrestlingHeadline(title: String): String {
        val t = withoutAirDate(title).replace(Regex("""(?i)^\s*(wwe|aew|tna|njpw)\s*[:\-–]?\s*"""), "").trim()
        val m = Regex("""^(.*?)\s*#\s*\d+\s*(.*)$""").find(t)
        val out = if (m != null) m.groupValues[2].trim().ifBlank { m.groupValues[1].trim() } else t
        return out.ifBlank { title }
    }
}