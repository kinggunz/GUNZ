package com.gunz.launcher

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.PointerIcon
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

// ---------------------------------------------------------------- Action Center

private fun tileState(ctx: Context, id: String): Boolean = try {
    when (id) {
        "wifi" -> (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
        "bt" -> BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        "gps" -> if (Build.VERSION.SDK_INT >= 28) (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled
        else Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
        "data" -> {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }
        else -> false
    }
} catch (e: Throwable) {
    false
}

/**
 * Toggle sebisa mungkin. Android 10+ melarang aplikasi menyalakan Wi-Fi/Bluetooth/Data langsung,
 * jadi di sana dibuka panel sistem kecil (bukan layar Pengaturan penuh).
 */
private fun tileToggle(act: Activity, id: String) {
    try {
        when (id) {
            "wifi" -> if (Build.VERSION.SDK_INT < 29) {
                @Suppress("DEPRECATION")
                val wm = act.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wm.isWifiEnabled = !wm.isWifiEnabled
            } else panel(act, Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            "data" -> if (Build.VERSION.SDK_INT >= 29) panel(act, Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            else act.startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
            "bt" -> {
                val a = BluetoothAdapter.getDefaultAdapter()
                if (a != null && Build.VERSION.SDK_INT < 31) {
                    @Suppress("DEPRECATION")
                    if (a.isEnabled) a.disable() else a.enable()
                } else act.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            "gps" -> act.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    } catch (e: Throwable) {
        Toast.makeText(act, "Tidak tersedia di perangkat ini", Toast.LENGTH_SHORT).show()
    }
}

private fun panel(act: Activity, action: String) {
    act.startActivity(Intent(action))
}

fun actionCenterView(act: Activity): View {
    val sv = ScrollView(act)
    sv.setBackgroundColor(Color.parseColor("#FF2B2B2B"))
    val col = LinearLayout(act)
    col.orientation = LinearLayout.VERTICAL
    col.setPadding(act.dp(12), act.dp(12), act.dp(12), act.dp(12))
    sv.addView(col)

    val grid = GridLayout(act)
    grid.columnCount = 3
    col.addView(grid)
    val tiles = listOf(
        Triple("wifi", "Wi-Fi", Glyph.WIFI), Triple("bt", "Bluetooth", Glyph.GEAR),
        Triple("data", "Data seluler", Glyph.GLOBE), Triple("gps", "Lokasi (GPS)", Glyph.HOME)
    )
    val views = HashMap<String, TextView>()
    fun paint(id: String) {
        val tv = views[id] ?: return
        val on = tileState(act, id)
        tv.background = GradientDrawable().apply {
            cornerRadius = act.dp(6).toFloat()
            setColor(Color.parseColor(if (on) "#FF0078D4" else "#FF3A3A3A"))
        }
    }
    for ((id, name, g) in tiles) {
        val tv = TextView(act)
        tv.text = name
        tv.setTextColor(Color.WHITE)
        tv.textSize = 12f
        tv.gravity = android.view.Gravity.CENTER
        tv.setPadding(act.dp(6), act.dp(14), act.dp(6), act.dp(14))
        val ic = GlyphIcon(g, Color.WHITE)
        ic.setBounds(0, 0, act.dp(22), act.dp(22))
        tv.setCompoundDrawables(null, ic, null, null)
        tv.compoundDrawablePadding = act.dp(6)
        tv.setOnClickListener {
            tileToggle(act, id)
            tv.postDelayed({ paint(id) }, 600)
        }
        val lp = GridLayout.LayoutParams()
        lp.width = 0
        lp.height = GridLayout.LayoutParams.WRAP_CONTENT
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        lp.setMargins(act.dp(4), act.dp(4), act.dp(4), act.dp(4))
        grid.addView(tv, lp)
        views[id] = tv
        paint(id)
    }

    fun label(t: String) {
        val tv = TextView(act)
        tv.text = t
        tv.setTextColor(Color.WHITE)
        tv.textSize = 13f
        tv.setPadding(act.dp(4), act.dp(14), 0, act.dp(2))
        col.addView(tv)
    }

    label("Volume")
    val am = act.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val vol = SeekBar(act)
    vol.max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    vol.progress = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    vol.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {
            if (u) am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
        }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    })
    col.addView(vol)

    label("Kecerahan")
    val br = SeekBar(act)
    br.max = 100
    val cur = act.window.attributes.screenBrightness
    br.progress = if (cur < 0f) 60 else (cur * 100).toInt()
    br.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {
            if (!u) return
            val lp = act.window.attributes
            lp.screenBrightness = (v / 100f).coerceIn(0.02f, 1f)
            act.window.attributes = lp
        }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    })
    col.addView(br)

    val note = TextView(act)
    note.text = "Android 10+ membatasi aplikasi menyalakan Wi-Fi/Data/Bluetooth langsung; tombol di atas membuka panel sistem kecil bila diperlukan."
    note.setTextColor(Color.parseColor("#99FFFFFF"))
    note.textSize = 11f
    note.setPadding(act.dp(4), act.dp(14), 0, 0)
    col.addView(note)
    return sv
}

// ---------------------------------------------------------------- Notifikasi gaya Windows

/** Menerima notifikasi (SMS, WhatsApp, dll) lalu meneruskannya ke launcher sebagai toast Windows. */
class NotifService : NotificationListenerService() {

    companion object {
        @Volatile var listener: ((app: String, title: String, text: String, pkg: String) -> Unit)? = null

        fun isEnabled(ctx: Context): Boolean {
            val s = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            return s.contains(ctx.packageName)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (sbn.packageName == packageName) return
        if (n.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return
        if (sbn.isOngoing) return
        val ex = n.extras
        val title = ex.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: return
        val text = ex.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        val app = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
        listener?.invoke(app, title, text, sbn.packageName)
    }
}

fun openNotificationAccess(ctx: Context) {
    try {
        ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
    }
}

// ---------------------------------------------------------------- Kursor Windows

object WinCursor {
    /** Panah putih berbingkai hitam seperti kursor Windows; dipasang di semua view launcher. */
    fun apply(v: View) {
        if (Build.VERSION.SDK_INT < 24) return
        try {
            val s = v.context.dp(24)
            val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val u = s / 24f
            val path = Path()
            path.moveTo(2f * u, 1f * u)
            path.lineTo(2f * u, 18f * u)
            path.lineTo(6.5f * u, 13.8f * u)
            path.lineTo(9.8f * u, 21.5f * u)
            path.lineTo(12.6f * u, 20.3f * u)
            path.lineTo(9.4f * u, 12.9f * u)
            path.lineTo(15.5f * u, 12.7f * u)
            path.close()
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.style = Paint.Style.FILL
            p.color = Color.WHITE
            c.drawPath(path, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.4f * u
            p.color = Color.BLACK
            c.drawPath(path, p)
            v.pointerIcon = PointerIcon.create(bmp, 2f * u, 1f * u)
        } catch (e: Throwable) {
        }
    }
}

// ---------------------------------------------------------------- Ikon baterai Windows

class BatteryIcon(ctx: Context) : View(ctx) {
    private var level = 100
    private var charging = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        background = hoverBg()
        refreshLevel()
    }

    fun refreshLevel() {
        val i = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val l = i.getIntExtra("level", 100)
        val sc = i.getIntExtra("scale", 100)
        level = if (sc > 0) l * 100 / sc else l
        charging = i.getIntExtra("plugged", 0) != 0
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bw = context.dp(20).toFloat()
        val bh = context.dp(10).toFloat()
        val x = (w - bw) / 2f
        val y = (h - bh) / 2f
        p.style = Paint.Style.STROKE
        p.strokeWidth = context.dp(1).toFloat()
        p.color = Color.WHITE
        c.drawRoundRect(x, y, x + bw, y + bh, 3f, 3f, p)
        p.style = Paint.Style.FILL
        c.drawRect(x + bw, y + bh * 0.3f, x + bw + context.dp(2), y + bh * 0.7f, p)
        p.color = if (charging) Color.parseColor("#6CCB5F") else if (level <= 15) Color.parseColor("#FF5A5A") else Color.WHITE
        val inner = (bw - context.dp(3)) * level / 100f
        c.drawRect(x + context.dp(1.5f.toInt()).toFloat(), y + context.dp(2), x + context.dp(1.5f.toInt()) + inner, y + bh - context.dp(2), p)
    }
}
