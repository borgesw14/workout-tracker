package app.workouttracker.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/** 1234.0 -> "1,234", 202.5 -> "202.5". */
fun formatNumber(value: Double): String =
    if (abs(value - value.roundToLong()) < 0.05) "%,d".format(value.roundToLong()) else "%,.1f".format(value)

/** Evenly spaced round tick values covering [min, max]. */
private fun niceTicks(min: Double, max: Double, count: Int = 4): List<Double> {
    val span = (max - min).takeIf { it > 0 } ?: (abs(max).takeIf { it > 0 } ?: 1.0)
    val raw = span / count
    val mag = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * mag >= raw } * mag
    val lo = floor(min / step) * step
    val hi = ceil(max / step) * step
    return generateSequence(lo) { it + step }.takeWhile { it <= hi + step / 2 }.toList()
}

private const val AXIS_WIDTH_DP = 44

/** Recessive horizontal grid lines with y-axis labels in the left gutter. */
private fun DrawScope.grid(ticks: List<Double>, toY: (Double) -> Float, measurer: TextMeasurer, style: TextStyle) {
    val left = AXIS_WIDTH_DP.dp.toPx()
    ticks.forEach { t ->
        val y = toY(t)
        drawLine(style.color.copy(alpha = 0.25f), Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        val label = measurer.measure(formatNumber(t), style)
        drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y - label.size.height / 2f))
    }
}

/**
 * One series over time: a 2dp line with 8dp markers. Tapping picks the nearest point;
 * the caller shows its value in a readout above the chart.
 */
@Composable
fun LineChart(
    values: List<Double>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    if (values.isEmpty()) return

    Canvas(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(values) {
                detectTapGestures { pos ->
                    val left = AXIS_WIDTH_DP.dp.toPx()
                    val step = if (values.size > 1) (size.width - left - 12.dp.toPx()) / (values.size - 1) else 0f
                    val i = if (step == 0f) 0 else ((pos.x - left - 6.dp.toPx()) / step).let { kotlin.math.round(it).toInt() }
                    onSelect(i.coerceIn(0, values.lastIndex))
                }
            },
    ) {
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val ticks = niceTicks(values.min(), values.max())
        val lo = ticks.first()
        val hi = ticks.last().takeIf { it > lo } ?: (lo + 1)
        val toY = { v: Double -> (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat() }
        grid(ticks, toY, measurer, axisStyle)

        val left = AXIS_WIDTH_DP.dp.toPx() + 6.dp.toPx()
        val right = size.width - 6.dp.toPx()
        val toX = { i: Int -> if (values.size == 1) (left + right) / 2 else left + i * (right - left) / (values.size - 1) }
        val points = values.mapIndexed { i, v -> Offset(toX(i), toY(v)) }

        val idx = selected.coerceIn(0, values.lastIndex)
        drawLine(
            axisStyle.color.copy(alpha = 0.5f),
            Offset(points[idx].x, top),
            Offset(points[idx].x, bottom),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )
        if (points.size > 1) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // Markers sit on a 2dp surface ring so they read where the line crosses them.
        val showMarkers = points.size <= 30
        points.forEachIndexed { i, p ->
            if (showMarkers || i == idx) {
                val r = if (i == idx) 6.dp.toPx() else 4.dp.toPx()
                drawCircle(surface, r + 2.dp.toPx(), p)
                drawCircle(color, r, p)
            }
        }
    }
}

/** Single-hue bars with rounded tops and 2dp gaps. Tapping a bar selects it. */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    if (values.isEmpty()) return

    Canvas(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(values) {
                detectTapGestures { pos ->
                    val left = AXIS_WIDTH_DP.dp.toPx()
                    val slot = (size.width - left) / values.size
                    onSelect(((pos.x - left) / slot).toInt().coerceIn(0, values.lastIndex))
                }
            },
    ) {
        val labelHeight = 18.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - labelHeight
        val ticks = niceTicks(0.0, values.max().coerceAtLeast(1.0))
        val hi = ticks.last()
        val toY = { v: Double -> (bottom - v / hi * (bottom - top)).toFloat() }
        grid(ticks, toY, measurer, axisStyle)

        val left = AXIS_WIDTH_DP.dp.toPx()
        val slot = (size.width - left) / values.size
        val gap = 2.dp.toPx()
        val barWidth = (slot * 0.6f).coerceAtMost(28.dp.toPx())
        values.forEachIndexed { i, v ->
            val x = left + i * slot + (slot - barWidth) / 2
            val y = toY(v)
            if (v > 0) {
                val radius = 4.dp.toPx().coerceAtMost((bottom - y) / 2)
                drawRoundRect(
                    color = if (i == selected) color else muted,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, bottom - y - gap / 2),
                    cornerRadius = CornerRadius(radius, radius),
                )
                // Square off the bottom corners so the bar sits on the baseline.
                drawRect(
                    if (i == selected) color else muted,
                    topLeft = Offset(x, bottom - radius - gap / 2),
                    size = Size(barWidth, radius),
                )
            }
            // Label every other bar, counting back from the latest, so labels never collide.
            if ((values.lastIndex - i) % 2 == 0) {
                val label = measurer.measure(labels[i], axisStyle)
                drawText(label, topLeft = Offset(x + barWidth / 2 - label.size.width / 2f, bottom + 2.dp.toPx()))
            }
        }
    }
}
