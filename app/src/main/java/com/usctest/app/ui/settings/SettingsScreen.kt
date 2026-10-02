package com.usctest.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Minus
import com.adamglin.phosphoricons.regular.Plus
import com.usctest.app.data.OfficialsRepository
import com.usctest.app.data.ProgressRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.model.FlashCardOrder
import com.usctest.app.data.model.TestVersion
import com.usctest.app.data.model.TestVersionSetting
import com.usctest.app.data.model.ThemeMode
import com.usctest.app.ui.common.SelectableList
import com.usctest.app.ui.common.rememberViewModel

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    officialsRepository: OfficialsRepository,
    progressRepository: ProgressRepository,
    onOpenWalkthrough: () -> Unit,
) {
    val viewModel = rememberViewModel { SettingsViewModel(settingsRepository, officialsRepository, progressRepository) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var showStatePicker by remember { mutableStateOf(false) }
    var showRepPicker by remember { mutableStateOf(false) }
    var showTestVersionPicker by remember { mutableStateOf(false) }
    var showFlashCardOrderPicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setReminder(true, uiState.settings.reminderHour, uiState.settings.reminderMinute)
        }
    }
    fun enableReminder() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setReminder(true, uiState.settings.reminderHour, uiState.settings.reminderMinute)
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(text = "Settings", style = MaterialTheme.typography.headlineLarge)

            SettingsSection(title = "Location") {
                SettingsRow(
                    label = "State",
                    value = uiState.selectedState?.stateName ?: "Not set",
                    onClick = { showStatePicker = true },
                    showTopDivider = false,
                )
                SettingsRow(
                    label = "Representative",
                    value = uiState.settings.selectedRepresentative ?: "Not set",
                    onClick = { if (uiState.selectedState != null) showRepPicker = true },
                )
            }

            SettingsSection(title = "Study") {
                SettingsSwitchRow(
                    label = "Focus Mode",
                    subtitle = "Practice only the 20 essential questions",
                    checked = uiState.settings.focusModeEnabled,
                    onCheckedChange = viewModel::setFocusModeEnabled,
                    showTopDivider = false,
                )
                SettingsRow(
                    label = "Test version",
                    value = when (val v = uiState.settings.testVersion) {
                        is TestVersionSetting.Auto -> "Auto (from filing date)"
                        is TestVersionSetting.Manual -> if (v.version == TestVersion.V2008) "2008 test" else "2025 test"
                    },
                    onClick = { showTestVersionPicker = true },
                )
                StepperRow(
                    label = "Practice question count",
                    value = uiState.settings.practiceQuestionCount,
                    range = 5..30,
                    step = 5,
                    onValueChange = { viewModel.setPracticeConfig(it, uiState.settings.practicePassingPercent) },
                )
                StepperRow(
                    label = "Practice passing percent",
                    value = uiState.settings.practicePassingPercent,
                    range = 50..100,
                    step = 5,
                    suffix = "%",
                    onValueChange = { viewModel.setPracticeConfig(uiState.settings.practiceQuestionCount, it) },
                )
                SettingsRow(
                    label = "Flash card order",
                    value = when (uiState.settings.flashCardOrder) {
                        FlashCardOrder.SEQUENTIAL -> "Sequential"
                        FlashCardOrder.RANDOM -> "Random"
                        FlashCardOrder.WEAKEST_FIRST -> "Weakest first"
                    },
                    onClick = { showFlashCardOrderPicker = true },
                )
            }

            SettingsSection(title = "Reminders") {
                SettingsSwitchRow(
                    label = "Daily study reminder",
                    checked = uiState.settings.reminderEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            enableReminder()
                        } else {
                            viewModel.setReminder(false, uiState.settings.reminderHour, uiState.settings.reminderMinute)
                        }
                    },
                    showTopDivider = false,
                )
                SettingsRow(
                    label = "Reminder time",
                    value = formatTime(uiState.settings.reminderHour, uiState.settings.reminderMinute),
                    onClick = { if (uiState.settings.reminderEnabled) showReminderTimePicker = true },
                )
            }

            SettingsSection(title = "Appearance") {
                SettingsRow(
                    label = "Theme",
                    value = when (uiState.settings.themeMode) {
                        ThemeMode.AUTO -> "Auto"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    },
                    onClick = { showThemePicker = true },
                    showTopDivider = false,
                )
                SettingsSwitchRow(
                    label = "Larger text",
                    subtitle = "An extra boost on top of your system font size",
                    checked = uiState.settings.largeFonts,
                    onCheckedChange = viewModel::setLargeFonts,
                )
            }

            SettingsSection(title = "Content") {
                SettingsInfoRow(
                    label = "Officials data last updated",
                    value = uiState.officialsUpdatedAt.take(10),
                    showTopDivider = false,
                )
                SettingsRow(
                    label = "Check for content updates",
                    value = uiState.contentUpdateMessage ?: "Tap to check",
                    onClick = viewModel::checkForContentUpdates,
                )
            }

            SettingsSection(title = "Help") {
                SettingsRow(
                    label = "Replay tutorial",
                    value = "",
                    onClick = onOpenWalkthrough,
                    showTopDivider = false,
                )
            }

            SettingsSection(title = "Data") {
                SettingsRow(
                    label = "Reset progress",
                    value = "",
                    onClick = { showResetConfirm = true },
                    destructive = true,
                    showTopDivider = false,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Version 1.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "This app is not affiliated with or endorsed by USCIS or DHS. It is an independent study tool.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showStatePicker) {
        FullScreenPickerDialog(title = "Select your state", onDismiss = { showStatePicker = false }) {
            SelectableList(
                items = uiState.states.map { it.stateName to it.stateCode },
                selectedLabel = uiState.selectedState?.stateName,
                onSelect = { code ->
                    viewModel.selectState(code)
                    showStatePicker = false
                    // Picking a new state clears the representative (the old one no longer
                    // applies), so chain straight into re-picking it instead of leaving
                    // "Representative" silently showing "Not set" until the user notices.
                    val hasReps = uiState.states.firstOrNull { it.stateCode == code }?.representatives?.isNotEmpty() == true
                    if (hasReps) showRepPicker = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showRepPicker) {
        FullScreenPickerDialog(title = "Select your representative", onDismiss = { showRepPicker = false }) {
            SelectableList(
                items = (uiState.selectedState?.representatives ?: emptyList()).map { it to it },
                selectedLabel = uiState.settings.selectedRepresentative,
                onSelect = { name ->
                    viewModel.selectRepresentative(name)
                    showRepPicker = false
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showTestVersionPicker) {
        val current = uiState.settings.testVersion
        RadioOptionDialog(
            title = "Test version",
            options = listOf(
                "Auto (from filing date)" to null,
                "2008 test" to TestVersion.V2008,
                "2025 test" to TestVersion.V2025,
            ),
            isSelected = { option ->
                when (val v = current) {
                    is TestVersionSetting.Auto -> option == null
                    is TestVersionSetting.Manual -> option == v.version
                }
            },
            onSelect = { viewModel.setTestVersionOverride(it); showTestVersionPicker = false },
            onDismiss = { showTestVersionPicker = false },
        )
    }

    if (showFlashCardOrderPicker) {
        RadioOptionDialog(
            title = "Flash card order",
            options = listOf(
                "Sequential" to FlashCardOrder.SEQUENTIAL,
                "Random" to FlashCardOrder.RANDOM,
                "Weakest first" to FlashCardOrder.WEAKEST_FIRST,
            ),
            isSelected = { it == uiState.settings.flashCardOrder },
            onSelect = { viewModel.setFlashCardOrder(it); showFlashCardOrderPicker = false },
            onDismiss = { showFlashCardOrderPicker = false },
        )
    }

    if (showThemePicker) {
        RadioOptionDialog(
            title = "Theme",
            options = listOf("Auto" to ThemeMode.AUTO, "Light" to ThemeMode.LIGHT, "Dark" to ThemeMode.DARK),
            isSelected = { it == uiState.settings.themeMode },
            onSelect = { viewModel.setThemeMode(it); showThemePicker = false },
            onDismiss = { showThemePicker = false },
        )
    }

    if (showReminderTimePicker) {
        ReminderTimeDialog(
            initialHour = uiState.settings.reminderHour,
            initialMinute = uiState.settings.reminderMinute,
            onConfirm = { hour, minute ->
                viewModel.setReminder(uiState.settings.reminderEnabled, hour, minute)
                showReminderTimePicker = false
            },
            onDismiss = { showReminderTimePicker = false },
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset all progress?") },
            text = { Text("Mastery levels, attempt history, and streaks will be permanently deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetProgress(); showResetConfirm = false }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    showTopDivider: Boolean = true,
) {
    if (showTopDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (value.isNotEmpty()) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String, showTopDivider: Boolean = true) {
    if (showTopDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    showTopDivider: Boolean = true,
) {
    if (showTopDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit,
    suffix: String = "",
    showTopDivider: Boolean = true,
) {
    if (showTopDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange((value - step).coerceIn(range)) }) {
                Icon(imageVector = PhosphorIcons.Regular.Minus, contentDescription = "Decrease")
            }
            Text(
                text = "$value$suffix",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            IconButton(onClick = { onValueChange((value + step).coerceIn(range)) }) {
                Icon(imageVector = PhosphorIcons.Regular.Plus, contentDescription = "Increase")
            }
        }
    }
}

@Composable
private fun <T> RadioOptionDialog(
    title: String,
    options: List<Pair<String, T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, optionValue) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(optionValue) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isSelected(optionValue), onClick = { onSelect(optionValue) })
                        Text(text = label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun FullScreenPickerDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { content() } },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder time") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatTime(hour: Int, minute: Int): String {
    val period = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%d:%02d %s".format(displayHour, minute, period)
}
