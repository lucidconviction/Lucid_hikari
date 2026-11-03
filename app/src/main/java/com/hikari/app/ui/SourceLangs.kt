package com.hikari.app.ui

import com.hikari.app.data.ProviderConfig

/**
 * Classifies a provider's language from its name (and fallback description).
 *
 * The logic mirrors the upstream's [com.codegeasse1.hikari.ui.source.SourceLangs]:
 *  - names/descriptions containing "EN" / "English" → EN
 *  - names/descriptions containing "HI" / "Hindi" (but not the English word) → HI
 *  - names/descriptions containing "TE" / "Telugu" → TE
 *  - names/descriptions containing "TA" / "Tamil" → TA
 *  - names/descriptions containing "TU" / "Telugu" (alternative) → TU (mapped to TE)
 *  - names/descriptions containing "KN" / "Kannada" → KN
 *  - names/descriptions containing "ML" / "Malayalam" → ML
 *  - names/descriptions containing "BO" / "Bhojpuri" → BO
 *  - names/descriptions containing "MA" / "Marathi" → MA
 *  - names/descriptions containing "PA" / "Punjabi" → PA
 *  - names/descriptions containing "UR" / "Urdu" → UR
 *  - names/descriptions containing "BD" / "Bengali" → BD
 *  - names/descriptions containing "NE" / "Nepali" → NE
 *  - names/descriptions containing "GU" / "Gujarati" → GU
 *  - names/descriptions containing "OR" / "Oriya" → OR
 *  - names/descriptions containing "AS" / "Assamese" → AS
 *  - names/descriptions containing "MN" / "Manipuri" → MN
 *  - names/descriptions containing "SD" / "Sindhi" → SD
 *  - names/descriptions containing "KS" / "Kashmiri" → KS
 *  - names/descriptions containing "SA" / "Sanskrit" → SA
 *  - names/descriptions containing "MR" / "Maithili" → MR
 *  - names/descriptions containing "DOG" / "Dogri" → DOG
 *  - names/descriptions containing "KOK" / "Konkani" → KOK
 *  - names/descriptions containing "LN" / "Lingayat" → LN
 *  - names/descriptions containing "HAW" / "Haryanvi" → HAW
 *  - names/descriptions containing "CMD" / "CMD" → CMD
 *  - anything else → OTHER
 *
 * The classification is order-sensitive: the first match wins.
 */
enum class SourceLang(val code: String, val label: String) {
    EN("en", "English"),
    HI("hi", "Hindi"),
    TE("te", "Telugu"),
    TA("ta", "Tamil"),
    KN("kn", "Kannada"),
    ML("ml", "Malayalam"),
    BO("bo", "Bhojpuri"),
    MA("ma", "Marathi"),
    PA("pa", "Punjabi"),
    UR("ur", "Urdu"),
    BD("bd", "Bengali"),
    NE("ne", "Nepali"),
    GU("gu", "Gujarati"),
    OR("or", "Odia"),
    AS("as", "Assamese"),
    MN("mn", "Manipuri"),
    SD("sd", "Sindhi"),
    KS("ks", "Kashmiri"),
    SA("sa", "Sanskrit"),
    MR("mr", "Maithili"),
    DOG("dog", "Dogri"),
    KOK("kok", "Konkani"),
    LN("ln", "Lingayat"),
    HAW("haw", "Haryanvi"),
    CMD("cmd", "CMD"),
    OTHER("other", "Other"),
}

/**
 * Returns the best-guess language tag for [config], based on its name and
 * description. Falls back to OTHER when nothing matches.
 *
 * Both fields are lower-cased once and matched with simple substrings — fast,
 * wrong sometimes, but good enough to group extensions without needing a
 * language field on every row.
 */
fun classifyProvider(p: ProviderConfig): SourceLang {
    val text = p.name.lowercase()
    return when {
        text.contains(" english") || text.contains("eng") -> SourceLang.EN
        text.contains(" hindi") || text.contains("hindi") -> SourceLang.HI
        text.contains(" telugu") || text.contains("telugu") -> SourceLang.TE
        text.contains(" tamil") || text.contains("tamil") -> SourceLang.TA
        text.contains(" kannada") || text.contains("kannada") -> SourceLang.KN
        text.contains(" malayalam") || text.contains("malayalam") -> SourceLang.ML
        text.contains(" bhojpuri") || text.contains("bhojpuri") -> SourceLang.BO
        text.contains(" marathi") || text.contains("marathi") -> SourceLang.MA
        text.contains(" punjabi") || text.contains("punjabi") -> SourceLang.PA
        text.contains(" urdu") || text.contains("urdu") -> SourceLang.UR
        text.contains(" bengali") || text.contains("bengali") || text.contains("bd") -> SourceLang.BD
        text.contains(" nepali") || text.contains("nepali") -> SourceLang.NE
        text.contains(" gujarati") || text.contains("gujarati") -> SourceLang.GU
        text.contains(" oriya") || text.contains("odia") || text.contains(" or ") -> SourceLang.OR
        text.contains(" assamese") || text.contains("assamese") -> SourceLang.AS
        text.contains(" manipuri") || text.contains("manipuri") -> SourceLang.MN
        text.contains(" sindhi") || text.contains("sindhi") -> SourceLang.SD
        text.contains(" kashmiri") || text.contains("kashmiri") -> SourceLang.KS
        text.contains(" sanskrit") || text.contains("sanskrit") -> SourceLang.SA
        text.contains(" maithili") || text.contains("maithili") -> SourceLang.MR
        text.contains(" dogri") || text.contains("dogri") -> SourceLang.DOG
        text.contains(" konkani") || text.contains("konkani") -> SourceLang.KOK
        text.contains(" lingayat") || text.contains("lingayat") -> SourceLang.LN
        text.contains(" haryanvi") || text.contains("haryanvi") -> SourceLang.HAW
        text.contains(" cmd") -> SourceLang.CMD
        else -> SourceLang.OTHER
    }
}
