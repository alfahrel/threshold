package alfahrel.my.id.threshold.ui.home

import alfahrel.my.id.threshold.ui.sheet.AboutSheet
import alfahrel.my.id.threshold.ui.sheet.AllPermissionsSheet
import alfahrel.my.id.threshold.ui.sheet.AppOptionsSheet
import alfahrel.my.id.threshold.ui.sheet.AppTimerSheet
import alfahrel.my.id.threshold.ui.settings.AppTimersActivity
import alfahrel.my.id.threshold.util.BaseActivity
import alfahrel.my.id.threshold.ui.sheet.HomeMenuSheet
import alfahrel.my.id.threshold.ui.settings.IgnoredAppsActivity
import alfahrel.my.id.threshold.ui.sheet.PermissionHelpSheet
import alfahrel.my.id.threshold.ui.sheet.PermissionsStatusSheet
import alfahrel.my.id.threshold.R
import alfahrel.my.id.threshold.data.model.AppUsageStat
import alfahrel.my.id.threshold.data.repository.UsageRepository
import alfahrel.my.id.threshold.ui.breakdown.AppUsageBreakdownActivity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.loadingindicator.LoadingIndicator
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class MainActivity : BaseActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingIndicator: LoadingIndicator

    private lateinit var adapter: HomeAdapter
    private lateinit var usageRepository: UsageRepository

    private var selectedDateMs: Long = todayStartMs()
    private var earliestDateMs: Long = selectedDateMs - 30L * 24 * 60 * 60 * 1000
    private var dataLoaded: Boolean = false
    private var returningFromPermissions: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        appBarLayout = findViewById(R.id.appBarLayout)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        recyclerView = findViewById(R.id.recyclerView)
        loadingIndicator = findViewById(R.id.loadingIndicator)

        setSupportActionBar(toolbar)

        usageRepository = UsageRepository(this)

        adapter = HomeAdapter(
            onAppClick = { stat -> navigateToBreakdown(stat) },
            onAppLongClick = { stat -> showAppOptionsSheet(stat) },
            onDaySelected = { dayMs ->
                selectedDateMs = dayMs
                loadData()
            },
            onGrantPermissions = { openPermissionsFlow() },
            onPermissionHelp = { showPermissionHelpSheet() }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            checkPermissionsAndLoad()
        }

        checkPermissionsAndLoad()
    }

    override fun onResume() {
        super.onResume()
        if (returningFromPermissions) {
            returningFromPermissions = false
            checkPermissionsAndLoad()
        } else if (!dataLoaded) {
            checkPermissionsAndLoad()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_calendar -> {
                showDatePicker()
                true
            }
            R.id.action_more -> {
                showMenuSheet()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkPermissionsAndLoad() {
        lifecycleScope.launch {
            val hasUsageStats = usageRepository.hasUsageStatsPermission()
            if (!hasUsageStats) {
                swipeRefresh.isRefreshing = false
                showAllPermissionsSheet()
                adapter.showNoPermissionState()
                return@launch
            }
            earliestDateMs = usageRepository.getEarliestDataTimestamp()
            loadData()
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            setLoading(true)

            val permissions = usageRepository.getAllPermissions()
            val stats = usageRepository.getStatsByDateMs(selectedDateMs)
            val ignoredPackages = usageRepository.getIgnoredPackages()
            val appTimers = usageRepository.getAppTimers()
            val weeklyStats = usageRepository.getWeeklyStats()
            val dailyAvgMs = usageRepository.getAverageDailyUsage()

            val filteredStats = stats
                .filter { it.totalTime > 0 && !ignoredPackages.contains(it.packageName) }
                .sortedByDescending { it.totalTime }

            adapter.submitData(
                selectedDateMs = selectedDateMs,
                stats = filteredStats,
                appTimers = appTimers,
                weeklyStats = weeklyStats,
                dailyAvgMs = dailyAvgMs,
                permissions = permissions
            )

            dataLoaded = true
            setLoading(false)
            swipeRefresh.isRefreshing = false

            filteredStats.forEach { stat ->
                val info = usageRepository.getAppInfo(stat.packageName)
                adapter.submitAppInfo(stat.packageName, info)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        recyclerView.visibility = if (loading) View.GONE else View.VISIBLE
    }

    private fun showDatePicker() {
        val constraintsBuilder = CalendarConstraints.Builder()
            .setStart(earliestDateMs)
            .setEnd(todayStartMs())
            .setValidator(DateValidatorPointBackward.now())

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .setSelection(selectedDateMs)
            .setCalendarConstraints(constraintsBuilder.build())
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            selectedDateMs = selection
            loadData()
        }

        picker.show(supportFragmentManager, "date_picker")
    }

    private fun showMenuSheet() {
        HomeMenuSheet(
            onIgnoredApps = { navigateToIgnoredApps() },
            onAppTimers = { navigateToAppTimers() },
            onCheckPermissions = { showPermissionsStatusSheet() },
            onAbout = { showAboutSheet() }
        ).show(supportFragmentManager, "menu_sheet")
    }

    private fun showAllPermissionsSheet() {
        AllPermissionsSheet(
            onGrant = { openPermissionsFlow() }
        ).show(supportFragmentManager, "all_permissions_sheet")
    }

    private fun showPermissionsStatusSheet() {
        lifecycleScope.launch {
            val permissions = usageRepository.getAllPermissions()
            PermissionsStatusSheet(
                permissions = permissions,
                onGrant = { openPermissionsFlow() }
            ).show(supportFragmentManager, "permissions_status_sheet")
        }
    }

    private fun showPermissionHelpSheet() {
        PermissionHelpSheet(
            onTryAgain = { openPermissionsFlow() }
        ).show(supportFragmentManager, "permission_help_sheet")
    }

    private fun showAboutSheet() {
        AboutSheet().show(supportFragmentManager, "about_sheet")
    }

    private fun openPermissionsFlow() {
        returningFromPermissions = true
        lifecycleScope.launch {
            usageRepository.requestNextMissingPermission(this@MainActivity)
        }
    }

    private fun navigateToBreakdown(stat: AppUsageStat) {
        val appTimers = adapter.getAppTimers()
        val hasTimer = appTimers.containsKey(stat.packageName)
        val timerLimit = appTimers[stat.packageName]
        AppUsageBreakdownActivity.Companion.start(this, stat, selectedDateMs, hasTimer, timerLimit)
    }

    private fun showAppOptionsSheet(stat: AppUsageStat) {
        val appTimers = adapter.getAppTimers()
        val hasTimer = appTimers.containsKey(stat.packageName)
        val timerLimit = appTimers[stat.packageName]
        lifecycleScope.launch {
            val info = usageRepository.getAppInfo(stat.packageName)
            AppOptionsSheet(
                stat = stat,
                appInfo = info,
                hasTimer = hasTimer,
                timerLimit = timerLimit,
                onSetTimer = {
                    AppTimerSheet(
                        stat = stat,
                        appInfo = info,
                        currentLimit = timerLimit,
                        onSave = { limitMinutes ->
                            usageRepository.setAppTimer(stat.packageName, limitMinutes)
                            dataLoaded = false
                            checkPermissionsAndLoad()
                        },
                        onRemove = {
                            usageRepository.removeAppTimer(stat.packageName)
                            dataLoaded = false
                            checkPermissionsAndLoad()
                        }
                    ).show(supportFragmentManager, "timer_sheet")
                },
                onIgnore = {
                    val ignored = usageRepository.getIgnoredPackages().toMutableSet()
                    ignored.add(stat.packageName)
                    usageRepository.setIgnoredPackages(ignored)
                    dataLoaded = false
                    checkPermissionsAndLoad()
                }
            ).show(supportFragmentManager, "app_options_sheet")
        }
    }

    private fun navigateToIgnoredApps() {
        startActivity(Intent(this, IgnoredAppsActivity::class.java))
    }

    private fun navigateToAppTimers() {
        startActivity(Intent(this, AppTimersActivity::class.java))
    }

    private fun todayStartMs(): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}