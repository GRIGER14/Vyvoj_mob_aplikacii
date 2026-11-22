package sk.receipttracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import sk.receipttracker.data.model.Expense

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun getExpenseById(id: Long): Flow<Expense?>

    @Query("SELECT * FROM expenses WHERE latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY date DESC")
    fun getExpensesWithLocation(): Flow<List<Expense>>

    @Query("""
        SELECT * FROM expenses 
        WHERE (:query IS NULL OR :query = '' OR title LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%')
          AND (:category IS NULL OR category = :category)
          AND (:fromMillis IS NULL OR date >= :fromMillis)
          AND (:toMillis IS NULL OR date <= :toMillis)
        ORDER BY date DESC
    """)
    fun searchExpenses(
        query: String?,
        category: String?,
        fromMillis: Long?,
        toMillis: Long?
    ): Flow<List<Expense>>

    @Query("SELECT COUNT(*) FROM expenses WHERE title = :storeName")
    suspend fun getStoreExpenseCount(storeName: String): Int

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE title = :storeName")
    suspend fun getStoreTotalSpent(storeName: String): Double

    @Query("SELECT * FROM expenses WHERE title = :storeName ORDER BY date DESC")
    fun getExpensesByStore(storeName: String): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)
}
