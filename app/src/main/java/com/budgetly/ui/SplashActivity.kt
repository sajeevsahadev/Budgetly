package com.budgetly.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.budgetly.data.db.AppDatabase
import com.budgetly.data.models.*
import com.budgetly.service.SmsParserService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            delay(1000)
            if (hasSmsPermission()) {
                withContext(Dispatchers.IO) { importHistoricalSms() }
            }
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }

    private fun hasSmsPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private suspend fun importHistoricalSms() {
        val parser = SmsParserService()
        val db = AppDatabase.getInstance(this)
        val toInsert = mutableListOf<Expense>()
        try {
            val since = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
            val cursor: Cursor? = contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                "date > ?", arrayOf(since.toString()), "date DESC"
            )
            cursor?.use { c ->
                val addrIdx = c.getColumnIndexOrThrow("address")
                val bodyIdx = c.getColumnIndexOrThrow("body")
                val dateIdx = c.getColumnIndexOrThrow("date")
                var count = 0
                while (c.moveToNext() && count < 500) {
                    val sender = c.getString(addrIdx) ?: continue
                    val body   = c.getString(bodyIdx)   ?: continue
                    val dateMs = c.getLong(dateIdx)
                    val parsed = parser.parseSms(sender, body) ?: continue
                    if (parsed.referenceNumber.isNotBlank() &&
                        db.expenseDao().getByReferenceNumber(parsed.referenceNumber) != null) continue
                    val override = if (parsed.merchantName.isNotBlank())
                        db.vendorCategoryOverrideDao().getOverride(parsed.merchantName.lowercase().trim())
                    else null
                    val category = override?.let { ExpenseCategory.fromString(it.category) } ?: parsed.category
                    toInsert.add(Expense(
                        amount = parsed.amount,
                        description = if (parsed.merchantName.isNotBlank()) "Payment at ${parsed.merchantName}" else "Bank Transaction",
                        merchantName = parsed.merchantName,
                        category = category,
                        paymentMode = parsed.paymentMode,
                        transactionType = parsed.transactionType,
                        bankName = parsed.bankName,
                        accountLast4 = parsed.accountLast4,
                        referenceNumber = parsed.referenceNumber,
                        date = Date(dateMs),
                        rawSmsBody = body,
                        isFromSms = true
                    ))
                    count++
                }
            }
            if (toInsert.isNotEmpty()) {
                db.expenseDao().insertAll(toInsert)
                Log.i("Splash", "Imported ${toInsert.size} SMS expenses")
            }
        } catch (e: Exception) {
            Log.e("Splash", "SMS import error", e)
        }
    }
}
