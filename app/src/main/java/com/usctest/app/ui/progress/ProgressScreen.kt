package com.usctest.app.ui.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usctest.app.data.CategoryAccuracy
import com.usctest.app.data.ModeAccuracy
import com.usctest.app.data.ProgressRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.local.MasteryLevel
import com.usctest.app.ui.common.AccuracyRing
import com.usctest.app.ui.common.CategoryAccuracyBar
import com.usctest.app.ui.common.rememberViewModel
import com.usctest.app.ui.theme.SuccessGreen

@Composable
fun ProgressScreen(
    settingsRepository: SettingsRepository,
    progressRepository: ProgressRepository,
) {
    val viewModel = rememberViewModel { ProgressViewModel(settingsRepository, progressRepository) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(text = "Progress", style = MaterialTheme.typography.headlineLarge)

            if (uiState.isLoading) return@Column

            ConfidenceHero(
                hasAnyData = uiState.hasAnyData,
                confidencePercent = uiState.confidencePercent,
                weakestCategories = uiState.weakestCategories,
            )

            ModeAccuracyRow(recall = uiState.recallAccuracy, practice = uiState.practiceAccuracy)

            if (uiState.masteryBreakdown.isNotEmpty()) {
                MasteryBreakdownSection(breakdown = uiState.masteryBreakdown)
            }

            if (uiState.categoryAccuracies.isNotEmpty()) {
                CategorySection(categories = uiState.categoryAccuracies)
            }
        }
    }
}

@Composable
private fun ConfidenceHero(hasAnyData: Boolean, confidencePercent: Int, weakestCategories: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasAnyData) {
                Text(
                    text = "Try a practice session or two, and this is where you'll see how you're doing.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            AccuracyRing(percent = confidencePercent, diameter = 140.dp, color = confidenceColor(confidencePercent))
            Text(
                text = confidenceBlurb(confidencePercent),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (confidencePercent < 80 && weakestCategories.isNotEmpty()) {
                Text(
                    text = "Spend a little more time on " + weakestCategories.joinToString(" and ") + ".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun confidenceColor(percent: Int) = when {
    percent >= 80 -> SuccessGreen
    percent >= 50 -> androidx.compose.ui.graphics.Color(0xFFE0A93A)
    else -> androidx.compose.ui.graphics.Color(0xFFCC6B49)
}

private fun confidenceBlurb(percent: Int): String = when {
    percent >= 80 -> "You're in great shape!"
    percent >= 50 -> "You're getting there."
    else -> "Keep at it — you'll get there."
}

@Composable
private fun ModeAccuracyRow(recall: ModeAccuracy, practice: ModeAccuracy) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ModeAccuracyCard(title = "Recall Mode", accuracy = recall, modifier = Modifier.weight(1f))
        ModeAccuracyCard(title = "Practice Test", accuracy = practice, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ModeAccuracyCard(title: String, accuracy: ModeAccuracy, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (accuracy.attempted) {
                AccuracyRing(percent = accuracy.percent, diameter = 88.dp, color = MaterialTheme.colorScheme.primary)
            } else {
                Text(
                    text = "Not tried yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MasteryBreakdownSection(breakdown: Map<MasteryLevel, Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "MASTERY",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MasteryTile("New", breakdown[MasteryLevel.NEW] ?: 0, Modifier.weight(1f))
            MasteryTile("Learning", breakdown[MasteryLevel.LEARNING] ?: 0, Modifier.weight(1f))
            MasteryTile("Familiar", breakdown[MasteryLevel.FAMILIAR] ?: 0, Modifier.weight(1f))
            MasteryTile("Mastered", breakdown[MasteryLevel.MASTERED] ?: 0, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MasteryTile(label: String, count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CategorySection(categories: List<CategoryAccuracy>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "BY CATEGORY",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            categories.forEach { category ->
                CategoryAccuracyBar(category = category.category, correct = category.correct, total = category.total)
            }
        }
    }
}
