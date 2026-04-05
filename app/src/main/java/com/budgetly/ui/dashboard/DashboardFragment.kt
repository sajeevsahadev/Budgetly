package com.budgetly.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.budgetly.R
import com.budgetly.databinding.FragmentDashboardBinding
import com.budgetly.ui.expenses.ExpenseAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import java.util.Calendar

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var expenseAdapter: ExpenseAdapter

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        observeData()

        // Greeting
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.tvGreeting.text = when {
            hour < 12 -> "Good Morning 🌅"
            hour < 17 -> "Good Afternoon ☀️"
            else -> "Good Evening 🌙"
        }
    }

    private fun setupRecyclerView() {
        expenseAdapter = ExpenseAdapter(
            onItemClick = { expense ->
                val action = DashboardFragmentDirections.actionDashboardToExpenseDetail(expense.id)
                findNavController().navigate(action)
            },
            onCategoryClick = { expense ->
                // Show category picker
            }
        )
        binding.rvRecentExpenses.apply {
            adapter = expenseAdapter
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        binding.btnAddExpense.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_addExpense)
        }
        binding.tvSeeAllExpenses.setOnClickListener {
            findNavController().navigate(R.id.expensesFragment)
        }
        binding.cardBudget.setOnClickListener {
            findNavController().navigate(R.id.budgetFragment)
        }
        binding.cardSubscriptions.setOnClickListener {
            findNavController().navigate(R.id.subscriptionsFragment)
        }
        binding.cardBills.setOnClickListener {
            findNavController().navigate(R.id.billsFragment)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.monthlySpend.collect { amount ->
                        binding.tvMonthlyTotal.text = currencyFormat.format(amount)
                    }
                }

                launch {
                    viewModel.recentExpenses.collect { expenses ->
                        expenseAdapter.submitList(expenses)
                        binding.tvNoExpenses.visibility =
                            if (expenses.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.upcomingBills.collect { bills ->
                        val count = bills.size
                        binding.tvBillCount.text = "$count due soon"
                        binding.tvBillCount.visibility =
                            if (count > 0) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.budgetAlerts.collect { alerts ->
                        if (alerts.isNotEmpty()) {
                            binding.cardBudgetAlert.visibility = View.VISIBLE
                            val alertText = alerts.joinToString(", ") { it.budget.category.displayName }
                            binding.tvBudgetAlertText.text =
                                "⚠️ Near limit: $alertText"
                        } else {
                            binding.cardBudgetAlert.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.monthlySubCost.collect { cost ->
                        binding.tvSubCost.text = "₹${String.format("%.0f", cost)}/mo"
                    }
                }

                launch {
                    viewModel.categorySummary.collect { summaries ->
                        if (summaries.isNotEmpty()) {
                            val top = summaries.first()
                            binding.tvTopCategory.text = "${top.category.icon} ${top.category.displayName}"
                            binding.tvTopCategoryAmount.text = currencyFormat.format(top.totalAmount)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
