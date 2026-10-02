package com.usctest.app.ui.practicetest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.X
import com.usctest.app.data.OfficialsRepository
import com.usctest.app.data.ProgressRepository
import com.usctest.app.data.QuestionRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.ui.common.AnswerText
import com.usctest.app.ui.common.CategoryAccuracyBar
import com.usctest.app.ui.common.rememberViewModel
import com.usctest.app.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeTestScreen(
    settingsRepository: SettingsRepository,
    questionRepository: QuestionRepository,
    officialsRepository: OfficialsRepository,
    progressRepository: ProgressRepository,
    onBack: () -> Unit,
) {
    val viewModel = rememberViewModel {
        PracticeTestViewModel(settingsRepository, questionRepository, officialsRepository, progressRepository)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showQuitConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practice Test") },
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
                uiState.isFinished -> PracticeResultsContent(uiState = uiState, onDone = onBack)
                else -> PracticeQuestionContent(uiState = uiState, viewModel = viewModel)
            }
        }
    }

    if (showQuitConfirm) {
        AlertDialog(
            onDismissRequest = { showQuitConfirm = false },
            title = { Text("Quit this test?") },
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
private fun PracticeQuestionContent(uiState: PracticeUiState, viewModel: PracticeTestViewModel) {
    val current = uiState.currentQuestion ?: return

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
            if (current.isMultiSelect) {
                Text(
                    text = "Select ${current.requiredSelectionCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                current.options.forEachIndexed { index, option ->
                    OptionRow(
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

            uiState.feedback?.let { feedback ->
                FeedbackBanner(feedback = feedback)
            }
        }

        Button(
            onClick = { if (uiState.feedback == null) viewModel.submit() else viewModel.next() },
            enabled = uiState.feedback != null || uiState.selectedIndices.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text(if (uiState.feedback == null) "Submit" else "Next")
        }
    }
}

@Composable
private fun OptionRow(
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
        shape = RoundedCornerShape(12.dp),
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
private fun FeedbackBanner(feedback: PracticeFeedback) {
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
            if (feedback.alsoAccepted.isNotEmpty()) {
                AlsoAcceptedChips(answers = feedback.alsoAccepted, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlsoAcceptedChips(answers: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "Also accepted",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            answers.forEach { answer ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = answer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PracticeResultsContent(uiState: PracticeUiState, onDone: () -> Unit) {
    val categoryBreakdown = uiState.records
        .groupBy { it.question.category }
        .map { (category, records) -> Triple(category, records.count { it.wasCorrect }, records.size) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ScoreRing(
                correct = uiState.correctCount,
                total = uiState.records.size,
                passingPercent = uiState.passingPercent,
                passed = uiState.passed,
            )
        }
        Text(
            text = if (uiState.passed) "You passed!" else "Not this time",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (uiState.passed) SuccessGreen else MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (categoryBreakdown.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                categoryBreakdown.forEach { (category, correct, total) ->
                    CategoryAccuracyBar(category = category, correct = correct, total = total)
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(uiState.records.filterNot { it.wasCorrect }) { record ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.padding(vertical = 14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = PhosphorIcons.Regular.X,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = record.question.text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    AnswerText(
                        text = "Correct answer: " + record.resolvedAnswers.joinToString(", ") { it.canonical },
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}

/** Score ring: the filled arc is the score, the short tick is where the passing bar sits, so
 * "did I clear the bar" reads at a glance instead of requiring two numbers be compared in your head. */
@Composable
private fun ScoreRing(correct: Int, total: Int, passingPercent: Int, passed: Boolean, modifier: Modifier = Modifier) {
    val scorePercent = if (total == 0) 0 else (correct * 100) / total
    val progressColor = if (passed) SuccessGreen else MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = MaterialTheme.colorScheme.onSurface

    Box(modifier = modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 14.dp.toPx()
            val diameter = size.minDimension - strokeWidthPx
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * (scorePercent / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )

            val markerAngleRad = Math.toRadians(-90.0 + 360.0 * passingPercent / 100.0)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = diameter / 2f
            val innerRadius = radius - strokeWidthPx * 0.9f
            val outerRadius = radius + strokeWidthPx * 0.5f
            val cosA = cos(markerAngleRad).toFloat()
            val sinA = sin(markerAngleRad).toFloat()
            drawLine(
                color = markerColor,
                start = Offset(center.x + innerRadius * cosA, center.y + innerRadius * sinA),
                end = Offset(center.x + outerRadius * cosA, center.y + outerRadius * sinA),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$correct/$total", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = "$scorePercent%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

