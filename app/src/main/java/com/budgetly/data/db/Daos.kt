package com.budgetly.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.budgetly.data.models.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

// ─── Expense DAO ──────────────────────────────────────────────────────────

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<Expense>)

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    @Query("""
        SELECT * FROM expenses 
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY date DESC
    """)
    fun getExpensesByDateRange(startDate: Date, endDate: Date): Flow<List<Expense>>

    @Query("""
        SELECT * FROM expenses 
        WHERE strftime('%m', date/1000, 'unixepoch') = :month
        AND strftime('%Y', date/1000, 'unixepoch') = :year
        ORDER BY date DESC
    """)
    fun getExpensesByMonth(month: String, year: String): Flow<List<Expense>>

    @Query("""
        SELECT * FROM expenses
        WHERE category = :category
        ORDER BY date DESC
    """)
    fun getExpensesByCategory(category: String): Flow<List<Expense>>

    @Query("""
        SELECT category, 
               SUM(amount) as totalAmount, 
               COUNT(*) as transactionCount
        FROM expenses
        WHERE transactionType = 'DEBIT'
        AND strftime('%m', date/1000, 'unixepoch') = :month
        AND strftime('%Y', date/1000, 'unixepoch') = :year
        GROUP BY category
        ORDER BY totalAmount DESC
    """)
    fun getCategorySummaryByMonth(month: String, year: String): Flow<List<CategorySummaryRaw>>

    @Query("""
        SELECT SUM(amount) FROM expenses
        WHERE transactionType = 'DEBIT'
        AND strftime('%m', date/1000, 'unixepoch') = :month
        AND strftime('%Y', date/1000, 'unixepoch') = :year
    """)
    suspend fun getTotalSpentByMonth(month: String, year: String): Double?

    @Query("""
        SELECT SUM(amount) FROM expenses
        WHERE transactionType = 'DEBIT'
        AND category = :category
        AND strftime('%m', date/1000, 'unixepoch') = :month
        AND strftime('%Y', date/1000, 'unixepoch') = :year
    """)
    suspend fun getSpentByCategoryAndMonth(category: String, month: String, year: String): Double?

    @Query("""
        SELECT * FROM expenses
        WHERE referenceNumber = :refNo
        LIMIT 1
    """)
    suspend fun getByReferenceNumber(refNo: String): Expense?

    @Query("""
        SELECT * FROM expenses
        WHERE merchantName LIKE '%' || :query || '%'
        OR description LIKE '%' || :query || '%'
        OR notes LIKE '%' || :query || '%'
        ORDER BY date DESC
        LIMIT 100
    """)
    fun searchExpenses(query: String): Flow<List<Expense>>

    @Query("""
        UPDATE expenses SET category = :newCategory, updatedAt = :now
        WHERE merchantName = :merchantName
    """)
    suspend fun updateCategoryByMerchant(merchantName: String, newCategory: String, now: Date = Date())

    @Query("SELECT * FROM expenses ORDER BY date DESC LIMIT :limit")
    fun getRecentExpenses(limit: Int = 10): Flow<List<Expense>>

    @Query("""
        SELECT * FROM expenses
        WHERE transactionType = 'DEBIT'
        AND date BETWEEN :startDate AND :endDate
        ORDER BY amount DESC
        LIMIT :limit
    """)
    suspend fun getTopExpenses(startDate: Date, endDate: Date, limit: Int = 5): List<Expense>

    @Query("""
        SELECT strftime('%m', date/1000, 'unixepoch') as month,
               strftime('%Y', date/1000, 'unixepoch') as year,
               SUM(CASE WHEN transactionType = 'DEBIT' THEN amount ELSE 0 END) as totalExpense,
               SUM(CASE WHEN transactionType = 'CREDIT' THEN amount ELSE 0 END) as totalIncome
        FROM expenses
        WHERE date >= :since
        GROUP BY year, month
        ORDER BY year DESC, month DESC
        LIMIT 12
    """)
    suspend fun getMonthlyTrends(since: Date): List<MonthlyTrendRaw>

    @Query("SELECT COUNT(*) FROM expenses WHERE isFromSms = 1")
    suspend fun getSmsExpenseCount(): Int

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}

// ─── Bill Reminder DAO ────────────────────────────────────────────────────

@Dao
interface BillReminderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bill: BillReminder): Long

    @Update
    suspend fun update(bill: BillReminder)

    @Delete
    suspend fun delete(bill: BillReminder)

    @Query("SELECT * FROM bill_reminders WHERE isActive = 1 ORDER BY dueDate ASC")
    fun getActiveBills(): Flow<List<BillReminder>>

    @Query("SELECT * FROM bill_reminders ORDER BY dueDate ASC")
    fun getAllBills(): Flow<List<BillReminder>>

    @Query("SELECT * FROM bill_reminders WHERE id = :id")
    suspend fun getById(id: Long): BillReminder?

    @Query("""
        SELECT * FROM bill_reminders
        WHERE isActive = 1 AND isPaid = 0
        AND dueDate BETWEEN :now AND :future
        ORDER BY dueDate ASC
    """)
    fun getUpcomingBills(now: Date, future: Date): Flow<List<BillReminder>>

    @Query("""
        UPDATE bill_reminders SET isPaid = 1, paidDate = :paidDate
        WHERE id = :id
    """)
    suspend fun markAsPaid(id: Long, paidDate: Date = Date())

    @Query("SELECT SUM(amount) FROM bill_reminders WHERE isActive = 1 AND isPaid = 0")
    suspend fun getTotalUnpaidAmount(): Double?
}

// ─── Subscription DAO ─────────────────────────────────────────────────────

@Dao
interface SubscriptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sub: Subscription): Long

    @Update
    suspend fun update(sub: Subscription)

    @Delete
    suspend fun delete(sub: Subscription)

    @Query("SELECT * FROM subscriptions WHERE isActive = 1 ORDER BY nextBillingDate ASC")
    fun getActiveSubscriptions(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions ORDER BY nextBillingDate ASC")
    fun getAllSubscriptions(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: Long): Subscription?

    @Query("""
        SELECT SUM(
            CASE billingCycle
                WHEN 'MONTHLY' THEN amount
                WHEN 'YEARLY' THEN amount / 12.0
                WHEN 'QUARTERLY' THEN amount / 3.0
                WHEN 'WEEKLY' THEN amount * 4.33
                ELSE 0
            END
        ) FROM subscriptions WHERE isActive = 1
    """)
    suspend fun getMonthlySubscriptionCost(): Double?

    @Query("""
        SELECT * FROM subscriptions
        WHERE isActive = 1
        AND nextBillingDate BETWEEN :now AND :future
        ORDER BY nextBillingDate ASC
    """)
    fun getUpcomingSubscriptions(now: Date, future: Date): Flow<List<Subscription>>
}

// ─── Budget DAO ───────────────────────────────────────────────────────────

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget): Long

    @Update
    suspend fun update(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)

    @Query("""
        SELECT * FROM budgets
        WHERE month = :month AND year = :year
        ORDER BY limitAmount DESC
    """)
    fun getBudgetsByMonth(month: Int, year: Int): Flow<List<Budget>>

    @Query("""
        SELECT * FROM budgets
        WHERE category = :category AND month = :month AND year = :year
        LIMIT 1
    """)
    suspend fun getBudgetForCategory(category: String, month: Int, year: Int): Budget?

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): Budget?

    @Query("""
        UPDATE budgets SET alertSent = 1, updatedAt = :now
        WHERE id = :id
    """)
    suspend fun markAlertSent(id: Long, now: Date = Date())
}

// ─── Vendor Override DAO ──────────────────────────────────────────────────

@Dao
interface VendorCategoryOverrideDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(override: VendorCategoryOverride)

    @Query("SELECT * FROM vendor_category_overrides WHERE merchantName = :merchant")
    suspend fun getOverride(merchant: String): VendorCategoryOverride?

    @Query("SELECT * FROM vendor_category_overrides")
    fun getAllOverrides(): Flow<List<VendorCategoryOverride>>

    @Query("DELETE FROM vendor_category_overrides WHERE merchantName = :merchant")
    suspend fun deleteOverride(merchant: String)
}

// ─── Raw Query Result Data Classes ───────────────────────────────────────

data class CategorySummaryRaw(
    val category: String,
    val totalAmount: Double,
    val transactionCount: Int
)

data class MonthlyTrendRaw(
    val month: String,
    val year: String,
    val totalExpense: Double,
    val totalIncome: Double
)
