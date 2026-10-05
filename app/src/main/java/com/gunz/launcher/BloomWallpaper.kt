package com.gunz.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Wallpaper "Bloom" biru bergaya Windows 11, digambar secara prosedural (vektor, tanpa file gambar)
 * sehingga ringan dan tidak melanggar hak cipta gambar asli. Ini satu-satunya wallpaper desktop.
 */
class BloomWallpaper : Drawable() {

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val petal = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val r = max(w, h) * 0.52f

        bg.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(Color.parseColor("#041A4D"), Color.parseColor("#0A4FC4"), Color.parseColor("#6FB7FF")),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(bounds, bg)

        // Tiga lapis kelopak, makin ke dalam makin terang.
        drawLayer(canvas, cx, cy, r, 7, 0f, "#1553D6", "#0B2E8F", 0.80f)
        drawLayer(canvas, cx, cy, r * 0.82f, 7, 25.7f, "#2F7BFF", "#1250C8", 0.85f)
        drawLayer(canvas, cx, cy, r * 0.62f, 7, 12.8f, "#7CC0FF", "#2F7BFF", 0.90f)
        drawLayer(canvas, cx, cy, r * 0.40f, 7, 38f, "#BFE3FF", "#5AA6FF", 0.95f)

        glow.shader = RadialGradient(
            cx, cy, r * 0.30f,
            intArrayOf(Color.parseColor("#CCFFFFFF"), Color.parseColor("#00FFFFFF")),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 0.30f, glow)
    }

    private fun drawLayer(
        c: Canvas, cx: Float, cy: Float, len: Float, n: Int, offsetDeg: Float,
        inner: String, outer: String, alpha: Float
    ) {
        for (i in 0 until n) {
            val a = Math.toRadians((offsetDeg + i * 360.0 / n))
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            // Kelopak melengkung: titik kontrol digeser ke samping (tegak lurus arah kelopak).
            val tipX = cx + ca * len
            val tipY = cy + sa * len
            val side = len * 0.42f
            val c1x = cx + ca * len * 0.45f - sa * side
            val c1y = cy + sa * len * 0.45f + ca * side
            val c2x = cx + ca * len * 0.45f + sa * side
            val c2y = cy + sa * len * 0.45f - ca * side
            path.reset()
            path.moveTo(cx, cy)
            path.quadTo(c1x, c1y, tipX, tipY)
            path.quadTo(c2x, c2y, cx, cy)
            petal.shader = LinearGradient(
                cx, cy, tipX, tipY,
                Color.parseColor(inner), Color.parseColor(outer), Shader.TileMode.CLAMP
            )
            petal.alpha = (alpha * 255).toInt()
            c.drawPath(path, petal)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
