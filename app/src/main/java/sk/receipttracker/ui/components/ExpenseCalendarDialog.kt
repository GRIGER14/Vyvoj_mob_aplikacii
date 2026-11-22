package sk.receipttracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import sk.receipttracker.data.model.Expense
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CalendarDay(
    val dayNumber: Int,
    val dateMillis: Long,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val expenseCount: Int,
    val totalAmount: Double
)

@Composable
fun ExpenseCalendarDialog(
    initialDateMillis: Long? = null,
    expenses: List<Expense>,
    onDateSelected: (Long) -> Unit,
    onClearDate: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    var displayedCalendar by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                timeInMillis = initialDateMillis ?: System.currentTimeMillis()
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        )
    }

    var selectedDayMillis by remember {
        mutableStateOf(initialDateMillis)
    }

    // Pre-calculate expense stats per day: "yyyy-MM-dd" -> (count, sum)
    val expensesByDay = remember(expenses) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        expenses.groupBy { sdf.format(Date(it.date)) }
    }

    val monthName = remember(displayedCalendar.timeInMillis) {
        val sdf = SimpleDateFormat("LLLL yyyy", Locale("sk", "SK"))
        val name = sdf.format(displayedCalendar.time)
        name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    val daysList = remember(displayedCalendar.timeInMillis, expensesByDay) {
        generateCalendarDays(displayedCalendar, expensesByDay)
    }

    val dayNames = listOf("Po", "Ut", "St", "Št", "Pi", "So", "Ne")

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with icon and Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Kalendár nákupov",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Month navigation row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val nextCal = displayedCalendar.clone() as Calendar
                            nextCal.add(Calendar.MONTH, -1)
                            displayedCalendar = nextCal
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Predchádzajúci mesiac"
                        )
                    }

                    Text(
                        text = monthName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IconButton(
                        onClick = {
                            val nextCal = displayedCalendar.clone() as Calendar
                            nextCal.add(Calendar.MONTH, 1)
                            displayedCalendar = nextCal
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Nasledujúci mesiac"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Day headers (Po, Ut, St, Št, Pi, So, Ne)
                Row(modifier = Modifier.fillMaxWidth()) {
                    dayNames.forEachIndexed { index, name ->
                        val isWeekend = index >= 5
                        Text(
                            text = name,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (isWeekend) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 7-column Calendar grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(daysList) { day ->
                        val isSelected = remember(selectedDayMillis, day.dateMillis) {
                            if (selectedDayMillis == null) false
                            else isSameDay(selectedDayMillis!!, day.dateMillis)
                        }

                        CalendarDayItem(
                            day = day,
                            isSelected = isSelected,
                            onClick = {
                                selectedDayMillis = day.dateMillis
                            }
                        )
                    }
                }

                // Legend row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dni s nákupmi / bločkami",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onClearDate != null) {
                        TextButton(
                            onClick = {
                                onClearDate()
                                onDismissRequest()
                            }
                        ) {
                            Text("Zrušiť filter")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    TextButton(onClick = onDismissRequest) {
                        Text("Zavrieť")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            selectedDayMillis?.let { onDateSelected(it) }
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Zvoliť")
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDayItem(
    day: CalendarDay,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val hasExpenses = day.expenseCount > 0

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        day.isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        hasExpenses -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
        day.isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        hasExpenses -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .then(
                if (day.isToday && !isSelected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                } else Modifier
            )
            .clickable { onClick() }
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.dayNumber.toString(),
                fontSize = 14.sp,
                fontWeight = if (isSelected || day.isToday || hasExpenses) FontWeight.Bold else FontWeight.Normal,
                color = contentColor
            )

            // Purchase indicator badge: icon / dot when purchases were made on this day
            if (hasExpenses) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (day.expenseCount > 1) {
                        // Badge with purchase icon and count
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp)
                        )
                    } else {
                        // Compact glowing dot
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.primary
                                )
                        )
                    }
                }
            }
        }
    }
}

private fun generateCalendarDays(
    monthCalendar: Calendar,
    expensesByDay: Map<String, List<Expense>>
): List<CalendarDay> {
    val result = mutableListOf<CalendarDay>()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val todayCal = Calendar.getInstance()

    val cal = monthCalendar.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)

    val currentMonth = cal.get(Calendar.MONTH)

    // In Java Calendar, Sunday = 1, Monday = 2 ... Saturday = 7
    // In Slovakia / Europe, Monday is day 0 in week grid
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val leadingDays = (firstDayOfWeek - Calendar.MONDAY + 7) % 7

    cal.add(Calendar.DAY_OF_MONTH, -leadingDays)

    for (i in 0 until 42) { // 6 weeks * 7 days
        val isCurrentMonth = cal.get(Calendar.MONTH) == currentMonth
        val isToday = isSameDay(cal.timeInMillis, todayCal.timeInMillis)
        val dateStr = sdf.format(cal.time)
        val dayExpenses = expensesByDay[dateStr] ?: emptyList()

        result.add(
            CalendarDay(
                dayNumber = cal.get(Calendar.DAY_OF_MONTH),
                dateMillis = cal.timeInMillis,
                isCurrentMonth = isCurrentMonth,
                isToday = isToday,
                expenseCount = dayExpenses.size,
                totalAmount = dayExpenses.sumOf { it.amount }
            )
        )

        cal.add(Calendar.DAY_OF_MONTH, 1)
        if (i >= 27 && !isCurrentMonth && cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
            break
        }
    }

    return result
}

private fun isSameDay(millis1: Long, millis2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = millis1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = millis2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
