package sk.receipttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import sk.receipttracker.ui.screens.AddEditExpenseScreen
import sk.receipttracker.ui.screens.AnalyticsScreen
import sk.receipttracker.ui.screens.ExpenseDetailScreen
import sk.receipttracker.ui.screens.ExpenseListScreen
import sk.receipttracker.ui.screens.MapScreen
import sk.receipttracker.ui.theme.ReceiptTrackerTheme
import sk.receipttracker.ui.viewmodel.ExpenseViewModel

class MainActivity : ComponentActivity() {

    private val expenseViewModel: ExpenseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReceiptTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ReceiptTrackerApp(viewModel = expenseViewModel)
                }
            }
        }
    }
}

sealed class Screen(val route: String) {
    object ExpenseList : Screen("expense_list")
    object AddExpense : Screen("add_expense")
    object EditExpense : Screen("edit_expense/{expenseId}") {
        fun createRoute(expenseId: Long) = "edit_expense/$expenseId"
    }
    object ExpenseDetail : Screen("expense_detail/{expenseId}") {
        fun createRoute(expenseId: Long) = "expense_detail/$expenseId"
    }
    object Map : Screen("map")
    object Analytics : Screen("analytics")
}

@Composable
fun ReceiptTrackerApp(viewModel: ExpenseViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.ExpenseList.route
    ) {
        composable(Screen.ExpenseList.route) {
            ExpenseListScreen(
                viewModel = viewModel,
                onAddExpenseClick = { navController.navigate(Screen.AddExpense.route) },
                onEditExpenseClick = { id -> navController.navigate(Screen.EditExpense.createRoute(id)) },
                onExpenseClick = { id -> navController.navigate(Screen.ExpenseDetail.createRoute(id)) },
                onOpenMapClick = { navController.navigate(Screen.Map.route) },
                onOpenAnalyticsClick = { navController.navigate(Screen.Analytics.route) }
            )
        }

        composable(Screen.AddExpense.route) {
            AddEditExpenseScreen(
                viewModel = viewModel,
                expenseId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.EditExpense.route,
            arguments = listOf(navArgument("expenseId") { type = NavType.LongType })
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getLong("expenseId") ?: 0L
            AddEditExpenseScreen(
                viewModel = viewModel,
                expenseId = expenseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ExpenseDetail.route,
            arguments = listOf(navArgument("expenseId") { type = NavType.LongType })
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getLong("expenseId") ?: 0L
            ExpenseDetailScreen(
                viewModel = viewModel,
                expenseId = expenseId,
                onNavigateBack = { navController.popBackStack() },
                onEditClick = { id -> navController.navigate(Screen.EditExpense.createRoute(id)) }
            )
        }

        composable(Screen.Map.route) {
            MapScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Analytics.route) {
            AnalyticsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
