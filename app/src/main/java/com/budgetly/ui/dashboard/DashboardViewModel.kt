package com.budgetly.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetly.data.models.*
import com.budgetly.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    // Monthly total spend
    val monthlySpend: StateFlow<Double> = repository.getExpensesByMonth(currentMonth, currentYear)
        .map { expenses -> expenses.filter { it.transactionType == TransactionType.DEBIT }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Recent expenses
    val recentExpenses: StateFlow<List<Expense>> = repository.getRecentExpenses(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Category breakdown for current month
    val categorySummary: StateFlow<List<CategorySummary>> =
        repository.getCategorySummary(currentMonth, currentYear)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Upcoming bills
    val upcomingBills: StateFlow<List<BillReminder>> = repository.getUpcomingBills(7)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Budget alerts (>= 90%)
    val budgetAlerts: StateFlow<List<BudgetWithSpent>> =
        repository.getBudgetsWithSpent(currentMonth, currentYear)
            .map { list -> list.filter { it.isAlertThreshold } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Monthly subscription cost
    private val _monthlySubCost = MutableStateFlow(0.0)
    val monthlySubCost: StateFlow<Double> = _monthlySubCost

    init {
        viewModelScope.launch {
            _monthlySubCost.value = repository.getMonthlySubscriptionCost()
        }
    }
}
