package com.usctest.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyPlanDao {
    @Upsert
    suspend fun upsert(plan: StudyPlanEntity)

    @Query("SELECT * FROM StudyPlanEntity WHERE id = 0")
    fun observe(): Flow<StudyPlanEntity?>

    @Query("SELECT * FROM StudyPlanEntity WHERE id = 0")
    suspend fun get(): StudyPlanEntity?

    @Query("DELETE FROM StudyPlanEntity")
    suspend fun clear()
}

@Dao
interface StudySessionDao {
    @Upsert
    suspend fun upsert(session: StudySessionEntity)

    @Query("SELECT * FROM StudySessionEntity WHERE date = :date")
    suspend fun get(date: String): StudySessionEntity?

    @Query("SELECT * FROM StudySessionEntity ORDER BY date DESC")
    fun observeAll(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM StudySessionEntity ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<StudySessionEntity>
}
