package com.usctest.app.domain

import com.usctest.app.data.local.AttemptMode
import com.usctest.app.data.local.MasteryLevel
import com.usctest.app.data.local.QuestionStatsEntity

/**
 * Leitner-style mastery banding. Recall answers are weighted above MCQ answers in both
 * directions — the app believes the harder, more diagnostic skill more. A correct recall
 * answer counts double toward promotion; a wrong recall answer demotes twice as far.
 */
object MasteryCalculator {

    private const val RECALL_WEIGHT = 2
    private const val MCQ_WEIGHT = 1

    private const val LEARNING_THRESHOLD = 1
    private const val FAMILIAR_THRESHOLD = 3
    private const val MASTERED_THRESHOLD = 5

    fun applyResult(
        current: QuestionStatsEntity,
        mode: AttemptMode,
        correct: Boolean,
        timestampMillis: Long,
    ): QuestionStatsEntity {
        val weight = if (mode == AttemptMode.RECALL) RECALL_WEIGHT else MCQ_WEIGHT

        return if (correct) {
            val newStreak = current.consecutiveCorrect + weight
            current.copy(
                timesSeen = current.timesSeen + 1,
                timesCorrect = current.timesCorrect + 1,
                lastSeenAt = timestampMillis,
                consecutiveCorrect = newStreak,
                masteryLevel = levelForStreak(newStreak),
                recallCorrect = current.recallCorrect + if (mode == AttemptMode.RECALL) 1 else 0,
                mcqCorrect = current.mcqCorrect + if (mode == AttemptMode.MCQ) 1 else 0,
            )
        } else {
            val demoteSteps = if (mode == AttemptMode.RECALL) 2 else 1
            val newLevel = demote(current.masteryLevel, demoteSteps)
            current.copy(
                timesSeen = current.timesSeen + 1,
                timesWrong = current.timesWrong + 1,
                lastSeenAt = timestampMillis,
                consecutiveCorrect = 0,
                masteryLevel = newLevel,
                recallWrong = current.recallWrong + if (mode == AttemptMode.RECALL) 1 else 0,
                mcqWrong = current.mcqWrong + if (mode == AttemptMode.MCQ) 1 else 0,
            )
        }
    }

    private fun levelForStreak(streak: Int): MasteryLevel = when {
        streak >= MASTERED_THRESHOLD -> MasteryLevel.MASTERED
        streak >= FAMILIAR_THRESHOLD -> MasteryLevel.FAMILIAR
        streak >= LEARNING_THRESHOLD -> MasteryLevel.LEARNING
        else -> MasteryLevel.NEW
    }

    private fun demote(level: MasteryLevel, steps: Int): MasteryLevel {
        val levels = MasteryLevel.entries
        val newOrdinal = (level.ordinal - steps).coerceAtLeast(0)
        return levels[newOrdinal]
    }
}
