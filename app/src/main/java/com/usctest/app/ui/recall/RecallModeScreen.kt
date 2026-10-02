package com.usctest.app.ui.recall

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.Microphone
import com.adamglin.phosphoricons.regular.MicrophoneSlash
import com.adamglin.phosphoricons.regular.X
import com.usctest.app.data.OfficialsRepository
import com.usctest.app.data.ProgressRepository
import com.usctest.app.data.QuestionRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.speech.SpeechRecognitionEvent
import com.usctest.app.speech.SpeechRecognizerManager
import com.usctest.app.ui.common.AnswerContent
import com.usctest.app.ui.common.LabeledTextField
import com.usctest.app.ui.common.rememberViewModel
import com.usctest.app.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecallModeScreen(
    settingsRepository: SettingsRepository,
    questionRepository: QuestionRepository,
    officialsRepository: OfficialsRepository,
    progressRepository: ProgressRepository,
    onBack: () -> Unit,
) {
    val viewModel = rememberViewModel {
        RecallModeViewModel(settingsRepository, questionRepository, officialsRepository, progressRepository)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showQuitConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recall Mode") },
                navigationIcon = {
                    IconButton(onClick = { if (uiState.isFinished) onBack() else showQuitConfirm = true }) {
                        Icon(imageVector = PhosphorIcons.Regular.X, contentDescription = "Quit")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.isFinished -> RecallResultsContent(uiState = uiState, onDone = onBack)
                else -> RecallQuestionContent(uiState = uiState, viewModel = viewModel)
            }
        }
    }

    if (showQuitConfirm) {
        AlertDialog(
            onDismissRequest = { showQuitConfirm = false },
            title = { Text("Quit this attempt?") },
            text = { Text("Your progress on this attempt won't be saved.") },
            confirmButton = {
                TextButton(onClick = { showQuitConfirm = false; onBack() }) { Text("Quit") }
            },
            dismissButton = {
                TextButton(onClick = { showQuitConfirm = false }) { Text("Keep going") }
            },
        )
    }
}

@Composable
private fun RecallQuestionContent(uiState: RecallUiState, viewModel: RecallModeViewModel) {
    val current = uiState.currentQuestion ?: return
    val context = LocalContext.current
    val speechRecognizer = remember { SpeechRecognizerManager(context) }
    DisposableEffect(Unit) { onDispose { speechRecognizer.stopListening() } }

    var isListening by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }
    var micLevel by remember { mutableStateOf(0f) }
    var autoRetryCount by remember { mutableStateOf(0) }
    val maxAutoRetries = 3

    // Mic state is scoped to this composable instance, which stays mounted across questions —
    // reset it whenever the active question changes so a stale error/listening state from the
    // previous question doesn't leak into the next one.
    LaunchedEffect(current.question.id) {
        speechRecognizer.stopListening()
        isListening = false
        speechError = null
        micLevel = 0f
        autoRetryCount = 0
    }

    fun startListening(isAutoRetry: Boolean = false) {
        if (!isAutoRetry) autoRetryCount = 0
        speechError = null
        isListening = true
        micLevel = 0f
        speechRecognizer.startListening { event ->
            when (event) {
                is SpeechRecognitionEvent.Listening -> isListening = true
                is SpeechRecognitionEvent.RmsChanged -> {
                    // rmsdB roughly spans -2 (silence) to 10 (loud) per SpeechRecognizer docs.
                    micLevel = ((event.rmsDb + 2f) / 12f).coerceIn(0f, 1f)
                }
                is SpeechRecognitionEvent.PartialResult -> {
                    autoRetryCount = 0
                    viewModel.onDictatedText(event.text)
                }
                is SpeechRecognitionEvent.FinalResult -> {
                    autoRetryCount = 0
                    viewModel.onDictatedText(event.text)
                }
                is SpeechRecognitionEvent.Done -> {
                    isListening = false
                    micLevel = 0f
                }
                is SpeechRecognitionEvent.Error -> {
                    micLevel = 0f
                    // The device timeout for "start speaking" is only ~1-2s and isn't
                    // adjustable via public API, so a lone timeout is often just the user
                    // starting a beat late. Retry silently instead of forcing a manual re-tap.
                    if (event.isTransient && autoRetryCount < maxAutoRetries) {
                        autoRetryCount++
                        startListening(isAutoRetry = true)
                    } else {
                        isListening = false
                        speechError = if (event.isTransient) {
                            "Still couldn't catch that — tap the mic and start speaking right away."
                        } else {
                            event.message
                        }
                    }
                }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startListening() else speechError = "Microphone permission is needed for voice input."
    }

    fun onMicClick() {
        if (isListening) {
            speechRecognizer.stopListening()
            isListening = false
            micLevel = 0f
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) startListening() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Question ${uiState.currentIndex + 1}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = current.question.text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        LabeledTextField(
            label = if (isListening) "Listening…" else "Your answer",
            value = uiState.inputText,
            onValueChange = viewModel::updateInput,
            placeholder = "Type or speak your answer",
            enabled = uiState.feedback == null,
        )

        speechError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MicButton(isListening = isListening, micLevel = micLevel, enabled = uiState.feedback == null, onClick = ::onMicClick)
            Text(
                text = if (isListening) "Tap to stop" else "Answer by voice",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        uiState.feedback?.let { feedback ->
            FeedbackBanner(feedback = feedback)
        }

        Button(
            onClick = { if (uiState.feedback == null) viewModel.submit() else viewModel.next() },
            enabled = uiState.feedback != null || uiState.inputText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (uiState.feedback == null) "Submit" else "Next")
        }
    }
}

@Composable
private fun MicButton(isListening: Boolean, micLevel: Float, enabled: Boolean, onClick: () -> Unit) {
    // Smooths the raw per-callback rms level so the reactive ring doesn't jitter frame to frame.
    val animatedLevel by animateFloatAsState(
        targetValue = micLevel,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "micLevel",
    )

    // Slow idle breathing so the button still feels "alive" between spikes in volume.
    val idlePulse = if (isListening) {
        val infiniteTransition = rememberInfiniteTransition(label = "micIdlePulse")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "micIdlePulseValue",
        ).value
    } else {
        0f
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(1f + idlePulse * 0.08f + animatedLevel * 0.9f)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(1f + idlePulse * 0.05f + animatedLevel * 0.55f)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
            )
        }
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(72.dp)
                .scale(1f + animatedLevel * 0.12f),
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) {
            Icon(
                imageVector = if (isListening) PhosphorIcons.Regular.MicrophoneSlash else PhosphorIcons.Regular.Microphone,
                contentDescription = if (isListening) "Stop listening" else "Answer by voice",
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun FeedbackBanner(feedback: RecallFeedback) {
    val tint = if (feedback.wasCorrect) SuccessGreen else MaterialTheme.colorScheme.error
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = tint.copy(alpha = 0.1f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            VerdictRow(wasCorrect = feedback.wasCorrect)
            if (!feedback.wasCorrect) {
                AnswerContent(display = feedback.display, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun VerdictRow(wasCorrect: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (wasCorrect) PhosphorIcons.Regular.Check else PhosphorIcons.Regular.X,
            contentDescription = null,
            tint = if (wasCorrect) SuccessGreen else MaterialTheme.colorScheme.error,
        )
        Text(
            text = if (wasCorrect) "Correct" else "Not quite",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RecallResultsContent(uiState: RecallUiState, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = if (uiState.passed) "You passed!" else "Not this time",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (uiState.passed) SuccessGreen else MaterialTheme.colorScheme.error,
        )
        Text(
            text = "${uiState.correctCount} correct, ${uiState.wrongCount} incorrect",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(uiState.records) { record ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.padding(vertical = 14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (record.wasCorrect) PhosphorIcons.Regular.Check else PhosphorIcons.Regular.X,
                            contentDescription = null,
                            tint = if (record.wasCorrect) SuccessGreen else MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = record.question.text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = "You said: ${record.submittedText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (!record.wasCorrect) {
                        AnswerContent(display = record.display, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}
