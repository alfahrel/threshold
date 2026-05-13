package alfahrel.my.id.threshold.service

import android.R
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class BlockOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var dismissHandler: Handler? = null
    private var dismissRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packageName = intent?.getStringExtra("packageName") ?: ""
        removeOverlay()
        showOverlay(packageName)
        dismissHandler = Handler(Looper.getMainLooper())
        dismissRunnable = Runnable {
            removeOverlay()
            stopSelf()
        }
        dismissHandler?.postDelayed(dismissRunnable!!, 30000)
        return START_NOT_STICKY
    }

    private fun showOverlay(packageName: String) {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER
            overlayView = createOverlayView(packageName)
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun createOverlayView(packageName: String): View {
        val context = this
        val surfaceColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            getColor(R.color.system_neutral1_10) else 0xFFFFFFFF.toInt()
        val primaryColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            getColor(R.color.system_accent1_600) else 0xFF6750A4.toInt()
        val onSurfaceColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            getColor(R.color.system_neutral1_900) else 0xFF1C1B1F.toInt()
        val onSurfaceVariantColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            getColor(R.color.system_neutral2_700) else 0xFF49454F.toInt()

        val appName = try {
            val pm = packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.split('.').last()
        }

        val timerPrefs = getSharedPreferences("app_timers", MODE_PRIVATE)
        val limitMinutes = timerPrefs.getInt(packageName, 0)

        val rootFrame = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xE6000000.toInt())
            isClickable = true
            isFocusable = true
        }

        val cardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val dp24 = (24 * resources.displayMetrics.density).toInt()
            val dp32 = (32 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply { setMargins(dp24, 0, dp24, 0) }
            setPadding(dp32, dp32, dp32, dp32)
            setBackgroundColor(surfaceColor)
            elevation = (6 * resources.displayMetrics.density)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height,
                            28 * resources.displayMetrics.density)
                    }
                }
                clipToOutline = true
            }
        }

        val title = TextView(context).apply {
            text = "Time's Up!"
            textSize = 28f
            setTextColor(onSurfaceColor)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (12 * resources.displayMetrics.density).toInt()
            }
        }

        val message = TextView(context).apply {
            text = "You've spent $limitMinutes minutes on $appName today.\n\nTime to step away and recharge! ☕ Go grab a coffee, stretch, or enjoy the world around you."
            textSize = 16f
            setTextColor(onSurfaceVariantColor)
            gravity = Gravity.CENTER
            setLineSpacing(6 * resources.displayMetrics.density, 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (32 * resources.displayMetrics.density).toInt() }
        }

        val button = Button(context).apply {
            text = "OK"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(primaryColor)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (56 * resources.displayMetrics.density).toInt()
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height,
                            16 * resources.displayMetrics.density)
                    }
                }
                clipToOutline = true
            }
            setOnClickListener { removeOverlay(); stopSelf() }
        }

        cardContainer.addView(title)
        cardContainer.addView(message)
        cardContainer.addView(button)
        rootFrame.addView(cardContainer)
        return rootFrame
    }

    private fun removeOverlay() {
        try {
            dismissRunnable?.let { dismissHandler?.removeCallbacks(it) }
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
            }
        } catch (e: Exception) { /* ignored */ }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        dismissHandler = null
        dismissRunnable = null
    }
}