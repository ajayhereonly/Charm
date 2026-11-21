package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.graphics3d.MeshBuilder
import com.example.python.PyPlotSpec
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface

@Composable
fun PlotCard(
    spec: PyPlotSpec,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = spec.title.ifBlank { "Matplotlib Figure" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Legend row
            if (spec.series.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    spec.series.forEach { s ->
                        val rgba = MeshBuilder.parseColor(s.colorHex)
                        val c = Color(rgba[0], rgba[1], rgba[2], 1f)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(c, CircleShape)
                            )
                            Text(
                                text = s.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            val allX = spec.series.flatMap { it.x }
            val allY = spec.series.flatMap { it.y }
            val minX = allX.minOrNull() ?: 0.0
            val maxX = (allX.maxOrNull() ?: 1.0).let { if (it == minX) minX + 1.0 else it }
            val rawMinY = allY.minOrNull() ?: 0.0
            val minY = if (spec.series.any { it.type == "bar" }) minOf(0.0, rawMinY) else rawMinY
            val maxY = (allY.maxOrNull() ?: 1.0).let { if (it == minY) minY + 1.0 else it }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(ObsidianBg, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val padL = 8f
                    val padR = 8f
                    val padT = 8f
                    val padB = 12f
                    val plotW = (w - padL - padR).coerceAtLeast(1f)
                    val plotH = (h - padT - padB).coerceAtLeast(1f)

                    // Grid lines
                    if (spec.showGrid) {
                        for (i in 0..4) {
                            val gy = padT + plotH * i / 4f
                            drawLine(
                                color = Color(0xFF1F2B47),
                                start = Offset(padL, gy),
                                end = Offset(w - padR, gy),
                                strokeWidth = 1f
                            )
                            val gx = padL + plotW * i / 4f
                            drawLine(
                                color = Color(0xFF1F2B47),
                                start = Offset(gx, padT),
                                end = Offset(gx, h - padB),
                                strokeWidth = 1f
                            )
                        }
                    }

                    fun mapX(x: Double): Float = padL + ((x - minX) / (maxX - minX)).toFloat() * plotW
                    fun mapY(y: Double): Float = padT + (1f - ((y - minY) / (maxY - minY)).toFloat()) * plotH

                    for (s in spec.series) {
                        val rgba = MeshBuilder.parseColor(s.colorHex)
                        val color = Color(rgba[0], rgba[1], rgba[2], 1f)
                        val n = minOf(s.x.size, s.y.size)
                        if (n == 0) continue

                        when (s.type) {
                            "bar" -> {
                                val slotW = plotW / n.coerceAtLeast(1)
                                val barW = (slotW * 0.68f).coerceAtLeast(4f)
                                val baseY = mapY(0.0).coerceIn(padT, h - padB)
                                for (i in 0 until n) {
                                    val cx = padL + slotW * (i + 0.5f)
                                    val ty = mapY(s.y[i])
                                    val top = minOf(ty, baseY)
                                    val barH = kotlin.math.abs(baseY - ty).coerceAtLeast(2f)
                                    drawRect(
                                        color = color.copy(alpha = 0.85f),
                                        topLeft = Offset(cx - barW / 2f, top),
                                        size = Size(barW, barH)
                                    )
                                }
                            }
                            "scatter" -> {
                                for (i in 0 until n) {
                                    drawCircle(
                                        color = color,
                                        radius = 5.5f,
                                        center = Offset(mapX(s.x[i]), mapY(s.y[i]))
                                    )
                                }
                            }
                            else -> {
                                val path = Path()
                                for (i in 0 until n) {
                                    val px = mapX(s.x[i])
                                    val py = mapY(s.y[i])
                                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                }
                                drawPath(
                                    path = path,
                                    color = color,
                                    style = Stroke(width = 3.2f, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Text(
                    text = "${spec.yLabel.ifBlank { "Y" }}: [${String.format("%.2f", minY)} .. ${String.format("%.2f", maxY)}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (spec.xLabel.isNotBlank()) {
                    Text(
                        text = spec.xLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
