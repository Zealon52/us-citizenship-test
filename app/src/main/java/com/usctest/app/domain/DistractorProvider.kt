package com.usctest.app.domain

import com.usctest.app.data.local.MasteryLevel
import com.usctest.app.data.model.AcceptableAnswer
import com.usctest.app.data.model.Question

/**
 * Chooses multiple-choice distractors for a question, per the app plan's resolution order:
 * curated -> caller-supplied preferred candidates -> same-[com.usctest.app.data.model.AnswerType]
 * slotting -> same-category sampling -> (last resort, since the bundled bank is still sample
 * content, not the full 100/128 questions) any other question's answers. `preferredDistractors`
 * exists for slots the question bank alone can't source well -- e.g. a state-specific senator
 * question should draw its wrong options from *other states'* real senators (via
 * [com.usctest.app.data.OfficialsRepository]), not from whatever unrelated answerType the tiny
 * sample bank happens to have lying around. Adaptive difficulty reads mastery: unfamiliar
 * questions get easy, clearly-wrong options; familiar/mastered questions get near-miss options
 * from the same slot.
 *
 * Hard invariant: a distractor must never equal (case/punctuation-insensitive) any acceptable
 * answer -- canonical or variant -- of this question or of any question it overlaps with. Getting
 * marked wrong for a genuinely correct answer is the one failure this app must never produce.
 */
object DistractorProvider {

    private const val REQUIRED = 3

    fun distractorsFor(
        question: Question,
        resolvedAnswers: List<AcceptableAnswer>,
        allQuestions: List<Question>,
        masteryLevel: MasteryLevel,
        preferredDistractors: List<String> = emptyList(),
        required: Int = REQUIRED,
    ): List<String> {
        val overlapIds = overlapSetFor(question, allQuestions)
        val excluded = exclusionTexts(question, resolvedAnswers, allQuestions, overlapIds)
        val correctTexts = resolvedAnswers.map { it.canonical }

        val picked = mutableListOf<String>()
        val pickedKeys = mutableSetOf<String>()

        fun tryAdd(text: String) {
            val key = normalizedKey(text)
            if (key.isBlank() || key in excluded || key in pickedKeys) return
            picked += text
            pickedKeys += key
        }

        question.distractors.forEach(::tryAdd)

        // Caller-supplied same-slot candidates that beat generic slotting -- e.g. other states'
        // senators for a state-specific senator question. Real names, not sample-bank filler.
        if (picked.size < required) {
            preferredDistractors.shuffled().forEach { if (picked.size < required) tryAdd(it) }
        }

        if (picked.size < required) {
            val sameType = candidatesFrom(allQuestions, question.id, overlapIds) { it.answerType == question.answerType }
            rank(sameType, correctTexts, masteryLevel).forEach { if (picked.size < required) tryAdd(it) }
        }
        if (picked.size < required) {
            val sameCategory = candidatesFrom(allQuestions, question.id, overlapIds) { it.category == question.category }
            rank(sameCategory, correctTexts, masteryLevel).forEach { if (picked.size < required) tryAdd(it) }
        }
        if (picked.size < required) {
            val anyOther = candidatesFrom(allQuestions, question.id, overlapIds) { true }
            rank(anyOther, correctTexts, masteryLevel).forEach { if (picked.size < required) tryAdd(it) }
        }

        return picked.take(required)
    }

    private fun overlapSetFor(question: Question, allQuestions: List<Question>): Set<Int> {
        val declared = question.overlapsWith.toSet()
        val reverseDeclared = allQuestions.filter { question.id in it.overlapsWith }.map { it.id }
        return declared + reverseDeclared
    }

    private fun exclusionTexts(
        question: Question,
        resolvedAnswers: List<AcceptableAnswer>,
        allQuestions: List<Question>,
        overlapIds: Set<Int>,
    ): Set<String> {
        val overlapAnswers = allQuestions.filter { it.id in overlapIds }.flatMap { it.acceptableAnswers }
        return (question.acceptableAnswers + resolvedAnswers + overlapAnswers)
            .flatMap { listOf(it.canonical) + it.variants }
            .map(::normalizedKey)
            .toSet()
    }

    private data class Candidate(val text: String, val subcategory: String?)

    private fun candidatesFrom(
        allQuestions: List<Question>,
        excludeQuestionId: Int,
        overlapIds: Set<Int>,
        predicate: (Question) -> Boolean,
    ): List<Candidate> = allQuestions
        .filter { it.id != excludeQuestionId && it.id !in overlapIds && predicate(it) }
        .flatMap { q -> q.acceptableAnswers.map { Candidate(it.canonical, q.subcategory) } }

    /** Orders candidates by closeness to the correct answer(s) -- near-miss first for
     * familiar/mastered questions (hardens the test), easy/distinguishable first otherwise. Also
     * shuffles same-score ties so a session doesn't always surface the same option in the same slot. */
    private fun rank(candidates: List<Candidate>, correctTexts: List<String>, masteryLevel: MasteryLevel): List<String> {
        val nearMissFirst = masteryLevel == MasteryLevel.FAMILIAR || masteryLevel == MasteryLevel.MASTERED
        val scored = candidates.shuffled().map { it to similarity(it.text, correctTexts) }
        val ordered = if (nearMissFirst) scored.sortedByDescending { it.second } else scored.sortedBy { it.second }
        return ordered.map { it.first.text }.distinct()
    }

    private fun similarity(text: String, correctTexts: List<String>): Int {
        val candidateTokens = AnswerNormalizer.normalize(text, applyStemming = false).tokenSet
        return correctTexts.maxOfOrNull { correct ->
            candidateTokens.intersect(AnswerNormalizer.normalize(correct, applyStemming = false).tokenSet).size
        } ?: 0
    }

    // applyStemming = false: this key drives distractor dedup/collision, not grading -- see
    // AnswerNormalizer.normalize's doc for why stemming's broader equivalence classes shouldn't
    // reach into distractor pool sizing.
    private fun normalizedKey(text: String): String = AnswerNormalizer.normalize(text, applyStemming = false).joined
}
