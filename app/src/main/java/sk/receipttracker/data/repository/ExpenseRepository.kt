package sk.receipttracker.data.repository

import kotlinx.coroutines.flow.Flow
import sk.receipttracker.data.local.ExpenseDao
import sk.receipttracker.data.model.Expense

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()
    val expensesWithLocation: Flow<List<Expense>> = expenseDao.getExpensesWithLocation()

    fun searchExpenses(
        query: String?,
        category: String?,
        fromMillis: Long?,
        toMillis: Long?
    ): Flow<List<Expense>> {
        return expenseDao.searchExpenses(query, category, fromMillis, toMillis)
    }

    suspend fun getStoreExpenseCount(storeName: String): Int {
        return expenseDao.getStoreExpenseCount(storeName)
    }

    suspend fun getStoreTotalSpent(storeName: String): Double {
        return expenseDao.getStoreTotalSpent(storeName)
    }

    fun getExpensesByStore(storeName: String): Flow<List<Expense>> {
        return expenseDao.getExpensesByStore(storeName)
    }

    fun getExpenseById(id: Long): Flow<Expense?> {
        return expenseDao.getExpenseById(id)
    }

    suspend fun insertExpense(expense: Expense): Long {
        return expenseDao.insertExpense(expense)
    }

    suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(expense)
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpense(expense)
    }
}
