package alfahrel.my.id.threshold.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeTools {

    fun formatTime(milliseconds: Long, showSeconds: Boolean = false): String {
        if (milliseconds <= 0) return "0m"

        val hours = milliseconds / (1000 * 60 * 60)
        val minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (milliseconds % (1000 * 60)) / 1000

        val parts = mutableListOf<String>()

        if (hours > 0) parts.add("${hours}h")

        if (minutes > 0) {
            parts.add("${minutes}m")
        } else if (hours > 0) {
            parts.add("0m")
        }

        if (showSeconds && seconds > 0) parts.add("${seconds}s")

        return if (parts.isEmpty()) "0m" else parts.joinToString(" ")
    }

    fun formatDate(dateMs: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(dateMs))
    }

    fun isToday(dateMs: Long): Boolean {
        val now = Calendar.getInstance(TimeZone.getDefault())
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = dateMs }
        return now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    }

    fun isYesterday(dateMs: Long): Boolean {
        val yesterday = Calendar.getInstance(TimeZone.getDefault()).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = dateMs }
        return yesterday.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    }

    fun getDateLabel(dateMs: Long): String {
        return when {
            isToday(dateMs) -> "Today"
            isYesterday(dateMs) -> "Yesterday"
            else -> formatDate(dateMs)
        }
    }

    fun weekdayShort(dateMs: Long): String {
        val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = dateMs }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val index = (dayOfWeek + 5) % 7
        return labels[index]
    }

    fun startOfDayMs(dateMs: Long): Long {
        return Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = dateMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun isSameDay(aMs: Long, bMs: Long): Boolean {
        val a = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = aMs }
        val b = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = bMs }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}