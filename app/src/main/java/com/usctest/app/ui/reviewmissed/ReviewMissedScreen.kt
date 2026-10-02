package com.usctest.app.ui.reviewmissed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.ClipboardText
import com.adamglin.phosphoricons.regular.Microphone
import com.adamglin.phosphoricons.regular.X
import com.usctest.app.data.OfficialsRepository
import com.usctest.app.data.ProgressRepository
import com.usctest.app.data.QuestionRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.ui.common.AnswerContent
import com.usctest.app.ui.common.AnswerText
import com.usctest.app.ui.common.LabeledTextField
import com.usctest.app.ui.common.rememberViewModel
import com.usctest.app.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewMissedScreen(
    settingsRepository: SettingsRepository,
    questionRepository: QuestionRepository,
    officialsRepository: OfficialsRepository,
    progressRepository: ProgressRepository,
    onBack: () -> Unit,
) {
    val viewModel = rememberViewModel {
        ReviewMissedViewModel(settingsRepository, questionRepository, officialsRepository, progressRepository)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showQuitConfirm by remember { mutableStateOf(false) }

    val inSession = uiState.format != null && !uiState.isFinished

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Missed") },
                navigationIcon = {
                    IconButton(onClick = { if (inSession) showQuitConfirm = true else onBack() }) {
                        Icon(imageVector = PhosphorIcons.Regular.X, contentDescription = if (inSession) "Quit" else "Back")
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
                uiState.showEmptyState -> EmptyMissedContent(onDone = onBack)
                uiState.showFormatPicker -> FormatPickerContent(missedCount = uiState.missedCount, onPick = viewModel::selectFormat)
                uiState.isFinished -> ReviewResultsContent(uiState = uiState, onDone = onBack)
                else -> ReviewQuestionContent(uiState = uiState, viewModel = viewModel)
            }
        }
    }

    if (showQuitConfirm) {
        AlertDialog(
            onDismissRequest = { showQuitConfirm = false },
            title = { Text("Quit this review?") },
            text = { Text("Your progress on this session won't be saved.") },
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
private fun EmptyMissedContent(onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = PhosphorIcons.Regular.ArrowsClockwise,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Nothing to review yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Keep practicing and we'll surface anything you miss here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onDone,
            modifier = Modifier.padding(top = 24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun FormatPickerContent(missedCount: Int, onPick: (ReviewFormat) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (missedCount == 1) "1 question to review" else "$missedCount questions to review",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Weighted toward your most-missed, least-recently-seen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            FormatOptionCard(
                title = "Recall",
                icon = PhosphorIcons.Regular.Microphone,
                onClick = { onPick(ReviewFormat.RECALL) },
                modifier = Modifier.weight(1f),
            )
            FormatOptionCard(
                title = "Multiple choice",
                icon = PhosphorIcons.Regular.ClipboardText,
                onClick = { onPick(ReviewFormat.MCQ) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FormatOptionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.aspectRatio(1.1f).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ReviewQuestionContent(uiState: ReviewMissedUiState, viewModel: ReviewMissedViewModel) {
    val current = uiState.currentQuestion ?: return

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Question ${uiState.currentIndex + 1} of ${uiState.questions.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = current.question.text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        if (uiState.format == ReviewFormat.RECALL) {
            LabeledTextField(
                label = "Your answer",
                value = uiState.inputText,
                onValueChange = viewModel::updateInput,
                placeholder = "Type your answer",
                enabled = uiState.feedback == null,
            )
        } else {
            if (current.isMultiSelect) {
                Text(
                    text = "Select ${current.requiredSelectionCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                current.options.forEachIndexed { index, option ->
                    ReviewOptionRow(
                        text = option.text,
                        selected = index in uiState.selectedIndices,
                        isMultiSelect = current.isMultiSelect,
                        revealCorrect = uiState.feedback != null,
                        isCorrectOption = option.isCorrect,
                        enabled = uiState.feedback == null,
                        onClick = { viewModel.toggleOption(index) },
                    )
                }
            }
        }

        uiState.feedback?.let { feedback -> ReviewFeedbackBanner(feedback = feedback) }

        val canSubmit = if (uiState.format == ReviewFormat.RECALL) {
            uiState.inputText.isNotBlank()
        } else {
            uiState.selectedIndices.isNotEmpty()
        }
        Button(
            onClick = { if (uiState.feedback == null) viewModel.submit() else viewModel.next() },
            enabled = uiState.feedback != null || canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (uiState.feedback == null) "Submit" else "Next")
        }
    }
}

@Composable
private fun ReviewOptionRow(
    text: String,
    selected: Boolean,
    isMultiSelect: Boolean,
    revealCorrect: Boolean,
    isCorrectOption: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = when {
        revealCorrect && isCorrectOption -> SuccessGreen.copy(alpha = 0.15f)
        revealCorrect && selected && !isCorrectOption -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isMultiSelect) {
                Checkbox(checked = selected, onCheckedChange = { onClick() }, enabled = enabled)
            } else {
                RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            }
            Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun ReviewFeedbackBanner(feedback: ReviewFeedback) {
    val tint = if (feedback.wasCorrect) SuccessGreen else MaterialTheme.colorScheme.error
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = tint.copy(alpha = 0.1f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (feedback.wasCorrect) PhosphorIcons.Regular.Check else PhosphorIcons.Regular.X,
                    contentDescription = null,
                    tint = if (feedback.wasCorrect) SuccessGreen else MaterialTheme.colorScheme.error,
                )
                Text(
                    text = if (feedback.wasCorrect) "Correct" else "Not quite",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (!feedback.wasCorrect && feedback.display != null) {
                AnswerContent(display = feedback.display, modifier = Modifier.padding(top = 8.dp))
            }
            if (feedback.alsoAccepted.isNotEmpty()) {
                AnswerText(
                    text = "Also accepted: " + feedback.alsoAccepted.joinToString(", "),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ReviewResultsContent(uiState: ReviewMissedUiState, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Review complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${uiState.correctCount} of ${uiState.records.size} correct this time",
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
                    if (record.submittedText != null) {
                        Text(
                            text = "You said: ${record.submittedText}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (!record.wasCorrect) {
                        if (record.display != null) {
                            AnswerContent(display = record.display, modifier = Modifier.padding(top = 6.dp))
                        } else {
                            AnswerText(
                                text = "Correct answer: " + record.resolvedAnswers.joinToString(", ") { it.canonical },
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}
