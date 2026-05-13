package alfahrel.my.id.threshold.service

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import java.util.Calendar

class AppBlockerAccessibilityService : AccessibilityService() {

    private val lastOverlayShownTimes = mutableMapOf<String, Long>()
    private val OVERLAY_SHOW_COOLDOWN_MS = 1000L
    private var lastHomeActionTime = 0L
    private val HOME_ACTION_COOLDOWN_MS = 500L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString()
                if (packageName != null && packageName != "alfahrel.my.id.threshold") {
                    if (isAppLimitExceeded(packageName)) {
                        blockApp(packageName)
                    }
                }
            }
        }
    }

    private fun isAppLimitExceeded(packageName: String): Boolean {
        return try {
            val timerPrefs = getSharedPreferences("app_timers", MODE_PRIVATE)
            val limitMinutes = timerPrefs.getInt(packageName, -1)
            if (limitMinutes <= 0) return false
            val usageMs = getTodayUsage(packageName)
            val usageMinutes = usageMs / (1000 * 60)
            usageMinutes >= limitMinutes
        } catch (e: Exception) {
            false
        }
    }

    private fun getTodayUsage(packageName: String): Long {
        return try {
            val usageStatsManager = getSystemService(USAGE_STATS_SERVICE)
                    as UsageStatsManager
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val start = calendar.timeInMillis
            val end = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, start, end
            )
            var totalTime = 0L
            for (stat in stats) {
                if (stat.packageName == packageName) {
                    totalTime += stat.totalTimeInForeground
                }
            }
            totalTime
        } catch (e: Exception) {
            0L
        }
    }

    private fun blockApp(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastHomeActionTime > HOME_ACTION_COOLDOWN_MS) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            lastHomeActionTime = now
        }
        val lastOverlayTime = lastOverlayShownTimes[packageName] ?: 0L
        if (now - lastOverlayTime > OVERLAY_SHOW_COOLDOWN_MS) {
            showBlockOverlay(packageName)
            lastOverlayShownTimes[packageName] = now
        }
    }

    private fun showBlockOverlay(packageName: String) {
        val intent = Intent(this, BlockOverlayService::class.java)
        intent.putExtra("packageName", packageName)
        startService(intent)
    }

    override fun onInterrupt() {}
    override fun onServiceConnected() { super.onServiceConnected() }
    override fun onDestroy() {
        super.onDestroy()
        lastOverlayShownTimes.clear()
    }
}