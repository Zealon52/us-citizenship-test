package com.usctest.app.domain

import com.usctest.app.data.local.MasteryLevel
import com.usctest.app.data.model.AcceptableAnswer
import com.usctest.app.data.model.AnswerMode
import com.usctest.app.data.model.AnswerType
import com.usctest.app.data.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistractorProviderTest {

    private fun question(
        id: Int,
        answers: List<String>,
        answerType: AnswerType = AnswerType.CONCEPT,
        category: String = "Government",
        subcategory: String? = null,
        distractors: List<String> = emptyList(),
        overlapsWith: List<Int> = emptyList(),
    ) = Question(
        id = id,
        text = "Question $id",
        acceptableAnswers = answers.map { AcceptableAnswer(canonical = it) },
        requiredCount = 1,
        answerMode = AnswerMode.ANY_OF,
        answerType = answerType,
        category = category,
        subcategory = subcategory,
        distractors = distractors,
        overlapsWith = overlapsWith,
    )

    // --- Curated path ---

    @Test
    fun `curated distractors are used when present and clean`() {
        val target = question(1, listOf("the Senate"), distractors = listOf("the House", "the Supreme Court", "the President"))
        val bank = listOf(target)

        val result = DistractorProvider.distractorsFor(target, target.acceptableAnswers, bank, MasteryLevel.NEW)

        assertEquals(setOf("the House", "the Supreme Court", "the President"), result.toSet())
    }

    @Test
    fun `curated distractor colliding with own answer is dropped and backfilled`() {
        val target = question(
            id = 1,
            answers = listOf("the Senate"),
            distractors = listOf("the Senate", "the Supreme Court", "the President"),
        )
        val filler = question(2, listOf("the House"), answerType = target.answerType)
        val bank = listOf(target, filler)

        val result = DistractorProvider.distractorsFor(target, target.acceptableAnswers, bank, MasteryLevel.NEW)

        assertFalse(result.contains("the Senate"))
        assertEquals(3, result.size)
    }

    // --- Hard invariant ---

    @Test
    fun `distractors never collide with own acceptable answers or variants`() {
        val target = question(1, listOf("the Senate", "the House"))
        val decoyWithVariant = Question(
            id = 2,
            text = "decoy",
            acceptableAnswers = listOf(AcceptableAnswer(canonical = "Congress", variants = listOf("the Senate"))),
            requiredCount = 1,
            answerMode = AnswerMode.ANY_OF,
            answerType = target.answerType,
            category = target.category,
        )
        val filler = List(5) { question(10 + it, listOf("Option $it"), answerType = target.answerType) }
        val bank = listOf(target, decoyWithVariant) + filler

        val result = DistractorProvider.distractorsFor(target, target.acceptableAnswers, bank, MasteryLevel.NEW)

        assertFalse(result.any { it.equals("the Senate", ignoreCase = true) })
        assertFalse(result.any { it.equals("the House", ignoreCase = true) })
    }

    @Test
    fun `distractors never collide with acceptable answers of overlapping questions`() {
        val target = question(1, listOf("speech"), category = "Government", overlapsWith = listOf(2))
        val overlapping = question(2, listOf("freedom of speech", "assembly"), category = "Government")
        val filler = List(5) { question(10 + it, listOf("Option $it"), answerType = target.answerType) }
        val bank = listOf(target, overlapping) + filler

        val result = DistractorProvider.distractorsFor(target, target.acceptableAnswers, bank, MasteryLevel.NEW)

        assertFalse(result.any { it.equals("freedom of speech", ignoreCase = true) })
        assertFalse(result.any { it.equals("assembly", ignoreCase = true) })
    }

    @Test
    fun `overlap exclusion is symmetric even when only declared on the other question`() {
        val overlapping = question(2, listOf("assembly"), category = "Government", overlapsWith = listOf(1))
        val target = question(1, listOf("speech"), category = "Government")
        val filler = List(5) { question(10 + it, listOf("Option $it"), answerType = target.answerType) }
        val bank = listOf(target, overlapping) + filler

        val result = DistractorProvider.distractorsFor(target, target.acceptableAnswers, bank, MasteryLevel.NEW)

        assertFalse(result.any { it.equals("assembly", ignoreCase = true) })
    }

    // --- Resolution order fallbacks ---

    @Test
    fun `falls back to same category when same answer type is exhausted`() {
        val target = question(1, listOf("the Constitution"), answerType = AnswerType.DOCUMENT, category = "Government")
        val sameCategoryDifferentType = question(2, listOf("the President"), answerType = AnswerType.PERSON, category = "Government")
        val bank = listOf(target, sameCategoryDifferentType)

        val result = DistractorProvider.distractorsFor(target, target.acceptableAnswers, bank, MasteryLevel.NEW)

        assertTrue(result.contains("the President"))
    }

    @Test
    fun `falls back to any other question when bank is too small for type or category slotting`() {
        val target = question(1, listOf("the Constitution"), answerType = AnswerType.DOCUMENT, category = "Government")
        val onlyOtherQuestion = question(2, listOf("Thanksgiving"), answerType = AnswerType.HOLIDAY, category = "History")
        val bank = listOf(target, onlyOtherQuestion)

        val result = DistractorProvider.distractorsFor(target, target.acceptableAnswers, bank, MasteryLevel.NEW)

        assertTrue(result.contains("Thanksgiving"))
    }

    @Test
    fun `never returns more than three distractors`() {
        val target = question(1, listOf("the Constitution"))
        val filler = List(10) { question(10 + it, listOf("Option $it"), answerType = target.answerType) }
        val bank = listOf(target) + filler

        val result = DistractorProvider.distractorsFor(target, target.acceptableAnswers, bank, MasteryLevel.MASTERED)

        assertTrue(result.size <= 3)
    }
}
