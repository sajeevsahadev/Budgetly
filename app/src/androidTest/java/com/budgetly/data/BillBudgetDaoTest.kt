package com.budgetly.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budgetly.data.db.AppDatabase
import com.budgetly.data.db.BillReminderDao
import com.budgetly.data.db.BudgetDao
import com.budgetly.data.db.SubscriptionDao
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
class BillDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: BillReminderDao

    @Before
    fun createDb() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.billReminderDao()
    }

    @After
    fun closeDb() = db.close()

    private fun futureBill(daysAhead: Int = 7, amount: Double = 500.0) = BillReminder(
        title = "Test Bill",
        amount = amount,
        dueDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, daysAhead) }.time,
        category = ExpenseCategory.BILLS_UTILITIES,
        recurrence = RecurrenceType.MONTHLY
    )

    @Test
    fun insertAndRetrieveBill() = runBlocking {
        val id = dao.insert(futureBill())
        assertTrue(id > 0)
        val retrieved = dao.getById(id)
        assertNotNull(retrieved)
        assertEquals("Test Bill", retrieved!!.title)
        assertEquals(false, retrieved.isPaid)
    }

    @Test
    fun markBillAsPaid() = runBlocking {
        val id = dao.insert(futureBill())
        dao.markAsPaid(id)
        val bill = dao.getById(id)
        assertTrue(bill!!.isPaid)
        assertNotNull(bill.paidDate)
    }

    @Test
    fun getActiveBillsExcludesInactive() = runBlocking {
        dao.insert(futureBill())
        dao.insert(futureBill().copy(isActive = false))

        val active = dao.getActiveBills().first()
        assertEquals(1, active.size)
    }

    @Test
    fun getUpcomingBillsWithinWindow() = runBlocking {
        val now = Date()
        val inTwoWeeks = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 14) }.time
        val inSixWeeks = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 42) }.time

        dao.insert(futureBill(7))   // within 30 days — should appear
        dao.insert(futureBill(35))  // outside 30 days — should NOT appear

        val upcoming = dao.getUpcomingBills(now, inTwoWeeks).first()
        assertEquals(1, upcoming.size)
    }

    @Test
    fun getTotalUnpaidAmount() = runBlocking {
        dao.insert(futureBill(amount = 300.0))
        dao.insert(futureBill(amount = 700.0))
        val paid = dao.insert(futureBill(amount = 200.0))
        dao.markAsPaid(paid)

        val total = dao.getTotalUnpaidAmount()
        assertEquals(1000.0, total ?: 0.0, 0.01)
    }

    @Test
    fun deleteBillRemovesIt() = runBlocking {
        val id = dao.insert(futureBill())
        val bill = dao.getById(id)!!
        dao.delete(bill)
        assertNull(dao.getById(id))
    }
}

@RunWith(AndroidJUnit4::class)
class BudgetDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: BudgetDao

    @Before
    fun createDb() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.budgetDao()
    }

    @After
    fun closeDb() = db.close()

    private val thisMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
    private val thisYear  = Calendar.getInstance().get(Calendar.YEAR)

    private fun budget(category: ExpenseCategory, limit: Double) = Budget(
        category = category,
        limitAmount = limit,
        month = thisMonth,
        year = thisYear
    )

    @Test
    fun insertAndGetBudgetByCategory() = runBlocking {
        dao.insert(budget(ExpenseCategory.FOOD_DINING, 5000.0))
        val found = dao.getBudgetForCategory(
            ExpenseCategory.FOOD_DINING.name, thisMonth, thisYear
        )
        assertNotNull(found)
        assertEquals(5000.0, found!!.limitAmount, 0.01)
    }

    @Test
    fun upsertBudgetReplacesExisting() = runBlocking {
        dao.insert(budget(ExpenseCategory.SHOPPING, 3000.0))
        dao.insert(budget(ExpenseCategory.SHOPPING, 5000.0)) // replace

        val budgets = dao.getBudgetsByMonth(thisMonth, thisYear).first()
        val shopping = budgets.filter { it.category == ExpenseCategory.SHOPPING }
        assertEquals(1, shopping.size)
        assertEquals(5000.0, shopping[0].limitAmount, 0.01)
    }

    @Test
    fun getBudgetsByMonthReturnsAll() = runBlocking {
        dao.insert(budget(ExpenseCategory.FOOD_DINING, 5000.0))
        dao.insert(budget(ExpenseCategory.TRANSPORT, 2000.0))
        dao.insert(budget(ExpenseCategory.HEALTH, 3000.0))

        val all = dao.getBudgetsByMonth(thisMonth, thisYear).first()
        assertEquals(3, all.size)
    }

    @Test
    fun markAlertSentUpdatesFlag() = runBlocking {
        val id = dao.insert(budget(ExpenseCategory.GROCERIES, 8000.0))
        dao.markAlertSent(id)
        val b = dao.getById(id)
        assertTrue(b!!.alertSent)
    }

    @Test
    fun deleteBudget() = runBlocking {
        val id = dao.insert(budget(ExpenseCategory.ENTERTAINMENT, 2000.0))
        val b = dao.getById(id)!!
        dao.delete(b)
        assertNull(dao.getById(id))
    }
}

@RunWith(AndroidJUnit4::class)
class SubscriptionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SubscriptionDao

    @Before
    fun createDb() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.subscriptionDao()
    }

    @After
    fun closeDb() = db.close()

    private fun sub(name: String, amount: Double, cycle: RecurrenceType = RecurrenceType.MONTHLY) =
        Subscription(
            name = name,
            amount = amount,
            billingCycle = cycle,
            nextBillingDate = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }.time,
            category = ExpenseCategory.ENTERTAINMENT,
            paymentMode = PaymentMode.CREDIT_CARD
        )

    @Test
    fun insertAndGetSubscription() = runBlocking {
        val id = dao.insert(sub("Netflix", 649.0))
        val retrieved = dao.getById(id)
        assertNotNull(retrieved)
        assertEquals("Netflix", retrieved!!.name)
    }

    @Test
    fun getMonthlySubscriptionCostSumsCorrectly() = runBlocking {
        dao.insert(sub("Netflix", 649.0, RecurrenceType.MONTHLY))
        dao.insert(sub("Spotify", 119.0, RecurrenceType.MONTHLY))
        dao.insert(sub("Annual Plan", 1200.0, RecurrenceType.YEARLY)) // = 100/mo

        val monthly = dao.getMonthlySubscriptionCost()
        // 649 + 119 + (1200/12) = 649 + 119 + 100 = 868
        assertEquals(868.0, monthly ?: 0.0, 1.0)
    }

    @Test
    fun inactiveSubsExcludedFromActive() = runBlocking {
        dao.insert(sub("Netflix", 649.0))
        dao.insert(sub("Cancelled", 99.0).copy(isActive = false))

        val active = dao.getActiveSubscriptions().first()
        assertEquals(1, active.size)
        assertEquals("Netflix", active[0].name)
    }

    @Test
    fun updateSubscriptionToggleActive() = runBlocking {
        val id = dao.insert(sub("Spotify", 119.0))
        val s = dao.getById(id)!!
        dao.update(s.copy(isActive = false))

        val updated = dao.getById(id)
        assertFalse(updated!!.isActive)
    }
}
