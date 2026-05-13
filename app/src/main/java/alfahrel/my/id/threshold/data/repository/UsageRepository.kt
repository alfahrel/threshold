package alfahrel.my.id.threshold.data.repository

import alfahrel.my.id.threshold.receiver.AdminReceiver
import alfahrel.my.id.threshold.service.AppBlockerAccessibilityService
import alfahrel.my.id.threshold.data.model.AppInfo
import alfahrel.my.id.threshold.data.model.AppUsageStat
import android.app.Activity
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.TimeZone
import kotlin.collections.iterator

class UsageRepository(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private val appInfoCache = mutableMapOf<String, AppInfo?>()

    data class AllPermissions(
        val usageStats: Boolean,
        val accessibility: Boolean,
        val overlay: Boolean,
        val deviceAdmin: Boolean
    ) {
        val allGranted get() = usageStats && accessibility && overlay && deviceAdmin
        val missingCount get() = listOf(usageStats, accessibility, overlay, deviceAdmin).count { !it }
    }

    suspend fun getAllPermissions(): AllPermissions = withContext(Dispatchers.IO) {
        AllPermissions(
            usageStats = hasUsageStatsPermission(),
            accessibility = hasAccessibilityPermission(),
            overlay = hasOverlayPermission(),
            deviceAdmin = hasDeviceAdminPermission()
        )
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasAccessibilityPermission(): Boolean {
        val serviceName = ComponentName(
            context,
            AppBlockerAccessibilityService::class.java
        ).flattenToString()
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled == 1 && enabledServices.contains(serviceName)
    }

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    fun hasDeviceAdminPermission(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, AdminReceiver::class.java)
        return dpm.isAdminActive(component)
    }

    fun requestUsageStatsPermission() {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    fun requestAccessibilityPermission() {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    fun requestOverlayPermission() {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    fun requestDeviceAdminPermission(activity: Activity) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(context, AdminReceiver::class.java)
            )
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enable admin to prevent app uninstallation"
            )
        }
        activity.startActivityForResult(intent, 1001)
    }

    suspend fun requestNextMissingPermission(activity: Activity) {
        val p = getAllPermissions()
        when {
            !p.usageStats -> requestUsageStatsPermission()
            !p.accessibility -> requestAccessibilityPermission()
            !p.overlay -> requestOverlayPermission()
            !p.deviceAdmin -> requestDeviceAdminPermission(activity)
        }
    }

    suspend fun getStatsByDateMs(dateMs: Long): List<AppUsageStat> =
        withContext(Dispatchers.IO) {
            val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
                timeInMillis = dateMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            getStatsByTimestamps(start, end)
        }

    suspend fun getStatsByTimestamps(start: Long, end: Long): List<AppUsageStat> =
        withContext(Dispatchers.IO) {
            try {
                val ignored = getIgnoredPackages()
                val launcher = getDefaultLauncherPackage()

                val totalTimes = mutableMapOf<String, Long>()
                val sessionStarts = mutableMapOf<String, MutableList<Long>>()
                val sessionDurations = mutableMapOf<String, MutableList<Long>>()
                val lastForeground = mutableMapOf<String, Long>()

                val events = usageStatsManager.queryEvents(start, end)

                while (events.hasNextEvent()) {
                    val event = UsageEvents.Event()
                    events.getNextEvent(event)

                    val pkg = event.packageName
                    if (pkg in ignored || pkg == launcher || pkg == context.packageName) continue

                    when (event.eventType) {
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            lastForeground[pkg] = maxOf(event.timeStamp, start)
                            sessionStarts.getOrPut(pkg) { mutableListOf() }
                                .add(maxOf(event.timeStamp, start))
                            sessionDurations.getOrPut(pkg) { mutableListOf() }.add(-1L)
                        }

                        UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            val fgTime = lastForeground[pkg] ?: return@withContext emptyList()
                            val duration = minOf(event.timeStamp, end) - fgTime
                            if (duration > 0) {
                                totalTimes[pkg] = (totalTimes[pkg] ?: 0L) + duration
                                val durations = sessionDurations[pkg]
                                if (!durations.isNullOrEmpty()) {
                                    durations[durations.size - 1] = duration
                                }
                            }
                            lastForeground.remove(pkg)
                        }
                    }
                }

                for ((pkg, fgTime) in lastForeground) {
                    val duration = end - fgTime
                    if (duration > 0) {
                        totalTimes[pkg] = (totalTimes[pkg] ?: 0L) + duration
                        val durations = sessionDurations[pkg]
                        if (!durations.isNullOrEmpty()) {
                            durations[durations.size - 1] = duration
                        }
                    }
                }

                totalTimes.map { (pkg, total) ->
                    AppUsageStat(
                        packageName = pkg,
                        totalTime = total,
                        startTimes = sessionStarts[pkg] ?: mutableListOf(),
                        sessionDurations = sessionDurations[pkg] ?: mutableListOf()
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun getWeeklyStats(): Map<Long, Long> = withContext(Dispatchers.IO) {
        val result = linkedMapOf<Long, Long>()
        val now = Calendar.getInstance(TimeZone.getDefault())
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
                timeInMillis = now.timeInMillis
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayStart = cal.timeInMillis
            val stats = getStatsByDateMs(dayStart)
            result[dayStart] = stats.fold(0L) { acc, s -> acc + s.totalTime }
        }
        result
    }

    suspend fun getAverageDailyUsage(): Long = withContext(Dispatchers.IO) {
        val now = Calendar.getInstance(TimeZone.getDefault())
        var totalMs = 0L
        var totalDays = 0
        for (i in 13 downTo 0) {
            val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
                timeInMillis = now.timeInMillis
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val stats = getStatsByDateMs(cal.timeInMillis)
            val dayTotal = stats.fold(0L) { acc, s -> acc + s.totalTime }
            if (dayTotal > 0) {
                totalMs += dayTotal
                totalDays++
            }
        }
        if (totalDays > 0) totalMs / totalDays else 0L
    }

    suspend fun getEarliestDataTimestamp(): Long = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                thirtyDaysAgo,
                now
            )
            var earliest = now
            for (stat in stats) {
                if (stat.firstTimeStamp > 0 && stat.firstTimeStamp < earliest) {
                    earliest = stat.firstTimeStamp
                }
            }
            if (earliest < now) earliest else thirtyDaysAgo
        } catch (e: Exception) {
            System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        }
    }

    suspend fun getAppInfo(packageName: String): AppInfo? = withContext(Dispatchers.IO) {
        appInfoCache[packageName]?.let { return@withContext it }
        try {
            val pm = context.packageManager
            val ai = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(packageName, 0)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                return@withContext null
            }
            val name = pm.getApplicationLabel(ai).toString()
            val iconBytes = try {
                drawableToByteArray(pm.getApplicationIcon(packageName))
            } catch (e: Exception) {
                ByteArray(0)
            }
            val info = AppInfo(packageName, name, iconBytes)
            appInfoCache[packageName] = info
            info
        } catch (e: Exception) {
            null
        }
    }

    fun getIgnoredPackages(): Set<String> {
        val prefs = context.getSharedPreferences("usage_stats_prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("ignored_packages", emptySet()) ?: emptySet()
    }

    fun setIgnoredPackages(packages: Set<String>) {
        context.getSharedPreferences("usage_stats_prefs", Context.MODE_PRIVATE)
            .edit().putStringSet("ignored_packages", packages).apply()
    }

    fun getAppTimers(): Map<String, Int> {
        val prefs = context.getSharedPreferences("app_timers", Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, value) ->
            if (value is Int) key to value else null
        }.toMap()
    }

    fun setAppTimer(packageName: String, limitMinutes: Int) {
        context.getSharedPreferences("app_timers", Context.MODE_PRIVATE)
            .edit().putInt(packageName, limitMinutes).apply()
    }

    fun removeAppTimer(packageName: String) {
        context.getSharedPreferences("app_timers", Context.MODE_PRIVATE)
            .edit().remove(packageName).apply()
    }

    private fun getDefaultLauncherPackage(): String? {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            context.packageManager
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }

    private fun drawableToByteArray(drawable: Drawable): ByteArray {
        val size = 192
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val src = drawable.bitmap
            if (src.width != size || src.height != size) {
                Bitmap.createScaledBitmap(src, size, size, true)
            } else {
                src
            }
        } else {
            val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else size
            val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else size
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val scale = minOf(size.toFloat() / w, size.toFloat() / h)
            val sw = (w * scale).toInt()
            val sh = (h * scale).toInt()
            val left = (size - sw) / 2
            val top = (size - sh) / 2
            drawable.setBounds(left, top, left + sw, top + sh)
            drawable.draw(canvas)
            bmp
        }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}