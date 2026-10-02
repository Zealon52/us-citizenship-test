package com.usctest.app.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.Calendar
import com.adamglin.phosphoricons.regular.Cards
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.ClipboardText
import com.adamglin.phosphoricons.regular.FileText
import com.adamglin.phosphoricons.regular.Microphone
import com.adamglin.phosphoricons.regular.Shield
import com.adamglin.phosphoricons.regular.User
import com.usctest.app.data.ProgressRepository
import com.usctest.app.data.QuestionRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.model.TestVersion
import com.usctest.app.ui.common.rememberViewModel
import com.usctest.app.ui.theme.SuccessGreen

@Composable
fun HomeScreen(
    settingsRepository: SettingsRepository,
    progressRepository: ProgressRepository,
    questionRepository: QuestionRepository,
    onOpenProfile: () -> Unit,
    onOpenRecallMode: () -> Unit,
    onOpenPracticeTest: () -> Unit,
    onOpenFlashCards: () -> Unit,
    onOpenReviewMissed: () -> Unit,
    onOpenAllQuestions: () -> Unit,
) {
    val viewModel = rememberViewModel { HomeViewModel(settingsRepository, progressRepository, questionRepository) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        HomeHeader(greetingName = uiState.greetingName, onOpenProfile = onOpenProfile)

        if (uiState.isLoading || uiState.daysUntilTest != null) {
            CountdownCard(
                daysUntilTest = uiState.daysUntilTest,
                confidencePercent = uiState.confidencePercent,
                isLoading = uiState.isLoading,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "STUDY MODES",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${uiState.totalQuestionCount} Civics Questions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            HomeCard(
                title = "Recall Mode",
                subtitle = "Spoken answers",
                icon = PhosphorIcons.Regular.Microphone,
                onClick = onOpenRecallMode,
                modifier = Modifier.weight(1f),
            )
            HomeCard(
                title = "Practice Test",
                subtitle = "${uiState.practiceQuestionCount} random questions",
                icon = PhosphorIcons.Regular.ClipboardText,
                onClick = onOpenPracticeTest,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            HomeCard(
                title = "Flash Cards",
                subtitle = "Self-paced review",
                icon = PhosphorIcons.Regular.Cards,
                onClick = onOpenFlashCards,
                modifier = Modifier.weight(1f),
            )
            HomeCard(
                title = "Review Missed",
                subtitle = "${uiState.missedQuestionCount} flagged items",
                icon = PhosphorIcons.Regular.ArrowsClockwise,
                onClick = onOpenReviewMissed,
                modifier = Modifier.weight(1f),
            )
        }

        AllQuestionsRow(totalQuestionCount = uiState.totalQuestionCount, onClick = onOpenAllQuestions)

        InfoPanel(activeTestVersion = uiState.activeTestVersion)
    }
}

@Composable
private fun HomeHeader(greetingName: String?, onOpenProfile: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                text = if (greetingName.isNullOrBlank()) "Good morning" else "Good morning, $greetingName",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Naturalization Civics Prep",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(40.dp).clickable { onOpenProfile() },
        ) {
            Icon(
                imageVector = PhosphorIcons.Regular.User,
                contentDescription = "Profile",
                modifier = Modifier.padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CountdownCard(daysUntilTest: Int?, confidencePercent: Int, isLoading: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = PhosphorIcons.Regular.Calendar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = when {
                        isLoading || daysUntilTest == null -> "Loading your countdown..."
                        daysUntilTest >= 0 -> "$daysUntilTest days until your test"
                        else -> "Your test date has passed"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ConfidenceRing(confidencePercent = confidencePercent, isLoading = isLoading)
            }
        }
    }
}

/**
 * Drawn with a single Canvas (rather than swapping between an indeterminate and a determinate
 * CircularProgressIndicator) so the loading and loaded states share identical size/stroke/track --
 * Material3's two indicator variants render at visually different sizes. The loading arc rotates
 * slowly (long tween, not the default indicator's fast spin) and the percent arc animates into
 * place instead of snapping, so the swap from loading to the real value reads as one continuous
 * motion rather than a jump.
 */
@Composable
private fun ConfidenceRing(confidencePercent: Int, isLoading: Boolean) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary

    val rotation by rememberInfiniteTransition(label = "confidenceRingRotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
        ),
        label = "rotationDegrees",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = confidencePercent / 100f,
        animationSpec = tween(durationMillis = 500),
        label = "confidenceProgress",
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color = trackColor, style = stroke)
            if (isLoading) {
                drawArc(
                    color = progressColor,
                    startAngle = rotation,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = stroke,
                )
            } else {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = stroke,
                )
            }
        }
        if (!isLoading) {
            Text(
                text = "$confidencePercent%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = progressColor,
            )
        }
    }
}

@Composable
private fun HomeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.aspectRatio(1.1f).clickable { onClick() },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AllQuestionsRow(totalQuestionCount: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Regular.FileText,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Text(text = "All Questions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "$totalQuestionCount Items",
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessGreen,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = PhosphorIcons.Regular.CaretRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoPanel(activeTestVersion: TestVersion) {
    val versionLabel = if (activeTestVersion == TestVersion.V2025) "2025" else "2008"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = PhosphorIcons.Regular.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Based on the official USCIS $versionLabel Naturalization Civics Test guidelines. " +
                    "Current for all state representatives.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
