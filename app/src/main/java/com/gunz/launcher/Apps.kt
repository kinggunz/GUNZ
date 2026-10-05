package com.gunz.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Environment
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.io.File

private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
private const val DEL = "DEL"

fun notepadView(ctx: Context): View {
    val prefs = ctx.getSharedPreferences("gunz", Context.MODE_PRIVATE)
    val et = EditText(ctx)
    et.setText(prefs.getString("note", ""))
    et.setTextColor(Color.BLACK)
    et.setHintTextColor(Color.GRAY)
    et.hint = "Ketik catatan di sini..."
    et.setBackgroundColor(Color.WHITE)
    et.gravity = Gravity.TOP or Gravity.START
    et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    et.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
    et.setPadding(ctx.dp(10), ctx.dp(8), ctx.dp(10), ctx.dp(8))
    et.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            prefs.edit().putString("note", s?.toString() ?: "").apply()
        }
    })
    return et
}

fun calcView(ctx: Context): View {
    val root = LinearLayout(ctx)
    root.orientation = LinearLayout.VERTICAL
    root.setBackgroundColor(Color.parseColor("#202020"))

    val display = TextView(ctx)
    display.text = "0"
    display.setTextColor(Color.WHITE)
    display.textSize = 32f
    display.maxLines = 1
    display.gravity = Gravity.END or Gravity.BOTTOM
    display.setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(16), ctx.dp(8))
    root.addView(display, LinearLayout.LayoutParams(MATCH, 0, 0.8f))

    var cur = "0"
    var acc: Double? = null
    var op: String? = null
    var fresh = true

    fun fmt(d: Double): String = when {
        d.isNaN() || d.isInfinite() -> "Tidak valid"
        d == Math.floor(d) && Math.abs(d) < 1e15 -> d.toLong().toString()
        else -> d.toString()
    }

    fun calc(a: Double, b: Double, o: String): Double = when (o) {
        "+" -> a + b
        "\u2212" -> a - b
        "\u00D7" -> a * b
        "\u00F7" -> if (b == 0.0) Double.NaN else a / b
        else -> b
    }

    fun press(k: String) {
        if (cur == "Tidak valid" && k != "C") {
            cur = "0"
            acc = null
            op = null
            fresh = true
        }
        when {
            k.length == 1 && k[0].isDigit() -> {
                if (fresh) {
                    cur = k
                    fresh = false
                } else if (cur.length < 15) {
                    cur = if (cur == "0") k else cur + k
                }
            }
            k == "." -> {
                if (fresh) {
                    cur = "0."
                    fresh = false
                } else if (!cur.contains('.')) {
                    cur += "."
                }
            }
            k == "C" -> {
                cur = "0"
                acc = null
                op = null
                fresh = true
            }
            k == DEL -> {
                if (!fresh && cur.length > 1) {
                    cur = cur.dropLast(1)
                } else {
                    cur = "0"
                    fresh = true
                }
            }
            k == "%" -> {
                cur = fmt((cur.toDoubleOrNull() ?: 0.0) / 100.0)
                fresh = true
            }
            k == "=" -> {
                val a = acc
                val o = op
                if (a != null && o != null) {
                    cur = fmt(calc(a, cur.toDoubleOrNull() ?: 0.0, o))
                    acc = null
                    op = null
                    fresh = true
                }
            }
            else -> {
                val v = cur.toDoubleOrNull() ?: 0.0
                val a = acc
                val o = op
                if (a != null && o != null && !fresh) {
                    val r = calc(a, v, o)
                    cur = fmt(r)
                    acc = if (r.isNaN() || r.isInfinite()) null else r
                } else {
                    acc = v
                }
                op = k
                fresh = true
            }
        }
        display.text = cur
    }

    val rows = listOf(
        listOf("C", DEL, "%", "\u00F7"),
        listOf("7", "8", "9", "\u00D7"),
        listOf("4", "5", "6", "\u2212"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "=")
    )
    val m = ctx.dp(2)
    for (r in rows) {
        val line = LinearLayout(ctx)
        line.orientation = LinearLayout.HORIZONTAL
        for (k in r) {
            val isOp = k == "\u00F7" || k == "\u00D7" || k == "\u2212" || k == "+" || k == "="
            val b = TextView(ctx)
            b.text = k
            b.gravity = Gravity.CENTER
            b.textSize = if (k == DEL) 15f else 20f
            b.setTextColor(Color.WHITE)
            val bg = GradientDrawable()
            bg.setColor(Color.parseColor(if (k == "=") "#4CC2FF" else if (isOp) "#3A3A3A" else "#2E2E2E"))
            bg.cornerRadius = ctx.dp(6).toFloat()
            b.background = bg
            if (k == "=") b.setTextColor(Color.BLACK)
            b.setOnClickListener { press(k) }
            val lp = LinearLayout.LayoutParams(0, MATCH, if (k == "0") 2f else 1f)
            lp.setMargins(m, m, m, m)
            line.addView(b, lp)
        }
        root.addView(line, LinearLayout.LayoutParams(MATCH, 0, 1f))
    }
    return root
}

internal class FileAdapter(private val ctx: Context) : BaseAdapter() {
    var items: List<File> = emptyList()

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val f = items[position]
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ctx.dp(12), ctx.dp(9), ctx.dp(12), ctx.dp(9))
        val iv = ImageView(ctx)
        iv.setImageDrawable(
            if (f.isDirectory) GlyphIcon(Glyph.FOLDER, Color.parseColor("#F5C242"))
            else GlyphIcon(Glyph.FILE, Color.parseColor("#B0BEC5"))
        )
        row.addView(iv, LinearLayout.LayoutParams(ctx.dp(22), ctx.dp(22)))
        val tv = TextView(ctx)
        tv.text = f.name
        tv.setTextColor(Color.WHITE)
        tv.textSize = 14f
        tv.maxLines = 1
        val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
        lp.leftMargin = ctx.dp(12)
        row.addView(tv, lp)
        return row
    }
}

fun explorerView(ctx: Context, needAccess: () -> Unit, register: (Runnable) -> Unit): View {
    val root = LinearLayout(ctx)
    root.orientation = LinearLayout.VERTICAL
    root.setBackgroundColor(Color.parseColor("#FF202020"))

    val top = LinearLayout(ctx)
    top.orientation = LinearLayout.HORIZONTAL
    top.gravity = Gravity.CENTER_VERTICAL
    top.setBackgroundColor(Color.parseColor("#FF2B2B2B"))

    val up = ImageView(ctx)
    up.setImageDrawable(GlyphIcon(Glyph.UP, Color.WHITE))
    up.setPadding(ctx.dp(10), ctx.dp(10), ctx.dp(10), ctx.dp(10))
    up.background = hoverBg()
    top.addView(up, LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(40)))

    val path = TextView(ctx)
    path.setTextColor(Color.parseColor("#CCFFFFFF"))
    path.textSize = 12f
    path.maxLines = 1
    top.addView(path, LinearLayout.LayoutParams(0, WRAP, 1f))

    val empty = TextView(ctx)
    empty.text = "Folder kosong atau belum ada izin akses. Ketuk di sini untuk memberi izin."
    empty.setTextColor(Color.parseColor("#B3FFFFFF"))
    empty.textSize = 13f
    empty.gravity = Gravity.CENTER
    empty.setPadding(ctx.dp(16), ctx.dp(24), ctx.dp(16), ctx.dp(24))
    empty.visibility = View.GONE
    empty.setOnClickListener { needAccess() }

    val list = ListView(ctx)
    list.divider = null
    val adapter = FileAdapter(ctx)
    list.adapter = adapter

    root.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))
    root.addView(empty, LinearLayout.LayoutParams(MATCH, WRAP))
    root.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))

    var dir: File = Environment.getExternalStorageDirectory()
    var shown: List<File> = emptyList()

    fun load() {
        path.text = dir.absolutePath
        val files = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        }
        shown = files?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        adapter.items = shown
        adapter.notifyDataSetChanged()
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    list.setOnItemClickListener { _, _, pos, _ ->
        val f = shown.getOrNull(pos)
        if (f != null && f.isDirectory) {
            dir = f
            load()
        }
    }
    up.setOnClickListener {
        val p = dir.parentFile
        if (p != null) {
            dir = p
            load()
        }
    }
    register(Runnable { load() })
    load()
    return root
}
