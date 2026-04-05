package com.budgetly.ui.budget

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.budgetly.R
import com.budgetly.data.models.*
import com.budgetly.data.repository.ExpenseRepository
import com.budgetly.databinding.FragmentBudgetBinding
import com.budgetly.databinding.ItemBudgetBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val cal = Calendar.getInstance()
    val currentMonth = cal.get(Calendar.MONTH) + 1
    val currentYear = cal.get(Calendar.YEAR)

    val budgetsWithSpent: StateFlow<List<BudgetWithSpent>> =
        repository.getBudgetsWithSpent(currentMonth, currentYear)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalBudget: StateFlow<Double> = budgetsWithSpent
        .map { list -> list.sumOf { it.budget.limitAmount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalSpent: StateFlow<Double> = budgetsWithSpent
        .map { list -> list.sumOf { it.spentAmount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun addOrUpdateBudget(category: ExpenseCategory, limitAmount: Double) = viewModelScope.launch {
        val budget = Budget(
            category = category,
            limitAmount = limitAmount,
            month = currentMonth,
            year = currentYear,
            alertThreshold = 0.90
        )
        repository.addOrUpdateBudget(budget)
    }

    fun deleteBudget(bws: BudgetWithSpent) = viewModelScope.launch {
        repository.deleteBudget(bws.budget)
    }
}

// ─── Adapter ──────────────────────────────────────────────────────────────

class BudgetAdapter(
    private val onDelete: (BudgetWithSpent) -> Unit,
    private val onEdit: (BudgetWithSpent) -> Unit
) : ListAdapter<BudgetWithSpent, BudgetAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(val binding: ItemBudgetBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBudgetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bws = getItem(position)
        with(holder.binding) {
            val cat = bws.budget.category
            tvBudgetCategory.text = "${cat.icon} ${cat.displayName}"
            tvBudgetLimit.text = "Budget: ₹${String.format("%.0f", bws.budget.limitAmount)}"
            tvBudgetSpent.text = "Spent: ₹${String.format("%.0f", bws.spentAmount)}"
            tvBudgetRemaining.text = "Left: ₹${String.format("%.0f", bws.remaining)}"

            val pct = bws.percentage.coerceIn(0.0, 100.0).toInt()
            progressBudget.progress = pct
            tvBudgetPercent.text = "$pct%"

            // Color the progress bar based on threshold
            val color = when {
                bws.percentage >= 100 -> Color.parseColor("#F44336") // Red
                bws.percentage >= 90 -> Color.parseColor("#FF9800")  // Orange
                bws.percentage >= 70 -> Color.parseColor("#FFC107")  // Amber
                else -> Color.parseColor("#4CAF50")                  // Green
            }
            progressBudget.setIndicatorColor(color)

            if (bws.isAlertThreshold) {
                tvBudgetAlert.visibility = View.VISIBLE
                tvBudgetAlert.text = if (bws.percentage >= 100) "⛔ Over budget!" else "⚠️ Near limit!"
            } else {
                tvBudgetAlert.visibility = View.GONE
            }

            viewCategoryColor.setBackgroundColor(Color.parseColor(cat.colorHex))

            root.setOnLongClickListener { onDelete(bws); true }
            root.setOnClickListener { onEdit(bws) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<BudgetWithSpent>() {
        override fun areItemsTheSame(old: BudgetWithSpent, new: BudgetWithSpent) =
            old.budget.id == new.budget.id
        override fun areContentsTheSame(old: BudgetWithSpent, new: BudgetWithSpent) =
            old == new
    }
}

// ─── Fragment ─────────────────────────────────────────────────────────────

@AndroidEntryPoint
class BudgetFragment : Fragment() {

    private var _binding: FragmentBudgetBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BudgetViewModel by viewModels()
    private lateinit var adapter: BudgetAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        observeData()

        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        binding.tvCurrentMonth.text = "${months[viewModel.currentMonth - 1]} ${viewModel.currentYear}"
    }

    private fun setupRecyclerView() {
        adapter = BudgetAdapter(
            onDelete = { bws ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Budget?")
                    .setMessage("Remove budget for '${bws.budget.category.displayName}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteBudget(bws)
                        Snackbar.make(binding.root, "Budget deleted", Snackbar.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onEdit = { bws -> showSetBudgetSheet(bws.budget.category, bws.budget.limitAmount) }
        )
        binding.rvBudgets.apply {
            this.adapter = this@BudgetFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupFab() {
        binding.fabAddBudget.setOnClickListener {
            showSetBudgetSheet(null, 0.0)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.budgetsWithSpent.collect { list ->
                        adapter.submitList(list)
                        binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

                        // Summary donut-style progress
                        val totalBudget = list.sumOf { it.budget.limitAmount }
                        val totalSpent = list.sumOf { it.spentAmount }
                        val overallPct = if (totalBudget > 0)
                            ((totalSpent / totalBudget) * 100).coerceIn(0.0, 100.0).toInt()
                        else 0
                        binding.progressOverall.progress = overallPct
                        binding.tvOverallPct.text = "$overallPct%"
                    }
                }
                launch {
                    viewModel.totalBudget.collect { total ->
                        binding.tvTotalBudget.text = "Total Budget: ₹${String.format("%.0f", total)}"
                    }
                }
                launch {
                    viewModel.totalSpent.collect { spent ->
                        binding.tvTotalSpent.text = "Total Spent: ₹${String.format("%.0f", spent)}"
                    }
                }
            }
        }
    }

    private fun showSetBudgetSheet(preCategory: ExpenseCategory?, preAmount: Double) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_set_budget, null)
        dialog.setContentView(sheetView)

        val spinnerCategory = sheetView.findViewById<android.widget.Spinner>(R.id.spinnerBudgetCategory)
        val etLimit = sheetView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etBudgetLimit)
        val sliderThreshold = sheetView.findViewById<com.google.android.material.slider.Slider>(R.id.sliderThreshold)
        val tvThreshold = sheetView.findViewById<android.widget.TextView>(R.id.tvThresholdValue)
        val btnSave = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveBudget)

        val categories = ExpenseCategory.values().filter { it != ExpenseCategory.SALARY }
        spinnerCategory.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item,
            categories.map { "${it.icon} ${it.displayName}" }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        preCategory?.let { spinnerCategory.setSelection(categories.indexOf(it)) }
        if (preAmount > 0) etLimit.setText(preAmount.toInt().toString())

        sliderThreshold.value = 90f
        tvThreshold.text = "Alert at: 90%"
        sliderThreshold.addOnChangeListener { _, value, _ ->
            tvThreshold.text = "Alert at: ${value.toInt()}%"
        }

        btnSave.setOnClickListener {
            val amount = etLimit.text.toString().toDoubleOrNull()
            if (amount == null || amount <= 0) { etLimit.error = "Enter valid amount"; return@setOnClickListener }
            val category = categories[spinnerCategory.selectedItemPosition]
            viewModel.addOrUpdateBudget(category, amount)
            dialog.dismiss()
            Snackbar.make(binding.root, "Budget set for ${category.displayName} ✓", Snackbar.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
