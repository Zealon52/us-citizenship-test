package com.usctest.app.data

import com.usctest.app.data.local.AttemptAnswerDao
import com.usctest.app.data.local.AttemptAnswerEntity
import com.usctest.app.data.local.AttemptDao
import com.usctest.app.data.local.AttemptEntity
import com.usctest.app.data.local.AttemptMode
import com.usctest.app.data.local.MasteryLevel
import com.usctest.app.data.local.QuestionStatsDao
import com.usctest.app.data.local.QuestionStatsEntity
import com.usctest.app.data.model.TestVersion
import com.usctest.app.domain.ExamRules
import com.usctest.app.domain.MasteryCalculator
import com.usctest.app.domain.ReadinessEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

/** Practice accuracy for one mode -- "attempted" distinguishes "never tried" from "tried and got 0%." */
data class ModeAccuracy(val percent: Int, val attempted: Boolean)

/** One answer to be recorded as part of finishing an attempt. */
data class AnswerRecord(
    val questionId: Int,
    val wasCorrect: Boolean,
    val submittedText: String? = null,
    val inputMethod: com.usctest.app.data.local.InputMethod? = null,
)

data class CategoryAccuracy(
    val category: String,
    val correct: Int,
    val total: Int,
) {
    val accuracyPercent: Int get() = if (total == 0) 0 else (correct * 100) / total
}

class ProgressRepository(
    private val questionStatsDao: QuestionStatsDao,
    private val attemptDao: AttemptDao,
    private val attemptAnswerDao: AttemptAnswerDao,
    private val questionRepository: QuestionRepository,
) {
    /** Records a finished attempt: the attempt row, every answer, and updated mastery per question. */
    suspend fun recordAttempt(
        testVersion: TestVersion,
        mode: AttemptMode,
        startedAt: Long,
        finishedAt: Long,
        passed: Boolean,
        endedEarly: Boolean,
        answers: List<AnswerRecord>,
    ): Long = withContext(Dispatchers.IO) {
        val attemptId = attemptDao.insert(
            AttemptEntity(
                testVersion = testVersion,
                mode = mode,
                startedAt = startedAt,
                finishedAt = finishedAt,
                questionCount = answers.size,
                correctCount = answers.count { it.wasCorrect },
                passed = passed,
                endedEarly = endedEarly,
            ),
        )

        attemptAnswerDao.insertAll(
            answers.map {
                AttemptAnswerEntity(
                    attemptId = attemptId,
                    questionId = it.questionId,
                    wasCorrect = it.wasCorrect,
                    submittedText = it.submittedText,
                    inputMethod = it.inputMethod,
                )
            },
        )

        for (answer in answers) {
            val current = questionStatsDao.get(answer.questionId, testVersion)
                ?: QuestionStatsEntity(questionId = answer.questionId, testVersion = testVersion)
            val updated = MasteryCalculator.applyResult(current, mode, answer.wasCorrect, finishedAt)
            questionStatsDao.upsert(updated)
        }

        attemptId
    }

    suspend fun getMastery(questionId: Int, testVersion: TestVersion): MasteryLevel =
        questionStatsDao.get(questionId, testVersion)?.masteryLevel ?: MasteryLevel.NEW

    /** Flash Cards' self-reported "I know this" — an unverified self-assessment, so it's applied
     * at MCQ weight (the weaker signal), never Recall weight, and isn't part of a formal Attempt. */
    suspend fun markKnown(questionId: Int, testVersion: TestVersion) = withContext(Dispatchers.IO) {
        val current = questionStatsDao.get(questionId, testVersion)
            ?: QuestionStatsEntity(questionId = questionId, testVersion = testVersion)
        val updated = MasteryCalculator.applyResult(current, AttemptMode.MCQ, correct = true, System.currentTimeMillis())
        questionStatsDao.upsert(updated)
    }

    suspend fun getAllStats(testVersion: TestVersion): List<QuestionStatsEntity> =
        questionStatsDao.getForVersion(testVersion)

    /** Missed questions for one test version, ranked by miss count then recency, for Review
     * Missed. Excludes questions since promoted to [MasteryLevel.MASTERED] — those have already
     * been drilled back to strength and don't need another review pass. */
    suspend fun getMissedQuestionIds(testVersion: TestVersion): List<Int> = withContext(Dispatchers.IO) {
        val mastered = questionStatsDao.getForVersion(testVersion)
            .filter { it.masteryLevel == MasteryLevel.MASTERED }
            .map { it.questionId }
            .toSet()
        attemptAnswerDao.getMissedQuestionSummaries(testVersion)
            .map { it.questionId }
            .filterNot { it in mastered }
    }

    suspend fun getCategoryAccuracy(testVersion: TestVersion): List<CategoryAccuracy> = withContext(Dispatchers.IO) {
        val stats = questionStatsDao.getForVersion(testVersion).associateBy { it.questionId }
        val questions = questionRepository.getQuestions(testVersion)

        questions
            .groupBy { it.category }
            .map { (category, questionsInCategory) ->
                var correct = 0
                var total = 0
                for (question in questionsInCategory) {
                    val s = stats[question.id] ?: continue
                    correct += s.timesCorrect
                    total += s.timesCorrect + s.timesWrong
                }
                CategoryAccuracy(category, correct, total)
            }
            .filter { it.total > 0 }
    }

    /** Branded to the user as their "Confidence Score" -- see [ReadinessEstimator]. */
    suspend fun getConfidenceEstimate(testVersion: TestVersion, rules: ExamRules): ReadinessEstimator.Result =
        withContext(Dispatchers.IO) {
            val bank = questionRepository.getQuestions(testVersion)
            val statsById = questionStatsDao.getForVersion(testVersion).associateBy { it.questionId }

            val accuracies = bank.map { question ->
                val stats = statsById[question.id]
                val probability = stats?.let {
                    val weightedTotal = (it.recallCorrect + it.recallWrong) * 2.0 + it.mcqCorrect + it.mcqWrong
                    if (weightedTotal == 0.0) null else (it.recallCorrect * 2.0 + it.mcqCorrect) / weightedTotal
                }
                ReadinessEstimator.QuestionAccuracy(question.category, probability)
            }

            ReadinessEstimator.estimate(accuracies, rules)
        }

    /** Recall Mode and Practice Test accuracy, tracked separately since they measure different skills. */
    suspend fun getModeAccuracy(testVersion: TestVersion): Pair<ModeAccuracy, ModeAccuracy> = withContext(Dispatchers.IO) {
        val stats = questionStatsDao.getForVersion(testVersion)
        val recallCorrect = stats.sumOf { it.recallCorrect }
        val recallTotal = stats.sumOf { it.recallCorrect + it.recallWrong }
        val mcqCorrect = stats.sumOf { it.mcqCorrect }
        val mcqTotal = stats.sumOf { it.mcqCorrect + it.mcqWrong }

        val recall = ModeAccuracy(percent = if (recallTotal == 0) 0 else (recallCorrect * 100) / recallTotal, attempted = recallTotal > 0)
        val practice = ModeAccuracy(percent = if (mcqTotal == 0) 0 else (mcqCorrect * 100) / mcqTotal, attempted = mcqTotal > 0)
        recall to practice
    }

    /** Counts per mastery level across the whole bank -- unattempted questions count as [MasteryLevel.NEW]. */
    suspend fun getMasteryBreakdown(testVersion: TestVersion): Map<MasteryLevel, Int> = withContext(Dispatchers.IO) {
        val bank = questionRepository.getQuestions(testVersion)
        val statsById = questionStatsDao.getForVersion(testVersion).associateBy { it.questionId }
        bank.groupingBy { statsById[it.id]?.masteryLevel ?: MasteryLevel.NEW }.eachCount()
    }

    /** Consecutive days (ending today or yesterday -- a day off doesn't reset it until the second
     * missed day) with at least one recorded attempt. Derived from [AttemptEntity] timestamps
     * directly rather than a separate daily-activity log, since nothing populates one yet. */
    suspend fun getCurrentStreakDays(): Int = withContext(Dispatchers.IO) {
        val zone = TimeZone.currentSystemDefault()
        val attemptDays = attemptDao.getRecent(limit = 500)
            .map { Instant.fromEpochMilliseconds(it.startedAt).toLocalDateTime(zone).date }
            .distinct()
            .sortedDescending()

        val today = Clock.System.todayIn(zone)
        var cursor = when (attemptDays.firstOrNull()) {
            today -> today
            today.minus(1, DateTimeUnit.DAY) -> today.minus(1, DateTimeUnit.DAY)
            else -> return@withContext 0
        }

        var streak = 0
        for (day in attemptDays) {
            if (day != cursor) break
            streak++
            cursor = cursor.minus(1, DateTimeUnit.DAY)
        }
        streak
    }

    suspend fun resetProgress() = withContext(Dispatchers.IO) {
        questionStatsDao.clearAll()
        attemptDao.clearAll()
        attemptAnswerDao.clearAll()
    }
}
