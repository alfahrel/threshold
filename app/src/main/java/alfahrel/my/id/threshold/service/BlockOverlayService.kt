package alfahrel.my.id.threshold.service

import alfahrel.my.id.threshold.ui.widget.BlockActivity
import android.app.Service
import android.content.Intent
import android.os.IBinder

class BlockOverlayService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packageName = intent?.getStringExtra("packageName") ?: ""
        val activityIntent = Intent(this, BlockActivity::class.java).apply {
            putExtra("packageName", packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(activityIntent)
        stopSelf()
        return START_NOT_STICKY
    }
}