package alfahrel.my.id.threshold.ui.sheet

import alfahrel.my.id.threshold.R
import alfahrel.my.id.threshold.data.repository.UsageRepository
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PermissionsStatusSheet(
    private val permissions: UsageRepository.AllPermissions,
    private val onGrant: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_permissions_status, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindRow(view, R.id.rowUsageStats, "Usage Stats", permissions.usageStats)
        bindRow(view, R.id.rowAccessibility, "Accessibility", permissions.accessibility)
        bindRow(view, R.id.rowOverlay, "Display Overlay", permissions.overlay)
        bindRow(view, R.id.rowDeviceAdmin, "Device Admin", permissions.deviceAdmin)

        val btnGrantAll = view.findViewById<View>(R.id.btnGrantAll)
        if (permissions.allGranted) {
            btnGrantAll.visibility = View.GONE
        } else {
            btnGrantAll.visibility = View.VISIBLE
            btnGrantAll.setOnClickListener {
                dismiss()
                onGrant()
            }
        }
    }

    private fun bindRow(root: View, rowId: Int, name: String, granted: Boolean) {
        val row = root.findViewById<View>(rowId)
        val icon = row.findViewById<ImageView>(R.id.ivPermissionIcon)
        val text = row.findViewById<TextView>(R.id.tvPermissionName)

        text.text = name

        if (granted) {
            icon.setImageResource(R.drawable.ic_rounded_check_circle_24)
            icon.setColorFilter(
                resources.getColor(android.R.color.holo_green_light, requireContext().theme)
            )
        } else {
            icon.setImageResource(R.drawable.ic_rounded_cancel_24)
            icon.setColorFilter(
                resources.getColor(android.R.color.holo_red_light, requireContext().theme)
            )
        }
    }
}

class PermissionHelpSheet(
    private val onTryAgain: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_permission_help, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnTryAgain).setOnClickListener {
            dismiss()
            onTryAgain()
        }
    }
}

class AboutSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_about, container, false)
}