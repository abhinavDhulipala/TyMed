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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.tymed.app.data.entity.ProfileColors
import com.tymed.app.ui.assistant.AssistantScreen
import com.tymed.app.ui.doses.DayScreen
import com.tymed.app.ui.doses.DoseScreen
import com.tymed.app.ui.doses.TodayScreen
import com.tymed.app.ui.incidents.IncidentFormScreen
import com.tymed.app.ui.incidents.IncidentsListScreen
import com.tymed.app.ui.medications.MedicationFormScreen
import com.tymed.app.ui.medications.MedicationsListScreen
import com.tymed.app.ui.profiles.FirstProfileScreen
import com.tymed.app.ui.profiles.ProfileSwitcherBar
import com.tymed.app.ui.profiles.ProfilesScreen
import com.tymed.app.ui.settings.SettingsScreen
import com.tymed.app.util.TimeFormatPreference
import kotlinx.coroutines.launch

private sealed class Tab(val route: String, val label: String) {
    data object Today : Tab("today", "Today")
    data object Medications : Tab("medications", "Medications")
    data object Incidents : Tab("incidents", "Incidents")
    data object Assistant : Tab("assistant", "Assistant")
    data object Settings : Tab("settings", "Settings")
}

private val TABS = listOf(Tab.Today, Tab.Medications, Tab.Incidents, Tab.Assistant, Tab.Settings)

@Composable
fun TymedApp(container: AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // Reactive, not a one-time snapshot: a profile added/renamed/deleted anywhere (e.g. the
    // Profiles screen's own FAB) must show up here immediately — this is also what the top bar
    // reads, and activeProfile.current can start pointing at a brand-new id the instant it's
    // created. null = still loading; everything below renders nothing until the first emission,
    // so there's no frame where the NavHost briefly queries an invalid profile id.
    val profiles by container.profileRepository.observeProfiles().collectAsState(initial = null)

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Backstop for BootReceiver — covers a fresh debug install or any other case a reboot
        // broadcast was missed. Deliberately unfiltered by profile: every profile's alarms must
        // keep ringing regardless of which one is active in the UI (it's a shared device).
        launch {
            val schedules = container.scheduleRepository.listAllEnabledSchedulesWithMedication()
            container.alarmScheduler.rearmAllScheduleAlarms(schedules)
        }
    }

    val loadedProfiles = profiles ?: return

    if (loadedProfiles.isEmpty()) {
        FirstProfileScreen(onCreate = { name ->
            coroutineScope.launch {
                val id = container.profileRepository.createProfile(name.trim(), ProfileColors.forIndex(0))
                container.activeProfile.switchTo(id)
            }
        })
        return
    }

    // The persisted active profile id may point at NO_PROFILE (first real run) or a since-deleted
    // profile — fall back to the first profile on the list in either case.
    LaunchedEffect(loadedProfiles) {
        if (loadedProfiles.none { it.id == container.activeProfile.current }) {
            container.activeProfile.switchTo(loadedProfiles.first().id)
        }
    }

    val activeProfileId = container.activeProfile.current
    val activeProfile = loadedProfiles.find { it.id == activeProfileId } ?: return

    LaunchedEffect(activeProfileId) {
        AiAssistantPreference.enabled = container.settingsRepository.getAiAssistantEnabled(activeProfileId)
        TimeFormatPreference.use24Hour = container.settingsRepository.getUse24HourFormat(activeProfileId)
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    // Collapsed (not just hidden) when the assistant is off, to actually free up bottom-bar
    // space — not everyone wants an on-device model running.
    val visibleTabs = TABS.filter { it != Tab.Assistant || AiAssistantPreference.enabled }

    Scaffold(
        topBar = {
            ProfileSwitcherBar(
                profiles = loadedProfiles,
                activeProfile = activeProfile,
                onSwitch = { id -> container.activeProfile.switchTo(id) },
                onManage = { navController.navigate("profiles") },
            )
        },
        bottomBar = {
            NavigationBar {
                visibleTabs.forEach { tab ->
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

            composable(Tab.Incidents.route) {
                IncidentsListScreen(
                    onIncidentClick = { id -> navController.navigate("incidents/$id") },
                    onAddClick = { navController.navigate("incidents/new") },
                )
            }
            composable("incidents/new") {
                IncidentFormScreen(
                    incidentId = null,
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                )
            }
            composable(
                "incidents/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                IncidentFormScreen(
                    incidentId = id,
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack(Tab.Incidents.route, inclusive = false) },
                )
            }

            composable(Tab.Assistant.route) { AssistantScreen() }
            composable(Tab.Settings.route) { SettingsScreen() }
            composable("profiles") { ProfilesScreen(onDone = { navController.popBackStack() }) }

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
    Tab.Incidents -> Icons.Default.Warning
    Tab.Assistant -> Icons.AutoMirrored.Filled.Chat
    Tab.Settings -> Icons.Default.Settings
}
