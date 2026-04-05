package com.budgetly.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.Date

// ─── Enums ───────────────────────────────────────────────────────────────────

enum class ExpenseCategory(val displayName: String, val icon: String, val colorHex: String) {
    FOOD_DINING("Food & Dining", "🍔", "#FF6B6B"),
    GROCERIES("Groceries", "🛒", "#4ECDC4"),
    SHOPPING("Shopping", "🛍️", "#45B7D1"),
    ENTERTAINMENT("Entertainment", "🎬", "#96CEB4"),
    TRANSPORT("Transport", "🚗", "#FFEAA7"),
    HEALTH("Health", "💊", "#DDA0DD"),
    BILLS_UTILITIES("Bills & Utilities", "💡", "#98D8C8"),
    EDUCATION("Education", "📚", "#F7DC6F"),
    TRAVEL("Travel", "✈️", "#82E0AA"),
    FUEL("Fuel", "⛽", "#F0B27A"),
    INSURANCE("Insurance", "🛡️", "#AED6F1"),
    SUBSCRIPTIONS("Subscriptions", "📱", "#C39BD3"),
    INVESTMENTS("Investments", "📈", "#76D7C4"),
    EMI("EMI / Loan", "🏦", "#F1948A"),
    SALARY("Salary / Income", "💰", "#A9DFBF"),
    CASH("Cash", "💵", "#FAD7A0"),
    OTHER("Other", "📌", "#BDC3C7");

    companion object {
        fun fromString(name: String): ExpenseCategory {
            return values().find { it.name == name } ?: OTHER
        }
    }
}

enum class PaymentMode(val displayName: String) {
    UPI("UPI"),
    CREDIT_CARD("Credit Card"),
    DEBIT_CARD("Debit Card"),
    NET_BANKING("Net Banking"),
    CASH("Cash"),
    WALLET("Wallet"),
    EMI("EMI"),
    OTHER("Other")
}

enum class TransactionType {
    DEBIT, CREDIT
}

enum class RecurrenceType(val displayName: String) {
    NONE("None"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly")
}

// ─── Expense Entity ────────────────────────────────────────────────────────

@Entity(tableName = "expenses")
@TypeConverters(Converters::class)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val description: String,
    val merchantName: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val paymentMode: PaymentMode = PaymentMode.OTHER,
    val transactionType: TransactionType = TransactionType.DEBIT,
    val bankName: String = "",
    val accountLast4: String = "",
    val referenceNumber: String = "",
    val date: Date = Date(),
    val rawSmsBody: String = "",         // original SMS for audit
    val isFromSms: Boolean = false,
    val isManualEntry: Boolean = false,
    val isCashExpense: Boolean = false,
    val notes: String = "",
    val tags: String = "",               // comma-separated
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

// ─── Bill Reminder Entity ──────────────────────────────────────────────────

@Entity(tableName = "bill_reminders")
@TypeConverters(Converters::class)
data class BillReminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val dueDate: Date,
    val category: ExpenseCategory = ExpenseCategory.BILLS_UTILITIES,
    val recurrence: RecurrenceType = RecurrenceType.MONTHLY,
    val paymentMode: PaymentMode = PaymentMode.OTHER,
    val reminderDaysBefore: Int = 3,     // alert N days before due
    val isPaid: Boolean = false,
    val paidDate: Date? = null,
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Date = Date()
)

// ─── Subscription Entity ───────────────────────────────────────────────────

@Entity(tableName = "subscriptions")
@TypeConverters(Converters::class)
data class Subscription(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                   // Netflix, Spotify, etc.
    val amount: Double,
    val billingCycle: RecurrenceType = RecurrenceType.MONTHLY,
    val nextBillingDate: Date,
    val category: ExpenseCategory = ExpenseCategory.SUBSCRIPTIONS,
    val paymentMode: PaymentMode = PaymentMode.CREDIT_CARD,
    val reminderEnabled: Boolean = true,
    val reminderDaysBefore: Int = 3,
    val isActive: Boolean = true,
    val iconUrl: String = "",
    val notes: String = "",
    val createdAt: Date = Date()
)

// ─── Budget Entity ─────────────────────────────────────────────────────────

@Entity(tableName = "budgets")
@TypeConverters(Converters::class)
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: ExpenseCategory,
    val limitAmount: Double,
    val month: Int,                     // 1-12
    val year: Int,
    val alertThreshold: Double = 0.90,  // 90% default
    val alertSent: Boolean = false,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

// ─── Vendor Category Override ──────────────────────────────────────────────

@Entity(tableName = "vendor_category_overrides")
data class VendorCategoryOverride(
    @PrimaryKey
    val merchantName: String,           // normalized merchant name
    val category: String,               // ExpenseCategory.name
    val updatedAt: Long = System.currentTimeMillis()
)

// ─── SMS Parser Rule ───────────────────────────────────────────────────────

@Entity(tableName = "sms_parser_rules")
data class SmsParserRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bankName: String,
    val senderPattern: String,          // regex for sender
    val debitPattern: String,           // regex to detect debit
    val creditPattern: String,          // regex to detect credit
    val amountPattern: String,          // regex group for amount
    val merchantPattern: String,        // regex group for merchant
    val isActive: Boolean = true
)

// ─── Type Converters ───────────────────────────────────────────────────────

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun fromCategory(value: String?): ExpenseCategory =
        value?.let { ExpenseCategory.fromString(it) } ?: ExpenseCategory.OTHER

    @TypeConverter
    fun categoryToString(category: ExpenseCategory?): String = category?.name ?: ExpenseCategory.OTHER.name

    @TypeConverter
    fun fromPaymentMode(value: String?): PaymentMode =
        value?.let { runCatching { PaymentMode.valueOf(it) }.getOrDefault(PaymentMode.OTHER) } ?: PaymentMode.OTHER

    @TypeConverter
    fun paymentModeToString(mode: PaymentMode?): String = mode?.name ?: PaymentMode.OTHER.name

    @TypeConverter
    fun fromTransactionType(value: String?): TransactionType =
        value?.let { runCatching { TransactionType.valueOf(it) }.getOrDefault(TransactionType.DEBIT) } ?: TransactionType.DEBIT

    @TypeConverter
    fun transactionTypeToString(type: TransactionType?): String = type?.name ?: TransactionType.DEBIT.name

    @TypeConverter
    fun fromRecurrence(value: String?): RecurrenceType =
        value?.let { runCatching { RecurrenceType.valueOf(it) }.getOrDefault(RecurrenceType.NONE) } ?: RecurrenceType.NONE

    @TypeConverter
    fun recurrenceToString(type: RecurrenceType?): String = type?.name ?: RecurrenceType.NONE.name
}

// ─── UI Data Classes ───────────────────────────────────────────────────────

data class CategorySummary(
    val category: ExpenseCategory,
    val totalAmount: Double,
    val transactionCount: Int,
    val percentage: Double = 0.0
)

data class BudgetWithSpent(
    val budget: Budget,
    val spentAmount: Double,
    val percentage: Double = if (budget.limitAmount > 0) (spentAmount / budget.limitAmount) * 100 else 0.0,
    val remaining: Double = budget.limitAmount - spentAmount,
    val isAlertThreshold: Boolean = percentage >= (budget.alertThreshold * 100)
)

data class MonthlyTrend(
    val month: Int,
    val year: Int,
    val totalExpense: Double,
    val totalIncome: Double
)

data class DashboardSummary(
    val totalThisMonth: Double,
    val totalThisWeek: Double,
    val totalToday: Double,
    val topCategory: ExpenseCategory?,
    val recentExpenses: List<Expense>,
    val upcomingBills: List<BillReminder>,
    val budgetAlerts: List<BudgetWithSpent>
)
