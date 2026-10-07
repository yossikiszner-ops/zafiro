package com.niki914.zafiro.app.overlay

import android.graphics.*
import android.graphics.drawable.Drawable

/** A quiet optical focus point, centered on the actual Android target. No idle animation. */
internal class OpticalFocusDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var feedback: Float = 0f
        set(value) { field = value; invalidateSelf() }
    private var opacity = 255
    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
        val radius = bounds.width() * .2f
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, radius * 1.9f,
            intArrayOf(Color.argb(opacity / 3, 170, 225, 245), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 1.9f, paint)
        paint.shader = null
        paint.color = Color.argb(opacity, 195, 235, 250)
        canvas.drawCircle(cx, cy, radius * .17f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = bounds.width() * .018f
        canvas.drawCircle(cx, cy, radius, paint)
        if (feedback > 0f) {
            paint.alpha = (opacity * (1f - feedback)).toInt()
            canvas.drawCircle(cx, cy, radius * (1f + feedback * 1.2f), paint)
        }
    }
    override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
