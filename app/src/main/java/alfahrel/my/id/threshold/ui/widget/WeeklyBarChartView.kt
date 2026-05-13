package alfahrel.my.id.threshold.ui.widget

import alfahrel.my.id.threshold.util.TimeTools
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.google.android.material.R
import com.google.android.material.color.MaterialColors

class WeeklyBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var weeklyStats: Map<Long, Long> = emptyMap()
    private var selectedDateMs: Long = 0L
    private var onDaySelected: ((Long) -> Unit)? = null

    private val sortedDays: List<Long> get() = weeklyStats.keys.sorted()

    private var animatedFraction: Float = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 350
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            this@WeeklyBarChartView.animatedFraction = it.animatedFraction
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
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val barRect = RectF()

    fun setData(
        weeklyStats: Map<Long, Long>,
        selectedDateMs: Long,
        onDaySelected: (Long) -> Unit
    ) {
        this.weeklyStats = weeklyStats
        this.selectedDateMs = selectedDateMs
        this.onDaySelected = onDaySelected
        animator.cancel()
        animatedFraction = 0f
        animator.start()
    }

    fun updateSelectedDay(selectedDateMs: Long) {
        this.selectedDateMs = selectedDateMs
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val days = sortedDays
        if (days.isEmpty()) return true

        val itemWidth = width.toFloat() / days.size
        val index = (event.x / itemWidth).toInt().coerceIn(0, days.size - 1)
        onDaySelected?.invoke(days[index])
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val days = sortedDays
        if (days.isEmpty()) {
            drawEmpty(canvas)
            return
        }

        val colorPrimaryVariant = MaterialColors.getColor(this, R.attr.colorPrimaryVariant)
        val colorOnSurfaceVariant = MaterialColors.getColor(this, R.attr.colorOnSurfaceVariant)
        val colorOutlineVariant = MaterialColors.getColor(this, R.attr.colorOutlineVariant)

        val maxMs = weeklyStats.values.maxOrNull() ?: 1L
        val itemWidth = width.toFloat() / days.size
        val barMaxHeight = height - 80f
        val barWidth = itemWidth - 12f
        val labelY = height.toFloat() - 16f
        val dotY = height.toFloat() - 4f

        days.forEachIndexed { index, dayMs ->
            val ms = weeklyStats[dayMs] ?: 0L
            val isSelected = TimeTools.isSameDay(dayMs, selectedDateMs)
            val isToday = TimeTools.isToday(dayMs)
            val isEmpty = ms == 0L

            val centerX = itemWidth * index + itemWidth / 2f

            val targetBarH = if (!isEmpty && maxMs > 0) {
                (ms.toFloat() / maxMs * barMaxHeight).coerceIn(4f, barMaxHeight)
            } else {
                4f
            }
            val barH = targetBarH * animatedFraction

            val barColor = when {
                isSelected -> colorPrimaryVariant
                isEmpty -> colorOutlineVariant and 0x40FFFFFF.toInt()
                else -> (colorPrimaryVariant and 0x00FFFFFF) or 0x73000000.toInt()
            }

            barPaint.color = when {
                isSelected -> colorPrimaryVariant
                isEmpty -> applyAlpha(colorOutlineVariant, 0.25f)
                else -> applyAlpha(colorPrimaryVariant, 0.45f)
            }

            val barTop = height - 48f - barH
            val barLeft = centerX - barWidth / 2f
            barRect.set(barLeft, barTop, barLeft + barWidth, height - 48f)
            canvas.drawRoundRect(barRect, 60f, 60f, barPaint)

            if (!isEmpty && animatedFraction > 0.5f) {
                valuePaint.color = if (isSelected) colorPrimaryVariant else colorOnSurfaceVariant
                valuePaint.isFakeBoldText = isSelected
                val valueText = TimeTools.formatTime(ms)
                canvas.drawText(valueText, centerX, barTop - 6f, valuePaint)
            }

            labelPaint.color = if (isSelected) colorPrimaryVariant else colorOnSurfaceVariant
            labelPaint.isFakeBoldText = isSelected || isToday
            canvas.drawText(TimeTools.weekdayShort(dayMs), centerX, labelY, labelPaint)

            if (isToday) {
                dotPaint.color = if (isSelected) colorPrimaryVariant else applyAlpha(colorOnSurfaceVariant, 0.5f)
                canvas.drawCircle(centerX, dotY, 6f, dotPaint)
            }
        }
    }

    private fun drawEmpty(canvas: Canvas) {
        val colorOnSurfaceVariant = MaterialColors.getColor(
            this, R.attr.colorOnSurfaceVariant
        )
        labelPaint.color = colorOnSurfaceVariant
        canvas.drawText("No data available", width / 2f, height / 2f, labelPaint)
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}