package com.budgetly.ui.expenses

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
import com.budgetly.databinding.ItemExpenseBinding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    private val _selectedCategory = MutableStateFlow<ExpenseCategory?>(null)

    val selectedMonth = _selectedMonth.asStateFlow()
    val selectedYear = _selectedYear.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val expenses: StateFlow<List<Expense>> = combine(
        _searchQuery.debounce(300),
        _selectedMonth,
        _selectedYear,
        _selectedCategory
    ) { query, month, year, category ->
        Triple(query, Pair(month, year), category)
    }.flatMapLatest { (query, monthYear, category) ->
        val (month, year) = monthYear
        when {
            query.isNotBlank() -> repository.searchExpenses(query)
            else -> repository.getExpensesByMonth(month, year)
        }.map { list ->
            if (category != null) list.filter { it.category == category } else list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalForPeriod: StateFlow<Double> = expenses
        .map { list -> list.filter { it.transactionType == TransactionType.DEBIT }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setMonth(month: Int, year: Int) {
        _selectedMonth.value = month
        _selectedYear.value = year
    }
    fun setCategory(category: ExpenseCategory?) { _selectedCategory.value = category }

    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        repository.deleteExpense(expense)
    }

    fun updateCategory(expense: Expense, newCategory: ExpenseCategory) = viewModelScope.launch {
        // Ask user if they want to update all expenses from this merchant
        repository.updateCategoryByMerchant(expense.merchantName, newCategory)
    }

    fun addManualExpense(
        amount: Double,
        description: String,
        category: ExpenseCategory,
        paymentMode: PaymentMode,
        date: java.util.Date,
        notes: String,
        isCash: Boolean
    ) = viewModelScope.launch {
        val expense = Expense(
            amount = amount,
            description = description,
            category = category,
            paymentMode = if (isCash) PaymentMode.CASH else paymentMode,
            transactionType = TransactionType.DEBIT,
            date = date,
            notes = notes,
            isManualEntry = true,
            isCashExpense = isCash
        )
        repository.addExpense(expense)
    }
}

// ─── RecyclerView Adapter ─────────────────────────────────────────────────

class ExpenseAdapter(
    private val onItemClick: (Expense) -> Unit,
    private val onCategoryClick: (Expense) -> Unit
) : ListAdapter<Expense, ExpenseAdapter.ViewHolder>(DiffCallback()) {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val dateFormat = SimpleDateFormat("dd MMM, EEE", Locale.getDefault())

    inner class ViewHolder(val binding: ItemExpenseBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExpenseBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val expense = getItem(position)
        with(holder.binding) {
            tvMerchant.text = expense.merchantName.ifBlank { expense.description }
            tvDescription.text = expense.description
            tvAmount.text = if (expense.transactionType == TransactionType.CREDIT)
                "+ ${currencyFormat.format(expense.amount)}"
            else
                "- ${currencyFormat.format(expense.amount)}"

            tvAmount.setTextColor(
                root.context.getColor(
                    if (expense.transactionType == TransactionType.CREDIT)
                        com.budgetly.R.color.green_500
                    else
                        com.budgetly.R.color.red_500
                )
            )

            tvDate.text = dateFormat.format(expense.date)
            tvCategory.text = "${expense.category.icon} ${expense.category.displayName}"

            // Payment mode chip
            tvPaymentMode.text = expense.paymentMode.displayName
            tvPaymentMode.visibility = View.VISIBLE

            // Cash badge
            ivCashBadge.visibility = if (expense.isCashExpense) View.VISIBLE else View.GONE

            // SMS badge
            ivSmsBadge.visibility = if (expense.isFromSms) View.VISIBLE else View.GONE

            // Set category icon background color
            viewCategoryColor.setBackgroundColor(
                android.graphics.Color.parseColor(expense.category.colorHex)
            )

            root.setOnClickListener { onItemClick(expense) }
            tvCategory.setOnClickListener { onCategoryClick(expense) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Expense>() {
        override fun areItemsTheSame(old: Expense, new: Expense) = old.id == new.id
        override fun areContentsTheSame(old: Expense, new: Expense) = old == new
    }
}
