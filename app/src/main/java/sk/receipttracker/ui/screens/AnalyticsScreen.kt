package sk.receipttracker.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.receipttracker.ui.viewmodel.AnalyticsPeriod
import sk.receipttracker.ui.viewmodel.CategoryStat
import sk.receipttracker.ui.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val CATEGORY_COLORS = listOf(
    Color(0xFF006D44), // Emerald
    Color(0xFF1976D2), // Blue
    Color(0xFFFF9800), // Orange
    Color(0xFFE91E63), // Pink
    Color(0xFF9C27B0), // Purple
    Color(0xFF009688), // Teal
    Color(0xFF607D8B), // Blue Grey
    Color(0xFFFF5722)  // Deep Orange
)

fun getCategoryColor(index: Int): Color {
    return CATEGORY_COLORS[index % CATEGORY_COLORS.size]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: ExpenseViewModel,
    onNavigateBack: () -> Unit
) {
    val categoryStats by viewModel.categoryStats.collectAsState()
    val periodTotalSpent by viewModel.periodTotalSpent.collectAsState()
    val periodExpenseCount by viewModel.periodExpenseCount.collectAsState()
    val currentPeriod by viewModel.analyticsPeriod.collectAsState()
    val analyticsMonth by viewModel.analyticsMonth.collectAsState()

    var showMonthDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Štatistiky a Grafy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Späť")
                    }
                },
                actions = {
                    IconButton(onClick = { showMonthDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Vybrať mesiac",
                            tint = if (currentPeriod == AnalyticsPeriod.SPECIFIC_MONTH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Period selector tabs
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AnalyticsPeriod.values().forEachIndexed { index, period ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = AnalyticsPeriod.values().size),
                        onClick = {
                            if (period == AnalyticsPeriod.SPECIFIC_MONTH) {
                                showMonthDialog = true
                            } else {
                                viewModel.setAnalyticsPeriod(period)
                            }
                        },
                        selected = period == currentPeriod
                    ) {
                        Text(
                            text = if (period == AnalyticsPeriod.SPECIFIC_MONTH && currentPeriod == AnalyticsPeriod.SPECIFIC_MONTH) {
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, analyticsMonth.first)
                                    set(Calendar.MONTH, analyticsMonth.second)
                                }
                                val sdf = SimpleDateFormat("LLL yyyy", Locale("sk", "SK"))
                                sdf.format(cal.time)
                            } else {
                                period.title
                            },
                            maxLines = 1,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Month navigation bar when SPECIFIC_MONTH is active
            if (currentPeriod == AnalyticsPeriod.SPECIFIC_MONTH) {
                val currentMonthLabel = remember(analyticsMonth) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, analyticsMonth.first)
                        set(Calendar.MONTH, analyticsMonth.second)
                    }
                    val sdf = SimpleDateFormat("LLLL yyyy", Locale("sk", "SK"))
                    val name = sdf.format(cal.time)
                    name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, analyticsMonth.first)
                                    set(Calendar.MONTH, analyticsMonth.second)
                                    add(Calendar.MONTH, -1)
                                }
                                viewModel.setAnalyticsMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Predchádzajúci mesiac")
                        }

                        TextButton(onClick = { showMonthDialog = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentMonthLabel,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        IconButton(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, analyticsMonth.first)
                                    set(Calendar.MONTH, analyticsMonth.second)
                                    add(Calendar.MONTH, 1)
                                }
                                viewModel.setAnalyticsMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Nasledujúci mesiac")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Summary Card (Total amount and purchase count for the selected period/month)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Počet nákupov",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Receipt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$periodExpenseCount",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    VerticalDivider(modifier = Modifier.height(36.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Celková suma",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.US, "%.2f €", periodTotalSpent),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (categoryStats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Žiadne dáta pre vybrané obdobie",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                // Donut Chart
                DonutChart(
                    stats = categoryStats,
                    totalSpent = periodTotalSpent,
                    modifier = Modifier
                        .size(220.dp)
                        .padding(8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Rozdelenie podľa kategórií",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Breakdown list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(categoryStats) { stat ->
                        val colorIndex = categoryStats.indexOf(stat)
                        CategoryStatItem(stat = stat, color = getCategoryColor(colorIndex))
                    }
                }
            }
        }
    }

    // Month Selector Dialog
    if (showMonthDialog) {
        MonthPickerDialog(
            currentYear = analyticsMonth.first,
            currentMonth = analyticsMonth.second,
            onMonthSelected = { year, month ->
                viewModel.setAnalyticsMonth(year, month)
                showMonthDialog = false
            },
            onDismissRequest = { showMonthDialog = false }
        )
    }
}

@Composable
fun MonthPickerDialog(
    currentYear: Int,
    currentMonth: Int,
    onMonthSelected: (year: Int, month: Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    var selectedYear by remember { mutableIntStateOf(currentYear) }
    var selectedMonth by remember { mutableIntStateOf(currentMonth) }

    val monthNames = listOf(
        "Január", "Február", "Marec", "Apríl", "Máj", "Jún",
        "Júl", "August", "September", "Október", "November", "December"
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedYear-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Predchádzajúci rok")
                }
                Text(
                    text = "$selectedYear",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = { selectedYear++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Nasledujúci rok")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 3x4 grid for 12 months
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0 until 3) {
                            val monthIdx = row * 3 + col
                            val isSelected = (selectedMonth == monthIdx)
                            Button(
                                onClick = { selectedMonth = monthIdx },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = if (isSelected) {
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                } else {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Text(
                                    text = monthNames[monthIdx].take(3),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onMonthSelected(selectedYear, selectedMonth)
                }
            ) {
                Text("Zvoliť mesiac")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Zrušiť")
            }
        }
    )
}

@Composable
fun DonutChart(
    stats: List<CategoryStat>,
    totalSpent: Double,
    modifier: Modifier = Modifier
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(stats) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 32.dp.toPx()
            var currentAngle = -90f

            stats.forEachIndexed { index, stat ->
                val sweepAngle = (stat.percentage / 100f * 360f) * animatedProgress.value
                val color = getCategoryColor(index)

                drawArc(
                    color = color,
                    startAngle = currentAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                currentAngle += sweepAngle
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Spolu",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Text(
                text = String.format(Locale.US, "%.2f €", totalSpent),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun CategoryStatItem(stat: CategoryStat, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stat.category,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = String.format(Locale.US, "%.2f € (%.1f%%)", stat.total, stat.percentage),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { stat.percentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = Color.LightGray.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Počet nákupov: ${stat.count}",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}
