package alfahrel.my.id.threshold.ui.sheet

import alfahrel.my.id.threshold.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class HomeMenuSheet(
    private val onIgnoredApps: () -> Unit,
    private val onAppTimers: () -> Unit,
    private val onCheckPermissions: () -> Unit,
    private val onAbout: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_home_menu, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.itemIgnoredApps).setOnClickListener {
            dismiss()
            onIgnoredApps()
        }

        view.findViewById<View>(R.id.itemAppTimers).setOnClickListener {
            dismiss()
            onAppTimers()
        }

        view.findViewById<View>(R.id.itemCheckPermissions).setOnClickListener {
            dismiss()
            onCheckPermissions()
        }

        view.findViewById<View>(R.id.itemAbout).setOnClickListener {
            dismiss()
            onAbout()
        }

        view.findViewById<View>(R.id.itemCancel).setOnClickListener {
            dismiss()
        }
    }
}