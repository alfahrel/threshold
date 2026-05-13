package alfahrel.my.id.threshold.ui.sheet

import alfahrel.my.id.threshold.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AllPermissionsSheet(
    private val onGrant: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_all_permissions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnGrantAll).setOnClickListener {
            dismiss()
            onGrant()
        }

        view.findViewById<View>(R.id.btnReject).setOnClickListener {
            dismiss()
        }
    }
}