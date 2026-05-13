package alfahrel.my.id.threshold.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.google.android.material.R
import com.google.android.material.color.MaterialColors
import java.util.Calendar

class HourlyBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var hourlyData: Map<Int, Int> = emptyMap()
    private var isToday: Boolean = false

    private val barItemWidthPx = (48 * resources.displayMetrics.density).toInt()

    private var animatedFraction: Float = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 350
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            this@HourlyBarChartView.animatedFraction = it.animatedFraction
            invalidate()
        }
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 28f
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 24f
    }

    private val barRect = RectF()

    fun setData(hourlyData: Map<Int, Int>, isToday: Boolean) {
        this.hourlyData = hourlyData
        this.isToday = isToday
        animator.cancel()
        animatedFraction = 0f
        animator.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = barItemWidthPx * 24
        val desiredHeight = MeasureSpec.getSize(heightMeasureSpec)
            .takeIf { it > 0 } ?: (180 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(desiredWidth, desiredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (hourlyData.isEmpty()) return

        val colorPrimaryVariant = MaterialColors.getColor(this, R.attr.colorPrimaryVariant)
        val colorOnSurfaceVariant = MaterialColors.getColor(this, R.attr.colorOnSurfaceVariant)
        val colorOutlineVariant = MaterialColors.getColor(this, R.attr.colorOutlineVariant)

        val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val maxSec = hourlyData.values.maxOrNull()?.takeIf { it > 0 } ?: 1

        val itemWidth = barItemWidthPx.toFloat()
        val barMaxHeight = height - 80f
        val barWidth = itemWidth - 12f
        val labelY = height.toFloat() - 16f

        for (hour in 0..23) {
            val seconds = hourlyData[hour] ?: 0
            val isEmpty = seconds == 0
            val isCurrent = isToday && hour == nowHour

            val centerX = itemWidth * hour + itemWidth / 2f

            val targetBarH = if (!isEmpty) {
                (seconds.toFloat() / maxSec * barMaxHeight).coerceIn(4f, barMaxHeight)
            } else {
                4f
            }
            val barH = targetBarH * animatedFraction

            barPaint.color = when {
                isCurrent && !isEmpty -> colorPrimaryVariant
                isEmpty -> applyAlpha(colorOutlineVariant, 0.25f)
                else -> applyAlpha(colorPrimaryVariant, 0.45f)
            }

            val barTop = height - 48f - barH
            val barLeft = centerX - barWidth / 2f
            barRect.set(barLeft, barTop, barLeft + barWidth, height - 48f)
            canvas.drawRoundRect(barRect, 60f, 60f, barPaint)

            if (!isEmpty && animatedFraction > 0.5f) {
                valuePaint.color = if (isCurrent) colorPrimaryVariant else colorOnSurfaceVariant
                valuePaint.isFakeBoldText = isCurrent
                canvas.drawText(formatSeconds(seconds), centerX, barTop - 6f, valuePaint)
            }

            labelPaint.color = if (isCurrent) colorPrimaryVariant else colorOnSurfaceVariant
            labelPaint.isFakeBoldText = isCurrent
            canvas.drawText(formatHourShort(hour), centerX, labelY, labelPaint)
        }
    }

    private fun formatHourShort(hour: Int): String = when {
        hour == 0 -> "12a"
        hour < 12 -> "${hour}a"
        hour == 12 -> "12p"
        else -> "${hour - 12}p"
    }

    private fun formatSeconds(seconds: Int): String {
        if (seconds < 60) return "${seconds}s"
        val mins = seconds / 60
        if (mins < 60) return "${mins}m"
        val hrs = mins / 60
        val rem = mins % 60
        return if (rem > 0) "${hrs}h${rem}m" else "${hrs}h"
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}