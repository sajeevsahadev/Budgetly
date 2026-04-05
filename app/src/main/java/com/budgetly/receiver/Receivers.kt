package com.budgetly.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.budgetly.R
import com.budgetly.data.db.AppDatabase
import com.budgetly.data.models.BillReminder
import com.budgetly.data.models.ExpenseCategory
import com.budgetly.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

// ─── Boot Receiver ────────────────────────────────────────────────────────

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.d("BootReceiver", "Device rebooted, restoring alarms")
            CoroutineScope(Dispatchers.IO).launch {
                rescheduleAllAlarms(context)
            }
        }
    }

    private suspend fun rescheduleAllAlarms(context: Context) {
        val db = AppDatabase.getInstance(context)
        val bills = db.billReminderDao().getActiveBills().first()
        bills.forEach { bill ->
            if (!bill.isPaid && bill.isActive) {
                BillAlarmScheduler.scheduleBillReminder(context, bill)
            }
        }
    }
}

// ─── Bill Reminder Receiver ───────────────────────────────────────────────

class BillReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val billId = intent.getLongExtra("bill_id", -1L)
        val billTitle = intent.getStringExtra("bill_title") ?: "Bill Payment"
        val amount = intent.getDoubleExtra("bill_amount", 0.0)

        NotificationHelper(context).sendBillReminder(billId, billTitle, amount)
    }
}

// ─── Alarm Scheduler ─────────────────────────────────────────────────────

object BillAlarmScheduler {

    fun scheduleBillReminder(context: Context, bill: BillReminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val cal = Calendar.getInstance().apply {
            time = bill.dueDate
            add(Calendar.DAY_OF_YEAR, -bill.reminderDaysBefore)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        // Only schedule future alarms
        if (cal.timeInMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, BillReminderReceiver::class.java).apply {
            putExtra("bill_id", bill.id)
            putExtra("bill_title", bill.title)
            putExtra("bill_amount", bill.amount)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            bill.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        cal.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Cannot schedule exact alarm", e)
            // Fall back to inexact alarm
            alarmManager.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        }
    }

    fun cancelBillReminder(context: Context, billId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BillReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            billId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }
}

// ─── Notification Helper ──────────────────────────────────────────────────

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_BILLS = "channel_bills"
        const val CHANNEL_BUDGET = "channel_budget"
        const val CHANNEL_EXPENSE = "channel_expense"

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL_BILLS, "Bill Reminders", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Reminders for upcoming bill payments"
                    enableVibration(true)
                })

                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL_BUDGET, "Budget Alerts", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when approaching budget limits"
                    enableVibration(true)
                })

                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL_EXPENSE, "New Expenses", NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for automatically tracked expenses"
                })
            }
        }
    }

    fun sendBillReminder(billId: Long, title: String, amount: Double) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_tab", "bills")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, billId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BILLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📅 Bill Due Soon: $title")
            .setContentText("₹${String.format("%.2f", amount)} is due soon. Tap to mark as paid.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(billId.toInt(), notification)
    }

    fun sendBudgetAlert(
        category: ExpenseCategory,
        spent: Double,
        limit: Double,
        percentage: Double
    ) {
        val percentStr = String.format("%.0f", percentage * 100)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_tab", "budget")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, category.ordinal + 1000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚠️ Budget Alert: ${category.displayName}")
            .setContentText("You've used $percentStr% of your ₹${String.format("%.0f", limit)} budget (₹${String.format("%.0f", spent)} spent)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(category.ordinal + 1000, notification)
    }

    fun sendExpenseTracked(amount: Double, merchant: String, category: ExpenseCategory) {
        val notification = NotificationCompat.Builder(context, CHANNEL_EXPENSE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${category.icon} Expense Tracked")
            .setContentText("₹${String.format("%.2f", amount)} at $merchant")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}
