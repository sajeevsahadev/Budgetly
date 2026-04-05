package com.budgetly.utils

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ─── Currency ─────────────────────────────────────────────────────────────

object CurrencyFormatter {
    private val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    fun format(amount: Double): String = format.format(amount)

    fun formatCompact(amount: Double): String = when {
        amount >= 1_00_000 -> "₹${String.format("%.1f", amount / 1_00_000)}L"
        amount >= 1_000 -> "₹${String.format("%.1f", amount / 1_000)}K"
        else -> "₹${String.format("%.0f", amount)}"
    }
}

// ─── Date Helpers ─────────────────────────────────────────────────────────

object DateUtils {
    private val fullFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val shortFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    fun formatFull(date: Date): String = fullFormat.format(date)
    fun formatShort(date: Date): String = shortFormat.format(date)
    fun formatMonthYear(date: Date): String = monthYearFormat.format(date)
    fun formatTime(date: Date): String = timeFormat.format(date)

    fun startOfMonth(month: Int = -1, year: Int = -1): Date {
        val cal = Calendar.getInstance()
        if (month > 0) cal.set(Calendar.MONTH, month - 1)
        if (year > 0) cal.set(Calendar.YEAR, year)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    fun endOfMonth(month: Int = -1, year: Int = -1): Date {
        val cal = Calendar.getInstance()
        if (month > 0) cal.set(Calendar.MONTH, month - 1)
        if (year > 0) cal.set(Calendar.YEAR, year)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        return cal.time
    }

    fun startOfToday(): Date {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    fun startOfWeek(): Date {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        return cal.time
    }

    fun daysUntil(date: Date): Int {
        val diff = date.time - System.currentTimeMillis()
        return (diff / (1000 * 60 * 60 * 24)).toInt()
    }

    fun isOverdue(date: Date): Boolean = date.before(Date())

    val MONTH_NAMES = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    val MONTH_NAMES_FULL = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
}

// ─── View Extensions ──────────────────────────────────────────────────────

fun Float.dpToPx(): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, this,
        Resources.getSystem().displayMetrics
    ).toInt()
}

fun Int.dpToPx(): Int = this.toFloat().dpToPx()

// ─── SMS Deduplication Key ────────────────────────────────────────────────

fun generateDeduplicationKey(amount: Double, date: Date, sender: String): String {
    val cal = Calendar.getInstance().apply { time = date }
    // Round to nearest 5 minutes to catch multi-part SMS
    val minutes = (cal.get(Calendar.MINUTE) / 5) * 5
    return "${sender}_${amount}_${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH)}_${cal.get(Calendar.DAY_OF_MONTH)}_${cal.get(Calendar.HOUR_OF_DAY)}_$minutes"
}

// ─── Shared Preferences Helpers ───────────────────────────────────────────

object AppPrefs {
    private const val PREFS_NAME = "app_prefs"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFirstRun(context: Context) = prefs(context).getBoolean("first_run", true)
    fun setFirstRunDone(context: Context) =
        prefs(context).edit().putBoolean("first_run", false).apply()

    fun isAppLockEnabled(context: Context) =
        prefs(context).getBoolean("app_lock_enabled", false)

    fun setAppLock(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("app_lock_enabled", enabled).apply()

    fun getLastSmsImportTime(context: Context) =
        prefs(context).getLong("last_sms_import", 0L)

    fun setLastSmsImportTime(context: Context, time: Long = System.currentTimeMillis()) =
        prefs(context).edit().putLong("last_sms_import", time).apply()

    fun getSelectedCurrency(context: Context) =
        prefs(context).getString("currency", "INR") ?: "INR"
}
