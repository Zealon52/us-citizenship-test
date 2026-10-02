package com.usctest.app.domain

import com.usctest.app.data.model.AcceptableAnswer
import com.usctest.app.data.model.Question
import com.usctest.app.data.model.StateOfficials

/**
 * Resolves a state-specific question's answer against the user's selected state, by matching
 * keywords in the question text. Only "senator" exists in the current sample content, but this
 * is written generally since governor/representative/capital questions are added in the full
 * content-authoring pass.
 */
object StateAnswerResolver {
    fun resolve(question: Question, officials: StateOfficials?, selectedRepresentative: String?): List<String> {
        if (!question.isStateSpecific || officials == null) return emptyList()
        val text = question.text.lowercase()
        return when {
            "senator" in text -> if (officials.hasNoVotingSenators) {
                listOf("No U.S. Senators (DC / territory)")
            } else {
                officials.senators
            }
            "representative" in text -> listOfNotNull(selectedRepresentative).ifEmpty { officials.representatives }
            "governor" in text -> listOf(officials.governor)
            "capital" in text -> listOf(officials.capital)
            else -> emptyList()
        }
    }

    /** Final answer list for grading/display: resolved officials data for state-specific
     * questions, or the question's own authored answers otherwise. */
    fun resolveAcceptableAnswers(
        question: Question,
        officials: StateOfficials?,
        selectedRepresentative: String?,
    ): List<AcceptableAnswer> = if (question.isStateSpecific) {
        resolve(question, officials, selectedRepresentative).map { AcceptableAnswer(canonical = it) }
    } else {
        question.acceptableAnswers
    }
}
