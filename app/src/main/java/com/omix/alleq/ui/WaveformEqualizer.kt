package com.omix.alleq.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omix.alleq.model.EqualizerDefaults

@Composable
fun WaveformEqualizerCard(
    bandGains: List<Float>,
    onBandGainChanged: (Int, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    ExpressiveCard(modifier = modifier) {
        CardHeader(title = "EQUALIZER")

        Spacer(modifier = Modifier.height(8.dp))

        var canvasWidth by remember { mutableFloatStateOf(0f) }
        var canvasHeight by remember { mutableFloatStateOf(0f) }
        var activeDraggingBand by remember { mutableIntStateOf(-1) }
        var localBandGains by remember { mutableStateOf(bandGains) }

        val currentOnBandGainChanged by rememberUpdatedState(onBandGainChanged)

        LaunchedEffect(bandGains) {
            if (activeDraggingBand == -1) {
                localBandGains = bandGains
            }
        }

        val haptic = LocalHapticFeedback.current
        val accentColor = MaterialTheme.colorScheme.primary
        val outlineColor = MaterialTheme.colorScheme.outlineVariant

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            EqualizerDefaults.BANDS.forEach { band ->
                val gain = localBandGains.getOrElse(band.index) { 0f }
                Text(
                    text = "${if (gain > 0) "+" else ""}${gain.toInt()}",
                    fontFamily = FontFamily.Default,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (gain != 0f) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .onSizeChanged { size ->
                    canvasWidth = size.width.toFloat()
                    canvasHeight = size.height.toFloat()
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (canvasWidth <= 0 || canvasHeight <= 0) return@awaitEachGesture

                        val bandCount = EqualizerDefaults.BANDS.size
                        val colWidth = canvasWidth / bandCount
                        val padding = 20f
                        val usableHeight = canvasHeight - padding * 2

                        val hitRadiusPx = 44.dp.toPx()
                        var targetBand = -1
                        var minDistanceSq = Float.MAX_VALUE

                        val snapshotGains = localBandGains

                        for (i in 0 until bandCount) {
                            val gain = snapshotGains.getOrElse(i) { 0f }.coerceIn(-15f, 15f)
                            val normalized = (gain + 15f) / 30f
                            val ptX = i * colWidth + colWidth / 2
                            val ptY = padding + (1f - normalized) * usableHeight

                            val dx = down.position.x - ptX
                            val dy = down.position.y - ptY
                            val distSq = dx * dx + dy * dy
                            if (distSq <= hitRadiusPx * hitRadiusPx && distSq < minDistanceSq) {
                                minDistanceSq = distSq
                                targetBand = i
                            }
                        }

                        if (targetBand == -1) {
                            return@awaitEachGesture
                        }

                        val activeBand = targetBand
                        activeDraggingBand = activeBand
                        down.consume()

                        fun calculateGain(y: Float): Float {
                            val relativeY = (y - padding).coerceIn(0f, usableHeight)
                            val normalized = 1f - (relativeY / usableHeight)
                            return (normalized * 30f - 15f).coerceIn(-15f, 15f)
                        }

                        var lastIntGain = snapshotGains.getOrElse(activeBand) { 0f }.toInt()
                        fun applyGain(y: Float) {
                            val newGain = calculateGain(y)
                            val currentInt = kotlin.math.round(newGain).toInt()
                            if (currentInt != lastIntGain) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                lastIntGain = currentInt
                            }
                            localBandGains = localBandGains.toMutableList().also {
                                if (activeBand in it.indices) it[activeBand] = newGain
                            }
                            currentOnBandGainChanged(activeBand, newGain)
                        }

                        applyGain(down.position.y)

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!pointer.pressed) break

                                pointer.consume()
                                applyGain(pointer.position.y)
                            }
                        } finally {
                            activeDraggingBand = -1
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                canvasWidth = size.width
                canvasHeight = size.height
                val bandCount = EqualizerDefaults.BANDS.size
                val colWidth = size.width / bandCount
                val padding = 20f
                val usableHeight = size.height - padding * 2

                val points = mutableListOf<Offset>()

                for (i in 0 until bandCount) {
                    val gain = localBandGains.getOrElse(i) { 0f }.coerceIn(-15f, 15f)
                    val normalized = (gain + 15f) / 30f
                    val x = i * colWidth + colWidth / 2
                    val y = padding + (1f - normalized) * usableHeight
                    points.add(Offset(x, y))

                    drawLine(
                        color = outlineColor,
                        start = Offset(x, padding),
                        end = Offset(x, size.height - padding),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (points.isNotEmpty()) {
                    val curvePath = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val controlX = (p0.x + p1.x) / 2
                            cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(curvePath)
                        lineTo(points.last().x, size.height)
                        lineTo(points.first().x, size.height)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(accentColor.copy(alpha = 0.25f), Color.Transparent)
                        )
                    )

                    drawPath(
                        path = curvePath,
                        color = accentColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    points.forEachIndexed { index, pt ->
                        val isDraggingThis = (index == activeDraggingBand)
                        if (isDraggingThis) {
                            drawCircle(
                                color = accentColor.copy(alpha = 0.35f),
                                radius = 16.dp.toPx(),
                                center = pt
                            )
                        }
                        drawCircle(
                            color = outlineColor,
                            radius = if (isDraggingThis) 9.dp.toPx() else 7.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = accentColor,
                            radius = if (isDraggingThis) 7.dp.toPx() else 5.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val shortLabels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
            shortLabels.forEach { label ->
                Text(
                    text = label,
                    fontFamily = FontFamily.Default,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
