package com.usctest.app.data

import com.usctest.app.data.local.StudyPlanDao
import com.usctest.app.data.local.StudyPlanEntity
import com.usctest.app.data.local.StudySessionDao
import com.usctest.app.data.local.StudySessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper over the study-plan tables. Only get/set for now — the actual daily-goal
 * math (StudyPlanGenerator) is built in the dedicated study-timeline step, once there's a
 * profile/test-date UI to drive it.
 */
class StudyPlanRepository(
    private val studyPlanDao: StudyPlanDao,
    private val studySessionDao: StudySessionDao,
) {
    fun observePlan(): Flow<StudyPlanEntity?> = studyPlanDao.observe()

    suspend fun getPlan(): StudyPlanEntity? = studyPlanDao.get()

    suspend fun savePlan(plan: StudyPlanEntity) = studyPlanDao.upsert(plan)

    suspend fun clearPlan() = studyPlanDao.clear()

    fun observeRecentSessions(): Flow<List<StudySessionEntity>> = studySessionDao.observeAll()

    suspend fun recordSession(session: StudySessionEntity) = studySessionDao.upsert(session)
}
