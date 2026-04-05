package com.budgetly.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.budgetly.data.db.AppDatabase
import com.budgetly.data.models.ExpenseCategory
import com.budgetly.receiver.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Runs daily to check budget thresholds and fire alerts.
 */
@HiltWorker
class BudgetCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: AppDatabase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val cal = Calendar.getInstance()
            val month = cal.get(Calendar.MONTH) + 1
            val year = cal.get(Calendar.YEAR)
            val monthStr = String.format("%02d", month)
            val yearStr = year.toString()

            val budgets = db.budgetDao().getBudgetsByMonth(month, year).first()
            val notificationHelper = NotificationHelper(applicationContext)

            budgets.forEach { budget ->
                if (budget.alertSent) return@forEach

                val spent = db.expenseDao().getSpentByCategoryAndMonth(
                    budget.category.name, monthStr, yearStr
                ) ?: 0.0

                val percentage = if (budget.limitAmount > 0) spent / budget.limitAmount else 0.0

                if (percentage >= budget.alertThreshold) {
                    notificationHelper.sendBudgetAlert(
                        budget.category,
                        spent,
                        budget.limitAmount,
                        percentage
                    )
                    db.budgetDao().markAlertSent(budget.id)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "budget_check_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val request = PeriodicWorkRequestBuilder<BudgetCheckWorker>(
                12, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

/**
 * One-time worker to import historical SMS on first launch.
 */
@HiltWorker
class SmsImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: AppDatabase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Actual import logic is in SplashActivity for now
        // This worker is reserved for background re-scans triggered from Settings
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "sms_import_work"

        fun scheduleOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<SmsImportWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
