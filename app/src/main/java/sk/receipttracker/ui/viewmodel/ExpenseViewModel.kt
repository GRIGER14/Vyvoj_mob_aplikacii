package sk.receipttracker.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sk.receipttracker.data.local.AppDatabase
import sk.receipttracker.data.model.Expense
import sk.receipttracker.data.repository.ExpenseRepository
import sk.receipttracker.util.FileHelper
import sk.receipttracker.util.LocationHelper

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExpenseRepository
    val allExpenses: StateFlow<List<Expense>>
    val expensesWithLocation: StateFlow<List<Expense>>

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ExpenseRepository(database.expenseDao())

        allExpenses = repository.allExpenses.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        expensesWithLocation = repository.expensesWithLocation.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    val filteredExpenses: StateFlow<List<Expense>> = combine(
        allExpenses,
        _selectedCategory
    ) { expenses, category ->
        if (category == null) {
            expenses
        } else {
            expenses.filter { it.category.equals(category, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalSpent: StateFlow<Double> = filteredExpenses.combine(filteredExpenses) { list, _ ->
        list.sumOf { it.amount }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun saveExpense(
        title: String,
        amount: Double,
        category: String,
        photoPath: String?,
        latitude: Double?,
        longitude: Double?,
        note: String = "",
        existingId: Long = 0L,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val expense = Expense(
                id = existingId,
                title = title,
                amount = amount,
                category = category,
                date = System.currentTimeMillis(),
                photoPath = photoPath,
                latitude = latitude,
                longitude = longitude,
                note = note
            )

            if (existingId == 0L) {
                repository.insertExpense(expense)
            } else {
                repository.updateExpense(expense)
            }
            onSuccess()
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            // Delete associated photo file
            FileHelper.deleteImageFile(expense.photoPath)
            repository.deleteExpense(expense)
        }
    }

    fun getExpense(id: Long) = repository.getExpenseById(id)

    fun fetchCurrentLocation(context: Context, onResult: (latitude: Double?, longitude: Double?) -> Unit) {
        viewModelScope.launch {
            val location = LocationHelper.getCurrentLocation(context)
            onResult(location?.latitude, location?.longitude)
        }
    }
}
