package alfahrel.my.id.threshold.ui.sheet

import alfahrel.my.id.threshold.R
import alfahrel.my.id.threshold.util.TimeTools
import alfahrel.my.id.threshold.data.model.AppInfo
import alfahrel.my.id.threshold.data.model.AppUsageStat
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AppOptionsSheet(
    private val stat: AppUsageStat,
    private val appInfo: AppInfo?,
    private val hasTimer: Boolean,
    private val timerLimit: Int?,
    private val onSetTimer: () -> Unit,
    private val onIgnore: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_app_options, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ivIcon = view.findViewById<ImageView>(R.id.ivAppOptionsIcon)
        val tvName = view.findViewById<TextView>(R.id.tvAppOptionsName)
        val tvUsage = view.findViewById<TextView>(R.id.tvAppOptionsUsage)
        val btnTimer = view.findViewById<View>(R.id.btnOptionsSetTimer)
        val tvTimerLabel = view.findViewById<TextView>(R.id.tvOptionsTimerLabel)
        val btnIgnore = view.findViewById<View>(R.id.btnOptionsIgnore)

        if (appInfo != null && appInfo.iconBytes.isNotEmpty()) {
            val bitmap = BitmapFactory.decodeByteArray(appInfo.iconBytes, 0, appInfo.iconBytes.size)
            ivIcon.setImageBitmap(bitmap)
        } else {
            ivIcon.setImageResource(R.drawable.ic_launcher_foreground)
        }

        tvName.text = appInfo?.appName ?: stat.packageName.split(".").last()
        tvUsage.text = TimeTools.formatTime(stat.totalTime)

        tvTimerLabel.text = if (hasTimer && timerLimit != null) {
            "Edit Timer (${timerLimit}m)"
        } else {
            "Set Timer"
        }

        btnTimer.setOnClickListener {
            dismiss()
            onSetTimer()
        }

        btnIgnore.setOnClickListener {
            dismiss()
            onIgnore()
        }
    }
}