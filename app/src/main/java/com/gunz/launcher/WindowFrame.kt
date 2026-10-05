package com.gunz.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Jendela mengambang ala Windows: bisa digeser, diubah ukuran, minimize, maximize, dan ditutup. */
class WindowFrame(
    ctx: Context,
    val title: String,
    icon: Drawable,
    content: View,
    private val host: Host,
    onPopOut: (() -> Unit)? = null
) : FrameLayout(ctx) {

    interface Host {
        fun layerWidth(): Int
        fun layerHeight(): Int
        fun onFocus(w: WindowFrame)
        fun onClose(w: WindowFrame)
        fun onMinimize(w: WindowFrame)
    }

    var minimized = false
    var maximized = false
    var onDispose: (() -> Unit)? = null

    private val bar = LinearLayout(ctx)
    private lateinit var maxBtn: ImageView
    private val saved = FloatArray(4)
    private val minW = ctx.dp(260)
    private val minH = ctx.dp(190)

    private var dragDx = 0f
    private var dragDy = 0f
    private var lastTap = 0L
    private var ignoreMove = false

    private var gx = 0f
    private var gy = 0f
    private var gw = 0
    private var gh = 0

    init {
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#FF202020"))
            cornerRadius = ctx.dp(8).toFloat()
            setStroke(ctx.dp(1), Color.parseColor("#FF4A4A4A"))
        }
        elevation = ctx.dp(16).toFloat()
        clipToOutline = true

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL

        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        setActive(true)

        val ic = ImageView(ctx)
        ic.setImageDrawable(icon)
        val iclp = LinearLayout.LayoutParams(ctx.dp(16), ctx.dp(16))
        iclp.leftMargin = ctx.dp(10)
        bar.addView(ic, iclp)

        val tv = TextView(ctx)
        tv.text = title
        tv.setTextColor(Color.WHITE)
        tv.textSize = 12f
        tv.maxLines = 1
        tv.gravity = Gravity.CENTER_VERTICAL
        tv.setPadding(ctx.dp(8), 0, ctx.dp(8), 0)
        bar.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        if (onPopOut != null) {
            bar.addView(capButton(ctx, Glyph.POPOUT, false) { onPopOut() })
        }
        bar.addView(capButton(ctx, Glyph.MINIMIZE, false) { minimize() })
        maxBtn = capButton(ctx, Glyph.MAXIMIZE, false) { toggleMax() }
        bar.addView(maxBtn)
        bar.addView(capButton(ctx, Glyph.CLOSE, true) { host.onClose(this) })

        col.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ctx.dp(34)))
        col.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        bar.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ignoreMove = false
                    dragDx = x - e.rawX
                    dragDy = y - e.rawY
                    if (e.eventTime - lastTap < 350) {
                        lastTap = 0
                        toggleMax()
                        ignoreMove = true
                    } else {
                        lastTap = e.eventTime
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!maximized && !ignoreMove) {
                        val maxX = (host.layerWidth() - context.dp(80)).toFloat()
                        val minX = (-(width - context.dp(80))).toFloat()
                        val maxY = (host.layerHeight() - context.dp(34)).toFloat()
                        x = (e.rawX + dragDx).coerceIn(minX, maxOf(minX, maxX))
                        y = (e.rawY + dragDy).coerceIn(0f, maxOf(0f, maxY))
                    }
                }
            }
            true
        }

        val grip = ImageView(ctx)
        grip.setImageDrawable(GlyphIcon(Glyph.GRIP, Color.parseColor("#99FFFFFF")))
        grip.setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(3), ctx.dp(3))
        grip.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gx = e.rawX
                    gy = e.rawY
                    gw = width
                    gh = height
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!maximized) {
                        val nw = (gw + (e.rawX - gx)).toInt().coerceAtLeast(minW)
                        val nh = (gh + (e.rawY - gy)).toInt().coerceAtLeast(minH)
                        setSizePx(nw, nh)
                    }
                }
            }
            true
        }

        addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(grip, FrameLayout.LayoutParams(ctx.dp(28), ctx.dp(28), Gravity.END or Gravity.BOTTOM))
    }

    fun setActive(active: Boolean) {
        bar.setBackgroundColor(Color.parseColor(if (active) "#FF2F2F2F" else "#FF262626"))
    }

    fun setSizePx(w: Int, h: Int) {
        val lp = layoutParams ?: FrameLayout.LayoutParams(w, h)
        lp.width = w
        lp.height = h
        layoutParams = lp
    }

    fun toggleMax() {
        if (!maximized) {
            saved[0] = x
            saved[1] = y
            saved[2] = width.toFloat()
            saved[3] = height.toFloat()
            x = 0f
            y = 0f
            setSizePx(host.layerWidth(), host.layerHeight())
            maximized = true
        } else {
            x = saved[0]
            y = saved[1]
            setSizePx(saved[2].toInt(), saved[3].toInt())
            maximized = false
        }
        maxBtn.setImageDrawable(GlyphIcon(if (maximized) Glyph.RESTORE else Glyph.MAXIMIZE, Color.WHITE))
    }

    /** Dipanggil setelah rotasi layar agar jendela tetap di dalam layar. */
    fun fit() {
        val lw = host.layerWidth()
        val lh = host.layerHeight()
        if (maximized) {
            x = 0f
            y = 0f
            setSizePx(lw, lh)
        } else {
            x = x.coerceIn(0f, maxOf(0, lw - context.dp(80)).toFloat())
            y = y.coerceIn(0f, maxOf(0, lh - context.dp(34)).toFloat())
        }
    }

    private fun resetAnim() {
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
    }

    fun animateIn() {
        alpha = 0f
        scaleX = 0.92f
        scaleY = 0.92f
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
    }

    fun animateOut(end: () -> Unit) {
        animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(120).withEndAction { end() }.start()
    }

    fun minimize() {
        minimized = true
        host.onMinimize(this)
        animate().alpha(0f).scaleX(0.82f).scaleY(0.82f).setDuration(140).withEndAction {
            if (minimized) {
                visibility = View.GONE
                resetAnim()
            }
        }.start()
    }

    fun restore() {
        minimized = false
        visibility = View.VISIBLE
        alpha = 0f
        scaleX = 0.85f
        scaleY = 0.85f
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
        host.onFocus(this)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            post { host.onFocus(this) }
        }
        return false
    }
}
