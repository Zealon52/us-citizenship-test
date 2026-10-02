package com.usctest.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.usctest.app.data.model.TestVersion
import kotlinx.coroutines.flow.Flow

@Dao
interface AttemptDao {
    @Insert
    suspend fun insert(attempt: AttemptEntity): Long

    @Update
    suspend fun update(attempt: AttemptEntity)

    @Query("SELECT * FROM AttemptEntity ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM AttemptEntity ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<AttemptEntity>

    @Query("DELETE FROM AttemptEntity")
    suspend fun clearAll()
}

@Dao
interface AttemptAnswerDao {
    @Insert
    suspend fun insertAll(answers: List<AttemptAnswerEntity>)

    @Query("SELECT * FROM AttemptAnswerEntity WHERE attemptId = :attemptId")
    suspend fun getForAttempt(attemptId: Long): List<AttemptAnswerEntity>

    /** Missed questions for one test version, most-missed and least-recently-seen first — feeds
     * Review Missed. Joined against [AttemptEntity] because question IDs are only unique within
     * a single bank (2008 vs. 2025 each number their own questions from 1). */
    @Query(
        """
        SELECT aa.questionId as questionId, COUNT(*) as missCount, MAX(aa.id) as lastMissId
        FROM AttemptAnswerEntity aa
        INNER JOIN AttemptEntity a ON aa.attemptId = a.id
        WHERE aa.wasCorrect = 0 AND a.testVersion = :testVersion
        GROUP BY aa.questionId
        ORDER BY missCount DESC, lastMissId ASC
        """,
    )
    suspend fun getMissedQuestionSummaries(testVersion: TestVersion): List<MissedQuestionSummary>

    @Query("DELETE FROM AttemptAnswerEntity")
    suspend fun clearAll()
}

data class MissedQuestionSummary(
    val questionId: Int,
    val missCount: Int,
    val lastMissId: Long,
)
