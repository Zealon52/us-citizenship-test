package com.usctest.app.data

import com.usctest.app.data.local.MasteryLevel
import com.usctest.app.data.model.Question
import com.usctest.app.domain.AnswerNormalizer
import com.usctest.app.domain.DistractorProvider
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the bundled question banks directly off disk (no Android context needed -- these
 * are plain JSON assets). Guards the content invariants from the app plan's Verification
 * section: every question has a usable answer set, and no distractor can ever be mistaken for
 * a genuinely correct answer.
 */
class QuestionBankContentTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val validCategories = setOf("Government", "History", "Integrated Civics")

    private fun loadBank(fileName: String): List<Question> {
        val file = File("src/main/assets/$fileName")
        return json.decodeFromString<List<Question>>(file.readText())
    }

    private fun assertBankSize(fileName: String, expectedSize: Int) {
        val bank = loadBank(fileName)
        assertTrue("expected $expectedSize questions, found ${bank.size}", bank.size == expectedSize)
        assertTrue("duplicate question ids found", bank.map { it.id }.distinct().size == bank.size)
    }

    private fun assertValidShape(fileName: String) {
        val bank = loadBank(fileName)
        for (q in bank) {
            assertTrue("Q${q.id}: blank text", q.text.isNotBlank())
            assertTrue("Q${q.id}: invalid category '${q.category}'", q.category in validCategories)
            if (!q.isStateSpecific) {
                assertTrue("Q${q.id}: no acceptable answers", q.acceptableAnswers.isNotEmpty())
                assertTrue(
                    "Q${q.id}: requiredCount ${q.requiredCount} exceeds ${q.acceptableAnswers.size} answers",
                    q.requiredCount <= q.acceptableAnswers.size,
                )
            }
        }
    }

    private fun assertNoDistractorCollisions(fileName: String) {
        val bank = loadBank(fileName)
        val violations = mutableListOf<String>()

        for (q in bank) {
            if (q.isStateSpecific) continue
            for (mastery in listOf(MasteryLevel.NEW, MasteryLevel.MASTERED)) {
                val distractors = DistractorProvider.distractorsFor(q, q.acceptableAnswers, bank, mastery)
                val ownAndOverlapAnswers = (listOf(q) + bank.filter { it.id in q.overlapsWith })
                    .flatMap { it.acceptableAnswers }
                    .flatMap { listOf(it.canonical) + it.variants }
                    // applyStemming = false to match DistractorProvider's own dedup key -- this
                    // invariant is about distractor/answer text overlap, not grading leniency, so
                    // it shouldn't inherit AnswerGrader's broader stemmed-equivalence classes.
                    .map { AnswerNormalizer.normalize(it, applyStemming = false).joined }
                    .toSet()

                for (d in distractors) {
                    if (AnswerNormalizer.normalize(d, applyStemming = false).joined in ownAndOverlapAnswers) {
                        violations += "Q${q.id} ($mastery): distractor '$d' collides with a correct answer"
                    }
                }
            }
        }

        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun `2008 bank has 100 questions with unique ids`() = assertBankSize("questions_2008.json", 100)

    @Test
    fun `2008 bank questions have valid shape`() = assertValidShape("questions_2008.json")

    @Test
    fun `2008 bank distractors never collide with a correct answer, at any mastery level`() =
        assertNoDistractorCollisions("questions_2008.json")

    @Test
    fun `2025 bank has 128 questions with unique ids`() = assertBankSize("questions_2025.json", 128)

    @Test
    fun `2025 bank questions have valid shape`() = assertValidShape("questions_2025.json")

    @Test
    fun `2025 bank distractors never collide with a correct answer, at any mastery level`() =
        assertNoDistractorCollisions("questions_2025.json")
}
