package com.usctest.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.usctest.app.data.SettingsRepository
import com.usctest.app.ui.common.DatePickerField
import com.usctest.app.ui.common.LabeledTextField
import com.usctest.app.ui.common.rememberViewModel
import kotlinx.datetime.LocalDate

@Composable
fun ProfileSetupScreen(
    settingsRepository: SettingsRepository,
    onContinue: () -> Unit,
) {
    val viewModel = rememberViewModel { ProfileSetupViewModel(settingsRepository) }

    var name by remember { mutableStateOf("") }
    var testDate by remember { mutableStateOf<LocalDate?>(null) }
    var filingDate by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Let's get you set up.", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "This only takes a moment.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LabeledTextField(
                label = "Name",
                value = name,
                onValueChange = { name = it },
                placeholder = "Enter your name",
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DatePickerField(
                    label = "Interview date",
                    date = testDate,
                    onDateSelected = { testDate = it },
                )
                Text(
                    text = "Your USCIS naturalization interview — powers your study timeline and countdown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DatePickerField(
                    label = "Filing date",
                    date = filingDate,
                    onDateSelected = { filingDate = it },
                )
                Text(
                    text = "When you filed Form N-400 — determines which official question set applies " +
                        "(2008 test before Oct 20, 2025; 2025 test on or after).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val canContinue = name.isNotBlank() && testDate != null && filingDate != null
            Button(
                onClick = { viewModel.submit(name, testDate, filingDate, onContinue) },
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue")
            }
        }
    }
}
