package com.niki914.zafiro.remoteview.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Public state vocabulary used by the overlay. The renderer never exposes tool/API jargon. */
enum class ZafiroGlassPhase {
    Dormant, Wake, Listening, Understanding, Searching, Reading, Thinking, Tool,
    Writing, Confirm, Sending, Speaking, Success, Permission, Recover, Error, Interrupted
}

/** Presence is deliberately broader than a face. Users can make intelligence abstract or invisible. */
enum class ZafiroPresenceStyle {
    OpticalEyes, PrismCore, PulseCore, Constellation, LiquidGlyph, Aperture,
    LightSlit, WaveformSoul, FireflyField, OrbitalCore, InkDrop, Invisible
}

data class ZafiroGlassAppearance(
    val darkness: Float = .82f,
    val transparency: Float = .18f,
    val refraction: Float = .62f,
    val distortion: Float = .24f,
    val opticalThickness: Float = .72f,
    val reflection: Float = .58f,
    val edgeBrightness: Float = .46f,
    val liquidAmount: Float = .36f,
    val viscosity: Float = .68f,
    val surfaceTension: Float = .76f,
    val curvature: Float = .88f,
    val motion: Float = .62f,
    val presence: ZafiroPresenceStyle = ZafiroPresenceStyle.PrismCore,
    val presenceIntensity: Float = .72f,
)

object ZafiroGlassPresets {
    val Signature = ZafiroGlassAppearance()
    val Obsidian = ZafiroGlassAppearance(darkness=.94f, transparency=.08f, refraction=.45f, reflection=.72f, liquidAmount=.22f)
    val PureGlass = ZafiroGlassAppearance(darkness=.42f, transparency=.52f, refraction=.86f, distortion=.36f, reflection=.82f)
    val Cinematic = ZafiroGlassAppearance(darkness=.9f, transparency=.12f, refraction=.72f, reflection=.84f, liquidAmount=.48f, motion=.72f)
    val Invisible = ZafiroGlassAppearance(darkness=.64f, transparency=.42f, presence=ZafiroPresenceStyle.Invisible, presenceIntensity=0f)
}

@Composable
fun ZafiroGlass(
    phase: ZafiroGlassPhase,
    modifier: Modifier = Modifier,
    appearance: ZafiroGlassAppearance = ZafiroGlassPresets.Signature,
    compactWidth: Dp = 156.dp,
    compactHeight: Dp = 48.dp,
    onClick: () -> Unit = {},
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    val expansion = remember { Animatable(if (expanded) 1f else 0f) }
    var drag by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(expanded) {
        expansion.animateTo(
            if (expanded) 1f else 0f,
            spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = .82f)
        )
    }
    val p = (expansion.value + drag).coerceIn(0f, 1f)
    val width = compactWidth + (332.dp - compactWidth) * p
    val height = compactHeight + (238.dp - compactHeight) * p
    val radius = ((compactHeight.value / 2) * appearance.curvature.coerceIn(0f, 1f) * (1f - .33f * p)).dp
    val accent = when (phase) {
        ZafiroGlassPhase.Success -> Color(0xFF78F5B0)
        ZafiroGlassPhase.Error -> Color(0xFFFF7373)
        ZafiroGlassPhase.Listening -> Color(0xFF88D7FF)
        else -> Color(0xFFB5E7FF)
    }
    val base = Color.Black.copy(alpha = appearance.darkness.coerceIn(0f,1f))
    Box(
        modifier = modifier
            .width(width).height(height)
            .graphicsLayer { alpha = 1f - appearance.transparency * .18f }
            .clip(RoundedCornerShape(radius))
            .drawBehind {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = .05f + appearance.reflection * .12f),
                            base,
                            Color(0xFF16212B).copy(alpha = .35f + appearance.opticalThickness * .25f)
                        )
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx())
                )
                drawRoundRect(
                    brush = Brush.linearGradient(listOf(Color.White.copy(alpha=appearance.edgeBrightness*.34f), Color.Transparent, accent.copy(alpha=.12f))),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clickable(onClick = onClick)
            .pointerInput(expanded) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dy -> drag = (drag + dy / 260f).coerceIn(-1f,1f) },
                    onDragEnd = {
                        val next = if (expanded) drag > -.18f else drag > .18f
                        drag = 0f
                        onExpandedChange(next)
                    },
                    onDragCancel = { drag = 0f }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        ZafiroPresence(appearance.presence, phase, accent, appearance.presenceIntensity)
    }
}

@Composable
private fun ZafiroPresence(style: ZafiroPresenceStyle, phase: ZafiroGlassPhase, accent: Color, intensity: Float) {
    if (style == ZafiroPresenceStyle.Invisible || intensity <= 0f) return
    val energy = intensity.coerceIn(0f, 1f) * if (phase == ZafiroGlassPhase.Dormant) .32f else 1f
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        when (style) {
            ZafiroPresenceStyle.OpticalEyes -> {
                drawCircle(accent.copy(alpha=energy), 3.2.dp.toPx(), Offset(c.x-10.dp.toPx(),c.y))
                drawCircle(accent.copy(alpha=energy), 3.2.dp.toPx(), Offset(c.x+10.dp.toPx(),c.y))
            }
            ZafiroPresenceStyle.PrismCore -> {
                val r=10.dp.toPx(); val pts=(0..3).map { i -> Offset(c.x+cos(PI/4+i*PI/2).toFloat()*r,c.y+sin(PI/4+i*PI/2).toFloat()*r) }
                for(i in pts.indices) drawLine(accent.copy(alpha=energy),pts[i],pts[(i+1)%pts.size],2.dp.toPx(),StrokeCap.Round)
            }
            ZafiroPresenceStyle.PulseCore -> { drawCircle(accent.copy(alpha=.12f*energy),15.dp.toPx(),c); drawCircle(accent.copy(alpha=energy),5.dp.toPx(),c) }
            ZafiroPresenceStyle.Constellation, ZafiroPresenceStyle.FireflyField -> {
                val offsets=listOf(-28 to -8,-13 to 9,3 to -5,20 to 8,31 to -10,10 to 16)
                offsets.forEachIndexed { i,o -> drawCircle(accent.copy(alpha=energy*(.38f+i*.08f)),(2+i%2).dp.toPx(),Offset(c.x+o.first.dp.toPx(),c.y+o.second.dp.toPx())) }
            }
            ZafiroPresenceStyle.Aperture -> { drawOval(accent.copy(alpha=energy),Offset(c.x-20.dp.toPx(),c.y-9.dp.toPx()),Size(40.dp.toPx(),18.dp.toPx()),style=Stroke(2.dp.toPx()));drawCircle(accent.copy(alpha=energy),4.dp.toPx(),c) }
            ZafiroPresenceStyle.LightSlit -> drawLine(accent.copy(alpha=energy),Offset(c.x-28.dp.toPx(),c.y),Offset(c.x+28.dp.toPx(),c.y),3.dp.toPx(),StrokeCap.Round)
            ZafiroPresenceStyle.WaveformSoul -> (-3..3).forEach { i -> val h=(7+12*(1-kotlin.math.abs(i)/3f)).dp.toPx();drawLine(accent.copy(alpha=energy),Offset(c.x+i*8.dp.toPx(),c.y-h/2),Offset(c.x+i*8.dp.toPx(),c.y+h/2),2.5.dp.toPx(),StrokeCap.Round) }
            ZafiroPresenceStyle.OrbitalCore -> { drawCircle(accent.copy(alpha=energy*.4f),16.dp.toPx(),c,style=Stroke(1.dp.toPx()));drawCircle(accent.copy(alpha=energy),4.dp.toPx(),c);drawCircle(accent,2.5.dp.toPx(),Offset(c.x+15.dp.toPx(),c.y)) }
            ZafiroPresenceStyle.InkDrop, ZafiroPresenceStyle.LiquidGlyph -> { drawCircle(accent.copy(alpha=energy*.35f),12.dp.toPx(),c);drawCircle(accent.copy(alpha=energy),5.dp.toPx(),Offset(c.x+4.dp.toPx(),c.y-3.dp.toPx())) }
            ZafiroPresenceStyle.Invisible -> Unit
        }
    }
}
