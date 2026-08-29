package com.example.radio_vertical

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import kotlin.math.*
import kotlin.random.Random

/**
 * MEGA VISUALIZADOR IAIO RADIO V5 - 20 MODOS PERSISTENTES 🚀
 * Calibrado para respuesta ultra-rápida (Ableton Style)
 */
@OptIn(UnstableApi::class)
@Composable
fun SpectrumVisualizer(
    isPlaying: Boolean, 
    energyL: Float,
    energyR: Float,
    currentMode: Int,
    onModeChange: (Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var smoothL by remember { mutableFloatStateOf(0f) }
    var smoothR by remember { mutableFloatStateOf(0f) }
    var peakL by remember { mutableFloatStateOf(0f) }
    var peakR by remember { mutableFloatStateOf(0f) }

    val currentEnergyL = rememberUpdatedState(energyL)
    val currentEnergyR = rememberUpdatedState(energyR)
    
    val waveformFlowState = PlaybackService.stutterProcessor.waveformFlow.collectAsState()
    val waveform = waveformFlowState.value

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                val el = currentEnergyL.value
                val er = currentEnergyR.value
                if (el > 0.001f || er > 0.001f) {
                    // ATAQUE TOTAL (1:1): Salto instantáneo hacia arriba
                    smoothL = if (el > smoothL) el else smoothL * 0.5f + el * 0.5f
                    smoothR = if (er > smoothR) er else smoothR * 0.5f + er * 0.5f
                    if (el > peakL) peakL = el
                    if (er > peakR) peakR = er
                } else {
                    smoothL *= 0.85f 
                    smoothR *= 0.85f
                }
                peakL *= 0.94f // Picos más rápidos
                peakR *= 0.94f
            }
        }
    }

    val totalModes = 23

    Box(
        modifier = Modifier
            .width(310.dp)
            .height(85.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .pointerInput(currentMode) { // KEY ACTUALIZADA PARA EVITAR STALENESS
                detectTapGestures(onDoubleTap = {
                    onModeChange((currentMode + 1) % totalModes)
                    vibratePhone(context, 20)
                })
            },
        contentAlignment = Alignment.Center
    ) {
        when (currentMode) {
            0 -> DigitalLedBars(smoothL, smoothR, peakL, peakR)
            1 -> VintageOscillator(isPlaying, smoothL, smoothR)
            2 -> Oscilloscope(isPlaying, waveform)
            3 -> AnalogVU(smoothL, smoothR)
            4 -> ChunkyBars(smoothL, smoothR, peakL, peakR)
            5 -> NeonWave(isPlaying, waveform, smoothL)
            6 -> RadialPips(smoothL, smoothR)
            7 -> MirrorWave(waveform, smoothL)
            8 -> MatrixRain(isPlaying, smoothL)
            9 -> LazerSpikes(waveform, smoothL)
            10 -> DotMatrix(smoothL, smoothR)
            11 -> FluidCurve(isPlaying, smoothL, smoothR)
            12 -> RGBGlow(smoothL, smoothR)
            13 -> CyberGrid(isPlaying, smoothL)
            14 -> PlasmaAura(smoothL, smoothR)
            15 -> TapeDeckBars(smoothL, smoothR)
            16 -> StrobeHit(isPlaying, smoothL)
            17 -> Blocks3D(smoothL, smoothR)
            18 -> HeartPulse(smoothL, smoothR)
            19 -> SonarRadar(isPlaying, smoothL)
            20 -> GalaxyVortex(isPlaying, smoothL, smoothR)
            21 -> SpectrumPeaks(smoothL, smoothR, peakL, peakR)
            22 -> LimbikFlow(isPlaying, smoothL, smoothR)
        }
        
        androidx.compose.material3.Text(
            text = "MODE ${currentMode + 1}",
            color = Color.White.copy(alpha = 0.12f),
            fontSize = 7.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
        )
    }
}

// IMPLEMENTACIONES DE MODOS (REFINADAS Y RÁPIDAS)
@Composable
fun DigitalLedBars(l: Float, r: Float, pl: Float, pr: Float) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
        LedBar(label = "L", level = l, peakLevel = pl)
        Spacer(Modifier.height(10.dp))
        LedBar(label = "R", level = r, peakLevel = pr)
    }
}

@Composable
fun AnalogVU(l: Float, r: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height; val phosphor = Color(0xFF00FF41)
        for (i in 0..1) {
            val centerX = if (i == 0) w * 0.25f else w * 0.75f
            val centerY = h * 0.9f
            val angle = -140f + (if (i == 0) l else r) * 100f
            drawArc(Color.White.copy(alpha = 0.1f), -150f, 120f, false, Offset(centerX - 40.dp.toPx(), centerY - 40.dp.toPx()), Size(80.dp.toPx(), 80.dp.toPx()), style = Stroke(2.dp.toPx()))
            val rad = Math.toRadians(angle.toDouble())
            val endX = centerX + cos(rad).toFloat() * 60.dp.toPx()
            val endY = centerY + sin(rad).toFloat() * 60.dp.toPx()
            drawLine(if ((if (i==0) l else r) > 0.85f) Color.Red else phosphor, Offset(centerX, centerY), Offset(endX, endY), 2.dp.toPx())
        }
    }
}

@Composable
fun ChunkyBars(l: Float, r: Float, pl: Float, pr: Float) {
    Canvas(Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width; val h = size.height; val barW = w / 20f
        for (i in 0 until 20) {
            val level = if (i < 10) l else r
            val barH = h * level * (0.3f + Random.nextFloat() * 0.7f)
            drawRect(Color.Cyan.copy(alpha = 0.6f), Offset(i * barW, h - barH), Size(barW - 1.dp.toPx(), barH))
        }
    }
}

@Composable
fun NeonWave(isPlaying: Boolean, waveform: FloatArray, energy: Float) {
    Canvas(Modifier.fillMaxSize()) {
        if (!isPlaying) return@Canvas
        val path = Path(); val w = size.width; val h = size.height
        for (i in waveform.indices) {
            val x = (w / waveform.size) * i
            val y = (h/2) + waveform[i] * h * 0.48f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFF00E5FF), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, Color(0xFF00E5FF).copy(alpha = 0.25f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun RadialPips(l: Float, r: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val count = 36
        for (i in 0 until count) {
            val angle = (i.toFloat() / count) * 360f
            val dist = 10.dp.toPx() + (if (i % 2 == 0) l else r) * 35.dp.toPx()
            val rad = Math.toRadians(angle.toDouble())
            drawCircle(Color.White.copy(alpha = 0.8f), 1.2.dp.toPx(), Offset(center.x + cos(rad).toFloat() * dist, center.y + sin(rad).toFloat() * dist))
        }
    }
}

@Composable
fun MirrorWave(waveform: FloatArray, energy: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height; val midY = h / 2f
        for (i in waveform.indices step 2) {
            val x = (w / waveform.size) * i
            val lineH = abs(waveform[i]) * h * 0.65f
            drawLine(Color.Magenta.copy(alpha = 0.9f), Offset(x, midY - lineH), Offset(x, midY + lineH), 1.8.dp.toPx())
        }
    }
}

@Composable
fun MatrixRain(isPlaying: Boolean, energy: Float) {
    Canvas(Modifier.fillMaxSize()) {
        if (!isPlaying) return@Canvas
        for (i in 0 until 20) {
            val x = Random.nextFloat() * size.width
            val y = Random.nextFloat() * size.height
            val alpha = (y / size.height) * energy.coerceAtLeast(0.1f)
            drawCircle(Color.Green.copy(alpha = alpha), 1.2.dp.toPx(), Offset(x, y))
            drawLine(Color.Green.copy(alpha = alpha * 0.5f), Offset(x, y - 10.dp.toPx()), Offset(x, y), 0.8.dp.toPx())
        }
    }
}

@Composable
fun LazerSpikes(waveform: FloatArray, energy: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height; val midY = h / 2f
        for (i in waveform.indices step 2) {
            val x = (w / waveform.size) * i
            val spike = waveform[i] * h * 0.95f
            drawLine(Color.Red, Offset(x, midY), Offset(x, midY - spike), 0.8.dp.toPx())
        }
    }
}

@Composable
fun DotMatrix(l: Float, r: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val rows = 10; val cols = 36
        val cellW = size.width / cols; val cellH = size.height / rows
        for (c in 0 until cols) {
            val level = if (c < cols/2) l else r
            val activeRows = (level * rows).toInt()
            for (rt in 0 until activeRows) {
                drawCircle(Color.Yellow, 1.8.dp.toPx(), Offset(c * cellW + cellW/2, size.height - rt * cellH - cellH/2))
            }
        }
    }
}

@Composable
fun FluidCurve(isPlaying: Boolean, l: Float, r: Float) {
    val anim = rememberInfiniteTransition()
    val phase by anim.animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(1200, easing = LinearEasing)))
    Canvas(Modifier.fillMaxSize()) {
        val path = Path(); val w = size.width; val h = size.height
        path.moveTo(0f, h/2)
        for (i in 0..20) {
            val x = (w / 20) * i
            val y = (h/2) + sin(i * 0.45f + phase) * h * 0.45f * (l + r)
            path.lineTo(x, y)
        }
        drawPath(path, Color.Cyan, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun RGBGlow(l: Float, r: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(Brush.radialGradient(listOf(Color.Red.copy(alpha = l), Color.Transparent)), l * 70.dp.toPx(), center.copy(x = center.x - 40.dp.toPx()))
        drawCircle(Brush.radialGradient(listOf(Color.Green.copy(alpha = r), Color.Transparent)), r * 70.dp.toPx(), center.copy(x = center.x + 40.dp.toPx()))
        drawCircle(Brush.radialGradient(listOf(Color.Blue.copy(alpha = (l+r)/2f), Color.Transparent)), (l+r) * 40.dp.toPx(), center)
    }
}

@Composable
fun CyberGrid(isPlaying: Boolean, energy: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        for (i in 0..10) {
            val y = (h/10) * i
            drawLine(Color.Blue.copy(alpha = 0.1f + 0.4f * energy), Offset(0f, y), Offset(w, y), 0.5f)
            val x = (w/10) * i
            drawLine(Color.Blue.copy(alpha = 0.1f + 0.4f * energy), Offset(x, 0f), Offset(x, h), 0.5f)
        }
        drawCircle(Color.Blue.copy(alpha = energy), 20.dp.toPx() * energy, Offset(w/2, h/2), style = Stroke(2.dp.toPx()))
    }
}

@Composable
fun PlasmaAura(l: Float, r: Float) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.sweepGradient(listOf(Color.Cyan.copy(alpha = l), Color.Magenta.copy(alpha = r), Color.Cyan.copy(alpha = l))))
    }
}

@Composable
fun TapeDeckBars(l: Float, r: Float) {
    Canvas(Modifier.fillMaxSize().padding(15.dp)) {
        val barH = 10.dp.toPx()
        drawRect(Color.DarkGray.copy(alpha = 0.3f), Offset(0f, 10.dp.toPx()), Size(size.width, barH))
        drawRect(Color(0xFFFF9800), Offset(0f, 10.dp.toPx()), Size(size.width * l, barH))
        drawRect(Color.DarkGray.copy(alpha = 0.3f), Offset(0f, 30.dp.toPx()), Size(size.width, barH))
        drawRect(Color(0xFFFF9800), Offset(0f, 30.dp.toPx()), Size(size.width * r, barH))
    }
}

@Composable
fun StrobeHit(isPlaying: Boolean, energy: Float) {
    Box(Modifier.fillMaxSize().background(if (energy > 0.82f) Color.White.copy(alpha = 0.3f * energy) else Color.Transparent))
}

@Composable
fun Blocks3D(l: Float, r: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(Color.White.copy(alpha = 0.08f), Offset(w*0.05f, h*0.05f), Size(w*0.9f, h*0.9f))
        drawRect(Color.White.copy(alpha = l), Offset(w*0.2f, h - h*0.85f*l), Size(w*0.25f, h*0.85f * l))
        drawRect(Color.White.copy(alpha = r), Offset(w*0.55f, h - h*0.85f*r), Size(w*0.25f, h*0.85f * r))
    }
}

@Composable
fun HeartPulse(l: Float, r: Float) {
    val energy = (l + r) / 2f
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(Color.Red.copy(alpha = 0.45f * energy), 5.dp.toPx() + 50.dp.toPx() * energy, center)
        drawCircle(Color.Red, 4.dp.toPx() + 15.dp.toPx() * energy, center)
    }
}

@Composable
fun SonarRadar(isPlaying: Boolean, energy: Float) {
    val anim = rememberInfiniteTransition()
    val sweep by anim.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)))
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(Color.Green.copy(alpha = 0.25f), size.height / 2.1f, center, style = Stroke(0.8.dp.toPx()))
        val rad = Math.toRadians(sweep.toDouble())
        drawLine(Color.Green.copy(alpha = energy.coerceAtLeast(0.3f)), center, Offset(center.x + cos(rad).toFloat() * 130f, center.y + sin(rad).toFloat() * 130f), 2.dp.toPx())
    }
}

@Composable
fun Oscilloscope(isPlaying: Boolean, waveform: FloatArray) {
    Canvas(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(Color(0xFF010501)).border(0.5.dp, Color(0xFF00FF41).copy(alpha = 0.2f), RoundedCornerShape(4.dp))) {
        val w = size.width; val h = size.height; val midY = h / 2f
        if (isPlaying && waveform.isNotEmpty()) {
            val path = Path()
            for (i in waveform.indices) {
                val x = (w / (waveform.size - 1)) * i
                val y = midY + (waveform[i] * h * 0.92f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFF00FF41).copy(alpha = 0.35f), style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, Color(0xFF00FF41), style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round))
        } else {
            drawLine(Color(0xFF00FF41).copy(alpha = 0.25f), Offset(0f, midY), Offset(w, midY), 1.2.dp.toPx())
        }
    }
}

@Composable
fun VintageOscillator(isPlaying: Boolean, levelL: Float, levelR: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "oscillator")
    val phase by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 2f * PI.toFloat(), animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(Color(0xFF030803)).border(1.dp, Color(0xFF1B3A1B), RoundedCornerShape(4.dp))) {
        val w = size.width; val h = size.height; val midY = h / 2f
        val phosphorColor = Color(0xFF00FF41)
        if (isPlaying) {
            val path = Path()
            val combinedEnergy = (levelL + levelR) / 2f
            for (i in 0..100) {
                val x = (w / 100) * i
                val progress = i.toFloat() / 100
                val noise = (Random.nextFloat() - 0.5f) * 18f * combinedEnergy
                val sine1 = sin(progress * 14f + phase) * 28f * levelL
                val sine2 = sin(progress * 32f - phase * 2.2f) * 15f * levelR
                val y = midY + sine1 + sine2 + noise
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, h.coerceAtMost(y.coerceAtLeast(0f)))
            }
            drawPath(path, phosphorColor.copy(alpha = 0.45f), style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, phosphorColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        } else {
            drawLine(phosphorColor.copy(alpha = 0.45f), Offset(0f, midY), Offset(w, midY), 1.8.dp.toPx())
        }
    }
}

@Composable
fun LedBar(label: String, level: Float, peakLevel: Float) {
    val ledCount = 42
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Text(text = label, color = if (label == "R") Color(0xFFFF5555) else Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(22.dp))
        Canvas(modifier = Modifier.weight(1f).height(12.dp)) {
            val width = size.width; val height = size.height
            val ledWidth = (width / ledCount) * 0.7f; val spacing = (width / ledCount) * 0.3f
            for (i in 0 until ledCount) {
                val ledLevel = i.toFloat() / ledCount
                val isActive = level > ledLevel; val isPeak = (peakLevel > ledLevel && peakLevel < ledLevel + (1f / ledCount))
                val baseColor = when { i < ledCount * 0.5 -> Color(0xFF00FF00); i < ledCount * 0.8 -> Color(0xFFFFFF00); else -> Color(0xFFFF0000) }
                val finalColor = if (isActive) baseColor else if (isPeak) baseColor.copy(alpha = 0.95f) else baseColor.copy(alpha = 0.15f)
                drawRoundRect(color = finalColor, topLeft = Offset(i * (ledWidth + spacing), 0f), size = Size(ledWidth, height), cornerRadius = CornerRadius(1.8.dp.toPx()))
            }
        }
    }
}

@Composable
fun GalaxyVortex(isPlaying: Boolean, l: Float, r: Float) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing)))
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val energy = (l + r) / 2f
        for (i in 0 until 12) {
            val angle = rotation + (i * 30f)
            val rad = Math.toRadians(angle.toDouble())
            val dist = 15.dp.toPx() + energy * 40.dp.toPx()
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.5f * energy),
                radius = 3.dp.toPx() + energy * 10.dp.toPx(),
                center = Offset(center.x + cos(rad).toFloat() * dist, center.y + sin(rad).toFloat() * dist)
            )
        }
    }
}

@Composable
fun SpectrumPeaks(l: Float, r: Float, pl: Float, pr: Float) {
    Canvas(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp)) {
        val w = size.width; val h = size.height
        val barW = w / 4f
        // L Bar
        drawRect(Color.Green.copy(alpha = 0.3f), Offset(w*0.1f, h - h*l), Size(barW, h*l))
        drawLine(Color.White, Offset(w*0.1f, h - h*pl), Offset(w*0.1f + barW, h - h*pl), 2.dp.toPx())
        // R Bar
        drawRect(Color.Green.copy(alpha = 0.3f), Offset(w*0.6f, h - h*r), Size(barW, h*r))
        drawLine(Color.White, Offset(w*0.6f, h - h*pr), Offset(w*0.6f + barW, h - h*pr), 2.dp.toPx())
    }
}

@Composable
fun LimbikFlow(isPlaying: Boolean, l: Float, r: Float) {
    val infiniteTransition = rememberInfiniteTransition()
    val phase by infiniteTransition.animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(2000, easing = LinearEasing)))
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val path = Path()
        path.moveTo(0f, h/2f)
        val energy = (l + r) / 2f
        for (i in 0..w.toInt() step 5) {
            val relX = i / w
            val y = h/2f + sin(relX * 10f + phase) * h * 0.4f * energy
            path.lineTo(i.toFloat(), y)
        }
        drawPath(path, Color(0xFF00FF41), style = Stroke(2.dp.toPx()))
        drawPath(path, Color(0xFF00FF41).copy(alpha = 0.2f), style = Stroke(8.dp.toPx()))
    }
}
