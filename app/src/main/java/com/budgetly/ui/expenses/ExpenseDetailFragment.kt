package com.budgetly.ui.expenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.budgetly.data.db.AppDatabase
import com.budgetly.data.models.Expense
import com.budgetly.data.models.ExpenseCategory
import com.budgetly.data.models.TransactionType
import com.budgetly.data.models.VendorCategoryOverride
import com.budgetly.databinding.FragmentExpenseDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────

@HiltViewModel
class ExpenseDetailViewModel @Inject constructor(
    private val db: AppDatabase
) : ViewModel() {

    private val _expense = MutableStateFlow<Expense?>(null)
    val expense: StateFlow<Expense?> = _expense

    fun loadExpense(id: Long) = viewModelScope.launch {
        _expense.value = db.expenseDao().getById(id)
    }

    fun updateCategory(
        expense: Expense,
        newCategory: ExpenseCategory,
        allFromMerchant: Boolean
    ) = viewModelScope.launch {
        if (allFromMerchant && expense.merchantName.isNotBlank()) {
            db.expenseDao().updateCategoryByMerchant(expense.merchantName, newCategory.name)
            db.vendorCategoryOverrideDao().insert(
                VendorCategoryOverride(
                    merchantName = expense.merchantName.lowercase().trim(),
                    category = newCategory.name
                )
            )
        } else {
            db.expenseDao().update(expense.copy(category = newCategory, updatedAt = Date()))
        }
        _expense.value = db.expenseDao().getById(expense.id)
    }

    fun deleteExpense(expense: Expense, onDone: () -> Unit) = viewModelScope.launch {
        db.expenseDao().delete(expense)
        onDone()
    }
}

// ─── Fragment ─────────────────────────────────────────────────────────────

@AndroidEntryPoint
class ExpenseDetailFragment : Fragment() {

    private var _binding: FragmentExpenseDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExpenseDetailViewModel by viewModels()
    private val args: ExpenseDetailFragmentArgs by navArgs()

    private val dateFormat = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpenseDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadExpense(args.expenseId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.expense.collect { expense ->
                    expense ?: return@collect
                    bindExpense(expense)
                }
            }
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
    }

    private fun bindExpense(expense: Expense) {
        with(binding) {
            tvDetailMerchant.text = expense.merchantName.ifBlank { expense.description }
            tvDetailDescription.text = expense.description

            tvDetailAmount.text =
                if (expense.transactionType == TransactionType.CREDIT)
                    "+ ₹${String.format("%.2f", expense.amount)}"
                else
                    "- ₹${String.format("%.2f", expense.amount)}"

            tvDetailAmount.setTextColor(
                requireContext().getColor(
                    if (expense.transactionType == TransactionType.CREDIT)
                        com.budgetly.R.color.green_500
                    else
                        com.budgetly.R.color.red_500
                )
            )

            tvDetailDate.text = dateFormat.format(expense.date)
            tvDetailCategory.text = "${expense.category.icon} ${expense.category.displayName}"
            tvDetailPayment.text = expense.paymentMode.displayName
            tvDetailBank.text = expense.bankName.ifBlank { "—" }
            tvDetailAccount.text =
                if (expense.accountLast4.isNotBlank()) "••••${expense.accountLast4}" else "—"
            tvDetailRef.text = expense.referenceNumber.ifBlank { "—" }
            tvDetailNotes.text = expense.notes.ifBlank { "No notes" }

            chipFromSms.visibility = if (expense.isFromSms) View.VISIBLE else View.GONE
            chipCash.visibility = if (expense.isCashExpense) View.VISIBLE else View.GONE
            chipManual.visibility = if (expense.isManualEntry) View.VISIBLE else View.GONE

            tvRawSms.text = expense.rawSmsBody.ifBlank { "—" }
            tvRawSms.visibility =
                if (expense.rawSmsBody.isNotBlank()) View.VISIBLE else View.GONE

            btnChangeCategory.setOnClickListener { showCategoryPicker(expense) }

            btnDelete.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Expense?")
                    .setMessage("This will permanently remove this transaction.")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteExpense(expense) { findNavController().navigateUp() }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun showCategoryPicker(expense: Expense) {
        val categories = ExpenseCategory.values()
        val items = categories.map { "${it.icon} ${it.displayName}" }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Category")
            .setItems(items) { _, which ->
                val newCat = categories[which]
                if (expense.merchantName.isNotBlank()) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Apply to All?")
                        .setMessage(
                            "Change ALL expenses from '${expense.merchantName}' to ${newCat.displayName}?"
                        )
                        .setPositiveButton("All Expenses") { _, _ ->
                            viewModel.updateCategory(expense, newCat, true)
                            Snackbar.make(
                                binding.root,
                                "All '${expense.merchantName}' → ${newCat.displayName}",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                        .setNegativeButton("Just This One") { _, _ ->
                            viewModel.updateCategory(expense, newCat, false)
                            Snackbar.make(binding.root, "Category updated ✓", Snackbar.LENGTH_SHORT).show()
                        }
                        .show()
                } else {
                    viewModel.updateCategory(expense, newCat, false)
                    Snackbar.make(binding.root, "Category updated ✓", Snackbar.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
