package com.usctest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usctest.app.data.model.ThemeMode
import com.usctest.app.data.model.UserSettings
import com.usctest.app.navigation.AppNavHost
import com.usctest.app.ui.theme.AppThemeMode
import com.usctest.app.ui.theme.UsCitizenshipTestTheme

/** The Settings font control is an additional multiplier on top of the system font scale,
 * never a replacement for it -- see the app plan's cross-cutting UI notes. */
private const val LARGE_FONTS_MULTIPLIER = 1.15f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = AppContainer(this)
        setContent {
            val settings by appContainer.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = UserSettings())
            val themeMode = when (settings.themeMode) {
                ThemeMode.AUTO -> AppThemeMode.AUTO
                ThemeMode.LIGHT -> AppThemeMode.LIGHT
                ThemeMode.DARK -> AppThemeMode.DARK
            }

            UsCitizenshipTestTheme(themeMode = themeMode) {
                val baseDensity = LocalDensity.current
                val fontScale = if (settings.largeFonts) baseDensity.fontScale * LARGE_FONTS_MULTIPLIER else baseDensity.fontScale
                CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale)) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavHost(appContainer)
                    }
                }
            }
        }
    }
}
