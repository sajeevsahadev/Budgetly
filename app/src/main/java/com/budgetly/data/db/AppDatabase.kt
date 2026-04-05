package com.budgetly.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.budgetly.data.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Expense::class,
        BillReminder::class,
        Subscription::class,
        Budget::class,
        VendorCategoryOverride::class,
        SmsParserRule::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun billReminderDao(): BillReminderDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun vendorCategoryOverrideDao(): VendorCategoryOverrideDao

    companion object {
        private const val DATABASE_NAME = "expense_tracker_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Seed default SMS parser rules for popular Indian banks
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    seedSmsParserRules(db)
                }
            }
        }

        private fun seedSmsParserRules(db: SupportSQLiteDatabase) {
            val rules = listOf(
                // HDFC Bank
                Triple("HDFC", "HDFCBK|HDFC-Bank|HD-HDFCBK",
                    "debited|debit|spent|paid|purchase"),
                // ICICI Bank
                Triple("ICICI", "ICICIB|ICICI-Bank|iMobile",
                    "debited|debit|spent|paid"),
                // SBI
                Triple("SBI", "SBI|SBIINB|SBIPSG",
                    "debited|debit|withdrawn"),
                // Axis Bank
                Triple("Axis Bank", "AXISBK|AXIS-Bank|UTIBMO",
                    "debited|debit|spent"),
                // Kotak
                Triple("Kotak", "KOTAK|KOTAKB",
                    "debited|debit|spent"),
                // Amazon Pay
                Triple("Amazon Pay", "AMAZON|AMZNPAY",
                    "paid|debit|debited"),
                // PhonePe
                Triple("PhonePe", "PHONEPE|PPAY",
                    "paid|debit|debited|sent"),
                // GPay
                Triple("GPay", "GPAY|GOOGLEPAY",
                    "paid|debit|debited|sent")
            )

            rules.forEach { (bank, senderPattern, debitPattern) ->
                db.execSQL("""
                    INSERT OR IGNORE INTO sms_parser_rules 
                    (bankName, senderPattern, debitPattern, creditPattern, amountPattern, merchantPattern, isActive)
                    VALUES (
                        '$bank', 
                        '$senderPattern',
                        '$debitPattern',
                        'credited|credit|received',
                        '(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)',
                        '(?:at|to|@|for)\\s+([A-Za-z0-9\\s&]+?)(?:\\s+on|\\s+via|\\.|\\s+Ref|$)',
                        1
                    )
                """.trimIndent())
            }
        }
    }
}
