package alfahrel.my.id.threshold.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "threshold protection enabled", Toast.LENGTH_SHORT).show()
        lockApp(context, true)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "threshold protection disabled", Toast.LENGTH_SHORT).show()
        lockApp(context, false)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Disabling device admin will allow the app to be uninstalled. " +
                "This will remove all usage tracking and app timers."

    private fun lockApp(context: Context, lock: Boolean) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(context, AdminReceiver::class.java)
            if (dpm.isAdminActive(componentName)) {
                dpm.setUninstallBlocked(componentName, context.packageName, lock)
            }
        } catch (e: Exception) { /* ignored */ }
    }

    override fun onPasswordChanged(context: Context, intent: Intent) { super.onPasswordChanged(context, intent) }
    override fun onPasswordFailed(context: Context, intent: Intent) { super.onPasswordFailed(context, intent) }
    override fun onPasswordSucceeded(context: Context, intent: Intent) { super.onPasswordSucceeded(context, intent) }
}