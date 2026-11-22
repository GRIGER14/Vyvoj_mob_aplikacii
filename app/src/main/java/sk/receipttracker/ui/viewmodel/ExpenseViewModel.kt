package sk.receipttracker.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.receipttracker.data.local.AppDatabase
import sk.receipttracker.data.model.Expense
import sk.receipttracker.data.repository.ExpenseRepository
import sk.receipttracker.util.ExportHelper
import sk.receipttracker.util.FileHelper
import sk.receipttracker.util.LocationHelper
import sk.receipttracker.util.OcrHelper
import sk.receipttracker.util.OcrResult
import java.util.Calendar

enum class AnalyticsPeriod(val title: String) {
    ALL_TIME("Všetko"),
    THIS_MONTH("Tento mesiac"),
    SPECIFIC_MONTH("Vybrať mesiac")
}

data class CategoryStat(
    val category: String,
    val total: Double,
    val percentage: Float,
    val count: Int
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExpenseRepository
    val allExpenses: StateFlow<List<Expense>>
    val expensesWithLocation: StateFlow<List<Expense>>

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    // Full-text search query (matches title or note)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Date range filter: Pair<startMillis, endMillis>?
    private val _dateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val dateRange: StateFlow<Pair<Long, Long>?> = _dateRange.asStateFlow()

    // Filter by specific day (epoch millis of start of the day in local timezone, or null for all days)
    private val _selectedDateMillis = MutableStateFlow<Long?>(null)
    val selectedDateMillis: StateFlow<Long?> = _selectedDateMillis.asStateFlow()

    // Mode for main screen: true = current month only (default), false = all time
    private val _isCurrentMonthOnly = MutableStateFlow(true)
    val isCurrentMonthOnly: StateFlow<Boolean> = _isCurrentMonthOnly.asStateFlow()

    // Analytics month: (year, month 0-based). Default to current year & month
    private val _analyticsMonth = MutableStateFlow<Pair<Int, Int>>(
        Pair(
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH)
        )
    )
    val analyticsMonth: StateFlow<Pair<Int, Int>> = _analyticsMonth.asStateFlow()

    private val _analyticsPeriod = MutableStateFlow(AnalyticsPeriod.ALL_TIME)
    val analyticsPeriod: StateFlow<AnalyticsPeriod> = _analyticsPeriod.asStateFlow()

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

    // Store Insights: Groups expenses with location by store name/title
    val storeInsights: StateFlow<List<sk.receipttracker.data.model.StoreInsight>> = expensesWithLocation.combine(expensesWithLocation) { list, _ ->
        list.groupBy { it.title.trim() }
            .mapNotNull { (storeName, storeExpenses) ->
                val firstValidLocation = storeExpenses.firstOrNull { it.latitude != null && it.longitude != null }
                if (firstValidLocation != null) {
                    val totalAmount = storeExpenses.sumOf { it.amount }
                    val latestExpense = storeExpenses.maxByOrNull { it.date } ?: firstValidLocation
                    sk.receipttracker.data.model.StoreInsight(
                        storeName = storeName,
                        totalAmount = totalAmount,
                        count = storeExpenses.size,
                        latitude = firstValidLocation.latitude!!,
                        longitude = firstValidLocation.longitude!!,
                        latestCategory = latestExpense.category,
                        latestDate = latestExpense.date
                    )
                } else null
            }
            .sortedByDescending { it.totalAmount }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Dynamic list of all categories (Default categories + any custom categories, with "Iné" always kept at the end)
    val allCategories: StateFlow<List<String>> = allExpenses.combine(allExpenses) { list, _ ->
        val baseWithoutIne = Expense.DEFAULT_CATEGORIES.filter { !it.equals("Iné", ignoreCase = true) }
        val customCats = list.map { it.category.trim() }
            .filter { it.isNotBlank() && !it.equals("Iné", ignoreCase = true) }
            .distinct()
        val allOtherCats = (baseWithoutIne + customCats).distinct()
        allOtherCats + "Iné"
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Expense.DEFAULT_CATEGORIES
    )

    val filteredExpenses: StateFlow<List<Expense>> = combine(
        allExpenses,
        _selectedCategory,
        _searchQuery,
        _dateRange,
        _selectedDateMillis,
        _isCurrentMonthOnly
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val expenses = args[0] as List<Expense>
        val category = args[1] as String?
        val query = (args[2] as String).trim()
        @Suppress("UNCHECKED_CAST")
        val range = args[3] as Pair<Long, Long>?
        val singleDateMillis = args[4] as Long?
        val currentMonthOnly = args[5] as Boolean

        // Calculate start and end of current month
        val currentMonthRange = if (currentMonthOnly && range == null && singleDateMillis == null) {
            val calStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val calEnd = (calStart.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            Pair(calStart.timeInMillis, calEnd.timeInMillis)
        } else null

        expenses.filter { expense ->
            // Search query filter (full-text search over title and note)
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                expense.title.contains(query, ignoreCase = true) ||
                        expense.note.contains(query, ignoreCase = true)
            }

            // Category filter
            val matchesCategory = if (category == null) {
                true
            } else {
                expense.category.equals(category, ignoreCase = true)
            }

            // Custom Date Range filter ("Od - Do")
            val matchesRange = if (range == null) {
                true
            } else {
                expense.date in range.first..range.second
            }

            // Single date filter (from calendar day selection)
            val matchesSingleDate = if (singleDateMillis == null) {
                true
            } else {
                val calExpense = Calendar.getInstance().apply { timeInMillis = expense.date }
                val calFilter = Calendar.getInstance().apply { timeInMillis = singleDateMillis }
                calExpense.get(Calendar.YEAR) == calFilter.get(Calendar.YEAR) &&
                        calExpense.get(Calendar.DAY_OF_YEAR) == calFilter.get(Calendar.DAY_OF_YEAR)
            }

            // Default current month filter (active if not in "All expenses" mode and no custom date filter is set)
            val matchesCurrentMonth = if (currentMonthRange != null) {
                expense.date in currentMonthRange.first..currentMonthRange.second
            } else {
                true
            }

            matchesQuery && matchesCategory && matchesRange && matchesSingleDate && matchesCurrentMonth
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

    // Total count of purchases in the selected analytics period
    val periodExpenseCount: StateFlow<Int> = combine(
        allExpenses,
        _analyticsPeriod,
        _analyticsMonth
    ) { expenses, period, (year, month) ->
        getFilteredAnalyticsExpenses(expenses, period, year, month).size
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // Analytics state: Category Breakdown, count and percentage
    val categoryStats: StateFlow<List<CategoryStat>> = combine(
        allExpenses,
        _analyticsPeriod,
        _analyticsMonth
    ) { expenses, period, (year, month) ->
        val filtered = getFilteredAnalyticsExpenses(expenses, period, year, month)
        val total = filtered.sumOf { it.amount }
        if (total == 0.0) {
            emptyList()
        } else {
            filtered.groupBy { it.category }
                .map { (cat, list) ->
                    val sum = list.sumOf { it.amount }
                    CategoryStat(
                        category = cat,
                        total = sum,
                        percentage = ((sum / total) * 100).toFloat(),
                        count = list.size
                    )
                }
                .sortedByDescending { it.total }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val periodTotalSpent: StateFlow<Double> = categoryStats.combine(categoryStats) { stats, _ ->
        stats.sumOf { it.total }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    private fun getFilteredAnalyticsExpenses(
        expenses: List<Expense>,
        period: AnalyticsPeriod,
        selectedYear: Int,
        selectedMonth: Int
    ): List<Expense> {
        return when (period) {
            AnalyticsPeriod.ALL_TIME -> expenses
            AnalyticsPeriod.THIS_MONTH -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfMonth = cal.timeInMillis
                expenses.filter { it.date >= startOfMonth }
            }
            AnalyticsPeriod.SPECIFIC_MONTH -> {
                val calStart = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val calEnd = (calStart.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                expenses.filter { it.date in calStart.timeInMillis..calEnd.timeInMillis }
            }
        }
    }

    fun setAnalyticsPeriod(period: AnalyticsPeriod) {
        _analyticsPeriod.value = period
    }

    fun setAnalyticsMonth(year: Int, month: Int) {
        _analyticsMonth.value = Pair(year, month)
        _analyticsPeriod.value = AnalyticsPeriod.SPECIFIC_MONTH
    }

    fun toggleCurrentMonthOnly() {
        _isCurrentMonthOnly.value = !_isCurrentMonthOnly.value
    }

    fun setCurrentMonthOnly(currentMonthOnly: Boolean) {
        _isCurrentMonthOnly.value = currentMonthOnly
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setDateRange(fromMillis: Long?, toMillis: Long?) {
        if (fromMillis != null && toMillis != null) {
            val startOfDay = Calendar.getInstance().apply {
                timeInMillis = fromMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val endOfDay = Calendar.getInstance().apply {
                timeInMillis = toMillis
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            _dateRange.value = Pair(startOfDay, endOfDay)
            _selectedDateMillis.value = null // clear single day when range is set
        } else {
            _dateRange.value = null
        }
    }

    fun clearDateRange() {
        _dateRange.value = null
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun selectDate(dateMillis: Long?) {
        _selectedDateMillis.value = dateMillis
        if (dateMillis != null) {
            _dateRange.value = null // clear range when single day is picked
        }
    }

    fun saveExpense(
        title: String,
        amount: Double,
        category: String,
        photoPath: String?,
        latitude: Double?,
        longitude: Double?,
        note: String = "",
        date: Long = System.currentTimeMillis(),
        existingId: Long = 0L,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val expense = Expense(
                id = existingId,
                title = title,
                amount = amount,
                category = category,
                date = date,
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
            FileHelper.deleteImageFile(expense.photoPath)
            repository.deleteExpense(expense)
        }
    }

    fun getExpense(id: Long) = repository.getExpenseById(id)

    suspend fun getStoreStats(storeName: String): Pair<Double, Int> {
        val total = repository.getStoreTotalSpent(storeName)
        val count = repository.getStoreExpenseCount(storeName)
        return Pair(total, count)
    }

    fun getExpensesByStore(storeName: String) = repository.getExpensesByStore(storeName)

    fun fetchCurrentLocation(context: Context, onResult: (latitude: Double?, longitude: Double?) -> Unit) {
        viewModelScope.launch {
            val location = LocationHelper.getCurrentLocation(context)
            onResult(location?.latitude, location?.longitude)
        }
    }

    // Feature #1: OCR processing
    fun processReceiptOcr(
        context: Context,
        photoPath: String,
        onResult: (OcrResult) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        OcrHelper.processReceipt(context, photoPath, onResult, onError)
    }

    // Feature #6: Exporting
    suspend fun exportToCsv(context: Context): Uri = withContext(Dispatchers.IO) {
        ExportHelper.exportToCsv(context, allExpenses.value)
    }

    suspend fun exportToPdf(context: Context): Uri = withContext(Dispatchers.IO) {
        ExportHelper.exportToPdf(context, allExpenses.value)
    }
}
