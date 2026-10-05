package com.gunz.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.widget.ImageView
import android.widget.LinearLayout
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

/** Satu entri aplikasi. id != null berarti aplikasi bawaan launcher (jendela internal). */
class AppItem(val label: String, val pkg: String, val icon: Drawable, val id: String? = null)

enum class Glyph {
    FOLDER, FILE, NOTE, CALC, GEAR, SEARCH, POWER, UP, BACK, FORWARD,
    MINIMIZE, MAXIMIZE, RESTORE, CLOSE, GRIP, PLAY, GLOBE, IMAGE, VIDEO,
    RELOAD, HOME, POPOUT, WIFI, VOLUME, CHEVRON_UP, BELL
}

/** Ikon vektor yang digambar langsung (tanpa emoji / font), dipakai di seluruh launcher. */
class GlyphIcon(
    private val glyph: Glyph,
    private val fg: Int = Color.WHITE,
    private val bg: Int = Color.TRANSPARENT
) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val rect = RectF()
    private var alphaValue = 255

    private fun mod(c: Int): Int =
        Color.argb(Color.alpha(c) * alphaValue / 255, Color.red(c), Color.green(c), Color.blue(c))

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val full = min(b.width(), b.height()).toFloat()
        val hasBg = bg != Color.TRANSPARENT
        if (hasBg) {
            bgPaint.color = mod(bg)
            val r = full * 0.22f
            rect.set(
                b.exactCenterX() - full / 2f, b.exactCenterY() - full / 2f,
                b.exactCenterX() + full / 2f, b.exactCenterY() + full / 2f
            )
            canvas.drawRoundRect(rect, r, r, bgPaint)
        }
        val inset = if (hasBg) full * 0.2f else full * 0.06f
        val s = full - 2f * inset
        val l = b.exactCenterX() - s / 2f
        val t = b.exactCenterY() - s / 2f
        fill.color = mod(fg)
        line.color = mod(fg)
        val sw = when (glyph) {
            Glyph.MINIMIZE, Glyph.MAXIMIZE, Glyph.RESTORE, Glyph.CLOSE, Glyph.GRIP -> 0.075f
            else -> 0.1f
        }
        line.strokeWidth = s * sw
        drawGlyph(canvas, l, t, s)
    }

    private fun drawGlyph(c: Canvas, l: Float, t: Float, s: Float) {
        fun px(u: Float): Float = l + u * s
        fun py(u: Float): Float = t + u * s
        fun ln(x1: Float, y1: Float, x2: Float, y2: Float) {
            c.drawLine(px(x1), py(y1), px(x2), py(y2), line)
        }
        fun box(x1: Float, y1: Float, x2: Float, y2: Float) {
            rect.set(px(x1), py(y1), px(x2), py(y2))
        }
        when (glyph) {
            Glyph.FOLDER -> {
                path.reset()
                path.moveTo(px(0f), py(0.2f))
                path.lineTo(px(0.4f), py(0.2f))
                path.lineTo(px(0.5f), py(0.32f))
                path.lineTo(px(1f), py(0.32f))
                path.lineTo(px(1f), py(0.84f))
                path.lineTo(px(0f), py(0.84f))
                path.close()
                c.drawPath(path, fill)
            }
            Glyph.FILE -> {
                path.reset()
                path.moveTo(px(0.18f), py(0.04f))
                path.lineTo(px(0.62f), py(0.04f))
                path.lineTo(px(0.84f), py(0.26f))
                path.lineTo(px(0.84f), py(0.96f))
                path.lineTo(px(0.18f), py(0.96f))
                path.close()
                c.drawPath(path, fill)
            }
            Glyph.NOTE -> {
                box(0.16f, 0.08f, 0.84f, 0.92f)
                c.drawRoundRect(rect, s * 0.06f, s * 0.06f, line)
                ln(0.3f, 0.36f, 0.7f, 0.36f)
                ln(0.3f, 0.54f, 0.7f, 0.54f)
                ln(0.3f, 0.72f, 0.55f, 0.72f)
            }
            Glyph.CALC -> {
                box(0.2f, 0.04f, 0.8f, 0.96f)
                c.drawRoundRect(rect, s * 0.06f, s * 0.06f, line)
                box(0.3f, 0.14f, 0.7f, 0.3f)
                c.drawRect(rect, fill)
                for (ry in listOf(0.46f, 0.62f, 0.78f)) {
                    for (cx in listOf(0.31f, 0.46f, 0.61f)) {
                        box(cx, ry, cx + 0.09f, ry + 0.09f)
                        c.drawRect(rect, fill)
                    }
                }
            }
            Glyph.GEAR -> {
                line.strokeWidth = s * 0.16f
                for (i in 0 until 8) {
                    val a = Math.PI * 2.0 * i / 8.0
                    val ca = cos(a).toFloat()
                    val sa = sin(a).toFloat()
                    c.drawLine(
                        px(0.5f + 0.3f * ca), py(0.5f + 0.3f * sa),
                        px(0.5f + 0.44f * ca), py(0.5f + 0.44f * sa), line
                    )
                }
                line.strokeWidth = s * 0.12f
                c.drawCircle(px(0.5f), py(0.5f), s * 0.27f, line)
            }
            Glyph.SEARCH -> {
                c.drawCircle(px(0.42f), py(0.42f), s * 0.28f, line)
                ln(0.63f, 0.63f, 0.92f, 0.92f)
            }
            Glyph.POWER -> {
                box(0.16f, 0.2f, 0.84f, 0.88f)
                c.drawArc(rect, -60f, 300f, false, line)
                ln(0.5f, 0.08f, 0.5f, 0.5f)
            }
            Glyph.UP -> {
                ln(0.5f, 0.88f, 0.5f, 0.14f)
                ln(0.22f, 0.42f, 0.5f, 0.14f)
                ln(0.78f, 0.42f, 0.5f, 0.14f)
            }
            Glyph.BACK -> {
                ln(0.9f, 0.5f, 0.1f, 0.5f)
                ln(0.4f, 0.2f, 0.1f, 0.5f)
                ln(0.4f, 0.8f, 0.1f, 0.5f)
            }
            Glyph.FORWARD -> {
                ln(0.1f, 0.5f, 0.9f, 0.5f)
                ln(0.6f, 0.2f, 0.9f, 0.5f)
                ln(0.6f, 0.8f, 0.9f, 0.5f)
            }
            Glyph.MINIMIZE -> ln(0.15f, 0.58f, 0.85f, 0.58f)
            Glyph.MAXIMIZE -> {
                box(0.2f, 0.2f, 0.8f, 0.8f)
                c.drawRect(rect, line)
            }
            Glyph.RESTORE -> {
                box(0.18f, 0.3f, 0.7f, 0.82f)
                c.drawRect(rect, line)
                path.reset()
                path.moveTo(px(0.3f), py(0.3f))
                path.lineTo(px(0.3f), py(0.18f))
                path.lineTo(px(0.82f), py(0.18f))
                path.lineTo(px(0.82f), py(0.7f))
                path.lineTo(px(0.7f), py(0.7f))
                c.drawPath(path, line)
            }
            Glyph.CLOSE -> {
                ln(0.2f, 0.2f, 0.8f, 0.8f)
                ln(0.8f, 0.2f, 0.2f, 0.8f)
            }
            Glyph.GRIP -> {
                ln(0.9f, 0.35f, 0.35f, 0.9f)
                ln(0.9f, 0.65f, 0.65f, 0.9f)
            }
            Glyph.PLAY -> {
                path.reset()
                path.moveTo(px(0.3f), py(0.14f))
                path.lineTo(px(0.3f), py(0.86f))
                path.lineTo(px(0.86f), py(0.5f))
                path.close()
                c.drawPath(path, fill)
            }
            Glyph.GLOBE -> {
                c.drawCircle(px(0.5f), py(0.5f), s * 0.42f, line)
                box(0.3f, 0.08f, 0.7f, 0.92f)
                c.drawOval(rect, line)
                ln(0.08f, 0.5f, 0.92f, 0.5f)
            }
            Glyph.IMAGE -> {
                box(0.08f, 0.16f, 0.92f, 0.84f)
                c.drawRoundRect(rect, s * 0.06f, s * 0.06f, line)
                c.drawCircle(px(0.3f), py(0.38f), s * 0.07f, fill)
                path.reset()
                path.moveTo(px(0.12f), py(0.78f))
                path.lineTo(px(0.42f), py(0.5f))
                path.lineTo(px(0.6f), py(0.66f))
                path.lineTo(px(0.72f), py(0.56f))
                path.lineTo(px(0.9f), py(0.76f))
                c.drawPath(path, line)
            }
            Glyph.VIDEO -> {
                box(0.06f, 0.22f, 0.72f, 0.78f)
                c.drawRoundRect(rect, s * 0.06f, s * 0.06f, line)
                path.reset()
                path.moveTo(px(0.72f), py(0.42f))
                path.lineTo(px(0.94f), py(0.28f))
                path.lineTo(px(0.94f), py(0.72f))
                path.lineTo(px(0.72f), py(0.58f))
                path.close()
                c.drawPath(path, fill)
            }
            Glyph.RELOAD -> {
                box(0.16f, 0.16f, 0.84f, 0.84f)
                c.drawArc(rect, -30f, 290f, false, line)
                path.reset()
                path.moveTo(px(0.68f), py(0.17f))
                path.lineTo(px(0.42f), py(0.02f))
                path.lineTo(px(0.42f), py(0.32f))
                path.close()
                c.drawPath(path, fill)
            }
            Glyph.HOME -> {
                path.reset()
                path.moveTo(px(0.08f), py(0.5f))
                path.lineTo(px(0.5f), py(0.12f))
                path.lineTo(px(0.92f), py(0.5f))
                c.drawPath(path, line)
                path.reset()
                path.moveTo(px(0.2f), py(0.42f))
                path.lineTo(px(0.2f), py(0.88f))
                path.lineTo(px(0.8f), py(0.88f))
                path.lineTo(px(0.8f), py(0.42f))
                c.drawPath(path, line)
            }
            Glyph.POPOUT -> {
                path.reset()
                path.moveTo(px(0.45f), py(0.18f))
                path.lineTo(px(0.12f), py(0.18f))
                path.lineTo(px(0.12f), py(0.88f))
                path.lineTo(px(0.82f), py(0.88f))
                path.lineTo(px(0.82f), py(0.55f))
                c.drawPath(path, line)
                ln(0.42f, 0.58f, 0.9f, 0.1f)
                path.reset()
                path.moveTo(px(0.62f), py(0.1f))
                path.lineTo(px(0.9f), py(0.1f))
                path.lineTo(px(0.9f), py(0.38f))
                c.drawPath(path, line)
            }
            Glyph.WIFI -> {
                for (r in listOf(0.2f, 0.42f, 0.64f)) {
                    box(0.5f - r, 0.88f - r, 0.5f + r, 0.88f + r)
                    c.drawArc(rect, 225f, 90f, false, line)
                }
                c.drawCircle(px(0.5f), py(0.84f), s * 0.05f, fill)
            }
            Glyph.VOLUME -> {
                path.reset()
                path.moveTo(px(0.06f), py(0.4f))
                path.lineTo(px(0.26f), py(0.4f))
                path.lineTo(px(0.46f), py(0.22f))
                path.lineTo(px(0.46f), py(0.78f))
                path.lineTo(px(0.26f), py(0.6f))
                path.lineTo(px(0.06f), py(0.6f))
                path.close()
                c.drawPath(path, fill)
                for (r in listOf(0.2f, 0.36f)) {
                    box(0.5f - r + 0.1f, 0.5f - r, 0.5f + r + 0.1f, 0.5f + r)
                    c.drawArc(rect, -45f, 90f, false, line)
                }
            }
            Glyph.CHEVRON_UP -> {
                ln(0.2f, 0.65f, 0.5f, 0.35f)
                ln(0.8f, 0.65f, 0.5f, 0.35f)
            }
            Glyph.BELL -> {
                path.reset()
                path.moveTo(px(0.2f), py(0.74f))
                path.cubicTo(px(0.3f), py(0.64f), px(0.3f), py(0.52f), px(0.3f), py(0.42f))
                path.cubicTo(px(0.3f), py(0.18f), px(0.7f), py(0.18f), px(0.7f), py(0.42f))
                path.cubicTo(px(0.7f), py(0.52f), px(0.7f), py(0.64f), px(0.8f), py(0.74f))
                path.close()
                c.drawPath(path, line)
                c.drawCircle(px(0.5f), py(0.86f), s * 0.06f, fill)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        line.colorFilter = colorFilter
        bgPaint.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Logo empat panel untuk tombol Start. */
class PanesIcon(private val paneColor: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paneColor }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()) * 0.78f
        val gap = s * 0.09f
        val half = (s - gap) / 2f
        val l = b.exactCenterX() - s / 2f
        val t = b.exactCenterY() - s / 2f
        canvas.drawRect(l, t, l + half, t + half, p)
        canvas.drawRect(l + half + gap, t, l + s, t + half, p)
        canvas.drawRect(l, t + half + gap, l + half, t + s, p)
        canvas.drawRect(l + half + gap, t + half + gap, l + s, t + s, p)
    }

    override fun setAlpha(alpha: Int) {
        p.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        p.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Ikon aplikasi bawaan launcher. */
fun builtinIcon(id: String): Drawable = when (id) {
    "explorer" -> GlyphIcon(Glyph.FOLDER, Color.WHITE, Color.parseColor("#F5A623"))
    "browser" -> GlyphIcon(Glyph.GLOBE, Color.WHITE, Color.parseColor("#1A73E8"))
    "youtube" -> GlyphIcon(Glyph.PLAY, Color.WHITE, Color.parseColor("#E62117"))
    "notepad" -> GlyphIcon(Glyph.NOTE, Color.WHITE, Color.parseColor("#2D9CDB"))
    "calc" -> GlyphIcon(Glyph.CALC, Color.WHITE, Color.parseColor("#27AE60"))
    "personalize" -> GlyphIcon(Glyph.IMAGE, Color.WHITE, Color.parseColor("#8E44AD"))
    else -> GlyphIcon(Glyph.GEAR, Color.WHITE, Color.parseColor("#6C7A89"))
}

/** Latar yang berubah saat disorot mouse atau ditekan. */
fun hoverBg(): StateListDrawable {
    val sl = StateListDrawable()
    val c = ColorDrawable(Color.parseColor("#22FFFFFF"))
    sl.addState(intArrayOf(android.R.attr.state_pressed), c)
    sl.addState(intArrayOf(android.R.attr.state_hovered), ColorDrawable(Color.parseColor("#22FFFFFF")))
    sl.addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
    return sl
}

/** Tombol caption ala Windows (minimize / maximize / close). Close berubah merah saat disorot. */
fun capButton(ctx: Context, g: Glyph, danger: Boolean, onClick: () -> Unit): ImageView {
    val iv = ImageView(ctx)
    iv.setImageDrawable(GlyphIcon(g, Color.WHITE))
    iv.scaleType = ImageView.ScaleType.FIT_CENTER
    iv.setPadding(ctx.dp(17), ctx.dp(11), ctx.dp(17), ctx.dp(11))
    val hl = Color.parseColor(if (danger) "#E81123" else "#33FFFFFF")
    val sl = StateListDrawable()
    sl.addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(hl))
    sl.addState(intArrayOf(android.R.attr.state_hovered), ColorDrawable(hl))
    sl.addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
    iv.background = sl
    iv.setOnClickListener { onClick() }
    iv.layoutParams = LinearLayout.LayoutParams(ctx.dp(46), LinearLayout.LayoutParams.MATCH_PARENT)
    return iv
}
