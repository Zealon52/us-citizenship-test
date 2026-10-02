package com.usctest.app.domain

import com.usctest.app.data.model.AnswerMode
import com.usctest.app.data.model.Question

/**
 * How a question's answer(s) should read on screen. The formal "any N of:" framing only earns
 * its place when the reader genuinely needs to know how many to memorize — everything else
 * should read like something a person would say, not a database dump.
 */
sealed interface AnswerDisplay {
    /** A single answer, or an ALL_OF answer joined into one sentence. No bullets, no label. */
    data class Plain(val text: String) : AnswerDisplay

    /** Several acceptable answers but only one is needed: lead with one, fold the rest in as an aside. */
    data class PrimaryWithAside(val primary: String, val others: List<String>) : AnswerDisplay

    /** N_OF with a large pool: show a couple of examples by default, the rest behind "+N more". */
    data class ExpandableExamples(
        val requiredCount: Int,
        val previewAnswers: List<String>,
        val allAnswers: List<String>,
        val hiddenCount: Int,
    ) : AnswerDisplay
}

object AnswerDisplayFormatter {

    fun format(question: Question, resolvedAnswers: List<String>): AnswerDisplay {
        if (resolvedAnswers.isEmpty()) return AnswerDisplay.Plain("")

        return when (question.answerMode) {
            AnswerMode.ALL_OF -> AnswerDisplay.Plain(joinWithAnd(resolvedAnswers))

            AnswerMode.ANY_OF -> if (resolvedAnswers.size == 1) {
                AnswerDisplay.Plain(resolvedAnswers.first())
            } else {
                AnswerDisplay.PrimaryWithAside(primary = resolvedAnswers.first(), others = resolvedAnswers.drop(1))
            }

            AnswerMode.N_OF -> {
                val previewSize = question.requiredCount + 1
                val preview = (question.previewAnswers?.takeIf { it.isNotEmpty() } ?: resolvedAnswers)
                    .take(previewSize)
                AnswerDisplay.ExpandableExamples(
                    requiredCount = question.requiredCount,
                    previewAnswers = preview,
                    allAnswers = resolvedAnswers,
                    hiddenCount = (resolvedAnswers.size - preview.size).coerceAtLeast(0),
                )
            }
        }
    }

    private fun joinWithAnd(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
    }
}
