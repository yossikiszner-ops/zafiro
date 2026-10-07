package com.niki914.zafiro.app.overlay

import android.graphics.*
import android.graphics.drawable.Drawable

/** A quiet optical focus point, centered on the actual Android target. No idle animation. */
internal class OpticalFocusDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var feedback: Float = 0f
        set(value) { field = value; invalidateSelf() }
    var cursorStyle: AgentCursorStyle = AgentCursorStyle.Optical
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
        paint.style = Paint.Style.STROKE; paint.strokeWidth = bounds.width() * .025f
        val r = radius
        when (cursorStyle) {
            AgentCursorStyle.Optical -> { canvas.drawCircle(cx, cy, r, paint); paint.style = Paint.Style.FILL; canvas.drawCircle(cx, cy, r * .17f, paint) }
            AgentCursorStyle.Arrow -> {
                val arrow = Path().apply {
                    moveTo(cx, cy); lineTo(cx + r * 1.05f, cy + r * .95f)
                    lineTo(cx + r * .6f, cy + r * 1.03f); lineTo(cx + r * .4f, cy + r * 1.5f)
                    lineTo(cx + r * .15f, cy + r * 1.4f); lineTo(cx + r * .31f, cy + r * .94f)
                    lineTo(cx - r * .12f, cy + r * 1.05f); close()
                }
                paint.style = Paint.Style.FILL; paint.color = Color.argb(opacity, 25, 40, 50); canvas.drawPath(arrow, paint)
                paint.style = Paint.Style.STROKE; paint.color = Color.argb(opacity, 215, 245, 255); canvas.drawPath(arrow, paint)
            }
            AgentCursorStyle.Halo -> { canvas.drawCircle(cx, cy, r, paint); canvas.drawCircle(cx, cy, r * .65f, paint) }
            AgentCursorStyle.Crosshair -> {
                canvas.drawLine(cx-r, cy, cx-r*.25f, cy, paint); canvas.drawLine(cx+r*.25f, cy, cx+r, cy, paint)
                canvas.drawLine(cx, cy-r, cx, cy-r*.25f, paint); canvas.drawLine(cx, cy+r*.25f, cx, cy+r, paint)
            }
            AgentCursorStyle.Diamond -> {
                val path = Path().apply { moveTo(cx, cy-r); lineTo(cx+r, cy); lineTo(cx, cy+r); lineTo(cx-r, cy); close() }
                canvas.drawPath(path, paint)
            }
            AgentCursorStyle.Aperture -> { repeat(6) { i -> canvas.drawArc(cx-r, cy-r, cx+r, cy+r, i*60f, 38f, false, paint) } }
            AgentCursorStyle.Comet -> { canvas.drawLine(cx-r*1.4f, cy+r*1.4f, cx, cy, paint); paint.style = Paint.Style.FILL; canvas.drawCircle(cx, cy, r*.32f, paint) }
            AgentCursorStyle.Orbit -> { canvas.drawOval(cx-r*1.3f, cy-r*.6f, cx+r*1.3f, cy+r*.6f, paint); canvas.drawOval(cx-r*.6f, cy-r*1.3f, cx+r*.6f, cy+r*1.3f, paint) }
            AgentCursorStyle.Brackets -> { repeat(4) { canvas.save(); canvas.rotate(it*90f, cx, cy); canvas.drawLine(cx-r,cy-r,cx-r*.45f,cy-r,paint); canvas.drawLine(cx-r,cy-r,cx-r,cy-r*.45f,paint); canvas.restore() } }
            AgentCursorStyle.Dot -> { paint.style = Paint.Style.FILL; canvas.drawCircle(cx, cy, r*.38f, paint) }
        }
        paint.style = Paint.Style.STROKE
        if (feedback > 0f) {
            paint.alpha = (opacity * (1f - feedback)).toInt()
            canvas.drawCircle(cx, cy, radius * (1f + feedback * 1.2f), paint)
        }
    }
    override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
