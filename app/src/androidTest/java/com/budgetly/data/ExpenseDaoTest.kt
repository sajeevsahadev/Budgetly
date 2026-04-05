package com.budgetly.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budgetly.data.db.AppDatabase
import com.budgetly.data.db.ExpenseDao
import com.budgetly.data.models.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ExpenseDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ExpenseDao

    @Before
    fun createDb() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.expenseDao()
    }

    @After
    fun closeDb() = db.close()

    private fun makeExpense(
        amount: Double = 100.0,
        category: ExpenseCategory = ExpenseCategory.FOOD_DINING,
        type: TransactionType = TransactionType.DEBIT,
        merchant: String = "Test Merchant",
        refNo: String = "",
        date: Date = Date()
    ) = Expense(
        amount = amount,
        description = "Test expense",
        merchantName = merchant,
        category = category,
        paymentMode = PaymentMode.UPI,
        transactionType = type,
        referenceNumber = refNo,
        date = date
    )

    // ─── Insert & Retrieve ────────────────────────────────────────────────

    @Test
    fun insertAndRetrieveExpense() = runBlocking {
        val expense = makeExpense(amount = 250.0)
        val id = dao.insert(expense)
        assertTrue("Insert should return positive ID", id > 0)

        val retrieved = dao.getById(id)
        assertNotNull(retrieved)
        assertEquals(250.0, retrieved!!.amount, 0.01)
        assertEquals(ExpenseCategory.FOOD_DINING, retrieved.category)
    }

    @Test
    fun insertMultipleAndGetAll() = runBlocking {
        dao.insert(makeExpense(amount = 100.0))
        dao.insert(makeExpense(amount = 200.0))
        dao.insert(makeExpense(amount = 300.0))

        val all = dao.getAllExpenses().first()
        assertEquals(3, all.size)
    }

    // ─── Deduplication by Reference Number ───────────────────────────────

    @Test
    fun getByReferenceNumberFindsExisting() = runBlocking {
        val expense = makeExpense(refNo = "REF123ABC")
        dao.insert(expense)

        val found = dao.getByReferenceNumber("REF123ABC")
        assertNotNull(found)
        assertEquals("REF123ABC", found!!.referenceNumber)
    }

    @Test
    fun getByReferenceNumberReturnsNullWhenMissing() = runBlocking {
        val result = dao.getByReferenceNumber("DOESNOTEXIST")
        assertNull(result)
    }

    // ─── Update ───────────────────────────────────────────────────────────

    @Test
    fun updateExpenseCategory() = runBlocking {
        val id = dao.insert(makeExpense(category = ExpenseCategory.OTHER))
        val expense = dao.getById(id)!!

        dao.update(expense.copy(category = ExpenseCategory.GROCERIES))

        val updated = dao.getById(id)
        assertEquals(ExpenseCategory.GROCERIES, updated!!.category)
    }

    // ─── Bulk Merchant Update ─────────────────────────────────────────────

    @Test
    fun updateCategoryByMerchantAffectsAllMatching() = runBlocking {
        dao.insert(makeExpense(merchant = "Zomato", category = ExpenseCategory.OTHER))
        dao.insert(makeExpense(merchant = "Zomato", category = ExpenseCategory.OTHER))
        dao.insert(makeExpense(merchant = "Swiggy", category = ExpenseCategory.OTHER))

        dao.updateCategoryByMerchant("Zomato", ExpenseCategory.FOOD_DINING.name)

        val all = dao.getAllExpenses().first()
        val zomatoExpenses = all.filter { it.merchantName == "Zomato" }
        val swiggyExpenses = all.filter { it.merchantName == "Swiggy" }

        assertTrue("All Zomato expenses should be FOOD_DINING",
            zomatoExpenses.all { it.category == ExpenseCategory.FOOD_DINING })
        assertTrue("Swiggy expense should remain OTHER",
            swiggyExpenses.all { it.category == ExpenseCategory.OTHER })
    }

    // ─── Delete ───────────────────────────────────────────────────────────

    @Test
    fun deleteExpenseRemovesIt() = runBlocking {
        val id = dao.insert(makeExpense())
        val expense = dao.getById(id)!!
        dao.delete(expense)

        val retrieved = dao.getById(id)
        assertNull(retrieved)
    }

    @Test
    fun deleteAllClearsTable() = runBlocking {
        dao.insert(makeExpense())
        dao.insert(makeExpense())
        dao.deleteAll()

        val all = dao.getAllExpenses().first()
        assertEquals(0, all.size)
    }

    // ─── Monthly Queries ──────────────────────────────────────────────────

    @Test
    fun getTotalSpentByMonthSumsDebitsOnly() = runBlocking {
        val cal = Calendar.getInstance()
        val month = String.format("%02d", cal.get(Calendar.MONTH) + 1)
        val year = cal.get(Calendar.YEAR).toString()

        dao.insert(makeExpense(amount = 500.0, type = TransactionType.DEBIT))
        dao.insert(makeExpense(amount = 300.0, type = TransactionType.DEBIT))
        dao.insert(makeExpense(amount = 1000.0, type = TransactionType.CREDIT)) // should NOT be summed

        val total = dao.getTotalSpentByMonth(month, year)
        assertEquals(800.0, total ?: 0.0, 0.01)
    }

    @Test
    fun getSpentByCategoryAndMonth() = runBlocking {
        val cal = Calendar.getInstance()
        val month = String.format("%02d", cal.get(Calendar.MONTH) + 1)
        val year = cal.get(Calendar.YEAR).toString()

        dao.insert(makeExpense(amount = 200.0, category = ExpenseCategory.FOOD_DINING))
        dao.insert(makeExpense(amount = 150.0, category = ExpenseCategory.FOOD_DINING))
        dao.insert(makeExpense(amount = 500.0, category = ExpenseCategory.SHOPPING))

        val foodTotal = dao.getSpentByCategoryAndMonth(
            ExpenseCategory.FOOD_DINING.name, month, year
        )
        assertEquals(350.0, foodTotal ?: 0.0, 0.01)
    }

    // ─── Search ───────────────────────────────────────────────────────────

    @Test
    fun searchExpensesByMerchantName() = runBlocking {
        dao.insert(makeExpense(merchant = "Netflix"))
        dao.insert(makeExpense(merchant = "Zomato"))
        dao.insert(makeExpense(merchant = "Spotify"))

        val results = dao.searchExpenses("net").first()
        assertEquals(1, results.size)
        assertEquals("Netflix", results[0].merchantName)
    }

    @Test
    fun searchExpensesIsCaseInsensitive() = runBlocking {
        dao.insert(makeExpense(merchant = "Amazon"))
        val results = dao.searchExpenses("AMAZON").first()
        assertEquals(1, results.size)
    }

    // ─── Category Summary ─────────────────────────────────────────────────

    @Test
    fun getCategorySummaryGroupsCorrectly() = runBlocking {
        val cal = Calendar.getInstance()
        val month = String.format("%02d", cal.get(Calendar.MONTH) + 1)
        val year = cal.get(Calendar.YEAR).toString()

        dao.insert(makeExpense(100.0, ExpenseCategory.FOOD_DINING))
        dao.insert(makeExpense(200.0, ExpenseCategory.FOOD_DINING))
        dao.insert(makeExpense(500.0, ExpenseCategory.SHOPPING))

        val summaries = dao.getCategorySummaryByMonth(month, year).first()

        val foodSummary = summaries.find { it.category == ExpenseCategory.FOOD_DINING.name }
        assertNotNull(foodSummary)
        assertEquals(300.0, foodSummary!!.totalAmount, 0.01)
        assertEquals(2, foodSummary.transactionCount)

        val shoppingSummary = summaries.find { it.category == ExpenseCategory.SHOPPING.name }
        assertNotNull(shoppingSummary)
        assertEquals(500.0, shoppingSummary!!.totalAmount, 0.01)
    }

    // ─── Recent Expenses ──────────────────────────────────────────────────

    @Test
    fun getRecentExpensesReturnsCorrectLimit() = runBlocking {
        repeat(15) { dao.insert(makeExpense()) }
        val recent = dao.getRecentExpenses(5).first()
        assertEquals(5, recent.size)
    }
}
