package com.alfahrel.threshold

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import es.antonborri.home_widget.HomeWidgetPlugin
import org.json.JSONArray
import java.util.Calendar

class UsageStatsWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_REFRESH = "com.alfahrel.threshold.REFRESH_WIDGET"
        private const val ACTION_OPEN_APP = "com.alfahrel.threshold.OPEN_APP"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_CONFIGURATION_CHANGED -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, UsageStatsWidget::class.java)
                )
                onUpdate(context, appWidgetManager, ids)
            }
            ACTION_REFRESH -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, UsageStatsWidget::class.java)
                )
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
            ACTION_OPEN_APP -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        }
    }

    private fun getIgnoredPackages(context: Context): Set<String> {
        val widgetData = HomeWidgetPlugin.getData(context)
        val raw = widgetData.getString("ignored_packages", null) ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val isSmall = minWidth < 110 || minHeight < 110

            val layoutId = if (isSmall) R.layout.usage_widget_small else R.layout.usage_widget
            val views = RemoteViews(context.packageName, layoutId)
            val usageData = getUsageStats(context)

            populateViews(context, views, usageData, isSmall)
            appWidgetManager.updateAppWidget(appWidgetId, views)

        } catch (e: Exception) {
            android.util.Log.e("UsageWidget", "updateAppWidget crashed: ${e.message}", e)
        }
    }

    private fun populateViews(
        context: Context,
        views: RemoteViews,
        usageData: UsageData,
        isSmall: Boolean
    ) {
        views.setTextViewText(R.id.total_time, formatTime(usageData.totalTime))

        if (!isSmall) {
            val containers = listOf(
                R.id.app1_container,
                R.id.app2_container,
                R.id.app3_container
            )
            for (i in containers.indices) {
                val visible = i < usageData.topApps.size
                views.setInt(
                    containers[i], "setVisibility",
                    if (visible) android.view.View.VISIBLE else android.view.View.GONE
                )
                if (visible) {
                    updateAppItem(context, views, usageData.topApps[i], i + 1)
                }
            }

        }

        // Refresh button
        val refreshIntent = Intent(context, UsageStatsWidget::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, 0, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent)

        // Tap widget to open app
        val openAppIntent = Intent(context, UsageStatsWidget::class.java).apply {
            action = ACTION_OPEN_APP
        }
        val openAppPendingIntent = PendingIntent.getBroadcast(
            context, 1, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, openAppPendingIntent)
    }

    private fun updateAppItem(
        context: Context,
        views: RemoteViews,
        app: AppInfo,
        position: Int
    ) {
        val nameId = when (position) {
            1 -> R.id.app1_name
            2 -> R.id.app2_name
            else -> R.id.app3_name
        }
        val timeId = when (position) {
            1 -> R.id.app1_time
            2 -> R.id.app2_time
            else -> R.id.app3_time
        }
        val iconId = when (position) {
            1 -> R.id.app1_icon
            2 -> R.id.app2_icon
            else -> R.id.app3_icon
        }

        views.setTextViewText(nameId, app.name)
        views.setTextViewText(timeId, formatTime(app.time))

        try {
            val icon = context.packageManager.getApplicationIcon(app.packageName)
            val bitmap = drawableToBitmap(icon)
            views.setImageViewBitmap(iconId, bitmap)
        } catch (e: Exception) {
            android.util.Log.e("UsageWidget", "Failed to set icon for ${app.packageName}: ${e.message}")
        }
    }

    private fun getUsageStats(context: Context): UsageData {
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val ignoredPackages = getIgnoredPackages(context)
            val launcherPackage = getDefaultLauncherPackage(context)

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = calendar.timeInMillis
            val end = System.currentTimeMillis()

            val totalTimes = mutableMapOf<String, Long>()
            val lastForeground = mutableMapOf<String, Long>()

            val events = usageStatsManager.queryEvents(start, end)

            while (events.hasNextEvent()) {
                val event = android.app.usage.UsageEvents.Event()
                events.getNextEvent(event)

                val pkg = event.packageName
                if (pkg in ignoredPackages || pkg == launcherPackage || pkg == "com.alfahrel.threshold") continue

                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        lastForeground[pkg] = maxOf(event.timeStamp, start)
                    }
                    android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val fgTime = lastForeground[pkg] ?: continue
                        val duration = event.timeStamp - fgTime
                        if (duration > 0) {
                            totalTimes[pkg] = (totalTimes[pkg] ?: 0L) + duration
                        }
                        lastForeground.remove(pkg)
                    }
                }
            }

            for ((pkg, fgTime) in lastForeground) {
                val duration = end - fgTime
                if (duration > 0) {
                    totalTimes[pkg] = (totalTimes[pkg] ?: 0L) + duration
                }
            }

            val totalTimeFiltered = totalTimes.values.fold(0L) { sum, t -> sum + t }

            val topApps = totalTimes.entries
                .sortedByDescending { it.value }
                .take(3)
                .mapNotNull { (pkg, time) ->
                    try {
                        val pm = context.packageManager
                        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getApplicationInfo(pkg, 0)
                        }
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        AppInfo(pkg, appName, time)
                    } catch (e: Exception) {
                        null
                    }
                }

            return UsageData(totalTimeFiltered, topApps)

        } catch (e: Exception) {
            android.util.Log.e("UsageWidget", "getUsageStats crashed: ${e.message}", e)
            return UsageData(0L, emptyList())
        }
    }

    private fun getDefaultLauncherPackage(context: Context): String? {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }

    private fun formatTime(milliseconds: Long): String {
        if (milliseconds <= 0) return "0m"
        val hours = milliseconds / (1000 * 60 * 60)
        val minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60)
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    data class AppInfo(val packageName: String, val name: String, val time: Long)
    data class UsageData(val totalTime: Long, val topApps: List<AppInfo>)
}