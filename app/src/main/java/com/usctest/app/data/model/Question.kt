package com.usctest.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: Int,
    val text: String,
    val acceptableAnswers: List<AcceptableAnswer>,
    val requiredCount: Int,
    val answerMode: AnswerMode,
    val answerType: AnswerType,
    val category: String,
    val subcategory: String? = null,
    val isStateSpecific: Boolean = false,
    val isEssential: Boolean = false,
    val distractors: List<String> = emptyList(),
    val answerNote: String? = null,
    val overlapsWith: List<Int> = emptyList(),
    /** For N_OF questions with a large answer pool: the best examples to show by default,
     * chosen at authoring time. Falls back to the first (requiredCount + 1) acceptable
     * answers if absent. */
    val previewAnswers: List<String>? = null,
)

@Serializable
data class AcceptableAnswer(
    val canonical: String,
    val variants: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    /** Keyword-tier match is suppressed if any of these also appear in the response -- for
     * keyword sets that are individually generic enough to also appear in a sibling question's
     * answer (e.g. Independence Day's "birthday" keyword also appears in Presidents Day's
     * parenthetical). Scoped narrowly to known collisions rather than a blanket rule. */
    val excludeKeywords: List<String> = emptyList(),
)

@Serializable
enum class AnswerMode { ANY_OF, N_OF, ALL_OF }

@Serializable
enum class AnswerType {
    PERSON, NUMBER, DATE, DOCUMENT, PLACE, RIGHT,
    RESPONSIBILITY, CONCEPT, WAR, BRANCH, SYMBOL, HOLIDAY, OTHER,
}

/** Which USCIS question bank is active, keyed by N-400 filing date. */
@Serializable
enum class TestVersion { V2008, V2025 }
