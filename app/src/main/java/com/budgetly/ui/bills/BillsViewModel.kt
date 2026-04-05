package com.budgetly.ui.bills

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.budgetly.data.models.*
import com.budgetly.data.repository.ExpenseRepository
import com.budgetly.databinding.ItemBillBinding
import com.budgetly.receiver.BillAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    val allBills: StateFlow<List<BillReminder>> = repository.getAllBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcomingBills: StateFlow<List<BillReminder>> = repository.getUpcomingBills(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _totalUnpaid = MutableStateFlow(0.0)
    val totalUnpaid: StateFlow<Double> = _totalUnpaid

    init {
        viewModelScope.launch {
            _totalUnpaid.value = repository.getTotalUnpaidAmount()
        }
    }

    fun addBill(
        title: String,
        amount: Double,
        dueDate: Date,
        category: ExpenseCategory,
        recurrence: RecurrenceType,
        reminderDays: Int,
        notes: String
    ) = viewModelScope.launch {
        val bill = BillReminder(
            title = title,
            amount = amount,
            dueDate = dueDate,
            category = category,
            recurrence = recurrence,
            reminderDaysBefore = reminderDays,
            notes = notes
        )
        repository.addBill(bill)
    }

    fun markAsPaid(bill: BillReminder) = viewModelScope.launch {
        repository.markBillAsPaid(bill.id)
        // If recurring, create next occurrence
        if (bill.recurrence != RecurrenceType.NONE) {
            val nextDate = getNextDueDate(bill.dueDate, bill.recurrence)
            val nextBill = bill.copy(
                id = 0,
                dueDate = nextDate,
                isPaid = false,
                paidDate = null
            )
            repository.addBill(nextBill)
        }
        _totalUnpaid.value = repository.getTotalUnpaidAmount()
    }

    fun deleteBill(bill: BillReminder) = viewModelScope.launch {
        repository.deleteBill(bill)
    }

    private fun getNextDueDate(current: Date, recurrence: RecurrenceType): Date {
        val cal = Calendar.getInstance().apply { time = current }
        when (recurrence) {
            RecurrenceType.DAILY    -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RecurrenceType.WEEKLY   -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RecurrenceType.MONTHLY  -> cal.add(Calendar.MONTH, 1)
            RecurrenceType.QUARTERLY-> cal.add(Calendar.MONTH, 3)
            RecurrenceType.YEARLY   -> cal.add(Calendar.YEAR, 1)
            RecurrenceType.NONE     -> {}
        }
        return cal.time
    }
}

// ─── Adapter ──────────────────────────────────────────────────────────────

class BillAdapter(
    private val onMarkPaid: (BillReminder) -> Unit,
    private val onDelete: (BillReminder) -> Unit,
    private val onEdit: (BillReminder) -> Unit
) : ListAdapter<BillReminder, BillAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())

    inner class ViewHolder(val binding: ItemBillBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBillBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bill = getItem(position)
        with(holder.binding) {
            tvBillTitle.text = bill.title
            tvBillAmount.text = "₹${String.format("%.2f", bill.amount)}"
            tvDueDate.text = "Due: ${dateFormat.format(bill.dueDate)}"
            tvRecurrence.text = bill.recurrence.displayName
            tvCategory.text = "${bill.category.icon} ${bill.category.displayName}"

            // Days until due
            val daysUntil = ((bill.dueDate.time - System.currentTimeMillis()) /
                    (1000 * 60 * 60 * 24)).toInt()

            tvDaysUntil.text = when {
                bill.isPaid -> "✓ Paid"
                daysUntil < 0 -> "⚠ Overdue by ${-daysUntil}d"
                daysUntil == 0 -> "🔴 Due Today"
                daysUntil <= 3 -> "🟡 Due in ${daysUntil}d"
                else -> "🟢 Due in ${daysUntil}d"
            }

            tvDaysUntil.setTextColor(
                root.context.getColor(when {
                    bill.isPaid -> com.budgetly.R.color.green_500
                    daysUntil < 0 -> com.budgetly.R.color.red_500
                    daysUntil <= 3 -> com.budgetly.R.color.amber_600
                    else -> com.budgetly.R.color.green_500
                })
            )

            btnMarkPaid.isEnabled = !bill.isPaid
            btnMarkPaid.text = if (bill.isPaid) "Paid ✓" else "Mark Paid"
            btnMarkPaid.setOnClickListener { onMarkPaid(bill) }

            root.setOnLongClickListener {
                onDelete(bill)
                true
            }
            root.setOnClickListener { onEdit(bill) }

            viewCategoryColor.setBackgroundColor(
                android.graphics.Color.parseColor(bill.category.colorHex)
            )
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<BillReminder>() {
        override fun areItemsTheSame(old: BillReminder, new: BillReminder) = old.id == new.id
        override fun areContentsTheSame(old: BillReminder, new: BillReminder) = old == new
    }
}
