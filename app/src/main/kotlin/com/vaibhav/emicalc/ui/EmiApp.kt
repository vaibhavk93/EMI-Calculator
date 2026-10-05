package com.vaibhav.emicalc.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vaibhav.emicalc.di.AppContainer
import com.vaibhav.emicalc.ui.screens.AmortisationScreen
import com.vaibhav.emicalc.ui.screens.CalculatorScreen
import com.vaibhav.emicalc.ui.screens.FnfScreen
import com.vaibhav.emicalc.ui.screens.HistoryScreen
import com.vaibhav.emicalc.ui.screens.LoanEditScreen
import com.vaibhav.emicalc.ui.screens.LoansScreen
import com.vaibhav.emicalc.ui.screens.RunwayScreen

object Routes {
    const val RUNWAY = "runway"
    const val LOANS = "loans"
    const val LOAN_EDIT = "loans/edit"
    const val AMORTISATION = "loans/schedule"
    const val FNF = "fnf"
    const val CALCULATOR = "calculator"
    const val HISTORY = "history"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.RUNWAY, "Runway", Icons.Filled.Timeline),
    Tab(Routes.FNF, "Exit", Icons.Filled.ExitToApp),
    Tab(Routes.LOANS, "Loans", Icons.Filled.AccountBalance),
    Tab(Routes.CALCULATOR, "Calc", Icons.Filled.Calculate),
    Tab(Routes.HISTORY, "History", Icons.Filled.History),
)

@Composable
fun EmiApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    // Deleted history is kept for a month so a delete can be undone, then swept.
    LaunchedEffect(Unit) { container.history.sweep() }

    // Resume where the user left off, but only once per process and only if they had
    // actually got somewhere. Landing them mid-form on every cold start would be worse
    // than a predictable home screen.
    var resumeHandled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!resumeHandled) {
            resumeHandled = true
            container.resume.latest()?.let { point ->
                // Only ever resume onto a top-level tab. Dropping someone into a
                // half-built form on a cold start is more disorienting than helpful;
                // the draft itself is restored once they open that form.
                if (tabs.any { it.route == point.route }) {
                    navController.navigate(point.route) {
                        popUpTo(Routes.RUNWAY) { inclusive = false }
                    }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.RUNWAY,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.RUNWAY) {
                RunwayScreen(
                    container = container,
                    onAddLoan = { navController.navigate(Routes.LOAN_EDIT) },
                    onOpenExit = { navController.navigate(Routes.FNF) },
                )
            }
            composable(Routes.FNF) { FnfScreen(container = container) }
            composable(Routes.LOANS) {
                LoansScreen(
                    container = container,
                    onAddLoan = { navController.navigate(Routes.LOAN_EDIT) },
                    onEditLoan = { id -> navController.navigate("${Routes.LOAN_EDIT}?id=$id") },
                    onViewSchedule = { id -> navController.navigate("${Routes.AMORTISATION}?id=$id") },
                )
            }
            // One route with an optional argument, so navigating to "loans/edit"
            // (add) and "loans/edit?id=..." (edit) both land here.
            composable(
                route = "${Routes.LOAN_EDIT}?id={id}",
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                LoanEditScreen(
                    container = container,
                    loanId = entry.arguments?.getString("id"),
                    onDone = { navController.popBackStack() },
                )
            }
            composable("${Routes.AMORTISATION}?id={id}") { entry ->
                AmortisationScreen(
                    container = container,
                    loanId = entry.arguments?.getString("id"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.CALCULATOR) { CalculatorScreen(container = container) }
            composable(Routes.HISTORY) { HistoryScreen(container = container) }
        }
    }
}
