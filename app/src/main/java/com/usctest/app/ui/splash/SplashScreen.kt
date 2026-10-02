package com.usctest.app.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.usctest.app.R
import com.usctest.app.data.SettingsRepository
import com.usctest.app.navigation.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val SPLASH_MIN_MILLIS = 1200L

@Composable
fun SplashScreen(
    settingsRepository: SettingsRepository,
    onResolved: (startDestination: String) -> Unit,
) {
    var skipped by remember { mutableStateOf(false) }

    LaunchedEffect(skipped) {
        val settings = settingsRepository.settings.first()
        if (!skipped) delay(SPLASH_MIN_MILLIS)
        val destination = when {
            !settings.profileSetupComplete -> Routes.PROFILE_SETUP
            !settings.onboardingComplete -> Routes.ONBOARDING
            !settings.hasSeenWalkthrough -> Routes.WALKTHROUGH
            else -> Routes.HOME
        }
        onResolved(destination)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { skipped = true } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier.size(76.dp),
            )
            Text(
                text = "US Citizenship Test",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Tap anywhere to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
