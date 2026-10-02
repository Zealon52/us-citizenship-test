package com.usctest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerNormalizerTest {

    @Test
    fun `lowercases and strips punctuation`() {
        // "Rights" stems to "right" -- see the stemming tests below.
        val result = AnswerNormalizer.normalize("The Bill of Rights!")
        assertEquals(listOf("bill", "right"), result.tokens)
    }

    @Test
    fun `collapses whitespace`() {
        val result = AnswerNormalizer.normalize("  Senate   House  ")
        assertEquals(listOf("senate", "house"), result.tokens)
    }

    @Test
    fun `drops stopwords`() {
        val result = AnswerNormalizer.normalize("the House of Representatives")
        assertEquals(listOf("house", "representative"), result.tokens)
    }

    @Test
    fun `expands digit numerals to words`() {
        val result = AnswerNormalizer.normalize("27")
        assertEquals(listOf("twenty", "seven"), result.tokens)
    }

    @Test
    fun `parenthetical numeral duplicate collapses to same token set as spelled-out form`() {
        val spelledOut = AnswerNormalizer.normalize("twenty-seven (27)")
        val digitsOnly = AnswerNormalizer.normalize("27")
        assertEquals(digitsOnly.tokenSet, spelledOut.tokenSet)
    }

    @Test
    fun `does not expand large numbers like years`() {
        val result = AnswerNormalizer.normalize("1787")
        assertEquals(listOf("1787"), result.tokens)
    }

    @Test
    fun `strips leading honorific only when treated as a person name`() {
        val asPerson = AnswerNormalizer.normalize("President Biden", isPersonName = true)
        assertEquals(listOf("biden"), asPerson.tokens)

        val notPerson = AnswerNormalizer.normalize("President of the Senate", isPersonName = false)
        assertEquals(listOf("president", "senate"), notPerson.tokens)
    }

    @Test
    fun `contraction its is treated as filler`() {
        val result = AnswerNormalizer.normalize("I think it's the Missouri River")
        assertEquals(listOf("i", "think", "missouri", "river"), result.tokens)
    }

    @Test
    fun `dotted abbreviations collapse to one token instead of scattering into single letters`() {
        assertEquals(listOf("us", "diplomat"), AnswerNormalizer.normalize("U.S. diplomat").tokens)
        assertEquals(listOf("uk"), AnswerNormalizer.normalize("U.K.").tokens)
        assertEquals(listOf("dc"), AnswerNormalizer.normalize("D.C.").tokens)
    }

    @Test
    fun `undotted and dotted abbreviation forms normalize to the same token`() {
        assertEquals(AnswerNormalizer.normalize("USA").tokens, AnswerNormalizer.normalize("U.S.A.").tokens)
        assertEquals(AnswerNormalizer.normalize("USA").tokens, AnswerNormalizer.normalize("US").tokens)
        assertEquals(AnswerNormalizer.normalize("UK").tokens, AnswerNormalizer.normalize("U.K.").tokens)
        assertEquals(AnswerNormalizer.normalize("DC").tokens, AnswerNormalizer.normalize("D.C.").tokens)
    }

    @Test
    fun `ordinal numerals expand to spelled-out ordinal words`() {
        assertEquals(listOf("sixteenth"), AnswerNormalizer.normalize("16th").tokens)
        assertEquals(listOf("thirty", "fourth"), AnswerNormalizer.normalize("34th").tokens)
        assertEquals(listOf("twenty", "second"), AnswerNormalizer.normalize("22nd").tokens)
        assertEquals(listOf("thirty", "fourth"), AnswerNormalizer.normalize("thirty-fourth").tokens)
    }

    @Test
    fun `plural and verb suffixes stem to match their base form`() {
        assertEquals(listOf("honor"), AnswerNormalizer.normalize("honors").tokens)
        assertEquals(listOf("vote"), AnswerNormalizer.normalize("voting").tokens)
        assertEquals(listOf("library"), AnswerNormalizer.normalize("libraries").tokens)
        assertEquals(AnswerNormalizer.normalize("Allies").tokens, AnswerNormalizer.normalize("Allied").tokens)
    }

    @Test
    fun `stemming is skipped for person names to avoid corrupting surnames`() {
        // "Adams" must not stem down to "Adam" -- a different person entirely.
        val result = AnswerNormalizer.normalize("Adams", isPersonName = true)
        assertEquals(listOf("adams"), result.tokens)
    }

    @Test
    fun `double-s words are not mistaken for a plural suffix`() {
        assertEquals(listOf("congress"), AnswerNormalizer.normalize("Congress").tokens)
    }
}
