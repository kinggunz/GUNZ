package com.gunz.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.ImageView

/** Latar desktop: gradasi bawaan, foto dari galeri, atau video yang diputar berulang tanpa suara. */
class WallpaperView(ctx: Context) : FrameLayout(ctx), TextureView.SurfaceTextureListener {

    private val image = ImageView(ctx)
    private var tv: TextureView? = null
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var videoUri: Uri? = null
    private var vw = 0
    private var vh = 0

    var onError: (() -> Unit)? = null

    init {
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        addView(image, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    fun showDrawable(d: android.graphics.drawable.Drawable) {
        stopVideo()
        image.setImageDrawable(null)
        background = d
    }

    fun showGradient(colors: IntArray) {
        stopVideo()
        image.setImageDrawable(null)
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
    }

    fun showImage(b: Bitmap) {
        stopVideo()
        background = ColorDrawable(Color.BLACK)
        image.setImageBitmap(b)
    }

    fun showVideo(uri: Uri) {
        image.setImageDrawable(null)
        background = ColorDrawable(Color.BLACK)
        videoUri = uri
        vw = 0
        vh = 0
        if (tv == null) {
            val t = TextureView(context)
            t.surfaceTextureListener = this
            tv = t
            addView(t, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        } else if (surface != null) {
            startPlayer()
        }
    }

    private fun stopVideo() {
        videoUri = null
        releasePlayer()
        val t = tv
        if (t != null) {
            t.surfaceTextureListener = null
            removeView(t)
            tv = null
        }
        surface?.release()
        surface = null
    }

    private fun releasePlayer() {
        val p = player
        player = null
        if (p != null) {
            try {
                p.setOnErrorListener(null)
                p.release()
            } catch (e: Exception) {
            }
        }
    }

    private fun startPlayer() {
        val uri = videoUri ?: return
        val s = surface ?: return
        releasePlayer()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(context, uri)
            mp.setSurface(s)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setOnVideoSizeChangedListener { _, w, h ->
                vw = w
                vh = h
                applyCrop()
            }
            mp.setOnPreparedListener { it.start() }
            mp.setOnErrorListener { _, _, _ ->
                releasePlayer()
                onError?.invoke()
                true
            }
            player = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            releasePlayer()
            onError?.invoke()
        }
    }

    private fun applyCrop() {
        val t = tv ?: return
        if (vw <= 0 || vh <= 0 || width <= 0 || height <= 0) return
        val viewAspect = width.toFloat() / height.toFloat()
        val vidAspect = vw.toFloat() / vh.toFloat()
        val sx: Float
        val sy: Float
        if (vidAspect > viewAspect) {
            sx = vidAspect / viewAspect
            sy = 1f
        } else {
            sx = 1f
            sy = viewAspect / vidAspect
        }
        val m = Matrix()
        m.setScale(sx, sy, width / 2f, height / 2f)
        t.setTransform(m)
    }

    fun pauseVideo() {
        try {
            val p = player
            if (p != null && p.isPlaying) p.pause()
        } catch (e: Exception) {
        }
    }

    fun resumeVideo() {
        try {
            val p = player
            if (p != null && !p.isPlaying) p.start()
        } catch (e: Exception) {
        }
    }

    fun release() {
        stopVideo()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyCrop()
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(st)
        if (videoUri != null) startPlayer()
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
        applyCrop()
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        releasePlayer()
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
}
