package com.usctest.app.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

sealed interface SpeechRecognitionEvent {
    data object Listening : SpeechRecognitionEvent
    data class RmsChanged(val rmsDb: Float) : SpeechRecognitionEvent
    data class PartialResult(val text: String) : SpeechRecognitionEvent
    data class FinalResult(val text: String) : SpeechRecognitionEvent
    data class Error(val message: String, val isTransient: Boolean) : SpeechRecognitionEvent
    data object Done : SpeechRecognitionEvent
}

/** Thin wrapper around Android's on-device [SpeechRecognizer] for free-form dictation. */
class SpeechRecognizerManager(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var modelDownloadRecognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * On API 33+, kicks off a background download of the on-device speech model if it isn't
     * already present, so recognition can work offline instead of always depending on network.
     * Safe to call repeatedly — no-ops once a download has already been triggered or isn't needed.
     */
    fun ensureOfflineModelDownloaded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (modelDownloadRecognizer != null) return
        if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return

        val downloadRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        modelDownloadRecognizer = downloadRecognizer
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        downloadRecognizer.triggerModelDownload(
            intent,
            ContextCompat.getMainExecutor(context),
            object : ModelDownloadListener {
                override fun onProgress(completedPercent: Int) = Unit
                override fun onSuccess() = releaseModelDownloadRecognizer()
                override fun onScheduled() = Unit
                override fun onError(error: Int) = releaseModelDownloadRecognizer()
            },
        )
    }

    private fun releaseModelDownloadRecognizer() {
        modelDownloadRecognizer?.destroy()
        modelDownloadRecognizer = null
    }

    fun startListening(listener: (SpeechRecognitionEvent) -> Unit) {
        stopListening()
        if (!isAvailable()) {
            listener(SpeechRecognitionEvent.Error("Speech recognition isn't available on this device.", isTransient = false))
            return
        }

        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speechRecognizer
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listener(SpeechRecognitionEvent.Listening)
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                listener(SpeechRecognitionEvent.RmsChanged(rmsdB))
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                val transient = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                listener(SpeechRecognitionEvent.Error(errorMessage(error), transient))
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) listener(SpeechRecognitionEvent.FinalResult(text))
                listener(SpeechRecognitionEvent.Done)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) listener(SpeechRecognitionEvent.PartialResult(text))
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Defaults cut off after ~1s of trailing silence, clipping answers mid-sentence.
            // Give people room to think and speak a full civics answer before it auto-stops.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
            // Use the on-device model once it's downloaded instead of always hitting the
            // network; the system still falls back to network if no offline model is ready.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        recognizer?.apply {
            setRecognitionListener(null)
            cancel()
            destroy()
        }
        recognizer = null
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error — check your connection."
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognizer error."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy — try again."
        else -> "Couldn't recognize speech. Try again."
    }
}
