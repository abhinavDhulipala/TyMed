package com.tymed.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.tymed.app.AppContainer
import com.tymed.app.ui.assistant.AssistantScreen
import com.tymed.app.ui.doses.DayScreen
import com.tymed.app.ui.doses.DoseScreen
import com.tymed.app.ui.doses.TodayScreen
import com.tymed.app.ui.medications.MedicationFormScreen
import com.tymed.app.ui.medications.MedicationsListScreen
import com.tymed.app.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private sealed class Tab(val route: String, val label: String) {
    data object Today : Tab("today", "Today")
    data object Medications : Tab("medications", "Medications")
    data object Assistant : Tab("assistant", "Assistant")
    data object Settings : Tab("settings", "Settings")
}

private val TABS = listOf(Tab.Today, Tab.Medications, Tab.Assistant, Tab.Settings)

@Composable
fun TymedApp(container: AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Backstop for BootReceiver — covers a fresh debug install or any other case a reboot
        // broadcast was missed.
        launch {
            val schedules = container.scheduleRepository.listAllEnabledSchedulesWithMedication()
            container.alarmScheduler.rearmAllScheduleAlarms(schedules)
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Today.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Today.route) {
                TodayScreen(
                    onDoseClick = { logId -> navController.navigate("dose/$logId") },
                    onDayClick = { dateStr -> navController.navigate("day/$dateStr") },
                    onAddMedication = {
                        navController.navigate(Tab.Medications.route)
                        navController.navigate("medications/new")
                    },
                )
            }

            composable(Tab.Medications.route) {
                MedicationsListScreen(
                    onMedicationClick = { id -> navController.navigate("medications/$id") },
                    onAddClick = { navController.navigate("medications/new") },
                )
            }
            composable("medications/new") {
                MedicationFormScreen(
                    medicationId = null,
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                )
            }
            composable(
                "medications/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                MedicationFormScreen(
                    medicationId = id,
                    onSaved = { navController.popBackStack() },
                    onDeleted = {
                        navController.popBackStack(Tab.Medications.route, inclusive = false)
                    },
                )
            }

            composable(Tab.Assistant.route) { AssistantScreen() }
            composable(Tab.Settings.route) { SettingsScreen() }

            composable(
                "day/{date}",
                arguments = listOf(navArgument("date") { type = NavType.StringType }),
            ) { entry ->
                val date = entry.arguments?.getString("date") ?: return@composable
                DayScreen(dateStr = date, onDoseClick = { logId -> navController.navigate("dose/$logId") })
            }
            composable(
                "dose/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                DoseScreen(
                    logId = id,
                    onEditMedication = { medicationId -> navController.navigate("medications/$medicationId") },
                )
            }
        }
    }
}

private fun tabIcon(tab: Tab) = when (tab) {
    Tab.Today -> Icons.Default.Today
    Tab.Medications -> Icons.Default.MedicalServices
    Tab.Assistant -> Icons.AutoMirrored.Filled.Chat
    Tab.Settings -> Icons.Default.Settings
}
