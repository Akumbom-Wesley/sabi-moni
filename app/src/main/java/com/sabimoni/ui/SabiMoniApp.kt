package com.sabimoni.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sabimoni.feature.capture.CaptureScreen
import com.sabimoni.feature.groups.GroupsScreen
import com.sabimoni.feature.reports.ReportsScreen
import com.sabimoni.feature.savings.SavingsScreen
import com.sabimoni.feature.settings.SettingsScreen
import com.sabimoni.ui.navigation.CaptureRoute
import com.sabimoni.ui.navigation.GroupsRoute
import com.sabimoni.ui.navigation.ReportsRoute
import com.sabimoni.ui.navigation.SavingsRoute
import com.sabimoni.ui.navigation.SettingsRoute
import kotlin.reflect.KClass

private data class TabSpec(
    val label: String,
    val icon: ImageVector,
    val route: Any,
    val routeClass: KClass<*>,
)

private val TABS = listOf(
    TabSpec("Today", Icons.Default.Edit, CaptureRoute, CaptureRoute::class),
    TabSpec("Groups", Icons.Default.Person, GroupsRoute, GroupsRoute::class),
    TabSpec("Reports", Icons.Default.List, ReportsRoute, ReportsRoute::class),
    TabSpec("Savings", Icons.Default.Star, SavingsRoute, SavingsRoute::class),
    TabSpec("Settings", Icons.Default.Settings, SettingsRoute, SettingsRoute::class),
)

@Composable
fun SabiMoniApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    Scaffold(
        // The Scaffold's container fills the whole window, system bars included, so this
        // is also the colour of the strip behind the clock and battery. A tonal surface
        // rather than plain `background`, which under a light dynamic palette is close
        // enough to white that the top of the screen read as blank (ADR-0024).
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        bottomBar = {
            SabiMoniBottomBar(
                navController = navController,
                currentDestination = backStackEntry?.destination,
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = CaptureRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<CaptureRoute> { CaptureScreen() }
            composable<GroupsRoute> { GroupsScreen() }
            composable<ReportsRoute> { ReportsScreen() }
            composable<SavingsRoute> { SavingsScreen() }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}

@Composable
private fun SabiMoniBottomBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
) {
    NavigationBar {
        TABS.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any {
                it.hasRoute(tab.routeClass)
            } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}
