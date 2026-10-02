package com.usctest.app.ui.walkthrough

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.FlagCheckered
import com.adamglin.phosphoricons.regular.ListChecks
import com.adamglin.phosphoricons.regular.Microphone
import com.adamglin.phosphoricons.regular.PencilSimple
import com.usctest.app.data.SettingsRepository
import com.usctest.app.ui.common.rememberViewModel
import com.usctest.app.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4

// Shown in both the Recall Mode and Practice Test mock previews below -- same question in both
// so a user flipping between the two walkthrough pages sees one consistent example, not two
// unrelated ones.
private const val SAMPLE_QUESTION = "What is the supreme law of the land?"
private const val SAMPLE_ANSWER = "The Constitution"

/**
 * First-launch walkthrough -- runs once, between onboarding and Home, explaining the app's three
 * study modes. See the app plan's first-launch walkthrough spec for the full content/layout
 * rationale. Screens 1-2's mocked UI is a visual match for the real Recall Mode/Practice Test
 * screens using the same design tokens, not a literal shared component -- extracting those
 * private composables into reusable pieces was judged unnecessary surface area for a screen the
 * user only ever sees once. Screen 1's illustration is intentionally left as a placeholder,
 * pending final visual design.
 */
@Composable
fun WalkthroughScreen(
    settingsRepository: SettingsRepository,
    onFinished: () -> Unit,
) {
    val viewModel = rememberViewModel { WalkthroughViewModel(settingsRepository) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    fun goToPage(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (pagerState.currentPage in 1..2) {
                    IconButton(onClick = { goToPage(pagerState.currentPage - 1) }) {
                        Icon(imageVector = PhosphorIcons.Regular.ArrowLeft, contentDescription = "Previous step")
                    }
                } else {
                    Box(modifier = Modifier.size(48.dp))
                }
                if (pagerState.currentPage < PAGE_COUNT - 1) {
                    TextButton(onClick = { viewModel.finish(onFinished) }) {
                        Text("Skip", modifier = Modifier.semantics { contentDescription = "Skip introduction" })
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> RecallModePage()
                    1 -> PracticeTestPage()
                    2 -> StudyPlanPage()
                    else -> ClosingPage(name = uiState.name, daysUntilTest = uiState.daysUntilTest)
                }
            }

            ProgressDots(
                totalPages = PAGE_COUNT,
                currentPage = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )

            Button(
                onClick = {
                    if (pagerState.currentPage == PAGE_COUNT - 1) {
                        viewModel.finish(onFinished)
                    } else {
                        goToPage(pagerState.currentPage + 1)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(if (pagerState.currentPage == PAGE_COUNT - 1) "Get Started" else "Next")
            }
        }
    }
}

@Composable
private fun ProgressDots(totalPages: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Step ${currentPage + 1} of $totalPages"
            liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(totalPages) { index ->
            val active = index == currentPage
            Box(
                modifier = Modifier
                    .size(if (active) 10.dp else 8.dp)
                    .background(
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun PageScaffold(
    title: String,
    body: String?,
    cornerIcon: ImageVector?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.4f)) {
            content()
            if (cornerIcon != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = cornerIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp),
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun RecallModePage() {
    PageScaffold(
        title = "The Real Test.",
        body = "This is Recall Mode — you answer from memory, just like the real interview. " +
            "No options to pick from.",
        // Illustration intentionally left as a placeholder pending final visual design.
        cornerIcon = null,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        ) {
            Column(modifier = Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = SAMPLE_QUESTION,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Your answer",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = SAMPLE_ANSWER,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = PhosphorIcons.Regular.Microphone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeTestPage() {
    PageScaffold(
        title = "Learn It First.",
        body = "Practice Test starts you off easier — multiple-choice, with hints. We'll show you " +
            "why an answer's right, and what else would've counted.",
        cornerIcon = PhosphorIcons.Regular.PencilSimple,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = SAMPLE_QUESTION,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                MockOption(text = SAMPLE_ANSWER, selected = true, correct = true)
                MockOption(text = "The Bill of Rights", selected = false, correct = false)
                MockOption(text = "The Declaration of Independence", selected = false, correct = false)
            }
        }
    }
}

@Composable
private fun MockOption(text: String, selected: Boolean, correct: Boolean) {
    val color = if (correct) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = color) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(text = text, modifier = Modifier.padding(start = 4.dp))
            if (correct) {
                Box(modifier = Modifier.weight(1f))
                Icon(imageVector = PhosphorIcons.Regular.Check, contentDescription = null, tint = SuccessGreen)
            }
        }
    }
}

@Composable
private fun StudyPlanPage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Study Exactly What You Need.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Column(modifier = Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FeatureRow(
                icon = PhosphorIcons.Regular.ArrowsClockwise,
                title = "Review Missed",
                body = "Practice picks up right where you slipped, focusing only on what you got wrong.",
            )
            FeatureRow(
                icon = PhosphorIcons.Regular.ListChecks,
                title = "All Questions",
                body = "Every question and its full answer, any time you want to browse.",
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ClosingPage(name: String?, daysUntilTest: Int?) {
    val title = if (daysUntilTest != null) "$daysUntilTest days until your test." else "You're all set."
    val body = when {
        daysUntilTest != null && !name.isNullOrBlank() -> "You're all set, $name. Let's start closing the gap."
        daysUntilTest != null -> "You're all set. Let's start closing the gap."
        else -> "Let's start closing the gap between practice and the real thing."
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = PhosphorIcons.Regular.FlagCheckered,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
