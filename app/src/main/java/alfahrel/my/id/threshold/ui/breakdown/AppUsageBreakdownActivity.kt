package alfahrel.my.id.threshold.ui.breakdown

import alfahrel.my.id.threshold.util.BaseActivity
import alfahrel.my.id.threshold.R
import alfahrel.my.id.threshold.util.TimeTools
import alfahrel.my.id.threshold.data.model.AppUsageStat
import alfahrel.my.id.threshold.data.repository.UsageRepository
import alfahrel.my.id.threshold.ui.widget.HourlyBarChartView
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class AppUsageBreakdownActivity : BaseActivity() {

    companion object {
        private const val EXTRA_PACKAGE_NAME = "packageName"
        private const val EXTRA_TOTAL_TIME = "totalTime"
        private const val EXTRA_START_TIMES = "startTimes"
        private const val EXTRA_SESSION_DURATIONS = "sessionDurations"
        private const val EXTRA_SELECTED_DATE_MS = "selectedDateMs"
        private const val EXTRA_HAS_TIMER = "hasTimer"
        private const val EXTRA_TIMER_LIMIT = "timerLimit"

        fun start(
            context: Context,
            stat: AppUsageStat,
            selectedDateMs: Long,
            hasTimer: Boolean,
            timerLimit: Int?
        ) {
            Intent(context, AppUsageBreakdownActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, stat.packageName)
                putExtra(EXTRA_TOTAL_TIME, stat.totalTime)
                putExtra(EXTRA_START_TIMES, stat.startTimes.toLongArray())
                putExtra(EXTRA_SESSION_DURATIONS, stat.sessionDurations.toLongArray())
                putExtra(EXTRA_SELECTED_DATE_MS, selectedDateMs)
                putExtra(EXTRA_HAS_TIMER, hasTimer)
                if (timerLimit != null) putExtra(EXTRA_TIMER_LIMIT, timerLimit)
                context.startActivity(this)
            }
        }
    }

    private lateinit var usageRepository: UsageRepository
    private lateinit var stat: AppUsageStat
    private var selectedDateMs: Long = 0L
    private var hasTimer: Boolean = false
    private var timerLimit: Int? = null
    private var usageToday: Long? = null

    private lateinit var toolbar: Toolbar
    private lateinit var ivAppIcon: ImageView
    private lateinit var tvAppName: TextView
    private lateinit var tvPackageName: TextView
    private lateinit var tvScreenTime: TextView
    private lateinit var tvSessions: TextView
    private lateinit var timerCard: View
    private lateinit var tvTimerStatus: TextView
    private lateinit var tvTimerLimit: TextView
    private lateinit var tvTimerUsed: TextView
    private lateinit var timerProgress: LinearProgressIndicator
    private lateinit var ivTimerIcon: ImageView
    private lateinit var tvHourlyTitle: TextView
    private lateinit var hourlyChartLoading: View
    private lateinit var hourlyBarChart: HourlyBarChartView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_app_usage_breakdown)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return finish()
        val totalTime = intent.getLongExtra(EXTRA_TOTAL_TIME, 0L)
        val startTimes = intent.getLongArrayExtra(EXTRA_START_TIMES)?.toList() ?: emptyList()
        val sessionDurations = intent.getLongArrayExtra(EXTRA_SESSION_DURATIONS)?.toList() ?: emptyList()
        stat = AppUsageStat(packageName, totalTime, startTimes, sessionDurations)
        selectedDateMs = intent.getLongExtra(EXTRA_SELECTED_DATE_MS, System.currentTimeMillis())
        hasTimer = intent.getBooleanExtra(EXTRA_HAS_TIMER, false)
        timerLimit = if (intent.hasExtra(EXTRA_TIMER_LIMIT)) intent.getIntExtra(EXTRA_TIMER_LIMIT, 0) else null

        usageRepository = UsageRepository(this)

        bindViews()
        setupToolbar()
        loadAppInfo()
        loadHourlyBreakdown()
        loadUsageToday()
    }

    private fun bindViews() {
        toolbar = findViewById(R.id.toolbar)
        ivAppIcon = findViewById(R.id.ivAppIcon)
        tvAppName = findViewById(R.id.tvAppName)
        tvPackageName = findViewById(R.id.tvPackageName)
        tvScreenTime = findViewById(R.id.tvScreenTime)
        tvSessions = findViewById(R.id.tvSessions)
        timerCard = findViewById(R.id.timerCard)
        tvTimerStatus = findViewById(R.id.tvTimerStatus)
        tvTimerLimit = findViewById(R.id.tvTimerLimit)
        tvTimerUsed = findViewById(R.id.tvTimerUsed)
        timerProgress = findViewById(R.id.timerProgress)
        ivTimerIcon = findViewById(R.id.ivTimerIcon)
        tvHourlyTitle = findViewById(R.id.tvHourlyTitle)
        hourlyChartLoading = findViewById(R.id.hourlyChartLoading)
        hourlyBarChart = findViewById(R.id.hourlyBarChart)

        tvScreenTime.text = TimeTools.formatTime(stat.totalTime)
        tvSessions.text = "${stat.sessionCount} sessions"
        tvPackageName.text = stat.packageName
        tvAppName.text = stat.packageName.split(".").last()
        tvHourlyTitle.text = if (isToday()) "Hourly Breakdown (Today)" else "Hourly Breakdown"

        if (hasTimer && timerLimit != null) {
            timerCard.visibility = View.VISIBLE
            updateTimerCard()
        } else {
            timerCard.visibility = View.GONE
        }

        findViewById<View>(R.id.btnSetTimer).setOnClickListener {
            showTimerBottomSheet()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadAppInfo() {
        lifecycleScope.launch {
            val info = usageRepository.getAppInfo(stat.packageName)
            if (info != null) {
                tvAppName.text = info.appName
                if (info.iconBytes.isNotEmpty()) {
                    val bitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeByteArray(info.iconBytes, 0, info.iconBytes.size)
                    }
                    ivAppIcon.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun loadUsageToday() {
        lifecycleScope.launch {
            usageToday = withContext(Dispatchers.IO) {
                try {
                    val usageStatsManager = getSystemService(USAGE_STATS_SERVICE)
                            as UsageStatsManager
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val stats = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        cal.timeInMillis,
                        System.currentTimeMillis()
                    )
                    stats.filter { it.packageName == stat.packageName }
                        .sumOf { it.totalTimeInForeground }
                } catch (e: Exception) {
                    null
                }
            }
            if (hasTimer && timerLimit != null) updateTimerCard()
        }
    }

    private fun loadHourlyBreakdown() {
        hourlyChartLoading.visibility = View.VISIBLE
        val scrollView = findViewById<HorizontalScrollView>(R.id.hourlyScrollView)
        scrollView.visibility = View.GONE

        lifecycleScope.launch {
            val breakdown: Map<Int, Int> = withContext(Dispatchers.IO) {
                if (isToday()) getTodayHourlyBreakdown() else stat.getHourlyBreakdown()
            }

            hourlyChartLoading.visibility = View.GONE
            scrollView.visibility = View.VISIBLE

            val widthPx = (1152 * resources.displayMetrics.density).toInt()
            hourlyBarChart.layoutParams = hourlyBarChart.layoutParams.also { it.width = widthPx }
            Log.d("HourlyChart", "layoutParams width set to: $widthPx")
            hourlyBarChart.requestLayout()

            hourlyBarChart.viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        hourlyBarChart.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        Log.d("HourlyChart", "onGlobalLayout width: ${hourlyBarChart.width}, height: ${hourlyBarChart.height}")
                        hourlyBarChart.setData(breakdown, isToday())
                        if (isToday()) {
                            val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                            val barWidthPx = (48 * resources.displayMetrics.density).toInt()
                            val offset = ((nowHour * barWidthPx) - barWidthPx * 3).coerceAtLeast(0)
                            scrollView.scrollTo(offset, 0)
                        }
                    }
                }
            )
        }
    }

    private fun getTodayHourlyBreakdown(): Map<Int, Int> {
        return try {
            val usageStatsManager = getSystemService(USAGE_STATS_SERVICE)
                    as UsageStatsManager
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val hourly = (0..23).associateWith { 0 }.toMutableMap()
            val events = usageStatsManager.queryEvents(cal.timeInMillis, System.currentTimeMillis())
            var lastFg = -1L
            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)
                if (event.packageName != stat.packageName) continue
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> lastFg = event.timeStamp
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (lastFg > 0) {
                            val hour = Calendar.getInstance()
                                .apply { timeInMillis = lastFg }
                                .get(Calendar.HOUR_OF_DAY)
                            hourly[hour] = (hourly[hour] ?: 0) + ((event.timeStamp - lastFg) / 1000).toInt()
                            lastFg = -1L
                        }
                    }
                }
            }
            if (lastFg > 0) {
                val hour = Calendar.getInstance()
                    .apply { timeInMillis = lastFg }
                    .get(Calendar.HOUR_OF_DAY)
                hourly[hour] = (hourly[hour] ?: 0) + ((System.currentTimeMillis() - lastFg) / 1000).toInt()
            }
            hourly
        } catch (e: Exception) {
            (0..23).associateWith { 0 }
        }
    }

    private fun updateTimerCard() {
        val limit = timerLimit ?: return
        val limitMs = limit * 60 * 1000L
        val usedMs = usageToday ?: stat.totalTime
        val progress = (usedMs.toFloat() / limitMs).coerceIn(0f, 1f)
        val isOver = usedMs >= limitMs

        val colorError = getColor(com.google.android.material.R.color.design_default_color_error)
        val colorPrimary = getColor(com.google.android.material.R.color.material_dynamic_primary40)
        val statusColor = if (isOver) colorError else colorPrimary

        tvTimerStatus.text = if (isOver) "Limit Reached" else "Daily Timer"
        tvTimerLimit.text = "$limit min limit"
        tvTimerLimit.setTextColor(statusColor)
        tvTimerUsed.text = TimeTools.formatTime(usageToday ?: 0L)
        timerProgress.progress = (progress * 100).toInt()
        timerProgress.setIndicatorColor(statusColor)
        ivTimerIcon.setImageResource(
            if (isOver) R.drawable.ic_rounded_timer_24 else R.drawable.ic_rounded_hourglass_top_24
        )
        ivTimerIcon.setColorFilter(statusColor)
    }

    private fun showTimerBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_app_timer, null)
        dialog.setContentView(view)

        var selectedMinutes = timerLimit ?: 30

        val tvTitle = view.findViewById<TextView>(R.id.tvTimerSheetTitle)
        val tvLimitLabel = view.findViewById<TextView>(R.id.tvLimitMinutes)
        val tvUsedToday = view.findViewById<TextView>(R.id.tvUsedToday)
        val slider = view.findViewById<Slider>(R.id.sliderTimer)
        val btnRemove = view.findViewById<MaterialButton>(R.id.btnRemoveTimer)
        val btnSet = view.findViewById<MaterialButton>(R.id.btnSetTimerConfirm)

        tvTitle.text = "Set Timer for ${tvAppName.text}"
        tvLimitLabel.text = "$selectedMinutes minutes"
        tvUsedToday.text = TimeTools.formatTime(usageToday ?: 0L)

        slider.value = selectedMinutes.toFloat()
        slider.valueFrom = 5f
        slider.valueTo = 300f
        slider.stepSize = 5f
        slider.addOnChangeListener { _, value, _ ->
            selectedMinutes = value.toInt()
            tvLimitLabel.text = "$selectedMinutes minutes"
        }

        btnRemove.visibility = if (hasTimer) View.VISIBLE else View.GONE
        btnRemove.setOnClickListener {
            usageRepository.removeAppTimer(stat.packageName)
            hasTimer = false
            timerLimit = null
            timerCard.visibility = View.GONE
            dialog.dismiss()
            Snackbar.make(findViewById(R.id.root), "Timer removed", Snackbar.LENGTH_SHORT).show()
        }

        btnSet.setOnClickListener {
            usageRepository.setAppTimer(stat.packageName, selectedMinutes)
            hasTimer = true
            timerLimit = selectedMinutes
            timerCard.visibility = View.VISIBLE
            updateTimerCard()
            dialog.dismiss()
            Snackbar.make(
                findViewById(R.id.root),
                "Timer set to $selectedMinutes minutes",
                Snackbar.LENGTH_SHORT
            ).show()
        }

        dialog.show()
    }

    private fun isToday(): Boolean = TimeTools.isToday(selectedDateMs)
}