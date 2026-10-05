package com.gunz.launcher

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import kotlin.math.abs

/** Tombol Gunz melayang di atas aplikasi lain; ketuk untuk kembali ke launcher. */
class FloatingButtonService : Service() {

    private var manager: WindowManager? = null
    private var button: ImageView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (button == null) show()
        return START_NOT_STICKY
    }

    private fun show() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = dp(56)

        val iv = ImageView(this)
        iv.setImageResource(R.drawable.ic_gunz_glyph)
        iv.setPadding(dp(8), dp(8), dp(8), dp(8))
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.parseColor("#E60A2A66"))
        bg.setStroke(dp(2), Color.parseColor("#FF4CC2FF"))
        iv.background = bg
        iv.elevation = dp(8).toFloat()

        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(8)
        lp.y = dp(160)

        val slop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        iv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        try {
                            wm.updateViewLayout(iv, lp)
                        } catch (ex: Exception) {
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) goToLauncher()
                }
            }
            true
        }

        try {
            wm.addView(iv, lp)
            manager = wm
            button = iv
        } catch (ex: Exception) {
            stopSelf()
        }
    }

    private fun goToLauncher() {
        val i = Intent(this, LauncherActivity::class.java)
        i.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        try {
            startActivity(i)
        } catch (ex: Exception) {
        }
    }

    override fun onDestroy() {
        val b = button
        if (b != null) {
            try {
                manager?.removeView(b)
            } catch (ex: Exception) {
            }
        }
        button = null
        manager = null
        super.onDestroy()
    }
}
