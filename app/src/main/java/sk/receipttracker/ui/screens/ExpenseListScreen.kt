package sk.receipttracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import sk.receipttracker.data.model.Expense
import sk.receipttracker.ui.viewmodel.ExpenseViewModel
import sk.receipttracker.util.ExportHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    viewModel: ExpenseViewModel,
    onAddExpenseClick: () -> Unit,
    onEditExpenseClick: (Long) -> Unit,
    onExpenseClick: (Long) -> Unit,
    onOpenMapClick: () -> Unit,
    onOpenAnalyticsClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val expenses by viewModel.filteredExpenses.collectAsState()
    val allExpenses by viewModel.allExpenses.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val totalSpent by viewModel.totalSpent.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedDateMillis by viewModel.selectedDateMillis.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val dateRange by viewModel.dateRange.collectAsState()
    val isCurrentMonthOnly by viewModel.isCurrentMonthOnly.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export výdavkov") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vyberte formát, do ktorého chcete exportovať záznamy o výdavkoch:")

                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportDialog = false
                                if (allExpenses.isEmpty()) {
                                    Toast.makeText(context, "Nemáte žiadne výdavky na export", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                isExporting = true
                                coroutineScope.launch {
                                    try {
                                        val uri = viewModel.exportToPdf(context)
                                        isExporting = false
                                        ExportHelper.shareFile(context, uri, "application/pdf", "Výkaz výdavkov (PDF)")
                                    } catch (e: Exception) {
                                        isExporting = false
                                        Toast.makeText(context, "Chyba pri generovaní PDF", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("PDF Výkaz s bločkami", fontWeight = FontWeight.Bold)
                                Text("Prehľadná tabuľka a galéria odfotených bločkov", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }

                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportDialog = false
                                if (allExpenses.isEmpty()) {
                                    Toast.makeText(context, "Nemáte žiadne výdavky na export", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                isExporting = true
                                coroutineScope.launch {
                                    try {
                                        val uri = viewModel.exportToCsv(context)
                                        isExporting = false
                                        ExportHelper.shareFile(context, uri, "text/csv", "Zoznam výdavkov (CSV)")
                                    } catch (e: Exception) {
                                        isExporting = false
                                        Toast.makeText(context, "Chyba pri generovaní CSV", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("CSV formát pre Excel / Tabuľky", fontWeight = FontWeight.Bold)
                                Text("Štruktúrovaná tabuľka s kategóriami a GPS", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Zrušiť")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Hľadať obchod, bloček, poznámku...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Vymazať vyhľadávanie")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Moje Výdavky", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(
                            onClick = {
                                isSearchActive = false
                                viewModel.setSearchQuery("")
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Zavrieť vyhľadávanie")
                        }
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Hľadať výdavky"
                            )
                        }
                    }
                    IconButton(onClick = { showDateRangePicker = true }) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Filtrovať podľa obdobia (Od - Do)",
                            tint = if (dateRange != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Filtrovať podľa dňa v kalendári",
                            tint = if (selectedDateMillis != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(onClick = onOpenAnalyticsClick) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = "Štatistiky a Grafy"
                        )
                    }
                    IconButton(onClick = onOpenMapClick) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Mapa výdavkov"
                        )
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Exportovať výdavky"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddExpenseClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Pridať výdavok")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Summary Card (Total amount for the shown period)
            val currentMonthLabel = remember {
                val sdf = SimpleDateFormat("LLLL yyyy", Locale("sk", "SK"))
                val name = sdf.format(Date())
                name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val summaryTitle = when {
                        dateRange != null -> "Výdavky za vybrané obdobie"
                        selectedDateMillis != null -> "Výdavky za vybraný deň"
                        isCurrentMonthOnly -> "Výdavky za tento mesiac ($currentMonthLabel)"
                        else -> "Celkové výdavky (Všetky nákupy)"
                    }

                    Text(
                        text = summaryTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.2f €", totalSpent),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Categories and Filters horizontal scroll chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button to toggle between "Tento mesiac" (default) and "Všetky nákupy"
                FilterChip(
                    selected = !isCurrentMonthOnly,
                    onClick = { viewModel.toggleCurrentMonthOnly() },
                    label = {
                        Text(if (isCurrentMonthOnly) "Zobraziť všetky nákupy" else "Všetky nákupy (aktívne)")
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                if (!isCurrentMonthOnly) {
                    OutlinedButton(
                        onClick = { viewModel.setCurrentMonthOnly(true) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Späť na tento mesiac", fontSize = 12.sp)
                    }
                }
                // Active Date Range filter badge (only shown when range filter is active, with ✕ to remove)
                if (dateRange != null) {
                    val sdf = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
                    val rangeText = "${sdf.format(Date(dateRange!!.first))} – ${sdf.format(Date(dateRange!!.second))}"
                    InputChip(
                        selected = true,
                        onClick = { showDateRangePicker = true },
                        label = { Text("Obdobie: $rangeText") },
                        leadingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { viewModel.clearDateRange() },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Zrušiť filter obdobia")
                            }
                        }
                    )
                }

                // Active Calendar single day badge (only shown when day filter is active, with ✕ to remove)
                if (selectedDateMillis != null) {
                    val dateFormatted = remember(selectedDateMillis) {
                        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                        sdf.format(Date(selectedDateMillis!!))
                    }
                    InputChip(
                        selected = true,
                        onClick = { showDatePicker = true },
                        label = { Text("Deň: $dateFormatted") },
                        leadingIcon = {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { viewModel.selectDate(null) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Zrušiť filter dňa")
                            }
                        }
                    )
                }

                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) },
                    label = { Text("Všetky kategórie") }
                )
                allCategories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            if (selectedCategory == category) {
                                viewModel.selectCategory(null)
                            } else {
                                viewModel.selectCategory(category)
                            }
                        },
                        label = { Text(category) }
                    )
                }
            }

            if (isExporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // List of expenses
            if (expenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Žiadne výdavky na zobrazenie",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Kliknite na tlačidlo + a pridajte nový výdavok s bločkom",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(expenses, key = { it.id }) { expense ->
                        ExpenseListItem(
                            expense = expense,
                            onClick = { onExpenseClick(expense.id) },
                            onEdit = { onEditExpenseClick(expense.id) },
                            onDelete = { viewModel.deleteExpense(expense) }
                        )
                    }
                }
            }
        }
    }

    // Krásny kalendár s označením dní nákupov
    if (showDatePicker) {
        sk.receipttracker.ui.components.ExpenseCalendarDialog(
            initialDateMillis = selectedDateMillis ?: System.currentTimeMillis(),
            expenses = allExpenses,
            onDateSelected = { dateMillis ->
                viewModel.selectDate(dateMillis)
                showDatePicker = false
            },
            onClearDate = if (selectedDateMillis != null) {
                { viewModel.selectDate(null) }
            } else null,
            onDismissRequest = { showDatePicker = false }
        )
    }

    // Material 3 DateRangePicker Dialog („Od – Do“)
    if (showDateRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = dateRange?.first,
            initialSelectedEndDateMillis = dateRange?.second
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis ?: start
                        if (start != null) {
                            viewModel.setDateRange(start, end)
                        }
                        showDateRangePicker = false
                    }
                ) {
                    Text("Použiť")
                }
            },
            dismissButton = {
                Row {
                    if (dateRange != null) {
                        TextButton(
                            onClick = {
                                viewModel.clearDateRange()
                                showDateRangePicker = false
                            }
                        ) {
                            Text("Zrušiť filter")
                        }
                    }
                    TextButton(onClick = { showDateRangePicker = false }) {
                        Text("Zavrieť")
                    }
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = {
                    Text(
                        text = "Vyberte obdobie (Od – Do)",
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                headline = {
                    val sdf = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
                    val startStr = dateRangePickerState.selectedStartDateMillis?.let { sdf.format(Date(it)) } ?: "Od"
                    val endStr = dateRangePickerState.selectedEndDateMillis?.let { sdf.format(Date(it)) } ?: "Do"
                    Text(
                        text = "$startStr – $endStr",
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                showModeToggle = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
            )
        }
    }
}

@Composable
fun ExpenseListItem(
    expense: Expense,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Receipt photo thumbnail
            if (!expense.photoPath.isNullOrBlank()) {
                AsyncImage(
                    model = File(expense.photoPath),
                    contentDescription = "Foto bločku",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(expense.category, fontSize = 11.sp) },
                        modifier = Modifier.height(26.dp)
                    )
                    if (expense.latitude != null && expense.longitude != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "GPS zaznamenané",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = expense.formattedDate(),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // Price & Actions
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = expense.formattedAmount(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Upraviť",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Zmazať",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
