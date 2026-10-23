package com.omix.alleq.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omix.alleq.model.VolumeStepPoint

@Composable
fun VolumeCurveGraph(
    definedPoints: List<VolumeStepPoint>,
    maxVolumeStep: Int,
    currentVolumeStep: Int,
    lastAdjustedParameterId: String,
    defaultPoint: VolumeStepPoint,
    modifier: Modifier = Modifier
) {
    val stepsCount = maxVolumeStep.coerceAtLeast(1)
    val parameterColors = remember {
        listOf(
            Color(0xFF4CAF50), // 31Hz
            Color(0xFF2196F3), // 62Hz
            Color(0xFF00BCD4), // 125Hz
            Color(0xFF009688), // 250Hz
            Color(0xFF8BC34A), // 500Hz
            Color(0xFFFFC107), // 1kHz
            Color(0xFFFF9800), // 2kHz
            Color(0xFFFF5722), // 4kHz
            Color(0xFF9C27B0), // 8kHz
            Color(0xFFE91E63), // 16kHz
            Color(0xFFFFD54F), // Pre-Amp
            Color(0xFFFF7043), // Bass Boost
            Color(0xFFAB47BC)  // Balance
        )
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceContainerHighest

    // Calculate interpolated VolumeStepPoint for each step 0..maxVolumeStep
    val stepPoints = remember(definedPoints, stepsCount, defaultPoint) {
        (0..stepsCount).map { step ->
            VolumeStepPoint.calculateForVolumeStep(definedPoints, step, defaultPoint)
        }
    }

    val activeStepAnim by animateFloatAsState(
        targetValue = currentVolumeStep.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "activeStepAnim"
    )

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Legend for highlighted parameter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val paramLabel = getParameterLabel(lastAdjustedParameterId)
            val paramColor = getParameterColor(lastAdjustedParameterId, parameterColors, primaryColor)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(paramColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ACTIVE PARAMETER: $paramLabel",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = paramColor,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = "VOL 0..$stepsCount",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(surfaceVariant)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val paddingX = 16.dp.toPx()
                val paddingY = 16.dp.toPx()
                val graphW = w - (paddingX * 2)
                val graphH = h - (paddingY * 2)

                // Grid center line (0 dB)
                val midY = paddingY + (graphH / 2f)
                drawLine(
                    color = outlineColor.copy(alpha = 0.4f),
                    start = Offset(paddingX, midY),
                    end = Offset(w - paddingX, midY),
                    strokeWidth = 1.dp.toPx()
                )

                fun xForStep(step: Int): Float {
                    return paddingX + (step.toFloat() / stepsCount.toFloat()) * graphW
                }

                fun yForValue(value: Float, minVal: Float = -15f, maxVal: Float = 15f): Float {
                    val norm = ((value - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                    return (paddingY + graphH) - (norm * graphH)
                }

                // Plot lines for all 13 parameters
                for (pIdx in 0..12) {
                    val paramId = when (pIdx) {
                        in 0..9 -> "band_$pIdx"
                        10 -> "pre_amp"
                        11 -> "bass_boost"
                        else -> "balance"
                    }

                    val isHighlighted = paramId.equals(lastAdjustedParameterId, ignoreCase = true)
                    val lineColor = parameterColors.getOrElse(pIdx) { primaryColor }
                    val alpha = if (isHighlighted) 1.0f else 0.25f
                    val strokeW = if (isHighlighted) 3.5.dp.toPx() else 1.5.dp.toPx()

                    val path = Path()
                    stepPoints.forEachIndexed { idx, pt ->
                        val value = when (pIdx) {
                            in 0..9 -> pt.bandGains.getOrElse(pIdx) { 0f }
                            10 -> pt.preAmpDb
                            11 -> (pt.bassBoostPercent / 100f) * 15f - 7.5f
                            else -> ((pt.leftChannelVolume - pt.rightChannelVolume) * 10f)
                        }

                        val x = xForStep(idx)
                        val y = yForValue(value, -15f, 15f)

                        if (idx == 0) {
                            path.moveTo(x, y)
                        } else {
                            val prevX = xForStep(idx - 1)
                            val prevVal = when (pIdx) {
                                in 0..9 -> stepPoints[idx - 1].bandGains.getOrElse(pIdx) { 0f }
                                10 -> stepPoints[idx - 1].preAmpDb
                                11 -> (stepPoints[idx - 1].bassBoostPercent / 100f) * 15f - 7.5f
                                else -> ((stepPoints[idx - 1].leftChannelVolume - stepPoints[idx - 1].rightChannelVolume) * 10f)
                            }
                            val prevY = yForValue(prevVal, -15f, 15f)

                            val cx1 = prevX + (x - prevX) / 2f
                            val cy1 = prevY
                            val cx2 = prevX + (x - prevX) / 2f
                            val cy2 = y
                            path.cubicTo(cx1, cy1, cx2, cy2, x, y)
                        }
                    }

                    // Glow background if highlighted
                    if (isHighlighted) {
                        drawPath(
                            path = path,
                            color = lineColor.copy(alpha = 0.35f),
                            style = Stroke(
                                width = strokeW * 2.5f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    drawPath(
                        path = path,
                        color = lineColor.copy(alpha = alpha),
                        style = Stroke(
                            width = strokeW,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // Draw defined custom step points as circles
                definedPoints.forEach { pt ->
                    val x = xForStep(pt.stepIndex)
                    drawCircle(
                        color = primaryColor,
                        radius = 4.dp.toPx(),
                        center = Offset(x, midY)
                    )
                    drawCircle(
                        color = surfaceVariant,
                        radius = 2.dp.toPx(),
                        center = Offset(x, midY)
                    )
                }

                // Active Volume Step Vertical Bar
                val activeX = xForStep(activeStepAnim.toInt().coerceIn(0, stepsCount))
                drawLine(
                    color = primaryColor,
                    start = Offset(activeX, paddingY),
                    end = Offset(activeX, h - paddingY),
                    strokeWidth = 2.5.dp.toPx()
                )

                drawCircle(
                    color = primaryColor,
                    radius = 5.dp.toPx(),
                    center = Offset(activeX, h - paddingY)
                )
            }
        }
    }
}

private fun getParameterLabel(paramId: String): String {
    return when {
        paramId.startsWith("band_") -> {
            val idx = paramId.removePrefix("band_").toIntOrNull() ?: 0
            val labels = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")
            labels.getOrElse(idx) { "Band $idx" }
        }
        paramId == "pre_amp" -> "Pre-Amp Gain"
        paramId == "bass_boost" -> "Bass Boost"
        paramId == "balance" -> "L/R Balance"
        else -> "EQ Band"
    }
}

private fun getParameterColor(paramId: String, colors: List<Color>, fallback: Color): Color {
    return when {
        paramId.startsWith("band_") -> {
            val idx = paramId.removePrefix("band_").toIntOrNull() ?: 0
            colors.getOrElse(idx) { fallback }
        }
        paramId == "pre_amp" -> colors.getOrElse(10) { fallback }
        paramId == "bass_boost" -> colors.getOrElse(11) { fallback }
        paramId == "balance" -> colors.getOrElse(12) { fallback }
        else -> fallback
    }
}
