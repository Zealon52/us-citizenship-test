package com.usctest.app.domain

import com.usctest.app.data.model.AcceptableAnswer
import com.usctest.app.data.model.AnswerMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerGraderTest {

    private val theConstitution = listOf(AcceptableAnswer(canonical = "the Constitution"))

    private val twentySeven = listOf(
        AcceptableAnswer(canonical = "twenty-seven (27)", variants = listOf("27", "twenty-seven")),
    )

    private val congress = listOf(
        AcceptableAnswer(canonical = "the Senate", variants = listOf("Senate")),
        AcceptableAnswer(
            canonical = "the House (of Representatives)",
            variants = listOf("House", "House of Representatives"),
        ),
    )

    private val holidays = listOf(
        AcceptableAnswer(canonical = "Thanksgiving"),
        AcceptableAnswer(canonical = "Independence Day", variants = listOf("Fourth of July", "July 4th")),
        AcceptableAnswer(canonical = "Labor Day"),
        AcceptableAnswer(canonical = "Christmas"),
    )

    private val riverAnswer = listOf(
        AcceptableAnswer(canonical = "Missouri (River)", keywords = listOf("missouri")),
    )

    // --- Tier 1: exact / variant match ---

    @Test
    fun `exact canonical match is correct`() {
        val result = AnswerGrader.grade("the Constitution", theConstitution, AnswerMode.ANY_OF, 1)
        assertTrue(result.isCorrect)
    }

    @Test
    fun `variant match is correct`() {
        val result = AnswerGrader.grade("27", twentySeven, AnswerMode.ANY_OF, 1)
        assertTrue(result.isCorrect)
    }

    @Test
    fun `spelled-out numeral matches digit-authored variant`() {
        val result = AnswerGrader.grade("twenty seven", twentySeven, AnswerMode.ANY_OF, 1)
        assertTrue(result.isCorrect)
    }

    // --- Tier 2: keyword match ---

    @Test
    fun `keyword match handles conversational framing`() {
        // Deliberately omits "River" so the canonical/variant exact-subset tier can't fire —
        // this isolates the keyword tier, which is why the answer's keywords list matters.
        val result = AnswerGrader.grade("I think it's Missouri", riverAnswer, AnswerMode.ANY_OF, 1)
        assertTrue(result.isCorrect)
    }

    @Test
    fun `excludeKeywords suppresses a keyword match that collides with a sibling question's answer`() {
        // Mirrors the real Veterans Day / Memorial Day collision: both answers' keyword sets
        // happen to co-occur in the other's canonical text, so a plain keyword-subset check
        // false-accepts across questions. excludeKeywords guards the specific collision.
        val veteransDay = listOf(
            AcceptableAnswer(
                canonical = "A holiday to honor people in the (U.S.) military",
                keywords = listOf("honor", "military"),
                excludeKeywords = listOf("died"),
            ),
        )
        val memorialDaysAnswer = "a holiday to honor soldiers who died in military service"

        assertFalse(AnswerGrader.grade(memorialDaysAnswer, veteransDay, AnswerMode.ANY_OF, 1).isCorrect)
        assertTrue(AnswerGrader.grade("it's a day to honor the military", veteransDay, AnswerMode.ANY_OF, 1).isCorrect)
    }

    @Test
    fun `keyword match tolerates plural-verb suffix variance via stemming`() {
        // Regression case: "honors" used to fail to match the keyword "honor" verbatim.
        val answer = listOf(AcceptableAnswer(canonical = "A holiday to honor veterans", keywords = listOf("honor")))
        val result = AnswerGrader.grade("it honors veterans who served", answer, AnswerMode.ANY_OF, 1)
        assertTrue(result.isCorrect)
    }

    @Test
    fun `ordinal numeral response matches a spelled-out ordinal keyword`() {
        // Regression case: "34th" never expanded, so it couldn't match a keyword authored as
        // the spelled-out ordinal "thirty-fourth".
        val answer = listOf(
            AcceptableAnswer(
                canonical = "34th president of the United States",
                keywords = listOf("thirty-fourth", "president"),
            ),
        )
        val result = AnswerGrader.grade(
            "he was the 34th president", answer, AnswerMode.ANY_OF, 1, isPersonAnswer = true,
        )
        assertTrue(result.isCorrect)
    }

    // --- Tier 3: fuzzy match ---

    @Test
    fun `single typo in single-token answer still matches`() {
        val result = AnswerGrader.grade("Missoura", listOf(AcceptableAnswer(canonical = "Missouri")), AnswerMode.ANY_OF, 1)
        assertTrue(result.isCorrect)
    }

    @Test
    fun `dissimilar single-token answer does not fuzzy match`() {
        val result = AnswerGrader.grade("Mississippi", listOf(AcceptableAnswer(canonical = "Missouri")), AnswerMode.ANY_OF, 1)
        assertFalse(result.isCorrect)
    }

    @Test
    fun `multi-word answer with one missing token still matches when it has 3+ content words`() {
        val answer = listOf(AcceptableAnswer(canonical = "Martin Luther King Jr Day"))
        val result = AnswerGrader.grade("Martin Luther King Day", answer, AnswerMode.ANY_OF, 1)
        assertTrue(result.isCorrect)
    }

    @Test
    fun `two-token answer requires both tokens, no partial credit`() {
        val result = AnswerGrader.grade("Missouri", listOf(AcceptableAnswer(canonical = "Missouri River")), AnswerMode.ANY_OF, 1)
        assertFalse(result.isCorrect)
    }

    @Test
    fun `dotted abbreviation no longer pads fuzzy match with throwaway single-letter tokens`() {
        // Regression case: "U.S." used to split into single-character tokens "u"/"s" that
        // trivially self-matched, inflating an unrelated response's fuzzy match count enough to
        // satisfy this two-token answer without ever mentioning "diplomat".
        val answer = listOf(AcceptableAnswer(canonical = "U.S. diplomat", keywords = listOf("diplomat")))
        assertFalse(AnswerGrader.grade("he was a U.S. president", answer, AnswerMode.ANY_OF, 1).isCorrect)
        assertTrue(AnswerGrader.grade("U.S. diplomat", answer, AnswerMode.ANY_OF, 1).isCorrect)
    }

    // --- Hard negatives: textually close but wrong ---

    @Test
    fun `similar but different holiday does not match`() {
        val result = AnswerGrader.grade("Labor Day", holidays.filterNot { it.canonical == "Labor Day" }, AnswerMode.N_OF, 2)
        assertFalse(result.isCorrect)
    }

    @Test
    fun `wrong branch of government does not match`() {
        val result = AnswerGrader.grade("the Supreme Court", congress, AnswerMode.ALL_OF, 2)
        assertFalse(result.isCorrect)
    }

    // --- Count logic per AnswerMode ---

    @Test
    fun `ANY_OF is correct with a single matched answer`() {
        val result = AnswerGrader.grade("Christmas", holidays, AnswerMode.ANY_OF, 1)
        assertTrue(result.isCorrect)
    }

    @Test
    fun `N_OF requires the configured count of distinct answers in one response`() {
        val oneOnly = AnswerGrader.grade("Thanksgiving", holidays, AnswerMode.N_OF, 2)
        assertFalse(oneOnly.isCorrect)

        val twoDistinct = AnswerGrader.grade("Thanksgiving and Christmas", holidays, AnswerMode.N_OF, 2)
        assertTrue(twoDistinct.isCorrect)
    }

    @Test
    fun `ALL_OF requires every acceptable answer present`() {
        val partial = AnswerGrader.grade("the Senate", congress, AnswerMode.ALL_OF, 2)
        assertFalse(partial.isCorrect)

        val complete = AnswerGrader.grade("the Senate and the House of Representatives", congress, AnswerMode.ALL_OF, 2)
        assertTrue(complete.isCorrect)
    }

    @Test
    fun `ALL_OF with no acceptable answers is never correct`() {
        val result = AnswerGrader.grade("anything", emptyList(), AnswerMode.ALL_OF, 0)
        assertFalse(result.isCorrect)
    }
}
