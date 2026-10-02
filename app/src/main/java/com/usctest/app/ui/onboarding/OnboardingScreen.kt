package com.usctest.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usctest.app.data.OfficialsRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.model.StateOfficials
import com.usctest.app.ui.common.SelectableList
import com.usctest.app.ui.common.rememberViewModel

@Composable
fun OnboardingScreen(
    officialsRepository: OfficialsRepository,
    settingsRepository: SettingsRepository,
    onContinue: () -> Unit,
) {
    val viewModel = rememberViewModel { OnboardingViewModel(officialsRepository, settingsRepository) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedState by remember { mutableStateOf<StateOfficials?>(null) }
    var selectedRepresentative by remember { mutableStateOf<String?>(null) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            Text(text = "Select Your State", style = MaterialTheme.typography.titleLarge)
            SelectableList(
                items = uiState.states.map { it.stateName to it },
                selectedLabel = selectedState?.stateName,
                onSelect = { state ->
                    selectedState = state
                    selectedRepresentative = null
                },
                modifier = Modifier.weight(1f),
            )

            Text(text = "Select Your Representative", style = MaterialTheme.typography.titleLarge)
            SelectableList(
                items = (selectedState?.representatives ?: emptyList()).map { it to it },
                selectedLabel = selectedRepresentative,
                onSelect = { rep -> selectedRepresentative = rep },
                modifier = Modifier.weight(1f),
            )

            Button(
                onClick = {
                    selectedState?.let { state ->
                        viewModel.completeOnboarding(state.stateCode, selectedRepresentative, onContinue)
                    }
                },
                enabled = selectedState != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue")
            }
        }
    }
}

