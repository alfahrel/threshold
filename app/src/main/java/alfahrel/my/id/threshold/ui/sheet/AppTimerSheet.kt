package alfahrel.my.id.threshold.ui.sheet

import alfahrel.my.id.threshold.R
import alfahrel.my.id.threshold.util.TimeTools
import alfahrel.my.id.threshold.data.model.AppInfo
import alfahrel.my.id.threshold.data.model.AppUsageStat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider

class AppTimerSheet(
    private val stat: AppUsageStat,
    private val appInfo: AppInfo?,
    private val currentLimit: Int?,
    private val onSave: (Int) -> Unit,
    private val onRemove: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_app_timer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvTitle = view.findViewById<TextView>(R.id.tvTimerSheetTitle)
        val tvLimitMinutes = view.findViewById<TextView>(R.id.tvLimitMinutes)
        val tvUsedToday = view.findViewById<TextView>(R.id.tvUsedToday)
        val slider = view.findViewById<Slider>(R.id.sliderTimer)
        val btnRemove = view.findViewById<View>(R.id.btnRemoveTimer)
        val btnConfirm = view.findViewById<View>(R.id.btnSetTimerConfirm)

        tvTitle.text = appInfo?.appName ?: stat.packageName.split(".").last()
        tvUsedToday.text = TimeTools.formatTime(stat.totalTime)

        val initial = currentLimit ?: 30
        slider.value = initial.toFloat().coerceIn(slider.valueFrom, slider.valueTo)
        tvLimitMinutes.text = "${initial}m"

        if (currentLimit != null) {
            btnRemove.visibility = View.VISIBLE
        }

        slider.addOnChangeListener { _, value, _ ->
            tvLimitMinutes.text = "${value.toInt()}m"
        }

        btnRemove.setOnClickListener {
            dismiss()
            onRemove()
        }

        btnConfirm.setOnClickListener {
            dismiss()
            onSave(slider.value.toInt())
        }
    }
}