package com.usctest.app.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usctest.app.data.CategoryAccuracy
import com.usctest.app.data.ModeAccuracy
import com.usctest.app.data.ProgressRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.local.MasteryLevel
import com.usctest.app.domain.ExamFormat
import com.usctest.app.domain.TestVersionResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ProgressUiState(
    val isLoading: Boolean = true,
    val hasAnyData: Boolean = false,
    val confidencePercent: Int = 0,
    val weakestCategories: List<String> = emptyList(),
    val recallAccuracy: ModeAccuracy = ModeAccuracy(percent = 0, attempted = false),
    val practiceAccuracy: ModeAccuracy = ModeAccuracy(percent = 0, attempted = false),
    val masteryBreakdown: Map<MasteryLevel, Int> = emptyMap(),
    val categoryAccuracies: List<CategoryAccuracy> = emptyList(),
)

class ProgressViewModel(
    private val settingsRepository: SettingsRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    /** Called every time the screen re-enters composition, since attempts made elsewhere
     * (Recall Mode, Practice Test...) should be reflected the next time this tab is opened. */
    fun refresh() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val profile = settingsRepository.profile.first()
            val version = TestVersionResolver.resolve(settings, profile.filingDate)
            val rules = ExamFormat.rulesFor(version)

            val confidence = progressRepository.getConfidenceEstimate(version, rules)
            val (recall, practice) = progressRepository.getModeAccuracy(version)
            val masteryBreakdown = progressRepository.getMasteryBreakdown(version)
            val categoryAccuracies = progressRepository.getCategoryAccuracy(version).sortedBy { it.accuracyPercent }

            _uiState.value = ProgressUiState(
                isLoading = false,
                hasAnyData = recall.attempted || practice.attempted,
                confidencePercent = confidence.confidencePercent,
                weakestCategories = confidence.weakestCategories,
                recallAccuracy = recall,
                practiceAccuracy = practice,
                masteryBreakdown = masteryBreakdown,
                categoryAccuracies = categoryAccuracies,
            )
        }
    }
}
