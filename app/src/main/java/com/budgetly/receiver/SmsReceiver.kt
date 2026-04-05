package com.budgetly.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.budgetly.data.db.AppDatabase
import com.budgetly.data.models.Expense
import com.budgetly.service.SmsParserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        // Group multi-part SMS
        val groupedBySender = mutableMapOf<String, StringBuilder>()
        messages.forEach { sms ->
            val sender = sms.originatingAddress ?: return@forEach
            groupedBySender.getOrPut(sender) { StringBuilder() }.append(sms.messageBody)
        }

        groupedBySender.forEach { (sender, bodyBuilder) ->
            val body = bodyBuilder.toString()
            processIncomingSms(context, sender, body)
        }
    }

    private fun processIncomingSms(context: Context, sender: String, body: String) {
        val parser = SmsParserService()
        val parsed = parser.parseSms(sender, body) ?: return

        Log.d(TAG, "Parsed SMS from $sender: ${parsed.amount} at ${parsed.merchantName}")

        val db = AppDatabase.getInstance(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Deduplicate by reference number
                if (parsed.referenceNumber.isNotBlank()) {
                    val existing = db.expenseDao().getByReferenceNumber(parsed.referenceNumber)
                    if (existing != null) {
                        Log.d(TAG, "Duplicate SMS transaction, skipping: ${parsed.referenceNumber}")
                        return@launch
                    }
                }

                // Check vendor override for category
                val vendorOverride = if (parsed.merchantName.isNotBlank()) {
                    db.vendorCategoryOverrideDao().getOverride(
                        parsed.merchantName.lowercase().trim()
                    )
                } else null

                val finalCategory = vendorOverride?.let {
                    com.budgetly.data.models.ExpenseCategory.fromString(it.category)
                } ?: parsed.category

                val expense = Expense(
                    amount = parsed.amount,
                    description = if (parsed.merchantName.isNotBlank())
                        "Payment at ${parsed.merchantName}"
                    else "Bank Transaction",
                    merchantName = parsed.merchantName,
                    category = finalCategory,
                    paymentMode = parsed.paymentMode,
                    transactionType = parsed.transactionType,
                    bankName = parsed.bankName,
                    accountLast4 = parsed.accountLast4,
                    referenceNumber = parsed.referenceNumber,
                    date = Date(),
                    rawSmsBody = body,
                    isFromSms = true
                )

                val id = db.expenseDao().insert(expense)
                Log.d(TAG, "Saved expense id=$id amount=${expense.amount}")

                // Check budget alert
                checkBudgetAlert(context, db, expense)

            } catch (e: Exception) {
                Log.e(TAG, "Error saving SMS expense", e)
            }
        }
    }

    private suspend fun checkBudgetAlert(
        context: Context,
        db: AppDatabase,
        expense: Expense
    ) {
        try {
            val cal = java.util.Calendar.getInstance()
            val month = cal.get(java.util.Calendar.MONTH) + 1
            val year = cal.get(java.util.Calendar.YEAR)

            val budget = db.budgetDao().getBudgetForCategory(
                expense.category.name, month, year
            ) ?: return

            if (budget.alertSent) return

            val spentKey = String.format("%02d", month)
            val yearKey = year.toString()
            val spent = db.expenseDao().getSpentByCategoryAndMonth(
                expense.category.name, spentKey, yearKey
            ) ?: 0.0

            val percentage = spent / budget.limitAmount
            if (percentage >= budget.alertThreshold) {
                // Send notification
                NotificationHelper(context).sendBudgetAlert(
                    expense.category,
                    spent,
                    budget.limitAmount,
                    percentage
                )
                db.budgetDao().markAlertSent(budget.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking budget alert", e)
        }
    }
}
