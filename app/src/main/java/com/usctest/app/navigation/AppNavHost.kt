package com.usctest.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Gear
import com.adamglin.phosphoricons.regular.House
import com.adamglin.phosphoricons.regular.Trophy
import com.usctest.app.AppContainer
import com.usctest.app.ui.allquestions.AllQuestionsScreen
import com.usctest.app.ui.common.ComingSoonScreen
import com.usctest.app.ui.flashcards.FlashCardsScreen
import com.usctest.app.ui.home.HomeScreen
import com.usctest.app.ui.onboarding.OnboardingScreen
import com.usctest.app.ui.practicetest.PracticeTestScreen
import com.usctest.app.ui.profile.ProfileScreen
import com.usctest.app.ui.progress.ProgressScreen
import com.usctest.app.ui.profile.ProfileSetupScreen
import com.usctest.app.ui.recall.RecallModeScreen
import com.usctest.app.ui.reviewmissed.ReviewMissedScreen
import com.usctest.app.ui.settings.SettingsScreen
import com.usctest.app.ui.splash.SplashScreen
import com.usctest.app.ui.walkthrough.WalkthroughScreen

private data class BottomNavDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomNavDestinations = listOf(
    BottomNavDestination(Routes.HOME, "Home", PhosphorIcons.Regular.House),
    BottomNavDestination(Routes.PROGRESS, "Progress", PhosphorIcons.Regular.Trophy),
    BottomNavDestination(Routes.SETTINGS, "Settings", PhosphorIcons.Regular.Gear),
)

@Composable
fun AppNavHost(appContainer: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomNavDestinations.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(imageVector = destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    settingsRepository = appContainer.settingsRepository,
                    onResolved = { destination ->
                        navController.navigate(destination) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.PROFILE_SETUP) {
                ProfileSetupScreen(
                    settingsRepository = appContainer.settingsRepository,
                    onContinue = {
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(Routes.PROFILE_SETUP) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    officialsRepository = appContainer.officialsRepository,
                    settingsRepository = appContainer.settingsRepository,
                    onContinue = {
                        navController.navigate(Routes.WALKTHROUGH) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.WALKTHROUGH) {
                WalkthroughScreen(
                    settingsRepository = appContainer.settingsRepository,
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.WALKTHROUGH) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    settingsRepository = appContainer.settingsRepository,
                    progressRepository = appContainer.progressRepository,
                    questionRepository = appContainer.questionRepository,
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    onOpenRecallMode = { navController.navigate(Routes.RECALL_MODE) },
                    onOpenPracticeTest = { navController.navigate(Routes.PRACTICE_TEST) },
                    onOpenFlashCards = { navController.navigate(Routes.FLASH_CARDS) },
                    onOpenReviewMissed = { navController.navigate(Routes.REVIEW_MISSED) },
                    onOpenAllQuestions = { navController.navigate(Routes.ALL_QUESTIONS) },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    settingsRepository = appContainer.settingsRepository,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.RECALL_MODE) {
                RecallModeScreen(
                    settingsRepository = appContainer.settingsRepository,
                    questionRepository = appContainer.questionRepository,
                    officialsRepository = appContainer.officialsRepository,
                    progressRepository = appContainer.progressRepository,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PRACTICE_TEST) {
                PracticeTestScreen(
                    settingsRepository = appContainer.settingsRepository,
                    questionRepository = appContainer.questionRepository,
                    officialsRepository = appContainer.officialsRepository,
                    progressRepository = appContainer.progressRepository,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.FLASH_CARDS) {
                FlashCardsScreen(
                    settingsRepository = appContainer.settingsRepository,
                    questionRepository = appContainer.questionRepository,
                    officialsRepository = appContainer.officialsRepository,
                    progressRepository = appContainer.progressRepository,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.REVIEW_MISSED) {
                ReviewMissedScreen(
                    settingsRepository = appContainer.settingsRepository,
                    questionRepository = appContainer.questionRepository,
                    officialsRepository = appContainer.officialsRepository,
                    progressRepository = appContainer.progressRepository,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ALL_QUESTIONS) {
                AllQuestionsScreen(
                    settingsRepository = appContainer.settingsRepository,
                    questionRepository = appContainer.questionRepository,
                    officialsRepository = appContainer.officialsRepository,
                    progressRepository = appContainer.progressRepository,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PROGRESS) {
                ProgressScreen(
                    settingsRepository = appContainer.settingsRepository,
                    progressRepository = appContainer.progressRepository,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settingsRepository = appContainer.settingsRepository,
                    officialsRepository = appContainer.officialsRepository,
                    progressRepository = appContainer.progressRepository,
                    onOpenWalkthrough = { navController.navigate(Routes.WALKTHROUGH) },
                )
            }
            composable(
                route = Routes.COMING_SOON,
                arguments = listOf(navArgument("title") { defaultValue = "" }),
            ) { backStackEntry ->
                ComingSoonScreen(title = backStackEntry.arguments?.getString("title") ?: "")
            }
        }
    }
}
