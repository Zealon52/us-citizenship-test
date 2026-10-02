package com.usctest.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.usctest.app.domain.AnswerDisplay

/** Renders a formatted [AnswerDisplay] — shared between All Questions (tap-to-reveal) and
 * Recall Mode (post-submit feedback) so the "what's the right answer" visual language matches. */
@Composable
fun AnswerContent(display: AnswerDisplay, modifier: Modifier = Modifier) {
    when (display) {
        is AnswerDisplay.Plain -> {
            AnswerText(display.text, modifier)
        }

        is AnswerDisplay.PrimaryWithAside -> {
            Column(modifier = modifier) {
                AnswerText(display.primary)
                Text(
                    text = "or: " + display.others.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        is AnswerDisplay.ExpandableExamples -> {
            var showAll by remember { mutableStateOf(false) }
            Column(modifier = modifier) {
                if (!showAll) {
                    AnswerText("Any ${display.requiredCount}, for example: " + display.previewAnswers.joinToString(", "))
                    if (display.hiddenCount > 0) {
                        Text(
                            text = "+${display.hiddenCount} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showAll = true }.padding(top = 4.dp),
                        )
                    }
                } else {
                    display.allAnswers.forEach { answer ->
                        AnswerText("• $answer")
                    }
                    Text(
                        text = "Show less",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showAll = false }.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Answers read as visually distinct from questions: regular weight, muted color, one step down in size. */
@Composable
fun AnswerText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
