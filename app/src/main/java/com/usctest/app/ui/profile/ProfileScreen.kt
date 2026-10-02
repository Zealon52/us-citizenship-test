package com.usctest.app.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.User
import com.usctest.app.data.SettingsRepository
import com.usctest.app.ui.common.DatePickerField
import com.usctest.app.ui.common.LabeledTextField
import com.usctest.app.ui.common.rememberViewModel
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
) {
    val viewModel = rememberViewModel { ProfileViewModel(settingsRepository) }

    var name by remember { mutableStateOf("") }
    var testDate by remember { mutableStateOf<LocalDate?>(null) }
    var filingDate by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(Unit) {
        val profile = viewModel.loadProfile()
        name = profile.name.orEmpty()
        testDate = profile.testDate
        filingDate = profile.filingDate
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = PhosphorIcons.Regular.ArrowLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(80.dp),
            ) {
                Icon(
                    imageVector = PhosphorIcons.Regular.User,
                    contentDescription = null,
                    modifier = Modifier.padding(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LabeledTextField(
                label = "Name",
                value = name,
                onValueChange = { name = it },
                placeholder = "Enter your name",
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DatePickerField(label = "Interview date", date = testDate, onDateSelected = { testDate = it })
                Text(
                    text = "Your USCIS naturalization interview — powers your study timeline and countdown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DatePickerField(label = "Filing date", date = filingDate, onDateSelected = { filingDate = it })
                Text(
                    text = "When you filed Form N-400 — determines which official question set applies " +
                        "(2008 test before Oct 20, 2025; 2025 test on or after).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = { viewModel.save(name, testDate, filingDate, onBack) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}
