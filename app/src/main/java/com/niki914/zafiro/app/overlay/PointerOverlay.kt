package com.niki914.zafiro.app.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import com.niki914.zafiro.animation.PointerCurveMath
import com.niki914.zafiro.animation.PointerCurveMath.MovementMode
import com.niki914.zafiro.app.R
import com.niki914.zafiro.chat.agentic.accessibility.IPointerOverlay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PointerOverlay : IPointerOverlay {

    companion object {
        private const val FADE_DURATION_MS = 300L
        private const val POINTER_SIZE_DP = 48
    }

    // Android overlay infrastructure.
    // The view is attached once in init() and stays in the window for its
    // entire lifetime — we toggle visibility via alpha only. This avoids
    // addView/removeView races where a re-attached view briefly renders at
    // default alpha=1 before we can set it to 0.
    private val focusDrawable = OpticalFocusDrawable()
    private var feedbackAnim: ValueAnimator? = null
    private var wm: WindowManager? = null
    private var view: ImageView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var attached = false
    private var density = 2f

    // Mutable state
    private var curX = 0f
    private var curY = 0f
    private var curHeading = PointerCurveMath.IDLE_HEADING_RAD
    private var screenW = 0
    private var screenH = 0
    private var runningAnim: ValueAnimator? = null

    private val handler = Handler(Looper.getMainLooper())

    // Tracks the unwrapped heading across frames to prevent ±180° jumps
    private var prevAngleDeg =
        Math.toDegrees(PointerCurveMath.IDLE_HEADING_RAD.toDouble()).toFloat()

    // ============================================================
    // Initialization
    // ============================================================

    fun init(ctx: Context) {
        AgentCursorPreferences.load(ctx)
        focusDrawable.cursorStyle = AgentCursorPreferences.appearance.value.style
        val dm = ctx.resources.displayMetrics
        density = dm.density
        screenW = dm.widthPixels
        screenH = dm.heightPixels
        val sizePx = (POINTER_SIZE_DP * density).toInt()

        wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        view = ImageView(ctx).apply {
            setImageDrawable(focusDrawable)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        lp = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
        }

        view?.alpha = 0f

        if (AgentCursorPreferences.appearance.value.enabled) tryAttach()
    }

    // ============================================================
    // IPointerOverlay
    // ============================================================

    override fun show(x: Float, y: Float) {
        handler.post {
            if (!AgentCursorPreferences.appearance.value.enabled) return@post
            view?.animate()?.cancel()
            tryAttach()
            curX = x
            curY = y
            curHeading = PointerCurveMath.IDLE_HEADING_RAD
            prevAngleDeg = Math.toDegrees(curHeading.toDouble()).toFloat()
            applyTransform(x, y, curHeading)
            view?.animate()?.alpha(AgentCursorPreferences.appearance.value.intensity)?.setDuration(FADE_DURATION_MS)?.start()
        }
    }

    fun refreshAppearance() {
        handler.post {
            val config = AgentCursorPreferences.appearance.value
            focusDrawable.cursorStyle = config.style
            if (!config.enabled) { cancelAnim(); feedbackAnim?.cancel(); view?.animate()?.cancel(); view?.alpha = 0f }
            else if ((view?.alpha ?: 0f) > 0f) view?.alpha = config.intensity
        }
    }

    override suspend fun animateTypingTo(x: Float, y: Float) {
        if (!AgentCursorPreferences.appearance.value.enabled) return
        val trajectory = PointerCurveMath.buildTrajectory(curX, curY, curHeading, x, y, MovementMode.FLY, screenW, screenH)
        animateAlongRaw(trajectory, (120 / AgentCursorPreferences.appearance.value.speed).toLong().coerceAtLeast(40))
    }

    override fun actionAccepted() {
        handler.post {
            val config = AgentCursorPreferences.appearance.value
            if (!config.enabled || !config.tapFeedback) return@post
            feedbackAnim?.cancel()
            feedbackAnim = ValueAnimator.ofFloat(.01f, 1f).apply {
                duration = 240
                addUpdateListener { focusDrawable.feedback = it.animatedValue as Float }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { focusDrawable.feedback = 0f }
                })
                start()
            }
        }
    }

    override fun hide() {
        handler.post {
            view?.animate()?.cancel()
            cancelAnim()
            feedbackAnim?.cancel()
            view?.animate()?.alpha(0f)?.setDuration(FADE_DURATION_MS)?.start()
        }
    }

    override fun dispose() {
        handler.post {
            view?.animate()?.cancel()
            cancelAnim()
            feedbackAnim?.cancel()
            if (attached) {
                try {
                    wm?.removeViewImmediate(view)
                } catch (_: Exception) {
                }
                attached = false
            }
        }
    }

    override suspend fun animateTo(
        x: Float, y: Float, mode: MovementMode,
    ) {
        if (!attached || !AgentCursorPreferences.appearance.value.enabled) return
        cancelAnim()
        val t = PointerCurveMath.buildTrajectory(
            curX, curY, curHeading, x, y, mode, screenW, screenH,
        )
        animateAlong(t)
    }

    override suspend fun showSwipe(
        sx: Float, sy: Float, ex: Float, ey: Float, duration: Long,
    ) {
        if (!attached || !AgentCursorPreferences.appearance.value.enabled) return
        cancelAnim()

        // Phase 2: translate along swipe path (straight line, NW heading)
        val swipe = PointerCurveMath.buildTrajectory(
            sx, sy, curHeading, ex, ey, MovementMode.TRANSLATE, screenW, screenH,
        )
        // Override duration to match the requested swipe duration
        animateAlongRaw(swipe, duration)
    }

    // ============================================================
    // Animation
    // ============================================================

    /** Animate along [trajectory] using easeInOutSine (FLY) or linear (TRANSLATE). */
    private suspend fun animateAlong(
        trajectory: PointerCurveMath.Trajectory,
    ) {
        animateAlongRaw(trajectory, (trajectory.totalDurationMs / AgentCursorPreferences.appearance.value.speed).toLong().coerceAtLeast(1))
    }

    /**
     * Drive a [ValueAnimator] along [trajectory] with an explicit [durationMs].
     * Per-frame: ease → sample → unwrap heading → apply transform.
     *
     * The heading unwinding is continuous across consecutive animations
     * (both within a showSwipe call and across separate animateTo calls)
     * because [prevAngleDeg] persists across animation boundaries.
     */
    private suspend fun animateAlongRaw(
        trajectory: PointerCurveMath.Trajectory,
        durationMs: Long,
    ) {
        if (trajectory.totalLen <= 0f) return

        val isFly = trajectory.mode == MovementMode.FLY

        suspendCancellableCoroutine<Unit> { cont ->
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                interpolator = LinearInterpolator()
                duration = durationMs
                addUpdateListener {
                    val timeFrac = it.animatedValue as Float
                    val distFrac = if (isFly) {
                        PointerCurveMath.easeInOutSine(timeFrac)
                    } else {
                        timeFrac // linear for TRANSLATE
                    }
                    val frame = trajectory.sampleAtDistance(distFrac)
                    val rawDeg = Math.toDegrees(frame.headingRad.toDouble()).toFloat()
                    val unwrapped = PointerCurveMath.unwrapAngle(rawDeg, prevAngleDeg)
                    prevAngleDeg = unwrapped
                    val headingRad =
                        Math.toRadians(unwrapped.toDouble()).toFloat()
                    handler.post {
                        curX = frame.x
                        curY = frame.y
                        curHeading = headingRad
                        applyTransform(frame.x, frame.y, headingRad)
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) {
                        runningAnim = null
                        // Trajectory already ended at correct
                        // position/heading on the last update frame
                        curX = trajectory.endX
                        curY = trajectory.endY
                        handler.post {
                            applyTransform(trajectory.endX, trajectory.endY, curHeading)
                        }
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onAnimationCancel(a: Animator) {
                        runningAnim = null
                        if (cont.isActive) cont.resume(Unit)
                    }
                })
            }
            cont.invokeOnCancellation {
                handler.post {
                    if (runningAnim === anim) {
                        anim.cancel()
                        runningAnim = null
                    }
                }
            }
            handler.post {
                if (cont.isActive) {
                    runningAnim = anim
                    anim.start()
                }
            }
        }
    }

    private fun cancelAnim() {
        val anim = runningAnim
        runningAnim = null
        if (anim != null) {
            handler.post { anim.cancel() }
        }
    }

    // ============================================================
    // Window management
    // ============================================================

    /**
     * Idempotent attach — no-op if already in the window.
     * Called from both [init] and [show] so that a late overlay-permission
     * grant is picked up on the next show attempt.
     *
     * Alpha is reset before every attach attempt so a view modified while
     * detached can never flash at alpha=1 when permission becomes available.
     */
    private fun tryAttach() {
        if (attached || wm == null || view == null || lp == null) return
        try {
            view!!.alpha = 0f
            wm!!.addView(view, lp)
            attached = true
        } catch (_: Exception) {
        }
    }

    private fun applyTransform(x: Float, y: Float, headingRad: Float) {
        val p = lp ?: return
        val half = (POINTER_SIZE_DP * density / 2f).toInt()
        p.x = x.toInt() - half
        p.y = y.toInt() - half
        view?.rotation = 0f
        try {
            if (attached) wm?.updateViewLayout(view, p)
        } catch (_: Exception) {
        }
    }

    private suspend fun delayOnMain(ms: Long) {
        suspendCancellableCoroutine<Unit> { cont ->
            handler.postDelayed({ if (cont.isActive) cont.resume(Unit) }, ms)
        }
    }
}
