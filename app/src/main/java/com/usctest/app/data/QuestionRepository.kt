package com.usctest.app.data

import android.content.Context
import com.usctest.app.data.model.Question
import com.usctest.app.data.model.TestVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Loads the bundled USCIS question banks from assets. Content is static and small
 * (100-128 questions), so it's parsed once and cached in memory per version.
 */
class QuestionRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<TestVersion, List<Question>>()

    suspend fun getQuestions(version: TestVersion): List<Question> = withContext(Dispatchers.IO) {
        cache.getOrPut(version) {
            val fileName = when (version) {
                TestVersion.V2008 -> "questions_2008.json"
                TestVersion.V2025 -> "questions_2025.json"
            }
            val text = context.assets.open(fileName).bufferedReader().use { it.readText() }
            json.decodeFromString<List<Question>>(text)
        }
    }

    suspend fun getQuestion(version: TestVersion, id: Int): Question? =
        getQuestions(version).firstOrNull { it.id == id }

    suspend fun getEssentialQuestions(version: TestVersion): List<Question> =
        getQuestions(version).filter { it.isEssential }
}
