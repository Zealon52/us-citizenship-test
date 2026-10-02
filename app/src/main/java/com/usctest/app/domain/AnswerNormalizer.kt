package com.usctest.app.domain

/** Result of normalizing free text for grading. Token order is kept for display/debugging,
 * but matching downstream is set-based — see [AnswerGrader]. */
data class NormalizedText(val tokens: List<String>) {
    val tokenSet: Set<String> get() = tokens.toSet()
    val joined: String get() = tokens.joinToString(" ")
}

/**
 * Pure text normalization, shared by grading of typed and dictated (ASR) responses.
 * Deliberately narrow in scope — this catches formatting/spelling variance, not paraphrase
 * breadth. Paraphrase coverage belongs in authored `variants`/`keywords` on each answer.
 */
object AnswerNormalizer {

    private val stopWords = setOf("the", "a", "an", "of", "is", "are", "it", "its")

    // Small numbers only (0-100) — civics answers that use numerals in this range ("27
    // amendments," "9 justices") get digit/word equivalence. Years (1787, 1776, etc.) are left
    // as digits; word-expanding a 4-digit year is ambiguous and not how anyone would type it.
    private val onesWords = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
        "nineteen",
    )
    private val tensWords = listOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
    )

    // Ordinal counterparts of the cardinal lists above -- civics answers about presidential order
    // ("34th president") are authored with spelled-out ordinal keywords ("thirty-fourth"), so a
    // response typed as a bare ordinal numeral needs its own expansion path, not just the cardinal
    // one used for counts ("27 amendments").
    private val onesOrdinalWords = listOf(
        "zeroth", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth",
        "ninth", "tenth", "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth",
        "sixteenth", "seventeenth", "eighteenth", "nineteenth",
    )
    private val tensOrdinalWords = listOf(
        "", "", "twentieth", "thirtieth", "fortieth", "fiftieth", "sixtieth", "seventieth",
        "eightieth", "ninetieth",
    )
    private val ordinalPattern = Regex("^(\\d{1,3})(st|nd|rd|th)$")
    private val vowels = setOf('a', 'e', 'i', 'o', 'u')

    /** Honorifics are only stripped when the caller knows the answer is a person's name —
     * blindly stripping "President" would wrongly mangle an answer like "President of the Senate." */
    private val honorifics = setOf(
        "president", "vice", "speaker", "senator", "governor", "representative",
        "justice", "chief", "mr", "mrs", "ms", "dr",
    )

    // Dotted abbreviations ("U.S.", "U.S.A.", "D.C.") must collapse into one token before
    // generic punctuation-stripping runs, or the periods scatter them into throwaway
    // single-character tokens ("u", "s") that trivially self-match in fuzzy comparison and can
    // inflate an unrelated answer's match count. Aliasing then folds equivalent undotted forms
    // ("USA") onto the same token as their dotted/abbreviated counterpart ("US") so either form
    // of a response matches either form of an authored answer.
    private val dottedAbbreviation = Regex("\\b(?:[a-z]\\.){2,}")
    private val abbreviationAliases = mapOf("usa" to "us")

    /**
     * @param applyStemming Grading wants plural/verb-suffix equivalence ("honors" == "honor") to
     * close paraphrase-coverage gaps. [com.usctest.app.domain.DistractorProvider]'s dedup-key use
     * of this same function does not -- broadening its equivalence classes would risk shrinking
     * an already-tight distractor pool in ways not exercised by that code's own tests, for a
     * problem (distractor phrasing overlap) this wasn't scoped to fix. Kept as one shared
     * function with an opt-out rather than a second copy, so tokenization stays single-sourced.
     */
    fun normalize(text: String, isPersonName: Boolean = false, applyStemming: Boolean = true): NormalizedText {
        val lower = text.lowercase()
        // Strip possessive/contraction "'s" before generic punctuation stripping, or the
        // apostrophe splits it into a bare "s" token that no stopword list would catch.
        val withoutContractions = lower.replace(Regex("'s\\b"), "")
        val withoutDottedAbbreviations = withoutContractions.replace(dottedAbbreviation) { it.value.replace(".", "") }
        val cleaned = withoutDottedAbbreviations.replace(Regex("[^a-z0-9\\s]"), " ")
        var tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }

        tokens = tokens.flatMap { expandNumeral(it) }
        tokens = tokens.map { abbreviationAliases[it] ?: it }
        tokens = tokens.filterNot { it in stopWords }

        if (isPersonName) {
            // Surnames routinely end in "s" ("Adams", "Williams") -- stemming would corrupt them
            // into a different name, so person answers only get honorific-stripping, not stemming.
            tokens = stripLeadingHonorifics(tokens)
        } else if (applyStemming) {
            tokens = tokens.map { stem(it) }
        }

        return NormalizedText(tokens)
    }

    private fun expandNumeral(token: String): List<String> {
        ordinalPattern.matchEntire(token)?.let { match ->
            val n = match.groupValues[1].toInt()
            if (n in 0..100) return numberToOrdinalWords(n).split(" ")
        }
        val n = token.toIntOrNull() ?: return listOf(token)
        if (n !in 0..100) return listOf(token)
        return numberToWords(n).split(" ")
    }

    private fun numberToWords(n: Int): String = when {
        n < 20 -> onesWords[n]
        n == 100 -> "one hundred"
        n % 10 == 0 -> tensWords[n / 10]
        else -> "${tensWords[n / 10]} ${onesWords[n % 10]}"
    }

    private fun numberToOrdinalWords(n: Int): String = when {
        n < 20 -> onesOrdinalWords[n]
        n == 100 -> "one hundredth"
        n % 10 == 0 -> tensOrdinalWords[n / 10]
        else -> "${tensWords[n / 10]} ${onesOrdinalWords[n % 10]}"
    }

    private fun stripLeadingHonorifics(tokens: List<String>): List<String> {
        var result = tokens
        while (result.isNotEmpty() && result.first() in honorifics) {
            result = result.drop(1)
        }
        return result
    }

    /**
     * Minimal suffix stemmer -- narrow on purpose, covering only the plural/verb suffix gaps
     * found in the answer-grading corpus ("honors"≠"honor", "voting"≠"vote", "libraries"≠
     * "library", "Allies"≠"Allied"), not a general-purpose (e.g. full Porter) stemmer.
     */
    private fun stem(word: String): String {
        if (word.length <= 3) return word

        if (word.endsWith("ies") && word.length > 4) return word.dropLast(3) + "y"

        // Sibilant plurals ("boxes", "watches") add a whole "-es" syllable, so both characters
        // come off. Everything else ending in "es" ("representatives", "votes", "states") is
        // just a word that already ends in "e" plus a regular "-s" plural -- only the "s" comes
        // off, or "representatives" would wrongly lose its final "e" too.
        val sibilantEs = listOf("ses", "xes", "zes", "ches", "shes")
        return when {
            word.endsWith("ing") && word.length > 5 -> restoreVerbStem(word.dropLast(3))
            word.endsWith("ed") && word.length > 4 -> restoreVerbStem(word.dropLast(2))
            sibilantEs.any { word.endsWith(it) } && word.length > 4 -> word.dropLast(2)
            word.endsWith("s") && !word.endsWith("ss") && word.length > 3 -> word.dropLast(1)
            else -> word
        }
    }

    /** Undoes the two English spelling changes that happen when "-ing"/"-ed" is added: a
     * trailing "y" becomes "i" ("ally" -> "allied"), and a silent "e" is dropped from short
     * consonant-vowel-consonant stems ("vote" -> "voting"). */
    private fun restoreVerbStem(base: String): String {
        var result = base
        if (result.length > 1 && result.endsWith("i") && result[result.length - 2] !in vowels) {
            result = result.dropLast(1) + "y"
        }
        if (result.length >= 3 &&
            result.last() !in vowels &&
            result[result.length - 2] in vowels &&
            result[result.length - 3] !in vowels
        ) {
            result += "e"
        }
        return result
    }
}
