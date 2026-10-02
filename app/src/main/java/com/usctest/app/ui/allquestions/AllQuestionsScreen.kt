package com.usctest.app.ui.allquestions

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.CaretDown
import com.usctest.app.data.OfficialsRepository
import com.usctest.app.data.ProgressRepository
import com.usctest.app.data.QuestionRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.local.MasteryLevel
import com.usctest.app.ui.common.AnswerContent
import com.usctest.app.ui.common.rememberViewModel
import com.usctest.app.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllQuestionsScreen(
    settingsRepository: SettingsRepository,
    questionRepository: QuestionRepository,
    officialsRepository: OfficialsRepository,
    progressRepository: ProgressRepository,
    onBack: () -> Unit,
) {
    val viewModel = rememberViewModel {
        AllQuestionsViewModel(settingsRepository, questionRepository, officialsRepository, progressRepository)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Questions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = PhosphorIcons.Regular.ArrowLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            CategoryFilterRow(
                categories = uiState.categories,
                selectedCategory = uiState.selectedCategory,
                onSelect = viewModel::selectCategory,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(uiState.visibleItems) { item ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    QuestionCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selectedCategory: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(selected = selectedCategory == null, onClick = { onSelect(null) }, label = { Text("All") })
        }
        items(categories) { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onSelect(if (selectedCategory == category) null else category) },
                label = { Text(category) },
            )
        }
    }
}

@Composable
private fun QuestionCard(item: QuestionListItem) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded }
            .padding(vertical = 14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = item.question.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.hasAttempts) {
                    MasteryRing(level = item.masteryLevel, modifier = Modifier.padding(end = 8.dp))
                }
                Icon(
                    imageVector = PhosphorIcons.Regular.CaretDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (expanded) {
            AnswerContent(display = item.display, modifier = Modifier.padding(top = 12.dp))

            item.question.answerNote?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MasteryRing(level: MasteryLevel, modifier: Modifier = Modifier) {
    val (progress, ringColor, label) = when (level) {
        MasteryLevel.NEW -> Triple(0f, Color.LightGray, "New — attempted, no correct answers yet")
        MasteryLevel.LEARNING -> Triple(0.35f, MaterialTheme.colorScheme.onSurfaceVariant, "Learning")
        MasteryLevel.FAMILIAR -> Triple(0.70f, MaterialTheme.colorScheme.onSurface, "Familiar")
        MasteryLevel.MASTERED -> Triple(1f, SuccessGreen, "Mastered")
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        Box(modifier = modifier.size(20.dp)) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = Color.LightGray.copy(alpha = 0.3f),
                strokeWidth = 3.dp,
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = ringColor,
                strokeWidth = 3.dp,
            )
        }
    }
}
