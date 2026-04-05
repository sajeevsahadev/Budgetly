package com.budgetly.ui.graphs

import android.graphics.Color
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
import com.budgetly.data.models.*
import com.budgetly.data.repository.ExpenseRepository
import com.budgetly.databinding.FragmentGraphsBinding
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────

@HiltViewModel
class GraphsViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val cal = Calendar.getInstance()
    val currentMonth = cal.get(Calendar.MONTH) + 1
    val currentYear = cal.get(Calendar.YEAR)

    val categorySummary: StateFlow<List<CategorySummary>> =
        repository.getCategorySummary(currentMonth, currentYear)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgetsWithSpent: StateFlow<List<BudgetWithSpent>> =
        repository.getBudgetsWithSpent(currentMonth, currentYear)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _monthlyTrends = MutableStateFlow<List<MonthlyTrend>>(emptyList())
    val monthlyTrends: StateFlow<List<MonthlyTrend>> = _monthlyTrends

    init {
        viewModelScope.launch {
            _monthlyTrends.value = repository.getMonthlyTrends(6)
        }
    }
}

// ─── Fragment ─────────────────────────────────────────────────────────────

@AndroidEntryPoint
class GraphsFragment : Fragment() {

    private var _binding: FragmentGraphsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GraphsViewModel by viewModels()

    private val MONTH_LABELS = arrayOf("Jan","Feb","Mar","Apr","May","Jun",
        "Jul","Aug","Sep","Oct","Nov","Dec")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGraphsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChartDefaults()
        observeData()
    }

    private fun setupChartDefaults() {
        setupPieChart(binding.pieChart)
        setupBarChart(binding.barChartBudget)
        setupLineChart(binding.lineChartTrend)
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.categorySummary.collect { summaries ->
                        updatePieChart(summaries)
                        updateCategoryList(summaries)
                    }
                }

                launch {
                    viewModel.budgetsWithSpent.collect { budgets ->
                        updateBudgetBarChart(budgets)
                    }
                }

                launch {
                    viewModel.monthlyTrends.collect { trends ->
                        updateTrendLineChart(trends)
                    }
                }
            }
        }
    }

    // ─── Pie Chart: Category Spending ────────────────────────────────────

    private fun setupPieChart(chart: PieChart) {
        chart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 52f
            transparentCircleRadius = 57f
            setHoleColor(Color.TRANSPARENT)
            setDrawCenterText(true)
            centerText = "Spending\nby Category"
            setCenterTextSize(13f)
            setCenterTextColor(Color.parseColor("#1E1E2E"))
            legend.isEnabled = false
            setEntryLabelColor(Color.TRANSPARENT)
            setUsePercentValues(true)
            setDrawEntryLabels(false)
            isRotationEnabled = true
            isHighlightPerTapEnabled = true
        }
    }

    private fun updatePieChart(summaries: List<CategorySummary>) {
        if (summaries.isEmpty()) {
            binding.pieChart.clear()
            binding.tvNoCategoryData.visibility = View.VISIBLE
            return
        }
        binding.tvNoCategoryData.visibility = View.GONE

        val entries = summaries.take(8).map { summary ->
            PieEntry(summary.totalAmount.toFloat(), summary.category.displayName)
        }
        val colors = summaries.take(8).map {
            Color.parseColor(it.category.colorHex)
        }

        val dataSet = PieDataSet(entries, "Categories").apply {
            this.colors = colors
            sliceSpace = 3f
            selectionShift = 8f
            valueFormatter = PercentFormatter(binding.pieChart)
            valueTextSize = 11f
            valueTextColor = Color.WHITE
        }

        val pieData = PieData(dataSet)
        binding.pieChart.apply {
            data = pieData
            animateY(1000, Easing.EaseInOutQuad)
            invalidate()
        }
    }

    // ─── Category List Below Pie ──────────────────────────────────────────

    private fun updateCategoryList(summaries: List<CategorySummary>) {
        binding.llCategoryLegend.removeAllViews()
        summaries.take(6).forEach { summary ->
            val row = layoutInflater.inflate(
                com.budgetly.R.layout.item_category_legend, binding.llCategoryLegend, false
            )
            row.findViewById<View>(R.id.viewLegendColor).setBackgroundColor(
                Color.parseColor(summary.category.colorHex)
            )
            row.findViewById<android.widget.TextView>(R.id.tvLegendName).text =
                "${summary.category.icon} ${summary.category.displayName}"
            row.findViewById<android.widget.TextView>(R.id.tvLegendAmount).text =
                "₹${String.format(",.0f", summary.totalAmount)}"
            row.findViewById<android.widget.TextView>(R.id.tvLegendPercent).text =
                "${String.format("%.1f", summary.percentage)}%"
            binding.llCategoryLegend.addView(row)
        }
    }

    // ─── Bar Chart: Budget vs Spent ───────────────────────────────────────

    private fun setupBarChart(chart: BarChart) {
        chart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            isHighlightFullBarEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                textSize = 10f
                setDrawGridLines(false)
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#EEEEEE")
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
        }
    }

    private fun updateBudgetBarChart(budgets: List<BudgetWithSpent>) {
        if (budgets.isEmpty()) {
            binding.barChartBudget.clear()
            binding.tvNoBudgetData.visibility = View.VISIBLE
            return
        }
        binding.tvNoBudgetData.visibility = View.GONE

        val budgetEntries = ArrayList<BarEntry>()
        val spentEntries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        budgets.take(6).forEachIndexed { idx, bws ->
            budgetEntries.add(BarEntry(idx.toFloat(), bws.budget.limitAmount.toFloat()))
            spentEntries.add(BarEntry(idx.toFloat(), bws.spentAmount.toFloat()))
            labels.add(bws.budget.category.icon)
        }

        val budgetDataSet = BarDataSet(budgetEntries, "Budget").apply {
            color = Color.parseColor("#90CAF9")
            valueTextSize = 9f
        }
        val spentDataSet = BarDataSet(spentEntries, "Spent").apply {
            colors = budgets.take(6).map { bws ->
                when {
                    bws.percentage >= 100 -> Color.parseColor("#F44336")
                    bws.percentage >= 90 -> Color.parseColor("#FF9800")
                    else -> Color.parseColor("#66BB6A")
                }
            }
            valueTextSize = 9f
        }

        val groupSpace = 0.3f
        val barSpace = 0.03f
        val barWidth = 0.32f

        val barData = BarData(budgetDataSet, spentDataSet).apply {
            this.barWidth = barWidth
        }

        binding.barChartBudget.apply {
            data = barData
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.setCenterAxisLabels(true)
            xAxis.axisMinimum = 0f
            xAxis.axisMaximum = budgets.take(6).size.toFloat()
            groupBars(0f, groupSpace, barSpace)
            animateY(1000)
            invalidate()
        }
    }

    // ─── Line Chart: Monthly Trend ────────────────────────────────────────

    private fun setupLineChart(chart: LineChart) {
        chart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            legend.isEnabled = true
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textSize = 10f
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#EEEEEE")
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            isScaleXEnabled = true
        }
    }

    private fun updateTrendLineChart(trends: List<MonthlyTrend>) {
        if (trends.isEmpty()) {
            binding.lineChartTrend.clear()
            return
        }

        // Sort oldest → newest
        val sorted = trends.sortedWith(compareBy({ it.year }, { it.month }))

        val expenseEntries = sorted.mapIndexed { idx, t ->
            Entry(idx.toFloat(), t.totalExpense.toFloat())
        }
        val incomeEntries = sorted.mapIndexed { idx, t ->
            Entry(idx.toFloat(), t.totalIncome.toFloat())
        }
        val labels = sorted.map { "${MONTH_LABELS[it.month - 1]}'${it.year % 100}" }

        val expenseSet = LineDataSet(expenseEntries, "Expenses").apply {
            color = Color.parseColor("#EF5350")
            setCircleColor(Color.parseColor("#EF5350"))
            lineWidth = 2.5f
            circleRadius = 5f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            fillAlpha = 50
            fillColor = Color.parseColor("#EF5350")
            setDrawFilled(true)
        }
        val incomeSet = LineDataSet(incomeEntries, "Income").apply {
            color = Color.parseColor("#66BB6A")
            setCircleColor(Color.parseColor("#66BB6A"))
            lineWidth = 2.5f
            circleRadius = 5f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        binding.lineChartTrend.apply {
            data = LineData(expenseSet, incomeSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelCount = labels.size
            animateX(1000)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
