package com.gunz.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.min

/** Preset keymapping per game (disimpan per nama paket). Posisi berupa pecahan layar (0..1). */
object KeyMap {
    val ACTIONS = listOf("JOY", "FIRE", "SCOPE", "GLOO", "RELOAD", "JUMP", "CROUCH", "LOOK")
    val LABEL = mapOf(
        "JOY" to "WASD", "FIRE" to "Tembak", "SCOPE" to "Scope", "GLOO" to "G",
        "RELOAD" to "R", "JUMP" to "Spasi", "CROUCH" to "C", "LOOK" to "Kamera"
    )
    private val DEF = mapOf(
        "JOY" to floatArrayOf(0.14f, 0.68f), "FIRE" to floatArrayOf(0.86f, 0.66f),
        "SCOPE" to floatArrayOf(0.78f, 0.44f), "GLOO" to floatArrayOf(0.60f, 0.84f),
        "RELOAD" to floatArrayOf(0.70f, 0.84f), "JUMP" to floatArrayOf(0.93f, 0.82f),
        "CROUCH" to floatArrayOf(0.93f, 0.62f), "LOOK" to floatArrayOf(0.70f, 0.40f)
    )

    val KEYLABEL = linkedMapOf(
        "UP" to "Maju", "LEFT" to "Kiri", "DOWN" to "Mundur", "RIGHT" to "Kanan", "FIRE" to "Tembak (selain klik kiri)",
        "GLOO" to "Gloo wall", "RELOAD" to "Reload", "JUMP" to "Lompat", "CROUCH" to "Jongkok", "SCOPE" to "Scope (selain klik kanan)"
    )
    val DEFKEYS = mapOf(
        "UP" to KeyEvent.KEYCODE_W, "LEFT" to KeyEvent.KEYCODE_A, "DOWN" to KeyEvent.KEYCODE_S, "RIGHT" to KeyEvent.KEYCODE_D,
        "FIRE" to KeyEvent.KEYCODE_F, "GLOO" to KeyEvent.KEYCODE_G, "RELOAD" to KeyEvent.KEYCODE_R,
        "JUMP" to KeyEvent.KEYCODE_SPACE, "CROUCH" to KeyEvent.KEYCODE_C, "SCOPE" to KeyEvent.KEYCODE_Q
    )

    class Preset {
        var sens = 1f
        var dpi = 800
        var cross = true
        val pos = HashMap<String, FloatArray>()
        /** Tombol keyboard per aksi (bisa diganti pengguna). */
        val keys = HashMap<String, Int>()
        init {
            for ((k, v) in DEFKEYS) keys[k] = v
            for ((k, v) in DEF) pos[k] = v.copyOf()
        }
    }

    fun load(ctx: Context, pkg: String): Preset {
        val p = Preset()
        val s = ctx.getSharedPreferences("gunz_keymap", Context.MODE_PRIVATE).getString(pkg, null) ?: return p
        try {
            val o = JSONObject(s)
            p.sens = o.optDouble("sens", 1.0).toFloat()
            p.dpi = o.optInt("dpi", 800)
            p.cross = o.optBoolean("cross", true)
            val ko = o.optJSONObject("keys")
            if (ko != null) for (k in DEFKEYS.keys) if (ko.has(k)) p.keys[k] = ko.getInt(k)
            val po = o.optJSONObject("pos")
            if (po != null) for (k in ACTIONS) {
                val a = po.optJSONArray(k) ?: continue
                p.pos[k] = floatArrayOf(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())
            }
        } catch (e: Exception) {
        }
        return p
    }

    fun save(ctx: Context, pkg: String, p: Preset) {
        val o = JSONObject()
        o.put("sens", p.sens.toDouble())
        o.put("dpi", p.dpi)
        o.put("cross", p.cross)
        val ko = JSONObject()
        for ((k, v) in p.keys) ko.put(k, v)
        o.put("keys", ko)
        val po = JSONObject()
        for ((k, v) in p.pos) po.put(k, org.json.JSONArray().put(v[0].toDouble()).put(v[1].toDouble()))
        o.put("pos", po)
        ctx.getSharedPreferences("gunz_keymap", Context.MODE_PRIVATE).edit().putString(pkg, o.toString()).apply()
    }
}

/** Layanan Aksesibilitas: menangkap keyboard/mouse lalu menyuntikkan sentuhan ke game. */
class KeyMapService : AccessibilityService() {

    companion object {
        @Volatile var instance: KeyMapService? = null
        @Volatile var game: String? = null
        private const val DUR = 50L
    }

    private class Slot {
        var x = 0f
        var y = 0f
        var tx = 0f
        var ty = 0f
        var want = false
        var stroke: GestureDescription.StrokeDescription? = null
        var pending: GestureDescription.StrokeDescription? = null
        var ending = false
    }

    private val slots = HashMap<String, Slot>()
    private var preset = KeyMap.Preset()
    private var overlay: KeymapOverlay? = null
    private var top: String? = null
    private var active = false
    private var pumping = false
    private var kw = false
    private var ka = false
    private var ks = false
    private var kd = false
    private var lookDx = 0f
    private var lookDy = 0f
    private var lastLook = 0L
    private val ui = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
        for (a in KeyMap.ACTIONS) slots[a] = Slot()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopMapping()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val p = e.packageName?.toString() ?: return
        if (p.startsWith("com.android.systemui") || p.contains("inputmethod")) return
        top = p
        if (p != packageName && game != p && !active) game = p
    }

    val isActive: Boolean get() = active

    fun currentPreset(): KeyMap.Preset = preset

    fun startMapping() {
        val g = game ?: return
        preset = KeyMap.load(this, g)
        active = true
        val o = overlay ?: KeymapOverlay(this, this).also { overlay = it }
        o.show(false)
    }

    fun stopMapping() {
        active = false
        releaseAll()
        overlay?.hide()
    }

    fun editLayout() {
        val g = game ?: return
        preset = KeyMap.load(this, g)
        val o = overlay ?: KeymapOverlay(this, this).also { overlay = it }
        active = false
        releaseAll()
        o.show(true)
    }

    fun saveEdited() {
        val g = game ?: return
        KeyMap.save(this, g, preset)
    }

    private fun releaseAll() {
        kw = false; ka = false; ks = false; kd = false
        for (s in slots.values) s.want = false
        if (!pumping) pump()
    }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        if (down && e.repeatCount == 0 && e.keyCode == KeyEvent.KEYCODE_F8) {
            if (active) stopMapping() else startMapping()
            return true
        }
        if (!active || (top != null && top != game)) return false
        if (e.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (down) stopMapping()
            return true
        }
        val act = preset.keys.entries.firstOrNull { it.value == e.keyCode }?.key ?: return false
        when (act) {
            "UP" -> kw = down
            "LEFT" -> ka = down
            "DOWN" -> ks = down
            "RIGHT" -> kd = down
            else -> slots[act]?.want = down
        }
        if (!pumping) pump()
        return true
    }

    /** Dipanggil overlay untuk event mouse yang ditangkap (pointer capture). */
    fun onMouse(ev: MotionEvent) {
        val k = preset.sens * preset.dpi / 800f
        if (ev.actionMasked == MotionEvent.ACTION_MOVE || ev.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            lookDx += ev.x * k
            lookDy += ev.y * k
            lastLook = SystemClock.uptimeMillis()
        }
        slots["FIRE"]?.want = (ev.buttonState and MotionEvent.BUTTON_PRIMARY) != 0
        slots["SCOPE"]?.want = (ev.buttonState and MotionEvent.BUTTON_SECONDARY) != 0
        if (!pumping) pump()
    }

    private fun pump() {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val b = GestureDescription.Builder()
        var n = 0
        val now = SystemClock.uptimeMillis()
        for (name in KeyMap.ACTIONS) {
            val s = slots[name] ?: continue
            val p = preset.pos[name] ?: continue
            val px = p[0] * w
            val py = p[1] * h
            when (name) {
                "JOY" -> {
                    var dx = (if (kd) 1f else 0f) - (if (ka) 1f else 0f)
                    var dy = (if (ks) 1f else 0f) - (if (kw) 1f else 0f)
                    val len = hypot(dx, dy)
                    if (len > 0f) { dx /= len; dy /= len }
                    val r = min(w, h) * 0.09f
                    s.want = len > 0f
                    s.tx = px + dx * r
                    s.ty = py + dy * r
                }
                "LOOK" -> {
                    if (lookDx != 0f || lookDy != 0f) {
                        val sx = if (s.stroke == null) px else s.x
                        val sy = if (s.stroke == null) py else s.y
                        s.tx = (sx + lookDx).coerceIn(w * 0.03f, w * 0.97f)
                        s.ty = (sy + lookDy).coerceIn(h * 0.03f, h * 0.97f)
                        lookDx = 0f
                        lookDy = 0f
                        s.want = true
                        // Dekat tepi layar: akhiri goresan, gerakan berikutnya mulai lagi dari pusat.
                        if (s.tx <= w * 0.05f || s.tx >= w * 0.95f || s.ty <= h * 0.05f || s.ty >= h * 0.95f) s.want = false
                    } else {
                        s.tx = s.x
                        s.ty = s.y
                        s.want = s.stroke != null && now - lastLook < 120
                    }
                }
                else -> {
                    s.tx = px
                    s.ty = py
                }
            }
            if (!s.want && s.stroke == null) continue
            val path = Path()
            if (s.want) {
                if (s.stroke == null) {
                    s.x = px
                    s.y = py
                }
                path.moveTo(s.x, s.y)
                path.lineTo(s.tx, s.ty)
                val st = s.stroke
                s.pending = if (st == null) GestureDescription.StrokeDescription(path, 0, DUR, true)
                else st.continueStroke(path, 0, DUR, true)
                s.ending = false
            } else {
                path.moveTo(s.x, s.y)
                path.lineTo(s.x, s.y)
                s.pending = s.stroke!!.continueStroke(path, 0, DUR, false)
                s.ending = true
            }
            b.addStroke(s.pending!!)
            n++
            if (n >= 9) break
        }
        if (n == 0) {
            pumping = false
            return
        }
        pumping = true
        val ok = dispatchGesture(b.build(), object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                for (s in slots.values) {
                    if (s.pending == null) continue
                    if (s.ending) s.stroke = null else { s.stroke = s.pending; s.x = s.tx; s.y = s.ty }
                    s.pending = null
                }
                ui.post { pump() }
            }

            override fun onCancelled(g: GestureDescription?) {
                for (s in slots.values) { s.stroke = null; s.pending = null; s.want = false }
                pumping = false
            }
        }, null)
        if (!ok) pumping = false
    }
}

/** Overlay layar penuh: crosshair saat bermain, penanda yang bisa digeser saat mengedit layout. */
class KeymapOverlay(ctx: Context, private val svc: KeyMapService) : View(ctx) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var edit = false
    private var attached = false
    private var drag: String? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val saveRect = RectF()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setOnCapturedPointerListener { _, ev ->
            svc.onMouse(ev)
            true
        }
    }

    private fun lp(touchable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val l = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT
        )
        l.gravity = Gravity.TOP or Gravity.START
        return l
    }

    fun show(editMode: Boolean) {
        edit = editMode
        try {
            if (attached) wm.updateViewLayout(this, lp(editMode)) else wm.addView(this, lp(editMode))
            attached = true
        } catch (e: Exception) {
            return
        }
        invalidate()
        if (!editMode) postDelayed({
            requestFocus()
            try { requestPointerCapture() } catch (e: Exception) { }
        }, 250)
    }

    fun hide() {
        try { releasePointerCapture() } catch (e: Exception) { }
        if (attached) {
            try { wm.removeView(this) } catch (e: Exception) { }
            attached = false
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pr = svc.currentPreset()
        if (!edit) {
            if (pr.cross) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                p.color = Color.parseColor("#E600FF66")
                val cx = w / 2f
                val cy = h / 2f
                val a = w * 0.012f
                c.drawLine(cx - a * 2.5f, cy, cx - a * 0.8f, cy, p)
                c.drawLine(cx + a * 0.8f, cy, cx + a * 2.5f, cy, p)
                c.drawLine(cx, cy - a * 2.5f, cx, cy - a * 0.8f, p)
                c.drawLine(cx, cy + a * 0.8f, cx, cy + a * 2.5f, p)
            }
            return
        }
        c.drawColor(Color.parseColor("#66000000"))
        val r = context.dp(26).toFloat()
        for ((k, v) in pr.pos) {
            p.style = Paint.Style.FILL
            p.color = Color.parseColor("#AA0078D4")
            val x = v[0] * w
            val y = v[1] * h
            c.drawCircle(x, y, if (k == "JOY") r * 1.8f else r, p)
            p.color = Color.WHITE
            p.textAlign = Paint.Align.CENTER
            p.textSize = context.dp(11).toFloat()
            c.drawText(KeyMap.LABEL[k] ?: k, x, y + p.textSize / 3f, p)
        }
        saveRect.set(w / 2f - context.dp(70), context.dp(12).toFloat(), w / 2f + context.dp(70), context.dp(52).toFloat())
        p.color = Color.parseColor("#0078D4")
        c.drawRoundRect(saveRect, 12f, 12f, p)
        p.color = Color.WHITE
        c.drawText("SIMPAN", saveRect.centerX(), saveRect.centerY() + p.textSize / 3f, p)
        c.drawText("Seret tombol ke posisi yang sama dengan tombol di game", w / 2f, saveRect.bottom + context.dp(20), p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!edit) return false
        val w = width.toFloat()
        val h = height.toFloat()
        val pr = svc.currentPreset()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (saveRect.contains(e.x, e.y)) {
                    svc.saveEdited()
                    hide()
                    return true
                }
                var best: String? = null
                var bd = context.dp(48).toFloat()
                for ((k, v) in pr.pos) {
                    val d = hypot(v[0] * w - e.x, v[1] * h - e.y)
                    if (d < bd) { bd = d; best = k }
                }
                drag = best
            }
            MotionEvent.ACTION_MOVE -> {
                val d = drag
                if (d != null) {
                    pr.pos[d] = floatArrayOf((e.x / w).coerceIn(0.02f, 0.98f), (e.y / h).coerceIn(0.02f, 0.98f))
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drag = null
        }
        return true
    }
}

/** Jendela "Keymapping" di dalam launcher (gaya Windows). */
fun keymapView(ctx: Context): View {
    val sv = ScrollView(ctx)
    sv.setBackgroundColor(Color.parseColor("#FF2B2B2B"))
    val col = LinearLayout(ctx)
    col.orientation = LinearLayout.VERTICAL
    col.setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
    sv.addView(col)

    fun label(t: String, size: Float = 13f): TextView {
        val tv = TextView(ctx)
        tv.text = t
        tv.setTextColor(Color.WHITE)
        tv.textSize = size
        tv.setPadding(0, ctx.dp(8), 0, ctx.dp(4))
        col.addView(tv)
        return tv
    }

    fun btn(t: String, onClick: () -> Unit) {
        val b = Button(ctx)
        b.text = t
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        col.addView(b)
    }

    val svc = KeyMapService.instance
    val game = KeyMapService.game
    label("Keymapping (seperti LDPlayer)", 16f)
    label(
        if (svc == null) "Layanan belum aktif. Aktifkan \"Gunz Keymapping\" di Aksesibilitas (izin ini wajib dari Android)."
        else "Game terpilih: " + (game ?: "belum ada - buka game dari launcher dulu")
    )
    if (svc == null) {
        btn("Aktifkan layanan Aksesibilitas") {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return sv
    }
    val pr = if (game != null) KeyMap.load(ctx, game) else KeyMap.Preset()
    fun persist() { if (game != null) KeyMap.save(ctx, game, pr) }

    val sensTxt = label("Sensitivitas mouse: " + "%.1f".format(pr.sens))
    val sens = SeekBar(ctx)
    sens.max = 49
    sens.progress = ((pr.sens - 0.1f) * 10f).toInt().coerceIn(0, 49)
    sens.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {
            pr.sens = 0.1f + v / 10f
            sensTxt.text = "Sensitivitas mouse: " + "%.1f".format(pr.sens)
        }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) { persist() }
    })
    col.addView(sens)

    val dpiTxt = label("DPI mouse: " + pr.dpi)
    val dpi = SeekBar(ctx)
    dpi.max = 28
    dpi.progress = ((pr.dpi - 400) / 100).coerceIn(0, 28)
    dpi.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {
            pr.dpi = 400 + v * 100
            dpiTxt.text = "DPI mouse: " + pr.dpi
        }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) { persist() }
    })
    col.addView(dpi)

    val cb = CheckBox(ctx)
    cb.text = "Tampilkan crosshair"
    cb.setTextColor(Color.WHITE)
    cb.isChecked = pr.cross
    cb.setOnCheckedChangeListener { _, v -> pr.cross = v; persist() }
    col.addView(cb)

    label("Tombol keyboard (ketuk lalu tekan tombol baru)")
    for ((act, name) in KeyMap.KEYLABEL) {
        val b = Button(ctx)
        b.isAllCaps = false
        fun refresh() { b.text = name + ": " + KeyEvent.keyCodeToString(pr.keys[act] ?: 0).removePrefix("KEYCODE_") }
        refresh()
        b.setOnClickListener {
            val dlg = android.app.AlertDialog.Builder(ctx).setTitle(name).setMessage("Tekan tombol keyboard yang diinginkan...")
                .setNegativeButton("Batal", null).create()
            dlg.setOnKeyListener { d, code, ev ->
                if (ev.action == KeyEvent.ACTION_DOWN && code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_ESCAPE) {
                    pr.keys[act] = code
                    persist()
                    refresh()
                    d.dismiss()
                    true
                } else false
            }
            dlg.show()
        }
        col.addView(b)
    }
    btn("Reset tombol & posisi ke default") {
        if (game != null) ctx.getSharedPreferences("gunz_keymap", Context.MODE_PRIVATE).edit().remove(game).apply()
        Toast.makeText(ctx, "Preset direset. Buka ulang jendela ini.", Toast.LENGTH_SHORT).show()
    }
    btn("Edit tata letak tombol (WASD, tembak, scope, gloo, reload, lompat)") { svc.editLayout() }
    btn("Mulai Keymapping (F8 = nyala/mati, Esc = berhenti)") { svc.startMapping() }
    label("Preset tersimpan otomatis per game.", 11f)
    return sv
}
