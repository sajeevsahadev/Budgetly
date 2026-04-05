package com.budgetly.ui.expenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.budgetly.R
import com.budgetly.data.models.ExpenseCategory
import com.budgetly.data.models.PaymentMode
import com.budgetly.databinding.FragmentExpensesBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ExpensesFragment : Fragment() {

    private var _binding: FragmentExpensesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExpensesViewModel by viewModels()
    private lateinit var adapter: ExpenseAdapter

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpensesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearchView()
        setupCategoryChips()
        setupFab()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = ExpenseAdapter(
            onItemClick = { expense ->
                findNavController().navigate(
                    ExpensesFragmentDirections.actionExpensesToDetail(expense.id)
                )
            },
            onCategoryClick = { expense ->
                showCategoryPicker(expense)
            }
        )

        binding.rvExpenses.apply {
            this.adapter = this@ExpensesFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        // Swipe to delete
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val expense = adapter.currentList[viewHolder.adapterPosition]
                viewModel.deleteExpense(expense)
                Snackbar.make(binding.root, "Expense deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        viewModel.addManualExpense(
                            expense.amount, expense.description, expense.category,
                            expense.paymentMode, expense.date, expense.notes, expense.isCashExpense
                        )
                    }.show()
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvExpenses)
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })
    }

    private fun setupCategoryChips() {
        // "All" chip
        binding.chipGroupCategory.removeAllViews()
        val allChip = Chip(requireContext()).apply {
            text = "All"
            isCheckable = true
            isChecked = true
            setOnClickListener { viewModel.setCategory(null) }
        }
        binding.chipGroupCategory.addView(allChip)

        // Category chips
        ExpenseCategory.values().forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = "${category.icon} ${category.displayName}"
                isCheckable = true
                setOnClickListener {
                    allChip.isChecked = false
                    viewModel.setCategory(category)
                }
            }
            binding.chipGroupCategory.addView(chip)
        }
    }

    private fun setupFab() {
        binding.fabAddExpense.setOnClickListener {
            showAddExpenseBottomSheet()
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.expenses.collect { expenses ->
                        adapter.submitList(expenses)
                        binding.tvExpenseCount.text = "${expenses.size} transactions"
                        binding.emptyState.visibility =
                            if (expenses.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.totalForPeriod.collect { total ->
                        binding.tvTotalAmount.text = currencyFormat.format(total)
                    }
                }
            }
        }
    }

    private fun showCategoryPicker(expense: com.budgetly.data.models.Expense) {
        val categories = ExpenseCategory.values()
        val items = categories.map { "${it.icon} ${it.displayName}" }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Change Category")
            .setMessage("All expenses from '${expense.merchantName}' will be updated")
            .setItems(items) { _, which ->
                val newCategory = categories[which]
                viewModel.updateCategory(expense, newCategory)
                Snackbar.make(
                    binding.root,
                    "All '${expense.merchantName}' expenses → ${newCategory.displayName}",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun showAddExpenseBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_add_expense, null)
        dialog.setContentView(sheetView)

        val etAmount = sheetView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAmount)
        val etDescription = sheetView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDescription)
        val etNotes = sheetView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNotes)
        val spinnerCategory = sheetView.findViewById<android.widget.Spinner>(R.id.spinnerCategory)
        val spinnerPaymentMode = sheetView.findViewById<android.widget.Spinner>(R.id.spinnerPaymentMode)
        val switchCash = sheetView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchCash)
        val btnSave = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)
        val btnCancel = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)

        // Setup spinners
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            ExpenseCategory.values().map { "${it.icon} ${it.displayName}" }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerCategory.adapter = categoryAdapter

        val paymentAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            PaymentMode.values().map { it.displayName }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerPaymentMode.adapter = paymentAdapter

        btnSave.setOnClickListener {
            val amount = etAmount.text.toString().toDoubleOrNull()
            if (amount == null || amount <= 0) {
                etAmount.error = "Enter valid amount"
                return@setOnClickListener
            }
            val description = etDescription.text.toString().ifBlank { "Manual Expense" }
            val category = ExpenseCategory.values()[spinnerCategory.selectedItemPosition]
            val paymentMode = PaymentMode.values()[spinnerPaymentMode.selectedItemPosition]
            val isCash = switchCash.isChecked
            val notes = etNotes.text.toString()

            viewModel.addManualExpense(amount, description, category, paymentMode, Date(), notes, isCash)
            dialog.dismiss()
            Snackbar.make(binding.root, "Expense added ✓", Snackbar.LENGTH_SHORT).show()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
