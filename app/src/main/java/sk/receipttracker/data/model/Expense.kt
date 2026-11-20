package sk.receipttracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val amount: Double,
    val category: String,
    val date: Long = System.currentTimeMillis(),
    val photoPath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val note: String = ""
) {
    fun formattedDate(): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(date))
    }

    fun formattedAmount(): String {
        return String.format(Locale.US, "%.2f €", amount)
    }

    companion object {
        val DEFAULT_CATEGORIES = listOf(
            "Jedlo",
            "Doprava",
            "Bývanie",
            "Zábava",
            "Zdravie",
            "Nákupy",
            "Iné"
        )
    }
}
