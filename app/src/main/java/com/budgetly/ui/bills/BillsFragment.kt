package com.budgetly.ui.bills

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.budgetly.R
import com.budgetly.data.models.ExpenseCategory
import com.budgetly.data.models.RecurrenceType
import com.budgetly.databinding.FragmentBillsBinding
import com.budgetly.receiver.BillAlarmScheduler
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class BillsFragment : Fragment() {

    private var _binding: FragmentBillsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BillsViewModel by viewModels()
    private lateinit var adapter: BillAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBillsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = BillAdapter(
            onMarkPaid = { bill ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Mark as Paid?")
                    .setMessage("${bill.title} — ₹${String.format("%.2f", bill.amount)}")
                    .setPositiveButton("Yes, Paid") { _, _ ->
                        viewModel.markAsPaid(bill)
                        if (bill.recurrence != RecurrenceType.NONE) {
                            Snackbar.make(binding.root, "Marked paid. Next ${bill.recurrence.displayName} bill created.", Snackbar.LENGTH_LONG).show()
                        } else {
                            Snackbar.make(binding.root, "Marked as paid ✓", Snackbar.LENGTH_SHORT).show()
                        }
                        BillAlarmScheduler.cancelBillReminder(requireContext(), bill.id)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onDelete = { bill ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Bill?")
                    .setMessage("Delete '${bill.title}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteBill(bill)
                        BillAlarmScheduler.cancelBillReminder(requireContext(), bill.id)
                        Snackbar.make(binding.root, "Bill deleted", Snackbar.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onEdit = { bill ->
                showAddBillSheet(bill)
            }
        )
        binding.rvBills.apply {
            this.adapter = this@BillsFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupFab() {
        binding.fabAddBill.setOnClickListener { showAddBillSheet(null) }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.allBills.collect { bills ->
                        adapter.submitList(bills)
                        binding.emptyState.visibility = if (bills.isEmpty()) View.VISIBLE else View.GONE
                        val unpaidCount = bills.count { !it.isPaid && it.isActive }
                        binding.tvBillSummary.text = "$unpaidCount unpaid bills"
                    }
                }
                launch {
                    viewModel.totalUnpaid.collect { total ->
                        binding.tvTotalUnpaid.text = "Total due: ₹${String.format("%.2f", total)}"
                    }
                }
            }
        }
    }

    private fun showAddBillSheet(existingBill: com.budgetly.data.models.BillReminder?) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_add_bill, null)
        dialog.setContentView(sheetView)

        val etTitle = sheetView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etBillTitle)
        val etAmount = sheetView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etBillAmount)
        val btnDueDate = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPickDueDate)
        val spinnerCategory = sheetView.findViewById<android.widget.Spinner>(R.id.spinnerBillCategory)
        val spinnerRecurrence = sheetView.findViewById<android.widget.Spinner>(R.id.spinnerRecurrence)
        val spinnerReminder = sheetView.findViewById<android.widget.Spinner>(R.id.spinnerReminderDays)
        val btnSave = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveBill)

        var selectedDate = existingBill?.dueDate ?: run {
            Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 7) }.time
        }
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        btnDueDate.text = dateFormat.format(selectedDate)

        // Pre-fill if editing
        existingBill?.let {
            etTitle.setText(it.title)
            etAmount.setText(it.amount.toString())
        }

        // Category spinner
        val categories = ExpenseCategory.values()
        spinnerCategory.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item,
            categories.map { "${it.icon} ${it.displayName}" }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Recurrence spinner
        val recurrences = RecurrenceType.values()
        spinnerRecurrence.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item,
            recurrences.map { it.displayName }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerRecurrence.setSelection(recurrences.indexOf(RecurrenceType.MONTHLY))

        // Reminder days
        val reminderOptions = listOf("1 day before", "3 days before", "5 days before", "7 days before")
        val reminderValues = listOf(1, 3, 5, 7)
        spinnerReminder.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, reminderOptions
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerReminder.setSelection(1) // Default 3 days

        btnDueDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { time = selectedDate }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(y, m, d)
                selectedDate = cal.time
                btnDueDate.text = dateFormat.format(selectedDate)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val amount = etAmount.text.toString().toDoubleOrNull()
            if (title.isBlank()) { etTitle.error = "Required"; return@setOnClickListener }
            if (amount == null || amount <= 0) { etAmount.error = "Enter valid amount"; return@setOnClickListener }

            val category = categories[spinnerCategory.selectedItemPosition]
            val recurrence = recurrences[spinnerRecurrence.selectedItemPosition]
            val reminderDays = reminderValues[spinnerReminder.selectedItemPosition]

            viewModel.addBill(title, amount, selectedDate, category, recurrence, reminderDays, "")
            dialog.dismiss()
            Snackbar.make(binding.root, "Bill reminder added ✓", Snackbar.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
