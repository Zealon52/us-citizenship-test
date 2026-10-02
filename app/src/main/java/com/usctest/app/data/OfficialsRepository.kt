package com.usctest.app.data

import android.content.Context
import com.usctest.app.data.model.OfficialsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

sealed interface ContentUpdateResult {
    data class Updated(val updatedAt: String) : ContentUpdateResult
    data object AlreadyCurrent : ContentUpdateResult
    data class Failed(val reason: String) : ContentUpdateResult
}

/**
 * Officials data (governors/senators/reps) ships bundled in assets so the app works fully
 * offline, but can be refreshed from a hosted JSON file so seat changes don't require an
 * app store release. The bundled copy is the permanent fallback: a malformed or unreachable
 * remote payload must never break state-specific questions.
 */
class OfficialsRepository(
    private val context: Context,
    private val remoteUrl: String = REMOTE_URL,
) {
    companion object {
        // Hosted via GitHub raw in the app's own private repo; bundled assets copy remains the
        // permanent offline fallback regardless of whether this URL is reachable.
        const val REMOTE_URL =
            "https://raw.githubusercontent.com/Zealon52/us-citizenship-test/master/app/src/main/assets/officials_fallback.json"
        private const val CACHE_FILE_NAME = "officials_cache.json"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient by lazy { OkHttpClient() }

    @Volatile
    private var cached: OfficialsData? = null

    suspend fun getOfficialsData(): OfficialsData = withContext(Dispatchers.IO) {
        cached ?: loadBestAvailable().also { cached = it }
    }

    suspend fun getStateOfficials(stateCode: String) =
        getOfficialsData().states.firstOrNull { it.stateCode.equals(stateCode, ignoreCase = true) }

    suspend fun checkForContentUpdates(): ContentUpdateResult = withContext(Dispatchers.IO) {
        if (remoteUrl.isBlank()) {
            return@withContext ContentUpdateResult.Failed("No content update source configured yet")
        }
        try {
            val request = Request.Builder().url(remoteUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ContentUpdateResult.Failed("Server returned ${response.code}")
                }
                val bodyText = response.body?.string()
                    ?: return@withContext ContentUpdateResult.Failed("Empty response")

                val parsed = try {
                    json.decodeFromString<OfficialsData>(bodyText)
                } catch (e: SerializationException) {
                    return@withContext ContentUpdateResult.Failed("Malformed content: ${e.message}")
                }

                if (!isValid(parsed)) {
                    return@withContext ContentUpdateResult.Failed("Content failed validation")
                }

                val current = cached ?: loadBestAvailable()
                if (parsed.updatedAt == current.updatedAt) {
                    return@withContext ContentUpdateResult.AlreadyCurrent
                }

                cacheFile().writeText(bodyText)
                cached = parsed
                ContentUpdateResult.Updated(parsed.updatedAt)
            }
        } catch (e: IOException) {
            ContentUpdateResult.Failed(e.message ?: "Network error")
        }
    }

    private fun loadBestAvailable(): OfficialsData {
        val cacheFile = cacheFile()
        if (cacheFile.exists()) {
            val fromCache = runCatching {
                val text = cacheFile.readText()
                val parsed = json.decodeFromString<OfficialsData>(text)
                if (isValid(parsed)) parsed else null
            }.getOrNull()
            if (fromCache != null) return fromCache
        }
        return loadBundled()
    }

    private fun loadBundled(): OfficialsData {
        val text = context.assets.open("officials_fallback.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(text)
    }

    private fun cacheFile(): File = File(context.filesDir, CACHE_FILE_NAME)

    /** Sanity-check shape before trusting a payload: all states present, no empty rep lists. */
    private fun isValid(data: OfficialsData): Boolean {
        if (data.states.size < 50) return false
        return data.states.all { state ->
            state.stateName.isNotBlank() &&
                state.stateCode.isNotBlank() &&
                state.capital.isNotBlank() &&
                state.governor.isNotBlank() &&
                state.representatives.isNotEmpty() &&
                (state.hasNoVotingSenators || state.senators.isNotEmpty())
        }
    }
}
