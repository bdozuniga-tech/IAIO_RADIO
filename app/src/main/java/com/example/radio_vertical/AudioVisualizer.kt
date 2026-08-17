package com.example.radio_vertical

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Visualizador de Espectro con dos modos persistentes.
 */
@Composable
fun SpectrumVisualizer(
    isPlaying: Boolean, 
    energyL: Float,
    energyR: Float
) {
    var smoothL by remember { mutableFloatStateOf(0f) }
    var smoothR by remember { mutableFloatStateOf(0f) }
    var peakL by remember { mutableFloatStateOf(0f) }
    var peakR by remember { mutableFloatStateOf(0f) }

    val currentEnergyL = rememberUpdatedState(energyL)
    val currentEnergyR = rememberUpdatedState(energyR)

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                val el = currentEnergyL.value
                val er = currentEnergyR.value
                
                if (el > 0.001f || er > 0.001f) {
                    // Seguimiento instantáneo con suavizado Limbik
                    smoothL = smoothL * 0.75f + el * 0.25f
                    smoothR = smoothR * 0.75f + er * 0.25f
                    
                    if (el > peakL) peakL = el
                    if (er > peakR) peakR = er
                } else {
                    // DECAY ANALÓGICO REAL: Sigue bajando frame a frame
                    smoothL *= 0.85f
                    smoothR *= 0.85f
                    if (smoothL < 0.001f) smoothL = 0f
                    if (smoothR < 0.001f) smoothR = 0f
                }
                
                // Caída de picos siempre activa
                peakL *= 0.98f
                peakR *= 0.98f
                if (peakL < 0.001f) peakL = 0f
                if (peakR < 0.001f) peakR = 0f
            }
        }
    }

    val isOscillatorMode = GlobalSettings.isOscillatorMode

    Box(
        modifier = Modifier
            .width(310.dp)
            .height(85.dp) // Aumentamos altura para que quepa todo
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.3f)) // Sutil fondo para "encuadre"
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (isOscillatorMode) {
            VintageOscillator(isPlaying, smoothL, smoothR)
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center // Centrado vertical absoluto
            ) {
                LedBar(label = "L", level = smoothL, peakLevel = peakL)
                Spacer(modifier = Modifier.height(10.dp)) // Espacio entre canales
                LedBar(label = "R", level = smoothR, peakLevel = peakR)
            }
        }
    }
}

object GlobalSettings {
    var isOscillatorMode by mutableStateOf(false)
}

@Composable
fun VintageOscillator(isPlaying: Boolean, levelL: Float, levelR: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "oscillator")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0A150A))
            .border(1.dp, Color(0xFF1B3A1B), RoundedCornerShape(4.dp))
    ) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val phosphorColor = Color(0xFF00FF41)
        val glowColor = Color(0xFF00FF41).copy(alpha = 0.3f)

        val gridCells = 8
        for (i in 1 until gridCells) {
            val x = (w / gridCells) * i
            drawLine(Color(0xFF1B3A1B), Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            val y = (h / gridCells) * i
            drawLine(Color(0xFF1B3A1B), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        if (isPlaying) {
            val path = Path()
            val points = 60
            val combinedEnergy = (levelL + levelR) / 2f
            
            for (i in 0..points) {
                val x = (w / points) * i
                val progress = i.toFloat() / points
                val noise = (Random.nextFloat() - 0.5f) * 12f * combinedEnergy
                val sine1 = sin(progress * 10f + phase) * 20f * levelL
                val sine2 = sin(progress * 25f - phase * 1.5f) * 10f * levelR
                val y = midY + sine1 + sine2 + noise
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, h.coerceAtMost(y.coerceAtLeast(0f)))
            }
            drawPath(path, glowColor, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, phosphorColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        } else {
            drawLine(phosphorColor.copy(alpha = 0.5f), Offset(0f, midY), Offset(w, midY), strokeWidth = 1.5.dp.toPx())
        }
    }
}

@Composable
fun LedBar(label: String, level: Float, peakLevel: Float) {
    val ledCount = 42 // Aumentamos de 26 a 42 para máxima precisión visual
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.Text(
            text = label,
            color = if (label == "R") Color(0xFFFF5555) else Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
            modifier = Modifier.width(22.dp)
        )
        Canvas(modifier = Modifier.weight(1f).height(12.dp)) {
            val width = size.width
            val height = size.height
            val ledWidth = (width / ledCount) * 0.6f // LEDs más finos
            val spacing = (width / ledCount) * 0.4f
            for (i in 0 until ledCount) {
                val ledLevel = i.toFloat() / ledCount
                val isActive = level > ledLevel
                val isPeak = (peakLevel > ledLevel && peakLevel < ledLevel + (1f / ledCount))
                val baseColor = when {
                    i < ledCount * 0.5 -> Color(0xFF00FF00) // Verde
                    i < ledCount * 0.8 -> Color(0xFFFFFF00) // Amarillo
                    else -> Color(0xFFFF0000) // Rojo
                }
                val finalColor = when {
                    isActive -> baseColor
                    isPeak -> baseColor.copy(alpha = 0.9f)
                    else -> baseColor.copy(alpha = 0.12f)
                }
                drawRoundRect(color = finalColor, topLeft = Offset(i * (ledWidth + spacing), 0f), size = Size(ledWidth, height), cornerRadius = CornerRadius(1.5.dp.toPx()))
                if (isActive || isPeak) {
                    drawRect(color = Color.White.copy(alpha = 0.3f), topLeft = Offset(i * (ledWidth + spacing) + 1.dp.toPx(), 1.dp.toPx()), size = Size(ledWidth * 0.4f, height * 0.25f))
                }
            }
        }
    }
}
