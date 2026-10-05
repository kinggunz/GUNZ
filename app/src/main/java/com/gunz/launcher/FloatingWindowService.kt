package com.gunz.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Pembungkus root jendela mengambang: mengatur fokus keyboard dan tombol Back. */
internal class OverlayRoot(ctx: Context) : FrameLayout(ctx) {
    var onFocusChange: ((Boolean) -> Unit)? = null
    var onBack: (() -> Boolean)? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onFocusChange?.invoke(false)
            return true
        }
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) onFocusChange?.invoke(true)
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/**
 * Menampilkan YouTube / Peramban sebagai jendela mengambang di ATAS semua aplikasi
 * (izin "Tampil di atas aplikasi lain"). Berjalan sebagai foreground service agar tidak dimatikan sistem.
 */
class FloatingWindowService : Service() {

    companion object {
        const val ACTION_OPEN = "com.gunz.launcher.action.OPEN_FLOAT"
        const val ACTION_CLOSE_ALL = "com.gunz.launcher.action.CLOSE_ALL_FLOAT"
        const val HOME_BROWSER = "https://www.google.com"
        const val HOME_YOUTUBE = "https://m.youtube.com"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_URL = "url"
        private const val CHANNEL = "gunz_float"
        private const val NOTIF_ID = 4107

        fun open(ctx: Context, kind: String, url: String?) {
            val i = Intent(ctx, FloatingWindowService::class.java)
            i.action = ACTION_OPEN
            i.putExtra(EXTRA_KIND, kind)
            if (url != null) i.putExtra(EXTRA_URL, url)
            ctx.startForegroundService(i)
        }
    }

    private lateinit var wm: WindowManager
    private val wins = ArrayList<OverlayWin>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        if (intent == null) {
            stopSelfNow()
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_CLOSE_ALL) {
            closeAll()
            stopSelfNow()
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_OPEN) {
            if (!Settings.canDrawOverlays(this)) {
                stopSelfNow()
                return START_NOT_STICKY
            }
            val kind = intent.getStringExtra(EXTRA_KIND) ?: "browser"
            val url = intent.getStringExtra(EXTRA_URL)
            try {
                val home = if (kind == "youtube") HOME_YOUTUBE else HOME_BROWSER
                val win = OverlayWin(kind, url ?: home)
                wins.add(win)
                win.show(wins.size - 1)
            } catch (e: Exception) {
                if (wins.isEmpty()) stopSelfNow()
            }
        }
        return START_NOT_STICKY
    }

    private fun stopSelfNow() {
        stopForeground(true)
        stopSelf()
    }

    private fun closeAll() {
        for (w in ArrayList(wins)) w.dispose()
        wins.clear()
    }

    private fun removeWin(w: OverlayWin) {
        w.dispose()
        wins.remove(w)
        if (wins.isEmpty()) stopSelfNow()
    }

    override fun onDestroy() {
        closeAll()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Jendela mengambang", NotificationManager.IMPORTANCE_LOW)
        )
        val openI = Intent(this, LauncherActivity::class.java)
        openI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(this, 0, openI, PendingIntent.FLAG_IMMUTABLE)
        val closeI = Intent(this, FloatingWindowService::class.java)
        closeI.action = ACTION_CLOSE_ALL
        val close = PendingIntent.getService(this, 1, closeI, PendingIntent.FLAG_IMMUTABLE)
        val act = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_gunz_glyph), "Tutup semua", close
        ).build()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_gunz_glyph)
            .setContentTitle("Gunz Launcher")
            .setContentText("Jendela mengambang aktif")
            .setContentIntent(open)
            .addAction(act)
            .setOngoing(true)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun screen(real: Boolean): DisplayMetrics {
        val dm = DisplayMetrics()
        if (real) wm.defaultDisplay.getRealMetrics(dm) else wm.defaultDisplay.getMetrics(dm)
        return dm
    }

    private inner class OverlayWin(val kind: String, startUrl: String) {

        private val themed: Context =
            ContextThemeWrapper(this@FloatingWindowService, android.R.style.Theme_Material_NoActionBar)
        private val isYt = kind == "youtube"
        val pane = WebPane(themed, startUrl, if (isYt) HOME_YOUTUBE else HOME_BROWSER, !isYt)
        private val root = OverlayRoot(themed)
        private val column = LinearLayout(themed)
        private val bubble = ImageView(themed)
        private val bar = LinearLayout(themed)
        private val maxBtn: ImageView
        private val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )

        private var minimized = false
        private var maximized = false
        private val saved = IntArray(4)
        private var added = false

        private var dx = 0f
        private var dy = 0f
        private var lastTap = 0L
        private var ignoreMove = false
        private var gx = 0f
        private var gy = 0f
        private var gw = 0
        private var gh = 0

        init {
            lp.gravity = Gravity.TOP or Gravity.START
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN

            setWindowBg()
            root.clipToOutline = true

            column.orientation = LinearLayout.VERTICAL
            bar.orientation = LinearLayout.HORIZONTAL
            bar.gravity = Gravity.CENTER_VERTICAL
            bar.setBackgroundColor(Color.parseColor("#FF2F2F2F"))

            val ic = ImageView(themed)
            ic.setImageDrawable(builtinIcon(if (isYt) "youtube" else "browser"))
            val iclp = LinearLayout.LayoutParams(dp(16), dp(16))
            iclp.leftMargin = dp(10)
            bar.addView(ic, iclp)

            val tv = TextView(themed)
            tv.text = if (isYt) "YouTube" else "Peramban"
            tv.setTextColor(Color.WHITE)
            tv.textSize = 12f
            tv.maxLines = 1
            tv.gravity = Gravity.CENTER_VERTICAL
            tv.setPadding(dp(8), 0, dp(8), 0)
            bar.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

            bar.addView(capButton(themed, Glyph.MINIMIZE, false) { minimize() })
            maxBtn = capButton(themed, Glyph.MAXIMIZE, false) { toggleMax() }
            bar.addView(maxBtn)
            bar.addView(capButton(themed, Glyph.CLOSE, true) { removeWin(this) })

            column.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)))
            column.addView(pane.view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            root.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

            bubble.setImageDrawable(builtinIcon(if (isYt) "youtube" else "browser"))
            bubble.setPadding(dp(10), dp(10), dp(10), dp(10))
            bubble.visibility = View.GONE
            root.addView(bubble, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

            val grip = ImageView(themed)
            grip.setImageDrawable(GlyphIcon(Glyph.GRIP, Color.parseColor("#99FFFFFF")))
            grip.setPadding(dp(8), dp(8), dp(3), dp(3))
            root.addView(grip, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.END or Gravity.BOTTOM))

            root.onFocusChange = { f -> setFocusable(f) }
            root.onBack = { pane.goBack() }

            bar.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        ignoreMove = false
                        dx = lp.x - e.rawX
                        dy = lp.y - e.rawY
                        if (e.eventTime - lastTap < 350) {
                            lastTap = 0
                            toggleMax()
                            ignoreMove = true
                        } else {
                            lastTap = e.eventTime
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!maximized && !ignoreMove) moveTo((e.rawX + dx).toInt(), (e.rawY + dy).toInt())
                    }
                }
                true
            }

            grip.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        gx = e.rawX
                        gy = e.rawY
                        gw = lp.width
                        gh = lp.height
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!maximized && !minimized) {
                            lp.width = (gw + (e.rawX - gx)).toInt().coerceAtLeast(dp(260))
                            lp.height = (gh + (e.rawY - gy)).toInt().coerceAtLeast(dp(190))
                            update()
                        }
                    }
                }
                true
            }

            val slop = ViewConfiguration.get(themed).scaledTouchSlop.toFloat()
            var bx = 0f
            var by = 0f
            var sx = 0
            var sy = 0
            var moved = false
            bubble.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        bx = e.rawX
                        by = e.rawY
                        sx = lp.x
                        sy = lp.y
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(e.rawX - bx) > slop || abs(e.rawY - by) > slop) moved = true
                        if (moved) moveTo(sx + (e.rawX - bx).toInt(), sy + (e.rawY - by).toInt())
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) unminimize()
                    }
                }
                true
            }
        }

        private fun setWindowBg() {
            val g = GradientDrawable()
            if (minimized) {
                g.shape = GradientDrawable.OVAL
                g.setColor(Color.parseColor(if (isYt) "#FF202020" else "#FF202020"))
                g.setStroke(dp(2), Color.parseColor("#FF4CC2FF"))
            } else {
                g.setColor(Color.parseColor("#FF202020"))
                g.cornerRadius = dp(8).toFloat()
                g.setStroke(dp(1), Color.parseColor("#FF4A4A4A"))
            }
            root.background = g
        }

        private fun update() {
            if (!added) return
            try {
                wm.updateViewLayout(root, lp)
            } catch (e: Exception) {
            }
        }

        private fun moveTo(nx: Int, ny: Int) {
            val dm = screen(true)
            val minX = -(lp.width - dp(60))
            val maxX = dm.widthPixels - dp(60)
            lp.x = nx.coerceIn(minX, maxOf(minX, maxX))
            lp.y = ny.coerceIn(0, maxOf(0, dm.heightPixels - dp(34)))
            update()
        }

        fun show(index: Int) {
            val dm = screen(true)
            val w = minOf(dp(380), (dm.widthPixels * 0.9f).toInt())
            val h = minOf(dp(340), (dm.heightPixels * 0.6f).toInt())
            lp.width = w
            lp.height = h
            lp.x = (dm.widthPixels - w) / 2 + (index % 5) * dp(24)
            lp.y = dp(80) + (index % 5) * dp(24)
            root.alpha = 0f
            wm.addView(root, lp)
            added = true
            root.animate().alpha(1f).setDuration(150).start()
        }

        fun setFocusable(f: Boolean) {
            val focusable = (lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0
            if (focusable == f) return
            if (f) {
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                try {
                    val imm = themed.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(root.windowToken, 0)
                } catch (e: Exception) {
                }
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            update()
        }

        private fun toggleMax() {
            if (minimized) return
            val dm = screen(false)
            if (!maximized) {
                saved[0] = lp.x
                saved[1] = lp.y
                saved[2] = lp.width
                saved[3] = lp.height
                lp.x = 0
                lp.y = 0
                lp.width = dm.widthPixels
                lp.height = dm.heightPixels
                maximized = true
            } else {
                lp.x = saved[0]
                lp.y = saved[1]
                lp.width = saved[2]
                lp.height = saved[3]
                maximized = false
            }
            maxBtn.setImageDrawable(GlyphIcon(if (maximized) Glyph.RESTORE else Glyph.MAXIMIZE, Color.WHITE))
            update()
        }

        private fun minimize() {
            if (minimized) return
            if (maximized) toggleMax()
            saved[0] = lp.x
            saved[1] = lp.y
            saved[2] = lp.width
            saved[3] = lp.height
            setFocusable(false)
            minimized = true
            column.visibility = View.GONE
            bubble.visibility = View.VISIBLE
            lp.width = dp(52)
            lp.height = dp(52)
            setWindowBg()
            update()
        }

        private fun unminimize() {
            if (!minimized) return
            minimized = false
            bubble.visibility = View.GONE
            column.visibility = View.VISIBLE
            lp.x = saved[0]
            lp.y = saved[1]
            lp.width = saved[2]
            lp.height = saved[3]
            setWindowBg()
            update()
        }

        fun dispose() {
            if (added) {
                try {
                    wm.removeView(root)
                } catch (e: Exception) {
                }
                added = false
            }
            pane.destroy()
        }
    }
}
