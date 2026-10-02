package com.usctest.app.domain

import com.usctest.app.data.model.AcceptableAnswer
import com.usctest.app.data.model.AnswerMode

data class GradeResult(
    val isCorrect: Boolean,
    val matchedAnswers: List<AcceptableAnswer>,
)

/**
 * Grades one free-text response (typed or a corrected ASR transcript) against a question's
 * acceptable answers. This is the highest-stakes logic in the app: a false accept tells someone
 * they're ready for a real interview when they aren't, which is worse than a false reject
 * (self-correctable, low stakes). Every threshold here is chosen to favor false-reject over
 * false-accept — see the "Answer grading" section of the app plan for the full policy.
 *
 * Deliberately decoupled from [com.usctest.app.data.model.Question] — state-specific questions
 * resolve their acceptable answers at runtime (from officials data), so this takes an already
 * -resolved answer list rather than a Question directly.
 */
object AnswerGrader {

    // Conservative on purpose: 1 edit absorbs a typo or a single ASR phoneme slip, no more.
    private const val SINGLE_TOKEN_MAX_EDIT_DISTANCE = 1

    fun grade(
        responseText: String,
        acceptableAnswers: List<AcceptableAnswer>,
        answerMode: AnswerMode,
        requiredCount: Int,
        isPersonAnswer: Boolean = false,
    ): GradeResult {
        val response = AnswerNormalizer.normalize(responseText, isPersonName = isPersonAnswer)
        val matched = acceptableAnswers.filter { matches(response, it, isPersonAnswer) }

        val isCorrect = when (answerMode) {
            AnswerMode.ANY_OF -> matched.isNotEmpty()
            AnswerMode.N_OF -> matched.size >= requiredCount
            AnswerMode.ALL_OF -> acceptableAnswers.isNotEmpty() && matched.size == acceptableAnswers.size
        }

        return GradeResult(isCorrect, matched)
    }

    private fun matches(response: NormalizedText, answer: AcceptableAnswer, isPersonAnswer: Boolean): Boolean {
        // excludeKeywords vetoes this whole answer, across every tier -- not just the keyword
        // tier. A short variant/canonical form can still collide with a sibling question's wrong
        // answer via the *fuzzy* tier's "one missing token is fine" tolerance (e.g. Veterans
        // Day's 4-token variant "honors people in the military" fuzzy-matches Memorial Day's
        // wrong answer on 3 of 4 tokens) -- see [AcceptableAnswer.excludeKeywords].
        if (answer.excludeKeywords.isNotEmpty()) {
            val excludeTokens = AnswerNormalizer.normalize(answer.excludeKeywords.joinToString(" "), isPersonAnswer).tokenSet
            if (excludeTokens.any { it in response.tokenSet }) return false
        }

        val forms = (listOf(answer.canonical) + answer.variants)
            .map { AnswerNormalizer.normalize(it, isPersonAnswer) }
            .filter { it.tokens.isNotEmpty() }

        // Tier 1: exact — every token of a canonical/variant form is literally present in the response.
        if (forms.any { isSubset(it.tokenSet, response.tokenSet) }) return true

        // Tier 2: keyword — looser, partial-identity match ("I think it's the Missouri River" -> "missouri").
        if (answer.keywords.isNotEmpty()) {
            val keywordTokens = AnswerNormalizer.normalize(answer.keywords.joinToString(" "), isPersonAnswer).tokenSet
            if (isSubset(keywordTokens, response.tokenSet)) return true
        }

        // Tier 3: fuzzy, scoped by answer shape — typos and ASR noise only, not paraphrase coverage.
        return forms.any { fuzzyMatches(it, response) }
    }

    private fun isSubset(required: Set<String>, present: Set<String>): Boolean =
        required.isNotEmpty() && required.all { it in present }

    private fun fuzzyMatches(answerForm: NormalizedText, response: NormalizedText): Boolean {
        if (answerForm.tokens.isEmpty()) return false

        return if (answerForm.tokens.size == 1) {
            val target = answerForm.tokens.first()
            response.tokens.any { levenshtein(it, target) <= SINGLE_TOKEN_MAX_EDIT_DISTANCE }
        } else {
            // Multi-word: count answer tokens found near-verbatim anywhere in the response.
            // Short (<=2 token) answers require every token matched — half of a two-word answer
            // isn't "close enough." Longer answers tolerate exactly one missing/mismatched token.
            val matchedTokenCount = answerForm.tokens.count { answerToken ->
                response.tokens.any { levenshtein(it, answerToken) <= SINGLE_TOKEN_MAX_EDIT_DISTANCE }
            }
            val requiredMatches = if (answerForm.tokens.size <= 2) answerForm.tokens.size else answerForm.tokens.size - 1
            matchedTokenCount >= requiredMatches
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }
}
