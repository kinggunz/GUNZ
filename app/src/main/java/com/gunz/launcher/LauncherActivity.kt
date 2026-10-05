package com.gunz.launcher

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tombol di taskbar: ikon + garis penanda (aktif / terbuka) seperti Windows. */
internal class TaskBtn(ctx: Context, val winId: String, icon: Drawable) : LinearLayout(ctx) {
    private val indicator = View(ctx)

    init {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val iv = ImageView(ctx)
        iv.setImageDrawable(icon)
        val ilp = LinearLayout.LayoutParams(ctx.dp(24), ctx.dp(24))
        ilp.topMargin = ctx.dp(7)
        addView(iv, ilp)
        val slp = LinearLayout.LayoutParams(ctx.dp(6), ctx.dp(3))
        slp.topMargin = ctx.dp(4)
        addView(indicator, slp)
        refresh(false, false)
    }

    fun refresh(open: Boolean, active: Boolean) {
        indicator.visibility = if (open) View.VISIBLE else View.INVISIBLE
        val lp = indicator.layoutParams as LinearLayout.LayoutParams
        lp.width = context.dp(if (active) 16 else 6)
        indicator.layoutParams = lp
        val ind = GradientDrawable()
        ind.setColor(Color.parseColor(if (active) "#4CC2FF" else "#99FFFFFF"))
        ind.cornerRadius = context.dp(2).toFloat()
        indicator.background = ind
        if (active) {
            val bg = GradientDrawable()
            bg.setColor(Color.parseColor("#26FFFFFF"))
            bg.cornerRadius = context.dp(6).toFloat()
            background = bg
        } else {
            background = hoverBg()
        }
    }
}

class LauncherActivity : Activity(), WindowFrame.Host {

    companion object {
        private const val REQ_IMAGE = 101
        private const val REQ_VIDEO = 102
        private const val REQ_STORAGE = 201
    }

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var wallpaper: WallpaperView
    private lateinit var layer: FrameLayout
    private lateinit var desktop: GridView
    private lateinit var taskRow: LinearLayout
    private lateinit var clock: TextView
    private lateinit var startPanel: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var startGrid: GridView
    private lateinit var trayFloat: ImageView
    private lateinit var anchor: View
    private lateinit var builtins: List<AppItem>
    private lateinit var searchPill: LinearLayout
    private lateinit var searchText: TextView
    private val trayOptional = ArrayList<View>()
    private var batteryIcon: BatteryIcon? = null
    private var taskbar: View? = null
    private var hotEdge: View? = null

    private val windows = ArrayList<WindowFrame>()
    private val taskButtons = HashMap<String, TaskBtn>()
    private val refreshers = HashMap<String, Runnable>()
    private val pinnedIds = listOf("explorer", "browser", "youtube")
    private var focused: WindowFrame? = null
    private var allApps: List<AppItem> = emptyList()
    private var startItems: List<AppItem> = emptyList()
    private var desktopItems: List<AppItem> = emptyList()
    private var desktopAdapter: IconAdapter? = null

    private var lastMouse = false
    private var lastX = 0f
    private var lastY = 0f
    private var clickedItem: AppItem? = null
    private var clickedAt = 0L
    private var comboUsed = false

    private val taskH: Int get() = dp(48)

    private val wallpapers = arrayOf(
        intArrayOf(Color.parseColor("#0B3C8C"), Color.parseColor("#1E88E5"), Color.parseColor("#7C4DFF")),
        intArrayOf(Color.parseColor("#0F2027"), Color.parseColor("#203A43"), Color.parseColor("#2C5364")),
        intArrayOf(Color.parseColor("#42275A"), Color.parseColor("#734B6D"), Color.parseColor("#D76D77")),
        intArrayOf(Color.parseColor("#134E5E"), Color.parseColor("#2E8B77"), Color.parseColor("#71B280"))
    )

    private class Spec(
        val title: String,
        val view: View,
        val w: Int,
        val h: Int,
        val pane: WebPane? = null,
        val dispose: (() -> Unit)? = null
    )

    private val tick = object : Runnable {
        override fun run() {
            val d = Date()
            val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
            val dt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d)
            clock.text = t + "\n" + dt
            batteryIcon?.refreshLevel()
            ui.postDelayed(this, 10000)
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("gunz", MODE_PRIVATE)
        builtins = listOf(
            AppItem("Penjelajah File", "", builtinIcon("explorer"), "explorer"),
            AppItem("Peramban", "", builtinIcon("browser"), "browser"),
            AppItem("YouTube", "", builtinIcon("youtube"), "youtube"),
            AppItem("Notepad", "", builtinIcon("notepad"), "notepad"),
            AppItem("Kalkulator", "", builtinIcon("calc"), "calc"),
            AppItem("Personalisasi", "", builtinIcon("personalize"), "personalize"),
            AppItem("Pengaturan", "", builtinIcon("settings"), "settings"),
            AppItem("Pusat Tindakan", "", builtinIcon("actioncenter"), "actioncenter"),
            AppItem("Keymapping", "", builtinIcon("keymap"), "keymap")
        )
        buildUi()
        setContentView(root)
        WinCursor.apply(root)
        NotifService.listener = { app, title, text, pkg -> runOnUiThread { showToast(app, title, text, pkg) } }
        applyAutoHide()
        applyWallpaper()
        applyTaskbarLayout()
        refreshDesktop()
        refreshStart()
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        try {
            stopService(Intent(this, FloatingButtonService::class.java))
        } catch (e: Exception) {
        }
        updateFloatTray()
        wallpaper.resumeVideo()
        for (r in refreshers.values) r.run()
        ui.removeCallbacks(tick)
        ui.post(tick)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
        wallpaper.pauseVideo()
        if (prefs.getBoolean("float_on", false) && Settings.canDrawOverlays(this)) {
            try {
                startService(Intent(this, FloatingButtonService::class.java))
            } catch (e: Exception) {
            }
        }
    }

    override fun onDestroy() {
        NotifService.listener = null
        wallpaper.release()
        for (w in ArrayList(windows)) w.onDispose?.invoke()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        closeStart()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTaskbarLayout()
        layer.post {
            for (w in windows) w.fit()
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Launcher tidak boleh keluar dengan tombol Back; Back hanya menutup menu Start.
        if (isStartOpen()) closeStart()
    }

    // ------------------------------------------------------------------ UI build

    private fun rounded(color: String, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Color.parseColor(color))
        g.cornerRadius = dp(radiusDp).toFloat()
        return g
    }

    private fun trayIcon(g: Glyph, sizeDp: Int, onClick: (() -> Unit)?): ImageView {
        val iv = ImageView(this)
        iv.setImageDrawable(GlyphIcon(g, Color.WHITE))
        iv.setPadding(dp(8), dp(12), dp(8), dp(12))
        iv.background = hoverBg()
        if (onClick != null) iv.setOnClickListener { onClick() }
        iv.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), MP)
        return iv
    }

    private fun buildUi() {
        root = FrameLayout(this)
        wallpaper = WallpaperView(this)
        wallpaper.onError = {
            runOnUiThread {
                prefs.edit().putString("wall_mode", "bloom").apply()
                applyWallpaper()
                toast("Video wallpaper tidak bisa diputar")
            }
        }
        root.addView(wallpaper, FrameLayout.LayoutParams(MP, MP))

        // ---- desktop icons
        desktop = GridView(this)
        desktop.numColumns = GridView.AUTO_FIT
        desktop.columnWidth = dp(88)
        desktop.verticalSpacing = dp(6)
        desktop.horizontalSpacing = dp(4)
        desktop.stretchMode = GridView.NO_STRETCH
        desktop.setPadding(dp(8), dp(12), dp(8), dp(8))
        desktop.clipToPadding = false
        desktop.isVerticalScrollBarEnabled = false
        desktop.setSelector(android.R.color.transparent)
        root.addView(desktop, FrameLayout.LayoutParams(MP, MP).also { it.bottomMargin = taskH })

        setupGrid(
            desktop,
            { desktopItems.getOrNull(it) },
            { item, x, y -> itemMenu(item, x, y) },
            { x, y -> desktopMenu(x, y) },
            { closeStart() }
        )
        desktop.setOnItemClickListener { _, _, pos, _ ->
            val item = desktopItems.getOrNull(pos)
            if (item != null) {
                closeStart()
                if (lastMouse) {
                    val now = SystemClock.uptimeMillis()
                    if (clickedItem === item && now - clickedAt < 500) {
                        clickedItem = null
                        desktopAdapter?.selected = null
                        openItem(item)
                    } else {
                        clickedItem = item
                        clickedAt = now
                        val ad = desktopAdapter
                        if (ad != null) {
                            ad.selected = item
                            ad.notifyDataSetChanged()
                        }
                    }
                } else {
                    openItem(item)
                }
            }
        }

        // ---- layer untuk jendela
        layer = FrameLayout(this)
        root.addView(layer, FrameLayout.LayoutParams(MP, MP).also { it.bottomMargin = taskH })

        // ---- menu Start
        startPanel = LinearLayout(this)
        startPanel.orientation = LinearLayout.VERTICAL
        startPanel.background = rounded("#F2202020", 14)
        startPanel.setPadding(dp(14), dp(14), dp(14), dp(10))
        startPanel.elevation = dp(24).toFloat()
        startPanel.visibility = View.GONE

        searchBox = EditText(this)
        searchBox.hint = "Ketik di sini untuk mencari"
        searchBox.setHintTextColor(Color.parseColor("#99FFFFFF"))
        searchBox.setTextColor(Color.WHITE)
        searchBox.textSize = 14f
        searchBox.setSingleLine(true)
        searchBox.imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        searchBox.background = rounded("#33FFFFFF", 20)
        searchBox.setPadding(dp(16), 0, dp(16), 0)
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                refreshStart()
            }
        })
        searchBox.setOnEditorActionListener { _, _, _ ->
            val first = startItems.firstOrNull()
            if (first != null) openItem(first)
            true
        }
        startPanel.addView(searchBox, LinearLayout.LayoutParams(MP, dp(40)))

        val label = TextView(this)
        label.text = "Aplikasi"
        label.setTextColor(Color.parseColor("#CCFFFFFF"))
        label.textSize = 13f
        label.typeface = android.graphics.Typeface.DEFAULT_BOLD
        label.setPadding(dp(4), dp(12), 0, dp(6))
        startPanel.addView(label, LinearLayout.LayoutParams(MP, WC))

        startGrid = GridView(this)
        startGrid.numColumns = 4
        startGrid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        startGrid.verticalSpacing = dp(4)
        startGrid.setSelector(android.R.color.transparent)
        startPanel.addView(startGrid, LinearLayout.LayoutParams(MP, 0, 1f))
        setupGrid(
            startGrid,
            { startItems.getOrNull(it) },
            { item, x, y -> itemMenu(item, x, y) },
            null,
            {}
        )
        startGrid.setOnItemClickListener { _, _, pos, _ ->
            val item = startItems.getOrNull(pos)
            if (item != null) openItem(item)
        }

        val footer = LinearLayout(this)
        footer.orientation = LinearLayout.HORIZONTAL
        footer.gravity = Gravity.CENTER_VERTICAL
        footer.setPadding(0, dp(8), 0, 0)
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_gunz_logo)
        footer.addView(logo, LinearLayout.LayoutParams(dp(32), dp(32)))
        val user = TextView(this)
        user.text = "Gunz User"
        user.setTextColor(Color.WHITE)
        user.textSize = 14f
        user.setPadding(dp(10), 0, 0, 0)
        footer.addView(user, LinearLayout.LayoutParams(0, WC, 1f))

        val settingsBtn = footerButton(Glyph.GEAR) { openWindow("settings") }
        footer.addView(settingsBtn, LinearLayout.LayoutParams(dp(44), dp(40)))
        val powerBtn = footerButton(Glyph.POWER) {
            val c = powerBtnAnchor
            if (c != null) {
                val loc = IntArray(2)
                c.getLocationOnScreen(loc)
                powerMenu(loc[0].toFloat(), loc[1].toFloat())
            }
        }
        powerBtnAnchor = powerBtn
        footer.addView(powerBtn, LinearLayout.LayoutParams(dp(44), dp(40)))
        startPanel.addView(footer, LinearLayout.LayoutParams(MP, WC))

        root.addView(startPanel, FrameLayout.LayoutParams(dp(390), dp(520), Gravity.BOTTOM or Gravity.START))

        // ---- taskbar
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(Color.parseColor("#F01C1C1C"))
        bar.elevation = dp(20).toFloat()
        bar.setPadding(dp(6), 0, 0, 0)

        val startBtn = ImageView(this)
        startBtn.setImageDrawable(PanesIcon(Color.parseColor("#4CC2FF")))
        startBtn.setPadding(dp(8), dp(8), dp(8), dp(8))
        startBtn.setOnClickListener { toggleStartFromButton() }
        startBtn.background = hoverBg()
        bar.addView(startBtn, LinearLayout.LayoutParams(dp(44), dp(40)))

        searchPill = LinearLayout(this)
        searchPill.orientation = LinearLayout.HORIZONTAL
        searchPill.gravity = Gravity.CENTER_VERTICAL
        searchPill.background = rounded("#22FFFFFF", 18)
        searchPill.setPadding(dp(11), 0, dp(11), 0)
        searchPill.setOnClickListener { openStart(true) }
        val pillIcon = ImageView(this)
        pillIcon.setImageDrawable(GlyphIcon(Glyph.SEARCH, Color.parseColor("#E6FFFFFF")))
        searchPill.addView(pillIcon, LinearLayout.LayoutParams(dp(18), dp(18)))
        searchText = TextView(this)
        searchText.text = "Cari"
        searchText.setTextColor(Color.parseColor("#CCFFFFFF"))
        searchText.textSize = 13f
        searchText.setPadding(dp(8), 0, 0, 0)
        searchPill.addView(searchText, LinearLayout.LayoutParams(WC, WC))
        val pillLp = LinearLayout.LayoutParams(WC, dp(34))
        pillLp.leftMargin = dp(6)
        pillLp.rightMargin = dp(8)
        bar.addView(searchPill, pillLp)

        taskRow = LinearLayout(this)
        taskRow.orientation = LinearLayout.HORIZONTAL
        taskRow.gravity = Gravity.CENTER_VERTICAL
        val sv = HorizontalScrollView(this)
        sv.isHorizontalScrollBarEnabled = false
        sv.addView(taskRow, FrameLayout.LayoutParams(WC, MP))
        bar.addView(sv, LinearLayout.LayoutParams(0, MP, 1f))
        for (id in pinnedIds) addTaskButton(id)

        val chevron = trayIcon(Glyph.CHEVRON_UP, 28, null)
        trayOptional.add(chevron)
        bar.addView(chevron)

        trayFloat = ImageView(this)
        trayFloat.setImageResource(R.drawable.ic_gunz_logo)
        trayFloat.setPadding(dp(8), dp(10), dp(8), dp(10))
        trayFloat.setOnClickListener { toggleFloat() }
        trayFloat.layoutParams = LinearLayout.LayoutParams(dp(36), MP)
        bar.addView(trayFloat)

        val wifi = trayIcon(Glyph.WIFI, 32) { openWindow("actioncenter") }
        trayOptional.add(wifi)
        bar.addView(wifi)
        val vol = trayIcon(Glyph.VOLUME, 32) { openWindow("actioncenter") }
        trayOptional.add(vol)
        bar.addView(vol)

        val battery = BatteryIcon(this)
        battery.layoutParams = LinearLayout.LayoutParams(dp(34), MP)
        battery.setOnClickListener { openWindow("actioncenter") }
        bar.addView(battery)
        batteryIcon = battery

        clock = TextView(this)
        clock.setTextColor(Color.WHITE)
        clock.textSize = 11f
        clock.gravity = Gravity.CENTER
        clock.setPadding(dp(8), 0, dp(8), 0)
        bar.addView(clock, LinearLayout.LayoutParams(WC, MP))

        bar.addView(trayIcon(Glyph.BELL, 32) { openWindow("actioncenter") })

        val showDesk = View(this)
        showDesk.setBackgroundColor(Color.parseColor("#44FFFFFF"))
        showDesk.setOnClickListener { showDesktop() }
        bar.addView(showDesk, LinearLayout.LayoutParams(dp(8), MP))

        root.addView(bar, FrameLayout.LayoutParams(MP, taskH, Gravity.BOTTOM))
        taskbar = bar
        val edge = View(this)
        edge.visibility = View.GONE
        edge.setOnHoverListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_HOVER_ENTER) peekTaskbar()
            false
        }
        edge.setOnClickListener { peekTaskbar() }
        root.addView(edge, FrameLayout.LayoutParams(MP, dp(10), Gravity.BOTTOM))
        hotEdge = edge

        anchor = View(this)
        root.addView(anchor, FrameLayout.LayoutParams(1, 1))
    }

    /** Sesuaikan taskbar untuk layar sempit (HP portrait) atau lebar (landscape / tablet). */
    private fun applyTaskbarLayout() {
        val wide = resources.configuration.screenWidthDp >= 600
        searchText.visibility = if (wide) View.VISIBLE else View.GONE
        for (v in trayOptional) v.visibility = if (wide) View.VISIBLE else View.GONE
    }

    private var powerBtnAnchor: View? = null

    private fun footerButton(g: Glyph, onClick: () -> Unit): ImageView {
        val t = ImageView(this)
        t.setImageDrawable(GlyphIcon(g, Color.WHITE))
        t.setPadding(dp(11), dp(10), dp(11), dp(10))
        t.background = hoverBg()
        t.setOnClickListener { onClick() }
        return t
    }

    /** Pasang deteksi mouse (klik kanan), tekan lama, dan tap latar pada GridView. */
    private fun setupGrid(
        gv: GridView,
        itemAt: (Int) -> AppItem?,
        onItemMenu: (AppItem, Float, Float) -> Unit,
        onBgMenu: ((Float, Float) -> Unit)?,
        onBgTap: () -> Unit
    ) {
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (gv.pointToPosition(e.x.toInt(), e.y.toInt()) == AdapterView.INVALID_POSITION) onBgTap()
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                if (gv.pointToPosition(e.x.toInt(), e.y.toInt()) == AdapterView.INVALID_POSITION) {
                    onBgMenu?.invoke(e.rawX, e.rawY)
                }
            }
        })
        gv.setOnTouchListener { _, e ->
            lastMouse = e.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE
            lastX = e.rawX
            lastY = e.rawY
            if (e.actionMasked == MotionEvent.ACTION_DOWN && (e.buttonState and MotionEvent.BUTTON_SECONDARY) != 0) {
                val item = itemAt(gv.pointToPosition(e.x.toInt(), e.y.toInt()))
                if (item != null) onItemMenu(item, e.rawX, e.rawY) else onBgMenu?.invoke(e.rawX, e.rawY)
                true
            } else {
                gd.onTouchEvent(e)
                false
            }
        }
        gv.setOnItemLongClickListener { _, _, pos, _ ->
            val item = itemAt(pos)
            if (item != null) {
                onItemMenu(item, lastX, lastY)
                true
            } else {
                false
            }
        }
    }

    // ------------------------------------------------------------------ adapter

    private inner class IconAdapter(private val items: List<AppItem>) : BaseAdapter() {
        var selected: AppItem? = null

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = items[position]
            val cell = LinearLayout(this@LauncherActivity)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(dp(4), dp(8), dp(4), dp(6))
            cell.layoutParams = AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, dp(92))
            if (item === selected) cell.setBackgroundColor(Color.parseColor("#55FFFFFF"))

            val img = ImageView(this@LauncherActivity)
            img.setImageDrawable(item.icon)
            cell.addView(img, LinearLayout.LayoutParams(dp(44), dp(44)))

            val tv = TextView(this@LauncherActivity)
            tv.text = if (item.label.endsWith(".exe")) item.label else item.label + ".exe"
            tv.setTextColor(Color.WHITE)
            tv.textSize = 11.5f
            tv.gravity = Gravity.CENTER
            tv.maxLines = 2
            tv.ellipsize = android.text.TextUtils.TruncateAt.END
            tv.setShadowLayer(3f, 1f, 1f, Color.BLACK)
            val lp = LinearLayout.LayoutParams(MP, WC)
            lp.topMargin = dp(4)
            cell.addView(tv, lp)

            cell.setOnHoverListener { v, e ->
                if (e.actionMasked == MotionEvent.ACTION_HOVER_ENTER) {
                    v.setBackgroundColor(Color.parseColor("#33FFFFFF"))
                } else if (e.actionMasked == MotionEvent.ACTION_HOVER_EXIT) {
                    v.setBackgroundColor(if (item === selected) Color.parseColor("#55FFFFFF") else Color.TRANSPARENT)
                }
                false
            }
            return cell
        }
    }

    // ------------------------------------------------------------------ apps & desktop data

    private fun loadApps() {
        Thread {
            try {
                val pm = packageManager
                val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val list = pm.queryIntentActivities(q, 0)
                    .filter { it.activityInfo.packageName != packageName }
                    .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm), null) }
                    .distinctBy { it.pkg }
                    .sortedBy { it.label.lowercase() }
                runOnUiThread {
                    allApps = list
                    refreshDesktop()
                    refreshStart()
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Gagal memuat daftar aplikasi") }
            }
        }.start()
    }

    private fun pins(): Set<String> = prefs.getStringSet("pins", emptySet()) ?: emptySet()

    private fun togglePin(pkg: String) {
        val s = HashSet<String>(pins())
        val pinned = !s.add(pkg)
        if (pinned) s.remove(pkg)
        prefs.edit().putStringSet("pins", s).apply()
        refreshDesktop()
        toast(if (pinned) "Dilepas dari desktop" else "Disematkan ke desktop")
    }

    private fun refreshDesktop() {
        val p = pins()
        desktopItems = builtins + allApps.filter { p.contains(it.pkg) }
        val ad = IconAdapter(desktopItems)
        desktopAdapter = ad
        desktop.adapter = ad
    }

    private fun refreshStart() {
        val q = searchBox.text.toString().trim().lowercase()
        startItems = (builtins + allApps).filter { q.isEmpty() || it.label.lowercase().contains(q) }
        startGrid.adapter = IconAdapter(startItems)
    }

    // ------------------------------------------------------------------ wallpaper

    /** Default selalu Bloom biru; foto/video dari galeri bisa dipilih, dan "Kembalikan" memulihkan Bloom. */
    private fun applyWallpaper() {
        when (prefs.getString("wall_mode", "bloom")) {
            "image" -> {
                val bmp = decodeFile(File(filesDir, "wallpaper.jpg"))
                if (bmp != null) wallpaper.showImage(bmp) else wallpaper.showDrawable(BloomWallpaper())
            }
            "video" -> {
                val u = prefs.getString("wall_uri", null)
                if (u != null) wallpaper.showVideo(Uri.parse(u)) else wallpaper.showDrawable(BloomWallpaper())
            }
            else -> wallpaper.showDrawable(BloomWallpaper())
        }
    }

    private fun gradientWallpaper() = applyWallpaper()

    private fun decodeFile(f: File): Bitmap? {
        if (!f.exists()) return null
        return try {
            val dm = resources.displayMetrics
            val target = maxOf(dm.widthPixels, dm.heightPixels)
            val b = BitmapFactory.Options()
            b.inJustDecodeBounds = true
            BitmapFactory.decodeFile(f.absolutePath, b)
            var sample = 1
            while (maxOf(b.outWidth, b.outHeight) / sample > target * 2) sample *= 2
            val o = BitmapFactory.Options()
            o.inSampleSize = sample
            BitmapFactory.decodeFile(f.absolutePath, o)
        } catch (e: Throwable) {
            null
        }
    }

    private fun nextWallpaper() {
        prefs.edit().putString("wall_mode", "bloom").apply()
        applyWallpaper()
    }

    private fun resetWallpaper() {
        prefs.edit().putString("wall_mode", "bloom").apply()
        applyWallpaper()
        toast("Wallpaper dikembalikan ke Bloom biru")
    }

    private fun pickWallpaper(video: Boolean) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = if (video) "video/*" else "image/*"
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        try {
            startActivityForResult(i, if (video) REQ_VIDEO else REQ_IMAGE)
        } catch (e: Exception) {
            toast("Galeri tidak tersedia di perangkat ini")
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_IMAGE -> setImageWallpaper(uri)
            REQ_VIDEO -> setVideoWallpaper(uri)
        }
    }

    private fun setImageWallpaper(uri: Uri) {
        toast("Menyiapkan wallpaper...")
        Thread {
            try {
                val dm = resources.displayMetrics
                val target = maxOf(dm.widthPixels, dm.heightPixels)
                val bounds = BitmapFactory.Options()
                bounds.inJustDecodeBounds = true
                contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > target * 2) sample *= 2
                val opts = BitmapFactory.Options()
                opts.inSampleSize = sample
                var bmp: Bitmap = contentResolver.openInputStream(uri).use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: throw IllegalStateException("decode gagal")
                val orient = contentResolver.openInputStream(uri).use { s ->
                    if (s == null) {
                        ExifInterface.ORIENTATION_NORMAL
                    } else {
                        ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    }
                }
                val deg = when (orient) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (deg != 0f) {
                    val m = Matrix()
                    m.postRotate(deg)
                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                }
                FileOutputStream(File(filesDir, "wallpaper.jpg")).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                prefs.edit().putString("wall_mode", "image").apply()
                runOnUiThread {
                    applyWallpaper()
                    toast("Wallpaper foto diganti")
                }
            } catch (e: Throwable) {
                runOnUiThread { toast("Gagal memuat gambar") }
            }
        }.start()
    }

    private fun setVideoWallpaper(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
        }
        prefs.edit().putString("wall_mode", "video").putString("wall_uri", uri.toString()).apply()
        applyWallpaper()
        toast("Wallpaper video diatur")
    }

    // ------------------------------------------------------------------ open things

    private fun openItem(item: AppItem) {
        closeStart()
        val id = item.id
        if (id != null) openWindow(id) else launchApp(item.pkg)
    }

    private fun launchApp(pkg: String, modeOverride: String? = null) {
        KeyMapService.game = pkg
        val mode = modeOverride ?: (prefs.getString("winmode", "window") ?: "window")
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) {
            toast("Aplikasi tidak bisa dibuka")
            return
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (mode != "fullscreen" && prefs.getBoolean("freeform", true)) {
                val dm = resources.displayMetrics
                val bl = mode == "borderless"
                val w = if (bl) dm.widthPixels else (dm.widthPixels * 0.6f).toInt()
                val h = if (bl) dm.heightPixels else (dm.heightPixels * 0.7f).toInt()
                val l = (dm.widthPixels - w) / 2
                val t = if (bl) 0 else dp(24)
                val opts = ActivityOptions.makeBasic()
                opts.setLaunchBounds(Rect(l, t, l + w, t + h))
                try {
                    // Mode jendela bebas (5). Dipakai hanya jika sistem mengizinkan; kalau tidak, diabaikan.
                    val m = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                    m.invoke(opts, 5)
                } catch (e: Throwable) {
                }
                startActivity(i, opts.toBundle())
            } else {
                startActivity(i)
            }
        } catch (e: Exception) {
            try {
                startActivity(i)
            } catch (e2: Exception) {
                toast("Gagal membuka aplikasi")
            }
        }
    }

    private fun openSettings(action: String, withPkg: Boolean = false) {
        try {
            val i = Intent(action)
            if (withPkg) i.data = Uri.parse("package:$packageName")
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            toast("Tidak tersedia di perangkat ini")
        }
    }

    private fun appInfo(pkg: String) {
        try {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            toast("Info aplikasi tidak tersedia")
        }
    }

    private fun exitLauncher() {
        toast("Pilih launcher lain sebagai Beranda. Untuk masuk lagi buka aplikasi Gunz Launcher.")
        openSettings(Settings.ACTION_HOME_SETTINGS)
    }

    private fun expandNotifications() {
        try {
            val sb = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        } catch (e: Throwable) {
            toast("Panel notifikasi tidak tersedia di perangkat ini")
        }
    }

    private fun ensureStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                toast("Izinkan akses semua file untuk Penjelajah File")
                openSettings(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, true)
            }
        } else if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_STORAGE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) refreshers["explorer"]?.run()
    }

    private fun freeformGuide() {
        AlertDialog.Builder(this)
            .setTitle("Mengambangkan aplikasi lain")
            .setMessage(
                "YouTube dan Peramban bawaan launcher SELALU bisa mengambang (juga di atas aplikasi lain) tanpa langkah apa pun.\n\n" +
                    "Untuk aplikasi pihak ketiga (Chrome, WhatsApp, dll) Android 9 butuh pengaturan sistem:\n" +
                    "1. Buka Pengaturan > Opsi pengembang.\n" +
                    "2. Aktifkan \"Aktifkan jendela bentuk bebas\" dan \"Paksa aktivitas dapat diubah ukurannya\".\n" +
                    "3. Mulai ulang HP.\n" +
                    "4. Buka aplikasi dari launcher ini, dan jendelanya tampil mengambang."
            )
            .setPositiveButton("Buka Opsi pengembang") { _, _ ->
                openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    // ------------------------------------------------------------------ menus

    private fun showMenu(x: Float, y: Float, entries: List<Pair<String, () -> Unit>>) {
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        anchor.x = x - loc[0]
        anchor.y = y - loc[1]
        val pm = PopupMenu(this, anchor)
        for ((i, en) in entries.withIndex()) pm.menu.add(0, i, i, en.first)
        pm.setOnMenuItemClickListener { mi ->
            entries[mi.itemId].second()
            true
        }
        pm.show()
    }

    private fun itemMenu(item: AppItem, x: Float, y: Float) {
        val e = ArrayList<Pair<String, () -> Unit>>()
        e.add(Pair("Buka", { openItem(item) }))
        if (item.id == null) {
            e.add(Pair("Buka - Jendela", { closeStart(); launchApp(item.pkg, "window") }))
            e.add(Pair("Buka - Borderless", { closeStart(); launchApp(item.pkg, "borderless") }))
            e.add(Pair("Buka - Layar penuh", { closeStart(); launchApp(item.pkg, "fullscreen") }))
            val pinned = pins().contains(item.pkg)
            e.add(Pair(if (pinned) "Lepas dari desktop" else "Sematkan ke desktop", { togglePin(item.pkg) }))
            e.add(Pair("Info aplikasi", { appInfo(item.pkg) }))
        }
        showMenu(x, y, e)
    }

    private fun desktopMenu(x: Float, y: Float) {
        val e = ArrayList<Pair<String, () -> Unit>>()
        e.add(Pair("Segarkan", { loadApps() }))
        e.add(Pair("Penjelajah File", { openWindow("explorer") }))
        e.add(Pair("Peramban", { openWindow("browser") }))
        e.add(Pair("YouTube", { openWindow("youtube") }))
        e.add(Pair("Notepad", { openWindow("notepad") }))
        e.add(Pair("Kalkulator", { openWindow("calc") }))
        e.add(Pair("Personalisasi (wallpaper)", { openWindow("personalize") }))
        e.add(Pair("Pusat Tindakan", { openWindow("actioncenter") }))
        e.add(Pair("Keymapping", { openWindow("keymap") }))
        e.add(Pair("Pengaturan", { openWindow("settings") }))
        showMenu(x, y, e)
    }

    private fun powerMenu(x: Float, y: Float) {
        val e = ArrayList<Pair<String, () -> Unit>>()
        e.add(Pair("Keluar dari Launcher", { exitLauncher() }))
        e.add(Pair("Tutup semua jendela", { closeAllWindows() }))
        e.add(Pair("Segarkan daftar aplikasi", { loadApps() }))
        showMenu(x, y, e)
    }

    // ------------------------------------------------------------------ start menu

    private fun isStartOpen(): Boolean = startPanel.visibility == View.VISIBLE

    private fun toggleStart() {
        if (isStartOpen()) closeStart() else openStart(true)
    }

    private fun toggleStartFromButton() {
        if (isStartOpen()) closeStart() else openStart(false)
    }

    private fun openStart(showKeyboard: Boolean) {
        val dm = resources.displayMetrics
        val lp = startPanel.layoutParams as FrameLayout.LayoutParams
        lp.width = minOf(dp(390), dm.widthPixels - dp(16))
        lp.height = minOf(dp(520), dm.heightPixels - taskH - dp(40))
        lp.gravity = Gravity.BOTTOM or Gravity.START
        lp.leftMargin = dp(8)
        lp.bottomMargin = taskH + dp(8)
        startPanel.layoutParams = lp
        searchBox.setText("")
        refreshStart()
        startPanel.visibility = View.VISIBLE
        searchBox.requestFocus()
        if (showKeyboard) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeStart() {
        if (!isStartOpen()) return
        startPanel.visibility = View.GONE
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(root.windowToken, 0)
    }

    // ------------------------------------------------------------------ floating button

    private fun updateFloatTray() {
        trayFloat.alpha = if (prefs.getBoolean("float_on", false)) 1f else 0.4f
    }

    private fun toggleFloat() {
        val on = !prefs.getBoolean("float_on", false)
        if (on && !Settings.canDrawOverlays(this)) {
            toast("Izinkan \"Tampil di atas aplikasi lain\" untuk Gunz Launcher, lalu ketuk lagi")
            openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true)
            return
        }
        prefs.edit().putBoolean("float_on", on).apply()
        updateFloatTray()
        toast(if (on) "Tombol mengambang aktif (muncul saat membuka aplikasi lain)" else "Tombol mengambang dimatikan")
    }

    private fun toggleFloatWeb() {
        val on = !prefs.getBoolean("float_web", false)
        if (on && !Settings.canDrawOverlays(this)) {
            toast("Izinkan \"Tampil di atas aplikasi lain\" untuk Gunz Launcher, lalu ketuk lagi")
            openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true)
            return
        }
        prefs.edit().putBoolean("float_web", on).apply()
        toast(if (on) "YouTube & Peramban sekarang mengambang di atas semua aplikasi" else "YouTube & Peramban dibuka di dalam launcher")
    }

    // ------------------------------------------------------------------ windows

    override fun layerWidth(): Int = if (layer.width > 0) layer.width else resources.displayMetrics.widthPixels

    override fun layerHeight(): Int =
        if (layer.height > 0) layer.height else resources.displayMetrics.heightPixels - taskH

    override fun onFocus(w: WindowFrame) {
        if (!windows.contains(w)) return
        if (isStartOpen()) closeStart()
        focused = w
        for (f in windows) f.setActive(f === w)
        if (layer.getChildAt(layer.childCount - 1) !== w) w.bringToFront()
        updateTasks()
    }

    override fun onClose(w: WindowFrame) {
        if (!windows.remove(w)) return
        val id = w.tag as? String
        if (id != null) {
            refreshers.remove(id)
            if (!pinnedIds.contains(id)) {
                val tb = taskButtons.remove(id)
                if (tb != null) taskRow.removeView(tb)
            }
        }
        hideKeyboard()
        if (focused === w) {
            focused = null
            val next = windows.lastOrNull { !it.minimized }
            if (next != null) onFocus(next)
        }
        updateTasks()
        w.animateOut {
            layer.removeView(w)
            w.onDispose?.invoke()
        }
    }

    override fun onMinimize(w: WindowFrame) {
        if (focused === w) focused = null
        hideKeyboard()
        updateTasks()
    }

    private fun openWindow(id: String) {
        closeStart()
        val existing = windows.firstOrNull { it.tag == id }
        if (existing != null) {
            if (existing.minimized) existing.restore() else onFocus(existing)
            return
        }
        val isWeb = id == "browser" || id == "youtube"
        if (isWeb && prefs.getBoolean("float_web", false) && Settings.canDrawOverlays(this)) {
            FloatingWindowService.open(this, id, null)
            return
        }
        val lw = layerWidth()
        val lh = layerHeight()
        val spec: Spec = when (id) {
            "notepad" -> Spec("Notepad", notepadView(this), 420, 360)
            "calc" -> Spec("Kalkulator", calcView(this), 300, 440)
            "explorer" -> {
                ensureStorage()
                Spec(
                    "Penjelajah File",
                    explorerView(this, { ensureStorage() }, { refreshers["explorer"] = it }),
                    480, 420
                )
            }
            "settings" -> Spec("Pengaturan", settingsView(), 420, 480)
            "personalize" -> Spec("Personalisasi", personalizeView(), 400, 260)
            "actioncenter" -> Spec("Pusat Tindakan", actionCenterView(this), 360, 440)
            "keymap" -> Spec("Keymapping", keymapView(this), 400, 480)
            "browser" -> {
                val p = WebPane(this, FloatingWindowService.HOME_BROWSER, FloatingWindowService.HOME_BROWSER, true)
                Spec("Peramban", p.view, 560, 420, p) { p.destroy() }
            }
            "youtube" -> {
                val p = WebPane(this, FloatingWindowService.HOME_YOUTUBE, FloatingWindowService.HOME_YOUTUBE, false)
                Spec("YouTube", p.view, 520, 380, p) { p.destroy() }
            }
            else -> return
        }
        val w = minOf(dp(spec.w), lw - dp(16))
        val h = minOf(dp(spec.h), lh - dp(16))
        val pane = spec.pane
        val popOut: (() -> Unit)? = if (pane != null) ({ popOutWeb(id, pane) }) else null
        val frame = WindowFrame(this, spec.title, builtinIcon(id), spec.view, this, popOut)
        frame.tag = id
        frame.onDispose = spec.dispose
        val off = (windows.size % 6) * dp(28)
        layer.addView(frame, FrameLayout.LayoutParams(w, h))
        frame.x = (dp(32) + off).coerceAtMost(maxOf(0, lw - w)).toFloat()
        frame.y = (dp(24) + off).coerceAtMost(maxOf(0, lh - h)).toFloat()
        windows.add(frame)
        addTaskButton(id)
        onFocus(frame)
        frame.animateIn()
    }

    /** Pindahkan jendela YouTube / Peramban ke lapisan mengambang di atas semua aplikasi. */
    private fun popOutWeb(id: String, pane: WebPane) {
        if (!Settings.canDrawOverlays(this)) {
            toast("Izinkan \"Tampil di atas aplikasi lain\" untuk Gunz Launcher, lalu ketuk tombol ini lagi")
            openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true)
            return
        }
        val url = pane.currentUrl()
        val frame = windows.firstOrNull { it.tag == id }
        if (frame != null) onClose(frame)
        FloatingWindowService.open(this, id, url)
        toast("Jendela mengambang aktif, tetap tampil di atas aplikasi lain")
    }

    private fun addTaskButton(id: String) {
        if (taskButtons.containsKey(id)) return
        val btn = TaskBtn(this, id, builtinIcon(id))
        btn.setOnClickListener { onTaskClick(id) }
        val lp = LinearLayout.LayoutParams(dp(48), MP)
        lp.rightMargin = dp(2)
        taskRow.addView(btn, lp)
        taskButtons[id] = btn
    }

    private fun onTaskClick(id: String) {
        val w = windows.firstOrNull { it.tag == id }
        if (w == null) {
            openWindow(id)
        } else if (w.minimized) {
            w.restore()
        } else if (focused === w) {
            w.minimize()
        } else {
            onFocus(w)
        }
    }

    private fun updateTasks() {
        for ((id, btn) in taskButtons) {
            val w = windows.firstOrNull { it.tag == id }
            val open = w != null
            val active = w != null && !w.minimized && focused === w
            btn.refresh(open, active)
        }
    }

    private fun showDesktop() {
        closeStart()
        for (w in windows) if (!w.minimized) w.minimize()
    }

    private fun closeAllWindows() {
        for (w in ArrayList(windows)) onClose(w)
    }

    private fun cycleWindows() {
        if (windows.isEmpty()) return
        val idx = windows.indexOf(focused)
        val next = windows[(idx + 1) % windows.size]
        if (next.minimized) next.restore() else onFocus(next)
    }

    // ------------------------------------------------------------------ panels

    private fun panel(): Pair<ScrollView, LinearLayout> {
        val sv = ScrollView(this)
        sv.setBackgroundColor(Color.parseColor("#FF202020"))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(12), dp(10), dp(12), dp(10))
        sv.addView(col)
        return Pair(sv, col)
    }

    private fun heading(col: LinearLayout, text: String) {
        val head = TextView(this)
        head.text = text
        head.setTextColor(Color.WHITE)
        head.textSize = 16f
        head.typeface = android.graphics.Typeface.DEFAULT_BOLD
        head.setPadding(dp(4), dp(4), dp(4), dp(10))
        col.addView(head, LinearLayout.LayoutParams(MP, WC))
    }

    private fun menuRow(col: LinearLayout, g: Glyph, text: String, onClick: () -> Unit) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(12), dp(12), dp(12), dp(12))
        r.background = rounded("#33FFFFFF", 8)
        val iv = ImageView(this)
        iv.setImageDrawable(GlyphIcon(g, Color.WHITE))
        r.addView(iv, LinearLayout.LayoutParams(dp(20), dp(20)))
        val t = TextView(this)
        t.text = text
        t.setTextColor(Color.WHITE)
        t.textSize = 14f
        val tlp = LinearLayout.LayoutParams(0, WC, 1f)
        tlp.leftMargin = dp(12)
        r.addView(t, tlp)
        r.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(MP, WC)
        lp.bottomMargin = dp(8)
        col.addView(r, lp)
    }

    private fun personalizeView(): View {
        val (sv, col) = panel()
        heading(col, "Latar belakang desktop (default: Bloom biru)")
        menuRow(col, Glyph.IMAGE, "Pilih foto dari galeri") { pickWallpaper(false) }
        menuRow(col, Glyph.VIDEO, "Pilih video dari galeri (wallpaper bergerak)") { pickWallpaper(true) }
        menuRow(col, Glyph.RELOAD, "Kembalikan ke Windows Bloom biru") { resetWallpaper() }
        return sv
    }

    private fun settingsView(): View {
        val (sv, col) = panel()
        heading(col, "Pengaturan - Gunz Launcher v3.0")
        menuRow(col, Glyph.IMAGE, "Personalisasi (ganti wallpaper)") { openWindow("personalize") }
        menuRow(col, Glyph.BELL, "Pusat Tindakan (Wi-Fi, Bluetooth, Volume, Kecerahan, Data, GPS)") { openWindow("actioncenter") }
        menuRow(col, Glyph.GRIP, "Keymapping keyboard & mouse") { openWindow("keymap") }
        menuRow(col, Glyph.BELL, "Notifikasi gaya Windows (beri akses notifikasi)") { openNotificationAccess(this) }
        menuRow(col, Glyph.MINIMIZE, "Taskbar otomatis sembunyi (nyala/mati)") {
            val v = !prefs.getBoolean("autohide", false)
            prefs.edit().putBoolean("autohide", v).apply()
            applyAutoHide()
            toast(if (v) "Taskbar auto-hide: nyala" else "Taskbar auto-hide: mati")
        }
        menuRow(col, Glyph.MAXIMIZE, "Mode buka aplikasi: " + modeLabel(prefs.getString("winmode", "window") ?: "window") + " (ketuk untuk ganti)") {
            val next = when (prefs.getString("winmode", "window")) { "window" -> "borderless"; "borderless" -> "fullscreen"; else -> "window" }
            prefs.edit().putString("winmode", next).apply()
            toast("Mode: " + modeLabel(next))
        }
        menuRow(col, Glyph.POPOUT, "Tombol mengambang (keluar-masuk launcher)") { toggleFloat() }
        menuRow(col, Glyph.PLAY, "YouTube & Peramban selalu mengambang (nyala/mati)") { toggleFloatWeb() }
        menuRow(col, Glyph.GLOBE, "Cara mengaktifkan jendela bebas untuk aplikasi lain") { freeformGuide() }
        menuRow(col, Glyph.POPOUT, "Izin tampil di atas aplikasi lain") { openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) }
        menuRow(col, Glyph.POWER, "Ganti Beranda / Keluar Launcher") { exitLauncher() }
        return sv
    }

    private fun modeLabel(m: String) = when (m) { "borderless" -> "Borderless"; "fullscreen" -> "Layar penuh"; else -> "Jendela" }

    // ------------------------------------------------------------------ keyboard

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_META_LEFT || keyCode == KeyEvent.KEYCODE_META_RIGHT) {
            comboUsed = false
            return true
        }
        val meta = event.isMetaPressed
        val alt = event.isAltPressed
        val ctrl = event.isCtrlPressed
        when {
            meta && keyCode == KeyEvent.KEYCODE_D -> {
                comboUsed = true
                showDesktop()
                return true
            }
            meta && keyCode == KeyEvent.KEYCODE_E -> {
                comboUsed = true
                openWindow("explorer")
                return true
            }
            meta && keyCode == KeyEvent.KEYCODE_I -> {
                comboUsed = true
                openWindow("settings")
                return true
            }
            alt && keyCode == KeyEvent.KEYCODE_F4 -> {
                val f = focused
                if (f != null) onClose(f)
                return true
            }
            alt && keyCode == KeyEvent.KEYCODE_TAB -> {
                cycleWindows()
                return true
            }
            ctrl && keyCode == KeyEvent.KEYCODE_ESCAPE -> {
                toggleStart()
                return true
            }
            keyCode == KeyEvent.KEYCODE_ESCAPE && isStartOpen() -> {
                closeStart()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_META_LEFT || keyCode == KeyEvent.KEYCODE_META_RIGHT) {
            if (!comboUsed) toggleStart()
            comboUsed = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun applyAutoHide() {
        val on = prefs.getBoolean("autohide", false)
        val tb = taskbar ?: return
        val lpL = layer.layoutParams as FrameLayout.LayoutParams
        lpL.bottomMargin = if (on) 0 else taskH
        layer.layoutParams = lpL
        val lpD = desktop.layoutParams as FrameLayout.LayoutParams
        lpD.bottomMargin = if (on) 0 else taskH
        desktop.layoutParams = lpD
        tb.translationY = if (on) taskH.toFloat() else 0f
        hotEdge?.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun peekTaskbar() {
        val tb = taskbar ?: return
        tb.animate().translationY(0f).setDuration(120).start()
        ui.removeCallbacks(hideBar)
        ui.postDelayed(hideBar, 3000)
    }

    private val hideBar = Runnable {
        if (prefs.getBoolean("autohide", false) && !isStartOpen()) {
            taskbar?.animate()?.translationY(taskH.toFloat())?.setDuration(120)?.start()
        }
    }

    /** Notifikasi masuk (SMS, WhatsApp, dll) tampil sebagai toast Windows di pojok kanan bawah. */
    private fun showToast(app: String, title: String, text: String, pkg: String) {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = rounded("#F22B2B2B", 8)
        card.setPadding(dp(14), dp(10), dp(14), dp(12))
        card.elevation = dp(24).toFloat()
        val a = TextView(this)
        a.text = app
        a.setTextColor(Color.parseColor("#B3FFFFFF"))
        a.textSize = 11f
        card.addView(a)
        val t = TextView(this)
        t.text = title
        t.setTextColor(Color.WHITE)
        t.textSize = 14f
        t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        t.maxLines = 1
        card.addView(t)
        val b = TextView(this)
        b.text = text
        b.setTextColor(Color.parseColor("#E6FFFFFF"))
        b.textSize = 12.5f
        b.maxLines = 3
        card.addView(b)
        val lp = FrameLayout.LayoutParams(minOf(dp(340), resources.displayMetrics.widthPixels - dp(16)), WC, Gravity.BOTTOM or Gravity.END)
        lp.bottomMargin = taskH + dp(10)
        lp.rightMargin = dp(8)
        root.addView(card, lp)
        card.setOnClickListener {
            root.removeView(card)
            launchApp(pkg, "window")
        }
        card.alpha = 0f
        card.animate().alpha(1f).setDuration(150).start()
        ui.postDelayed({ if (card.parent != null) root.removeView(card) }, 6000)
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }
}
