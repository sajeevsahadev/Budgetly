package com.budgetly.data.repository

import com.budgetly.data.db.*
import com.budgetly.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val billDao: BillReminderDao,
    private val subscriptionDao: SubscriptionDao,
    private val budgetDao: BudgetDao,
    private val vendorOverrideDao: VendorCategoryOverrideDao
) {

    // ─── Expenses ─────────────────────────────────────────────────────────

    fun getAllExpenses(): Flow<List<Expense>> = expenseDao.getAllExpenses()

    fun getRecentExpenses(limit: Int = 10): Flow<List<Expense>> =
        expenseDao.getRecentExpenses(limit)

    fun getExpensesByDateRange(start: Date, end: Date): Flow<List<Expense>> =
        expenseDao.getExpensesByDateRange(start, end)

    fun getExpensesByMonth(month: Int, year: Int): Flow<List<Expense>> =
        expenseDao.getExpensesByMonth(
            String.format("%02d", month),
            year.toString()
        )

    fun searchExpenses(query: String): Flow<List<Expense>> =
        expenseDao.searchExpenses(query)

    suspend fun addExpense(expense: Expense): Long = expenseDao.insert(expense)

    suspend fun updateExpense(expense: Expense) = expenseDao.update(expense)

    suspend fun deleteExpense(expense: Expense) = expenseDao.delete(expense)

    suspend fun updateCategoryByMerchant(merchantName: String, newCategory: ExpenseCategory) {
        expenseDao.updateCategoryByMerchant(merchantName, newCategory.name)
        // Save vendor override for future SMS
        vendorOverrideDao.insert(
            VendorCategoryOverride(
                merchantName = merchantName.lowercase().trim(),
                category = newCategory.name
            )
        )
    }

    fun getCategorySummary(month: Int, year: Int): Flow<List<CategorySummary>> =
        expenseDao.getCategorySummaryByMonth(
            String.format("%02d", month),
            year.toString()
        ).map { rawList ->
            val total = rawList.sumOf { it.totalAmount }
            rawList.map { raw ->
                CategorySummary(
                    category = ExpenseCategory.fromString(raw.category),
                    totalAmount = raw.totalAmount,
                    transactionCount = raw.transactionCount,
                    percentage = if (total > 0) (raw.totalAmount / total) * 100 else 0.0
                )
            }
        }

    suspend fun getMonthlyTrends(monthsBack: Int = 6): List<MonthlyTrend> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -monthsBack)
        return expenseDao.getMonthlyTrends(cal.time).map { raw ->
            MonthlyTrend(
                month = raw.month.toInt(),
                year = raw.year.toInt(),
                totalExpense = raw.totalExpense,
                totalIncome = raw.totalIncome
            )
        }
    }

    // ─── Bills ────────────────────────────────────────────────────────────

    fun getActiveBills(): Flow<List<BillReminder>> = billDao.getActiveBills()

    fun getAllBills(): Flow<List<BillReminder>> = billDao.getAllBills()

    fun getUpcomingBills(daysAhead: Int = 30): Flow<List<BillReminder>> {
        val now = Date()
        val future = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, daysAhead)
        }.time
        return billDao.getUpcomingBills(now, future)
    }

    suspend fun addBill(bill: BillReminder): Long = billDao.insert(bill)

    suspend fun updateBill(bill: BillReminder) = billDao.update(bill)

    suspend fun deleteBill(bill: BillReminder) = billDao.delete(bill)

    suspend fun markBillAsPaid(billId: Long) = billDao.markAsPaid(billId)

    suspend fun getTotalUnpaidAmount(): Double = billDao.getTotalUnpaidAmount() ?: 0.0

    // ─── Subscriptions ────────────────────────────────────────────────────

    fun getActiveSubscriptions(): Flow<List<Subscription>> =
        subscriptionDao.getActiveSubscriptions()

    fun getAllSubscriptions(): Flow<List<Subscription>> =
        subscriptionDao.getAllSubscriptions()

    fun getUpcomingSubscriptions(daysAhead: Int = 30): Flow<List<Subscription>> {
        val now = Date()
        val future = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, daysAhead)
        }.time
        return subscriptionDao.getUpcomingSubscriptions(now, future)
    }

    suspend fun addSubscription(sub: Subscription): Long = subscriptionDao.insert(sub)

    suspend fun updateSubscription(sub: Subscription) = subscriptionDao.update(sub)

    suspend fun deleteSubscription(sub: Subscription) = subscriptionDao.delete(sub)

    suspend fun getMonthlySubscriptionCost(): Double =
        subscriptionDao.getMonthlySubscriptionCost() ?: 0.0

    // ─── Budget ───────────────────────────────────────────────────────────

    fun getBudgetsByMonth(month: Int, year: Int): Flow<List<Budget>> =
        budgetDao.getBudgetsByMonth(month, year)

    fun getBudgetsWithSpent(month: Int, year: Int): Flow<List<BudgetWithSpent>> =
        budgetDao.getBudgetsByMonth(month, year).map { budgets ->
            budgets.map { budget ->
                val spent = expenseDao.getSpentByCategoryAndMonth(
                    budget.category.name,
                    String.format("%02d", month),
                    year.toString()
                ) ?: 0.0
                BudgetWithSpent(budget = budget, spentAmount = spent)
            }
        }

    suspend fun addOrUpdateBudget(budget: Budget): Long = budgetDao.insert(budget)

    suspend fun deleteBudget(budget: Budget) = budgetDao.delete(budget)

    // ─── Dashboard ────────────────────────────────────────────────────────

    suspend fun getDashboardSummary(): DashboardSummary {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)

        // This month range
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        // This week range
        val weekStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }.time

        // Today
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.time

        val now = Date()

        return DashboardSummary(
            totalThisMonth = expenseDao.getTotalSpentByMonth(
                String.format("%02d", month), year.toString()
            ) ?: 0.0,
            totalThisWeek = expenseDao.getTopExpenses(weekStart, now, 100).sumOf { it.amount },
            totalToday = expenseDao.getTopExpenses(todayStart, now, 100).sumOf { it.amount },
            topCategory = null, // Computed from flow in ViewModel
            recentExpenses = expenseDao.getTopExpenses(monthStart, now, 5),
            upcomingBills = emptyList(), // From flow
            budgetAlerts = emptyList()  // From flow
        )
    }
}
