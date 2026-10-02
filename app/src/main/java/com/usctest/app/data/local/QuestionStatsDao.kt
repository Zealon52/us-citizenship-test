package com.usctest.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.usctest.app.data.model.TestVersion
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionStatsDao {
    @Upsert
    suspend fun upsert(stats: QuestionStatsEntity)

    @Query("SELECT * FROM QuestionStatsEntity WHERE questionId = :questionId AND testVersion = :testVersion")
    suspend fun get(questionId: Int, testVersion: TestVersion): QuestionStatsEntity?

    @Query("SELECT * FROM QuestionStatsEntity WHERE testVersion = :testVersion")
    fun observeForVersion(testVersion: TestVersion): Flow<List<QuestionStatsEntity>>

    @Query("SELECT * FROM QuestionStatsEntity WHERE testVersion = :testVersion")
    suspend fun getForVersion(testVersion: TestVersion): List<QuestionStatsEntity>

    @Query("DELETE FROM QuestionStatsEntity")
    suspend fun clearAll()
}
