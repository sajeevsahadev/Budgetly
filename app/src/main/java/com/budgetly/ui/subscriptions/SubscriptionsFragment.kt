package com.budgetly.ui.subscriptions

import android.app.DatePickerDialog
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.budgetly.R
import com.budgetly.data.models.*
import com.budgetly.data.repository.ExpenseRepository
import com.budgetly.databinding.FragmentSubscriptionsBinding
import com.budgetly.databinding.ItemSubscriptionBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    val subscriptions: StateFlow<List<Subscription>> = repository.getAllSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _monthlyCost = MutableStateFlow(0.0)
    val monthlyCost: StateFlow<Double> = _monthlyCost

    private val _yearlyCost = MutableStateFlow(0.0)
    val yearlyCost: StateFlow<Double> = _yearlyCost

    init {
        viewModelScope.launch {
            val monthly = repository.getMonthlySubscriptionCost()
            _monthlyCost.value = monthly
            _yearlyCost.value = monthly * 12
        }
    }

    fun addSubscription(
        name: String, amount: Double, billingCycle: RecurrenceType,
        nextBillingDate: Date, category: ExpenseCategory,
        paymentMode: PaymentMode, reminderDays: Int
    ) = viewModelScope.launch {
        val sub = Subscription(
            name = name, amount = amount, billingCycle = billingCycle,
            nextBillingDate = nextBillingDate, category = category,
            paymentMode = paymentMode, reminderDaysBefore = reminderDays
        )
        repository.addSubscription(sub)
        refreshCosts()
    }

    fun toggleActive(sub: Subscription) = viewModelScope.launch {
        repository.updateSubscription(sub.copy(isActive = !sub.isActive))
        refreshCosts()
    }

    fun deleteSubscription(sub: Subscription) = viewModelScope.launch {
        repository.deleteSubscription(sub)
        refreshCosts()
    }

    private suspend fun refreshCosts() {
        val monthly = repository.getMonthlySubscriptionCost()
        _monthlyCost.value = monthly
        _yearlyCost.value = monthly * 12
    }
}

// ─── Adapter ──────────────────────────────────────────────────────────────

class SubscriptionAdapter(
    private val onToggle: (Subscription) -> Unit,
    private val onDelete: (Subscription) -> Unit,
    private val onEdit: (Subscription) -> Unit
) : ListAdapter<Subscription, SubscriptionAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

    inner class ViewHolder(val binding: ItemSubscriptionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSubscriptionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sub = getItem(position)
        with(holder.binding) {
            tvSubName.text = sub.name
            tvSubAmount.text = "₹${String.format("%.0f", sub.amount)}/${sub.billingCycle.displayName.lowercase()}"
            tvNextBilling.text = "Next: ${dateFormat.format(sub.nextBillingDate)}"
            tvSubCategory.text = sub.category.icon

            val daysUntil = ((sub.nextBillingDate.time - System.currentTimeMillis()) /
                    (1000 * 60 * 60 * 24)).toInt()
            tvDaysUntil.text = when {
                daysUntil <= 0 -> "Due today"
                daysUntil <= 7 -> "In ${daysUntil}d"
                else -> "In ${daysUntil}d"
            }

            switchActive.isChecked = sub.isActive
            switchActive.setOnCheckedChangeListener(null)
            switchActive.setOnCheckedChangeListener { _, _ -> onToggle(sub) }

            // Muted appearance if inactive
            root.alpha = if (sub.isActive) 1.0f else 0.5f

            root.setOnLongClickListener { onDelete(sub); true }
            root.setOnClickListener { onEdit(sub) }

            viewSubColor.setBackgroundColor(
                android.graphics.Color.parseColor(sub.category.colorHex)
            )
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Subscription>() {
        override fun areItemsTheSame(old: Subscription, new: Subscription) = old.id == new.id
        override fun areContentsTheSame(old: Subscription, new: Subscription) = old == new
    }
}

// ─── Fragment ─────────────────────────────────────────────────────────────

@AndroidEntryPoint
class SubscriptionsFragment : Fragment() {

    private var _binding: FragmentSubscriptionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SubscriptionsViewModel by viewModels()
    private lateinit var adapter: SubscriptionAdapter

    // Known popular subscriptions for quick-add
    private val popularSubs = listOf(
        Triple("Netflix", 649.0, ExpenseCategory.ENTERTAINMENT),
        Triple("Spotify", 119.0, ExpenseCategory.ENTERTAINMENT),
        Triple("Amazon Prime", 299.0, ExpenseCategory.ENTERTAINMENT),
        Triple("Hotstar", 299.0, ExpenseCategory.ENTERTAINMENT),
        Triple("YouTube Premium", 189.0, ExpenseCategory.ENTERTAINMENT),
        Triple("Jio", 666.0, ExpenseCategory.BILLS_UTILITIES),
        Triple("Airtel", 599.0, ExpenseCategory.BILLS_UTILITIES),
        Triple("Google One", 130.0, ExpenseCategory.SUBSCRIPTIONS),
        Triple("Microsoft 365", 420.0, ExpenseCategory.SUBSCRIPTIONS),
        Triple("Byju's", 2500.0, ExpenseCategory.EDUCATION),
        Triple("Unacademy", 999.0, ExpenseCategory.EDUCATION),
        Triple("Swiggy One", 299.0, ExpenseCategory.FOOD_DINING),
        Triple("Zomato Gold", 149.0, ExpenseCategory.FOOD_DINING),
        Triple("Cult.fit", 999.0, ExpenseCategory.HEALTH),
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubscriptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupButtons()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = SubscriptionAdapter(
            onToggle = { viewModel.toggleActive(it) },
            onDelete = { sub ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Remove Subscription?")
                    .setMessage("Remove '${sub.name}' from tracking?")
                    .setPositiveButton("Remove") { _, _ -> viewModel.deleteSubscription(sub) }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onEdit = { showAddSubscriptionSheet(it) }
        )
        binding.rvSubscriptions.apply {
            this.adapter = this@SubscriptionsFragment.adapter
            layoutManager = GridLayoutManager(requireContext(), 2)
        }
    }

    private fun setupButtons() {
        binding.fabAddSub.setOnClickListener { showAddSubscriptionSheet(null) }
        binding.btnQuickAdd.setOnClickListener { showQuickAddDialog() }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.subscriptions.collect { subs ->
                        adapter.submitList(subs)
                        binding.tvSubCount.text = "${subs.count { it.isActive }} active"
                        binding.emptyState.visibility = if (subs.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.monthlyCost.collect { cost ->
                        binding.tvMonthlyCost.text = "₹${String.format("%.0f", cost)}/month"
                    }
                }
                launch {
                    viewModel.yearlyCost.collect { cost ->
                        binding.tvYearlyCost.text = "₹${String.format(",.0f", cost)}/year"
                    }
                }
            }
        }
    }

    private fun showQuickAddDialog() {
        val items = popularSubs.map { "${it.third.icon} ${it.first} — ₹${it.second.toInt()}/mo" }
        val selected = BooleanArray(items.size)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Quick Add Popular Subscriptions")
            .setMultiChoiceItems(items.toTypedArray(), selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            .setPositiveButton("Add Selected") { _, _ ->
                val nextMonth = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }.time
                selected.forEachIndexed { idx, checked ->
                    if (checked) {
                        val (name, amount, category) = popularSubs[idx]
                        viewModel.addSubscription(
                            name, amount, RecurrenceType.MONTHLY, nextMonth,
                            category, PaymentMode.CREDIT_CARD, 3
                        )
                    }
                }
                Snackbar.make(binding.root, "Subscriptions added ✓", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddSubscriptionSheet(existing: Subscription?) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_add_subscription, null)
        dialog.setContentView(sheetView)

        val etName = sheetView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSubName)
        val etAmount = sheetView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSubAmount)
        val btnNextDate = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPickNextDate)
        val spinnerCycle = sheetView.findViewById<android.widget.Spinner>(R.id.spinnerBillingCycle)
        val spinnerCategory = sheetView.findViewById<android.widget.Spinner>(R.id.spinnerSubCategory)
        val spinnerPayment = sheetView.findViewById<android.widget.Spinner>(R.id.spinnerSubPayment)
        val btnSave = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveSub)

        var selectedDate = existing?.nextBillingDate ?: Calendar.getInstance().apply {
            add(Calendar.MONTH, 1)
        }.time
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        btnNextDate.text = dateFormat.format(selectedDate)

        existing?.let {
            etName.setText(it.name)
            etAmount.setText(it.amount.toString())
        }

        val cycles = RecurrenceType.values().filter { it != RecurrenceType.NONE }
        spinnerCycle.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, cycles.map { it.displayName }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerCycle.setSelection(cycles.indexOf(RecurrenceType.MONTHLY))

        val categories = ExpenseCategory.values()
        spinnerCategory.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, categories.map { "${it.icon} ${it.displayName}" }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerCategory.setSelection(categories.indexOf(ExpenseCategory.SUBSCRIPTIONS))

        val paymentModes = PaymentMode.values()
        spinnerPayment.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, paymentModes.map { it.displayName }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        btnNextDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { time = selectedDate }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(y, m, d)
                selectedDate = cal.time
                btnNextDate.text = dateFormat.format(selectedDate)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val amount = etAmount.text.toString().toDoubleOrNull()
            if (name.isBlank()) { etName.error = "Required"; return@setOnClickListener }
            if (amount == null || amount <= 0) { etAmount.error = "Enter valid amount"; return@setOnClickListener }

            val cycle = cycles[spinnerCycle.selectedItemPosition]
            val category = categories[spinnerCategory.selectedItemPosition]
            val payment = paymentModes[spinnerPayment.selectedItemPosition]

            viewModel.addSubscription(name, amount, cycle, selectedDate, category, payment, 3)
            dialog.dismiss()
            Snackbar.make(binding.root, "Subscription added ✓", Snackbar.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
