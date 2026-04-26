package com.alfahrel.threshold

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.util.*

class MainActivity: FlutterActivity() {
    private val CHANNEL = "usage_stats"
    
    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler {
            call, result ->
            when (call.method) {
                "hasPermission" -> {
                    result.success(hasUsageStatsPermission())
                }
                "requestPermission" -> {
                    openUsageAccessSettings()
                    result.success(null)
                }
                "hasAccessibilityPermission" -> {
                    result.success(isAccessibilityServiceEnabled())
                }
                "requestAccessibilityPermission" -> {
                    openAccessibilitySettings()
                    result.success(null)
                }
                "hasOverlayPermission" -> {
                    result.success(Settings.canDrawOverlays(this))
                }
                "requestOverlayPermission" -> {
                    openOverlaySettings()
                    result.success(null)
                }
                "hasDeviceAdminPermission" -> {
                    result.success(isDeviceAdminActive())
                }
                "requestDeviceAdminPermission" -> {
                    requestDeviceAdmin()
                    result.success(null)
                }
                "getStatsByTimestamps" -> {
                    val start = call.argument<Long>("start") ?: 0
                    val end = call.argument<Long>("end") ?: System.currentTimeMillis()
                    result.success(getUsageStats(start, end))
                }
                "getEarliestDataTimestamp" -> {
                    result.success(getEarliestDataTimestamp())
                }
                "getAppInfo" -> {
                    val packageName = call.argument<String>("packageName")
                    result.success(if (packageName != null) getAppInfo(packageName) else null)
                }
                "setIgnoredPackages" -> {
                    val packages = call.argument<List<String>>("packages")
                    if (packages != null) {
                        val prefs = getSharedPreferences("usage_stats_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putStringSet("ignored_packages", packages.toSet()).apply()
                        result.success(null)
                    } else {
                        result.error("INVALID_ARGUMENT", "packages cannot be null", null)
                    }
                }
                "getIgnoredPackages" -> {
                    val prefs = getSharedPreferences("usage_stats_prefs", Context.MODE_PRIVATE)
                    val packages = prefs.getStringSet("ignored_packages", setOf()) ?: setOf()
                    result.success(packages.toList())
                }
                "setAppTimer" -> {
                    val packageName = call.argument<String>("packageName")
                    val limitMinutes = call.argument<Int>("limitMinutes")
                    if (packageName != null && limitMinutes != null) {
                        setAppTimer(packageName, limitMinutes)
                        result.success(null)
                    } else {
                        result.error("INVALID_ARGUMENT", "Invalid arguments", null)
                    }
                }
                "getAppTimers" -> {
                    result.success(getAppTimers())
                }
                "removeAppTimer" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        removeAppTimer(packageName)
                        result.success(null)
                    } else {
                        result.error("INVALID_ARGUMENT", "packageName cannot be null", null)
                    }
                }
                "getAppUsageToday" -> {
                    val packageName = call.argument<String>("packageName")
                    result.success(if (packageName != null) getAppUsageToday(packageName) else null)
                }
                "getAppHourlyBreakdownToday" -> {
                    val packageName = call.argument<String>("packageName")
                    result.success(if (packageName != null) getAppHourlyBreakdownToday(packageName) else null)
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = ComponentName(this, AppBlockerAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val isEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        return isEnabled == 1 && enabledServices.contains(serviceName)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun isDeviceAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, AdminReceiver::class.java)
        return dpm.isAdminActive(componentName)
    }

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        val componentName = ComponentName(this, AdminReceiver::class.java)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
        intent.putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Enable admin to prevent app uninstallation"
        )
        startActivity(intent)
    }

    private fun getUsageStats(start: Long, end: Long): List<Map<String, Any>> {
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

            val prefs = getSharedPreferences("usage_stats_prefs", Context.MODE_PRIVATE)
            val ignoredPackages = prefs.getStringSet("ignored_packages", setOf()) ?: setOf()
            val launcherPackage = getDefaultLauncherPackage()

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                start,
                end
            )

            if (stats == null || stats.isEmpty()) return emptyList()

            val aggregatedStats = mutableMapOf<String, MutableMap<String, Any>>()

            for (stat in stats) {
                if (stat.totalTimeInForeground > 0) {
                    val packageName = stat.packageName
                    if (packageName in ignoredPackages ||
                        packageName == launcherPackage ||
                        packageName == "com.alfahrel.threshold") continue

                    if (aggregatedStats.containsKey(packageName)) {
                        val existing = aggregatedStats[packageName]!!
                        val totalTime = existing["totalTime"] as Long
                        existing["totalTime"] = totalTime + stat.totalTimeInForeground
                    } else {
                        aggregatedStats[packageName] = mutableMapOf(
                            "packageName" to packageName,
                            "totalTime" to stat.totalTimeInForeground,
                            "startTimes" to mutableListOf<Long>(),
                            "sessionDurations" to mutableListOf<Long>()
                        )
                    }
                }
            }

            try {
                val events = usageStatsManager.queryEvents(start, end)
                val sessionStarts = mutableMapOf<String, MutableList<Long>>()
                val sessionDurations = mutableMapOf<String, MutableList<Long>>()
                val lastForeground = mutableMapOf<String, Long>()

                while (events.hasNextEvent()) {
                    val event = android.app.usage.UsageEvents.Event()
                    events.getNextEvent(event)

                    when (event.eventType) {
                        android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            lastForeground[event.packageName] = event.timeStamp
                            sessionStarts.getOrPut(event.packageName) { mutableListOf<Long>() }
                                .add(event.timeStamp)
                            sessionDurations.getOrPut(event.packageName) { mutableListOf<Long>() }
                                .add(-1L)
                        }
                        android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            val fgTime = lastForeground[event.packageName]
                            if (fgTime != null) {
                                val duration = event.timeStamp - fgTime
                                val durations = sessionDurations[event.packageName]
                                if (durations != null && durations.isNotEmpty()) {
                                    durations[durations.size - 1] = duration
                                }
                                lastForeground.remove(event.packageName)
                            }
                        }
                    }
                }

                // Handle sessions still in foreground (app currently open)
                val now = System.currentTimeMillis()
                for ((pkg, fgTime) in lastForeground) {
                    val durations = sessionDurations[pkg]
                    if (durations != null && durations.isNotEmpty()) {
                        durations[durations.size - 1] = now - fgTime
                    }
                }

                for ((packageName, starts) in sessionStarts) {
                    if (aggregatedStats.containsKey(packageName)) {
                        aggregatedStats[packageName]!!["startTimes"] = starts
                        aggregatedStats[packageName]!!["sessionDurations"] =
                            sessionDurations[packageName] ?: mutableListOf<Long>()
                    }
                }
            } catch (e: Exception) {
                // ignored
            }

            return aggregatedStats.values.toList()

        } catch (e: Exception) {
            return emptyList()
        }
    }

    // Queries today's events directly to get accurate per-hour usage in seconds.
    // This is separate from getUsageStats because queryEvents has a limited lookback
    // window and can't reliably cover multi-day ranges like totalTime can.
    private fun getAppHourlyBreakdownToday(packageName: String): Map<String, Long> {
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val start = calendar.timeInMillis
            val end = System.currentTimeMillis()

            // Initialize all 24 hours to 0
            val hourlySeconds = mutableMapOf<String, Long>()
            for (i in 0..23) hourlySeconds[i.toString()] = 0L

            val events = usageStatsManager.queryEvents(start, end)
            var lastForeground = -1L

            while (events.hasNextEvent()) {
                val event = android.app.usage.UsageEvents.Event()
                events.getNextEvent(event)

                if (event.packageName != packageName) continue

                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        lastForeground = event.timeStamp
                    }
                    android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (lastForeground > 0) {
                            val durationMs = event.timeStamp - lastForeground
                            val hour = Calendar.getInstance().apply {
                                timeInMillis = lastForeground
                            }.get(Calendar.HOUR_OF_DAY)
                            val key = hour.toString()
                            hourlySeconds[key] = (hourlySeconds[key] ?: 0L) + (durationMs / 1000)
                            lastForeground = -1L
                        }
                    }
                }
            }

            // Handle currently open session
            if (lastForeground > 0) {
                val durationMs = end - lastForeground
                val hour = Calendar.getInstance().apply {
                    timeInMillis = lastForeground
                }.get(Calendar.HOUR_OF_DAY)
                val key = hour.toString()
                hourlySeconds[key] = (hourlySeconds[key] ?: 0L) + (durationMs / 1000)
            }

            return hourlySeconds
        } catch (e: Exception) {
            return (0..23).associate { it.toString() to 0L }
        }
    }

    private fun getDefaultLauncherPackage(): String? {
        return try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }

    private fun getEarliestDataTimestamp(): Long {
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)

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

            return if (earliest < now) earliest else thirtyDaysAgo
        } catch (e: Exception) {
            return System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        }
    }

    private fun getAppInfo(packageName: String): Map<String, Any>? {
        return try {
            val pm = packageManager

            val appInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(packageName, 0)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                return null
            }

            val appName = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName.split('.').last()
            }

            val iconBytes = try {
                val icon = pm.getApplicationIcon(packageName)
                drawableToByteArray(icon)
            } catch (e: Exception) {
                ByteArray(0)
            }

            mapOf(
                "appName" to appName,
                "icon" to iconBytes
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun drawableToByteArray(drawable: Drawable): ByteArray {
        try {
            val size = 192

            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                val sourceBitmap = drawable.bitmap
                if (sourceBitmap.width != size || sourceBitmap.height != size) {
                    Bitmap.createScaledBitmap(sourceBitmap, size, size, true)
                } else {
                    sourceBitmap
                }
            } else {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else size
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else size

                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                val scale = minOf(size.toFloat() / width, size.toFloat() / height)
                val scaledWidth = (width * scale).toInt()
                val scaledHeight = (height * scale).toInt()
                val left = (size - scaledWidth) / 2
                val top = (size - scaledHeight) / 2

                drawable.setBounds(left, top, left + scaledWidth, top + scaledHeight)
                drawable.draw(canvas)
                bitmap
            }

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            return stream.toByteArray()
        } catch (e: Exception) {
            return ByteArray(0)
        }
    }

    private fun setAppTimer(packageName: String, limitMinutes: Int) {
        val prefs = getSharedPreferences("app_timers", Context.MODE_PRIVATE)
        prefs.edit().putInt(packageName, limitMinutes).apply()
    }

    private fun getAppTimers(): Map<String, Int> {
        val prefs = getSharedPreferences("app_timers", Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, value) ->
            if (value is Int) key to value else null
        }.toMap()
    }

    private fun removeAppTimer(packageName: String) {
        val prefs = getSharedPreferences("app_timers", Context.MODE_PRIVATE)
        prefs.edit().remove(packageName).apply()
    }

    private fun getAppUsageToday(packageName: String): Int? {
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val start = calendar.timeInMillis
            val end = System.currentTimeMillis()

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                start,
                end
            )

            var totalTime = 0L
            for (stat in stats) {
                if (stat.packageName == packageName) {
                    totalTime += stat.totalTimeInForeground
                }
            }

            return totalTime.toInt()
        } catch (e: Exception) {
            return null
        }
    }
}