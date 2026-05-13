package alfahrel.my.id.threshold.ui.home

import alfahrel.my.id.threshold.R
import alfahrel.my.id.threshold.util.TimeTools
import alfahrel.my.id.threshold.data.model.AppInfo
import alfahrel.my.id.threshold.data.model.AppUsageStat
import alfahrel.my.id.threshold.data.repository.UsageRepository
import alfahrel.my.id.threshold.ui.widget.WeeklyBarChartView
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class HomeAdapter(
    private val onAppClick: (AppUsageStat) -> Unit,
    private val onAppLongClick: (AppUsageStat) -> Unit,
    private val onDaySelected: (Long) -> Unit,
    private val onGrantPermissions: () -> Unit,
    private val onPermissionHelp: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var selectedDateMs: Long = 0L
    private var stats: List<AppUsageStat> = emptyList()
    private var appInfoMap: Map<String, AppInfo?> = emptyMap()
    private var appTimers: Map<String, Int> = emptyMap()
    private var weeklyStats: Map<Long, Long> = emptyMap()
    private var dailyAvgMs: Long = 0L
    private var permissions: UsageRepository.AllPermissions? = null
    private var showNoPermission: Boolean = false

    fun getAppTimers(): Map<String, Int> = appTimers

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP_TOP = 1
        private const val TYPE_APP_MIDDLE = 2
        private const val TYPE_APP_BOTTOM = 3
        private const val TYPE_APP_FULL = 4
        private const val TYPE_EMPTY = 5
        private const val TYPE_NO_PERMISSION = 6
    }

    fun submitData(
        selectedDateMs: Long,
        stats: List<AppUsageStat>,
        appTimers: Map<String, Int>,
        weeklyStats: Map<Long, Long>,
        dailyAvgMs: Long,
        permissions: UsageRepository.AllPermissions
    ) {
        this.selectedDateMs = selectedDateMs
        this.stats = stats
        this.appTimers = appTimers
        this.weeklyStats = weeklyStats
        this.dailyAvgMs = dailyAvgMs
        this.permissions = permissions
        this.showNoPermission = false
        notifyDataSetChanged()
    }

    fun submitAppInfo(packageName: String, info: AppInfo?) {
        val index = stats.indexOfFirst { it.packageName == packageName }
        if (index == -1) return
        appInfoMap = appInfoMap.toMutableMap().also { it[packageName] = info }
        notifyItemChanged(index + 1)
    }

    fun showNoPermissionState() {
        showNoPermission = true
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        if (showNoPermission) return 1
        return 1 + if (stats.isEmpty()) 1 else stats.size
    }

    override fun getItemViewType(position: Int): Int {
        if (showNoPermission) return TYPE_NO_PERMISSION
        if (position == 0) return TYPE_HEADER
        if (stats.isEmpty()) return TYPE_EMPTY
        val appIndex = position - 1
        val total = stats.size
        return when {
            total == 1 -> TYPE_APP_FULL
            total == 2 && appIndex == 0 -> TYPE_APP_TOP
            total == 2 && appIndex == 1 -> TYPE_APP_BOTTOM
            appIndex == 0 -> TYPE_APP_TOP
            appIndex == total - 1 -> TYPE_APP_BOTTOM
            else -> TYPE_APP_MIDDLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_home_header, parent, false)
            )
            TYPE_APP_TOP -> AppViewHolder(
                inflater.inflate(R.layout.item_app_usage_top, parent, false)
            )
            TYPE_APP_MIDDLE -> AppViewHolder(
                inflater.inflate(R.layout.item_app_usage_middle, parent, false)
            )
            TYPE_APP_BOTTOM -> AppViewHolder(
                inflater.inflate(R.layout.item_app_usage_bottom, parent, false)
            )
            TYPE_APP_FULL -> AppViewHolder(
                inflater.inflate(R.layout.item_app_usage_full, parent, false)
            )
            TYPE_EMPTY -> EmptyViewHolder(
                inflater.inflate(R.layout.item_empty_day, parent, false)
            )
            TYPE_NO_PERMISSION -> NoPermissionViewHolder(
                inflater.inflate(R.layout.item_no_permission, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(
                selectedDateMs = selectedDateMs,
                stats = stats,
                appTimers = appTimers,
                weeklyStats = weeklyStats,
                dailyAvgMs = dailyAvgMs,
                permissions = permissions,
                onDaySelected = onDaySelected,
                onGrantPermissions = onGrantPermissions,
                onPermissionHelp = onPermissionHelp
            )
            is AppViewHolder -> {
                val stat = stats[position - 1]
                holder.bind(
                    stat = stat,
                    appInfo = appInfoMap[stat.packageName],
                    hasTimer = appTimers.containsKey(stat.packageName),
                    onClick = { onAppClick(stat) },
                    onLongClick = { onAppLongClick(stat) }
                )
            }
            is EmptyViewHolder -> holder.bind()
            is NoPermissionViewHolder -> holder.bind(onGrantPermissions)
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val tvDateLabel: TextView = view.findViewById(R.id.tvDateLabel)
        private val tvTotalUsage: TextView = view.findViewById(R.id.tvTotalUsage)
        private val tvDailyAvg: TextView = view.findViewById(R.id.tvDailyAvg)
        private val layoutDailyAvg: View = view.findViewById(R.id.layoutDailyAvg)
        private val weeklyBarChart: WeeklyBarChartView = view.findViewById(R.id.weeklyBarChart)
        private val tvAppsHeader: TextView = view.findViewById(R.id.tvAppsHeader)
        private val missingPermissionsCard: MaterialCardView =
            view.findViewById(R.id.missingPermissionsCard)
        private val tvMissingPermissionsSubtitle: TextView =
            view.findViewById(R.id.tvMissingPermissionsSubtitle)
        private val missingPermissionsList: LinearLayout =
            view.findViewById(R.id.missingPermissionsList)
        private val btnGrantPermissions: View = view.findViewById(R.id.btnGrantPermissions)
        private val btnPermissionHelp: View = view.findViewById(R.id.btnPermissionHelp)

        fun bind(
            selectedDateMs: Long,
            stats: List<AppUsageStat>,
            appTimers: Map<String, Int>,
            weeklyStats: Map<Long, Long>,
            dailyAvgMs: Long,
            permissions: UsageRepository.AllPermissions?,
            onDaySelected: (Long) -> Unit,
            onGrantPermissions: () -> Unit,
            onPermissionHelp: () -> Unit
        ) {
            tvDateLabel.text = TimeTools.getDateLabel(selectedDateMs)

            val totalMs = stats.fold(0L) { acc, s -> acc + s.totalTime }
            tvTotalUsage.text = if (stats.isEmpty()) "—" else TimeTools.formatTime(totalMs)

            if (dailyAvgMs > 0) {
                layoutDailyAvg.visibility = View.VISIBLE
                tvDailyAvg.text = TimeTools.formatTime(dailyAvgMs)
            } else {
                layoutDailyAvg.visibility = View.GONE
            }

            weeklyBarChart.setData(weeklyStats, selectedDateMs, onDaySelected)

            tvAppsHeader.visibility = if (stats.isNotEmpty()) View.VISIBLE else View.GONE

            permissions?.let { p ->
                if (!p.allGranted) {
                    missingPermissionsCard.visibility = View.VISIBLE
                    tvMissingPermissionsSubtitle.text =
                        "${p.missingCount} permission${if (p.missingCount > 1) "s" else ""} needed"

                    missingPermissionsList.removeAllViews()
                    val context = itemView.context
                    val missingItems = buildList {
                        if (!p.usageStats) add(Pair("Usage Stats", R.drawable.ic_rounded_bar_chart_24))
                        if (!p.accessibility) add(Pair("Accessibility", R.drawable.ic_rounded_accessibility_24))
                        if (!p.overlay) add(Pair("Display Overlay", R.drawable.ic_rounded_layers_24))
                        if (!p.deviceAdmin) add(Pair("Device Admin", R.drawable.ic_rounded_admin_panel_settings_24))
                    }
                    missingItems.forEach { (name, iconRes) ->
                        val row = LayoutInflater.from(context)
                            .inflate(R.layout.item_permission_row, missingPermissionsList, false)
                        row.findViewById<ImageView>(R.id.ivPermissionIcon).setImageResource(iconRes)
                        row.findViewById<TextView>(R.id.tvPermissionName).text = name
                        missingPermissionsList.addView(row)
                    }

                    btnGrantPermissions.setOnClickListener { onGrantPermissions() }
                    btnPermissionHelp.setOnClickListener { onPermissionHelp() }
                } else {
                    missingPermissionsCard.visibility = View.GONE
                }
            }
        }
    }

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val card: MaterialCardView = view.findViewById(R.id.cardAppUsage)
        private val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        private val timerBadge: View = view.findViewById(R.id.timerBadge)
        private val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        private val tvUsageTime: TextView = view.findViewById(R.id.tvUsageTime)
        private val tvSessionCount: TextView = view.findViewById(R.id.tvSessionCount)

        fun bind(
            stat: AppUsageStat,
            appInfo: AppInfo?,
            hasTimer: Boolean,
            onClick: () -> Unit,
            onLongClick: () -> Unit
        ) {
            if (appInfo != null && appInfo.iconBytes.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(
                    appInfo.iconBytes, 0, appInfo.iconBytes.size
                )
                ivAppIcon.setImageBitmap(bitmap)
            } else {
                ivAppIcon.setImageResource(R.drawable.ic_launcher_foreground)
            }

            tvAppName.text = appInfo?.appName ?: stat.packageName.split(".").last()
            tvUsageTime.text = TimeTools.formatTime(stat.totalTime)
            tvSessionCount.text = "${stat.sessionCount} sessions"
            timerBadge.visibility = if (hasTimer) View.VISIBLE else View.GONE

            card.setOnClickListener { onClick() }
            card.setOnLongClickListener {
                onLongClick()
                true
            }

            card.alpha = 0f
            card.translationY = 60f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay((adapterPosition * 50L).coerceAtMost(400L))
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {}
    }

    class NoPermissionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val btnGrant: View = view.findViewById(R.id.btnGrantPermissionsEmpty)

        fun bind(onGrantPermissions: () -> Unit) {
            btnGrant.setOnClickListener { onGrantPermissions() }
        }
    }
}