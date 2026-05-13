package alfahrel.my.id.threshold.data.model

import java.util.Calendar

data class AppUsageStat(
    val packageName: String,
    val totalTime: Long,
    val startTimes: List<Long>,
    val sessionDurations: List<Long>
) {
    val sessionCount: Int get() = startTimes.size

    fun getHourlyBreakdown(): Map<Int, Int> {
        val hourlyUsage = (0..23).associateWith { 0 }.toMutableMap()
        for (i in startTimes.indices) {
            if (i >= sessionDurations.size) continue
            val durationMs = sessionDurations[i]
            if (durationMs < 0) continue
            val cal = Calendar.getInstance().apply { timeInMillis = startTimes[i] }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            hourlyUsage[hour] = (hourlyUsage[hour] ?: 0) + (durationMs / 1000).toInt()
        }
        return hourlyUsage
    }
}

data class AppInfo(
    val packageName: String,
    val appName: String,
    val iconBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppInfo) return false
        return packageName == other.packageName
    }

    override fun hashCode(): Int = packageName.hashCode()
}