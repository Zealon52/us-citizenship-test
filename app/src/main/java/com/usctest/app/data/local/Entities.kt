package com.usctest.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.usctest.app.data.model.TestVersion

enum class MasteryLevel { NEW, LEARNING, FAMILIAR, MASTERED }

enum class AttemptMode { RECALL, MCQ }

enum class InputMethod { TYPED, DICTATED, DICTATED_EDITED }

/** Per-question mastery tracking, split by Recall vs. MCQ performance since they measure different skills. */
@Entity(primaryKeys = ["questionId", "testVersion"])
data class QuestionStatsEntity(
    val questionId: Int,
    val testVersion: TestVersion,
    val timesSeen: Int = 0,
    val timesCorrect: Int = 0,
    val timesWrong: Int = 0,
    val lastSeenAt: Long? = null,
    val consecutiveCorrect: Int = 0,
    val masteryLevel: MasteryLevel = MasteryLevel.NEW,
    val recallCorrect: Int = 0,
    val recallWrong: Int = 0,
    val mcqCorrect: Int = 0,
    val mcqWrong: Int = 0,
)

/** One full practice/recall session. */
@Entity
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val testVersion: TestVersion,
    val mode: AttemptMode,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val questionCount: Int = 0,
    val correctCount: Int = 0,
    val passed: Boolean = false,
    val endedEarly: Boolean = false,
)

/** One answered question within an attempt. inputMethod is diagnostics only — never affects scoring. */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = AttemptEntity::class,
            parentColumns = ["id"],
            childColumns = ["attemptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("attemptId")],
)
data class AttemptAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val attemptId: Long,
    val questionId: Int,
    val wasCorrect: Boolean,
    val submittedText: String? = null,
    val inputMethod: InputMethod? = null,
)

/** Optional study timeline, driven by the user's test date. Single row (id = 0). */
@Entity
data class StudyPlanEntity(
    @PrimaryKey val id: Int = 0,
    val testDate: String? = null,
    val filingDate: String? = null,
    val createdAt: Long,
    val phase: String,
    val dailyNewTarget: Int,
    val dailyReviewTarget: Int,
)

/** Daily study activity log, powering the streak counter and calendar heat strip. */
@Entity
data class StudySessionEntity(
    @PrimaryKey val date: String,
    val questionsStudied: Int = 0,
    val minutesStudied: Int = 0,
    val goalMet: Boolean = false,
)
