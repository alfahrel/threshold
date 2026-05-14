package alfahrel.my.id.threshold.ui.widget

import alfahrel.my.id.threshold.R
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BlockActivity : AppCompatActivity() {

    private lateinit var breatheOuter: View
    private lateinit var breatheMiddle: View
    private lateinit var breatheInner: View
    private lateinit var tvBreathe: TextView
    private var breatheAnimator: AnimatorSet? = null
    private var isInhaling = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_block)

        breatheOuter = findViewById(R.id.breatheOuter)
        breatheMiddle = findViewById(R.id.breatheMiddle)
        breatheInner = findViewById(R.id.breatheInner)
        tvBreathe = findViewById(R.id.tvBreathe)

        val packageName = intent.getStringExtra("packageName") ?: ""
        val appName = getAppName(packageName)
        val limitMinutes = getSharedPreferences("app_timers", MODE_PRIVATE).getInt(packageName, 0)

        findViewById<TextView>(R.id.tvMessage).text =
            "You've reached your threshold. \nTake a moment to breathe and recharge."

        findViewById<Button>(R.id.btnOk).setOnClickListener {
            finish()
        }

        startBreatheAnimation()
    }

    private fun startBreatheAnimation() {
        isInhaling = true
        runBreathe()
    }

    private fun runBreathe() {
        val duration = if (isInhaling) 4000L else 6000L
        val targetScale = if (isInhaling) 1.15f else 0.9f
        val label = if (isInhaling) "Breathe In" else "Breathe Out"

        tvBreathe.text = label

        val outerAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(breatheOuter, View.SCALE_X, targetScale),
                ObjectAnimator.ofFloat(breatheOuter, View.SCALE_Y, targetScale)
            )
        }
        val middleAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(breatheMiddle, View.SCALE_X, targetScale),
                ObjectAnimator.ofFloat(breatheMiddle, View.SCALE_Y, targetScale)
            )
        }
        val innerAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(breatheInner, View.SCALE_X, targetScale),
                ObjectAnimator.ofFloat(breatheInner, View.SCALE_Y, targetScale)
            )
        }

        breatheAnimator = AnimatorSet().apply {
            playTogether(outerAnim, middleAnim, innerAnim)
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isInhaling = !isInhaling
                    runBreathe()
                }
            })
            start()
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val ai = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            packageName.split('.').last()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        breatheAnimator?.cancel()
    }

    override fun onBackPressed() {
        // block back press
    }
}