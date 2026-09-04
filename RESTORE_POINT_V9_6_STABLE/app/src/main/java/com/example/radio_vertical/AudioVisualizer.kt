package com.example.radio_vertical

import android.content.res.Configuration
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import kotlin.math.*
import kotlin.random.Random

@OptIn(UnstableApi::class)
@Composable
fun SpectrumVisualizer(
    isPlaying: Boolean, 
    energyL: Float,
    energyR: Float,
    bandsL: FloatArray,
    bandsR: FloatArray,
    waveform: FloatArray,
    isMagnetActive: Boolean,
    isMono: Boolean,
    currentMode: Int,
    currentSpeed: Float = 0f,
    tiltX: Float = 0f,
    tiltY: Float = 0f,
    modifier: Modifier = Modifier.fillMaxWidth().height(130.dp),
    onModeChange: (Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var smoothL by remember { mutableFloatStateOf(0f) }
    var smoothR by remember { mutableFloatStateOf(0f) }
    var peakL by remember { mutableFloatStateOf(0f) }
    var peakR by remember { mutableFloatStateOf(0f) }

    // ESTADO DE 5 BANDAS CON PEAK HOLD (TOTAL 10)
    var bandsStateL by remember { mutableStateOf(FloatArray(5) { 0f }) }
    var peaksStateL by remember { mutableStateOf(FloatArray(5) { 0f }) }
    var peaksAgeL by remember { mutableStateOf(LongArray(5) { 0L }) }
    
    var bandsStateR by remember { mutableStateOf(FloatArray(5) { 0f }) }
    var peaksStateR by remember { mutableStateOf(FloatArray(5) { 0f }) }
    var peaksAgeR by remember { mutableStateOf(LongArray(5) { 0L }) }

    val currentEnergyL = rememberUpdatedState(energyL)
    val currentEnergyR = rememberUpdatedState(energyR)
    val currentBandsL = rememberUpdatedState(bandsL)
    val currentBandsR = rememberUpdatedState(bandsR)

    LaunchedEffect(Unit) {
        var lastFrameTimeNanos = 0L
        while (true) {
            withFrameNanos { time ->
                if (lastFrameTimeNanos == 0L) {
                    lastFrameTimeNanos = time
                    return@withFrameNanos
                }
                val delta = (time - lastFrameTimeNanos) / 1_000_000_000f
                lastFrameTimeNanos = time

                val el = currentEnergyL.value
                val er = currentEnergyR.value
                val bl = currentBandsL.value
                val br = currentBandsR.value

                // REACCIÓN AGRESIVA (V98)
                // Bajamos los factores para que el decaimiento sea mucho más rápido e instantáneo
                val decayFactor = (0.75f.toDouble().pow((delta * 60).toDouble())).toFloat() 
                val peakDecayFactor = (0.85f.toDouble().pow((delta * 60).toDouble())).toFloat() 

                val newBandsL = FloatArray(5)
                val newPeaksL = peaksStateL.copyOf()
                val newAgeL = peaksAgeL.copyOf()
                
                val newBandsR = FloatArray(5)
                val newPeaksR = peaksStateR.copyOf()
                val newAgeR = peaksAgeR.copyOf()

                val now = System.currentTimeMillis()

                for (i in 0 until 5) {
                    // LEFT CHANNEL
                    val targetL = bl[i]
                    newBandsL[i] = if (targetL > bandsStateL[i]) {
                        // Ataque instantáneo para picos de energía
                        targetL 
                    } else {
                        // Decaimiento suave para ocultar micro-pausas de red
                        bandsStateL[i] * decayFactor + targetL * (1f - decayFactor)
                    }
                    
                    if (targetL >= newPeaksL[i]) {
                        newPeaksL[i] = targetL
                        newAgeL[i] = now + 450 // Hold un poco más largo
                    } else if (now > newAgeL[i]) {
                        newPeaksL[i] *= peakDecayFactor
                    }

                    // RIGHT CHANNEL
                    val targetR = if (isMono) targetL else br[i]
                    newBandsR[i] = if (targetR > bandsStateR[i]) {
                        targetR
                    } else {
                        bandsStateR[i] * decayFactor + targetR * (1f - decayFactor)
                    }
                    
                    if (targetR >= newPeaksR[i]) {
                        newPeaksR[i] = targetR
                        newAgeR[i] = now + 450
                    } else if (now > newAgeR[i]) {
                        newPeaksR[i] *= peakDecayFactor
                    }
                }
                
                bandsStateL = newBandsL
                peaksStateL = newPeaksL
                peaksAgeL = newAgeL
                
                bandsStateR = newBandsR
                peaksStateR = newPeaksR
                peaksAgeR = newAgeR

                if (el > 0.001f || er > 0.001f) {
                    smoothL = if (el > smoothL) el else smoothL * decayFactor + el * (1f - decayFactor)
                    smoothR = if (er > smoothR) er else smoothR * decayFactor + er * (1f - decayFactor)
                    if (el > peakL) peakL = el
                    if (er > peakR) peakR = er
                } else {
                    val decayFactor = (0.85f.toDouble().pow((delta * 60).toDouble())).toFloat()
                    smoothL *= decayFactor
                    smoothR *= decayFactor
                }
                peakL *= peakDecayFactor
                peakR *= peakDecayFactor
            }
        }
    }

    val totalModes = 26

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .pointerInput(currentMode) {
                detectTapGestures(onDoubleTap = { offset ->
                    // Navegación Circular Izquierda/Derecha
                    val isLeft = offset.x < size.width / 2f
                    val newMode = if (isLeft) {
                        (currentMode - 1 + totalModes) % totalModes
                    } else {
                        (currentMode + 1) % totalModes
                    }
                    onModeChange(newMode)
                    vibratePhone(context, 35) // Vibración un poco más fuerte para el gesto
                })
            },
        contentAlignment = Alignment.Center
    ) {
        // LÓGICA DE PALPITACIÓN SINCRONIZADA (Beat Pulse)
        // Calculamos el pulso de opacidad igual que en el corazón de favoritos
        val bpmValue = PlaybackService.stutterProcessor.bpmFlow.collectAsState().value
        val pulseTiming = if (bpmValue > 0) 60000 / bpmValue else 800
        val beatPulseAnim = rememberInfiniteTransition(label = "visBeatPulse")
        val beatAlpha by beatPulseAnim.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(pulseTiming / 2), RepeatMode.Reverse),
            label = "alpha"
        )

        // Color base: Cyan si hay Magneto, Verde IAIO normal si no. 
        // Aplicamos beatAlpha para que "palpite" con el ritmo.
        val visBaseColor = if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)
        val visPulseColor = visBaseColor.copy(alpha = if (isPlaying) beatAlpha else 0.5f)
        
        when (currentMode) {
            0 -> DigitalLedBars(bandsStateL.toList(), peaksStateL.toList(), bandsStateR.toList(), peaksStateR.toList(), isMono, isMagnetActive, beatAlpha)
            1 -> VintageOscillator(isPlaying, smoothL, smoothR, isMagnetActive, beatAlpha)
            2 -> Oscilloscope(isPlaying, waveform, isMagnetActive, beatAlpha)
            3 -> AnalogVU(smoothL, smoothR, isMagnetActive, beatAlpha)
            4 -> ChunkyBars(bandsStateL.toList(), bandsStateR.toList(), isMagnetActive, beatAlpha)
            5 -> NeonWave(isPlaying, waveform, smoothL, isMagnetActive, beatAlpha)
            6 -> RadialPips(smoothL, smoothR, isMagnetActive, beatAlpha)
            7 -> MirrorWave(waveform, smoothL, isMagnetActive, beatAlpha)
            8 -> MatrixRain(isPlaying, smoothL, isMagnetActive, beatAlpha)
            9 -> LazerSpikes(waveform, smoothL, isMagnetActive, beatAlpha)
            10 -> DotMatrix(bandsStateL.toList(), bandsStateR.toList(), isMagnetActive, beatAlpha)
            11 -> FluidCurve(isPlaying, smoothL, smoothR, isMagnetActive, beatAlpha)
            12 -> RGBGlow(smoothL, smoothR, isMagnetActive, beatAlpha)
            13 -> CyberGrid(isPlaying, smoothL, isMagnetActive, beatAlpha)
            14 -> PlasmaAura(smoothL, smoothR, isMagnetActive, beatAlpha)
            15 -> TapeDeckBars(bandsStateL.toList(), bandsStateR.toList(), isMagnetActive, beatAlpha)
            16 -> StrobeHit(isPlaying, smoothL, isMagnetActive, beatAlpha)
            17 -> Blocks3D(smoothL, smoothR, isMagnetActive, beatAlpha)
            18 -> HeartPulse(smoothL, smoothR, isMagnetActive, beatAlpha)
            19 -> SonarRadar(isPlaying, smoothL, isMagnetActive, beatAlpha)
            20 -> GalaxyVortex(isPlaying, smoothL, smoothR, isMagnetActive, beatAlpha)
            21 -> SpectrumPeaks(bandsStateL.toList(), peaksStateL.toList(), bandsStateR.toList(), peaksStateR.toList(), isMagnetActive, beatAlpha)
            22 -> LimbikFlow(isPlaying, smoothL, smoothR, isMagnetActive, beatAlpha)
            23 -> MilkdropEvolution(isPlaying, smoothL, smoothR, waveform, isMagnetActive, beatAlpha)
            24 -> StarfieldVisualizer(isPlaying, (smoothL + smoothR) / 2f, isMagnetActive, beatAlpha, tiltX, tiltY)
            25 -> SpeedometerVisualizer(currentSpeed, isMagnetActive, beatAlpha, bandsStateL.toList(), bandsStateR.toList())
        }
        
        androidx.compose.material3.Text(
            text = if (isMono) "MODE ${currentMode + 1} • MONO" else "MODE ${currentMode + 1} • STEREO",
            color = Color.White.copy(alpha = 0.12f),
            fontSize = 7.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
        )
    }
}

@Composable
fun DigitalLedBars(bandsL: List<Float>, peaksL: List<Float>, bandsR: List<Float>, peaksR: List<Float>, isMono: Boolean, isMagnetActive: Boolean, beatAlpha: Float) {
    val labels = listOf("SUB", "LOW", "MID", "HIGH", "TREB")
    
    // El color cambia a Cyan si el magneto está activo, y palpita al ritmo (BPM)
    val baseColor = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
    
    Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
        // CABECERA L/R
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(
                text = "LEFT CHANNEL",
                color = Color(0xFF00FF41).copy(alpha = 0.8f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            if (!isMono) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "RIGHT CHANNEL",
                    color = Color(0xFF00FF41).copy(alpha = 0.8f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // CUERPO DE BARRAS
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // CANAL L
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                labels.forEachIndexed { i, label ->
                    VerticalLedBar(
                        modifier = Modifier.weight(1f),
                        label = label,
                        level = bandsL[i],
                        peak = peaksL[i],
                        color = baseColor
                    )
                }
            }
            
            if (!isMono) {
                Spacer(Modifier.width(8.dp).background(Color.White.copy(alpha = 0.05f)))
                
                // CANAL R
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    labels.forEachIndexed { i, label ->
                        VerticalLedBar(
                            modifier = Modifier.weight(1f),
                            label = label,
                            level = bandsR[i],
                            peak = peaksR[i],
                            color = baseColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VerticalLedBar(modifier: Modifier, label: String, level: Float, peak: Float, color: Color) {
    val ledCount = 22 // Más resolución vertical
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val ledH = (h / ledCount) * 0.75f
                val spacing = (h / ledCount) * 0.25f
                
                for (i in 0 until ledCount) {
                    val ledLevel = i.toFloat() / ledCount
                    val isActive = level > ledLevel
                    // El peak es un único LED flotante
                    val isPeakLed = peak >= ledLevel && peak < (ledLevel + 1f/ledCount)
                    
                    val baseColor = when {
                        i > ledCount * 0.85 -> Color.Red     // Zona de saturación
                        i > ledCount * 0.65 -> Color.Yellow  // Zona de advertencia
                        else -> color                         // Zona segura (IAIO Green)
                    }
                    
                    val finalColor = when {
                        isActive -> baseColor
                        isPeakLed -> baseColor.copy(alpha = 0.95f) // LED flotante brillante
                        else -> baseColor.copy(alpha = 0.08f)      // LED apagado (fantasma)
                    }
                    
                    drawRoundRect(
                        color = finalColor,
                        topLeft = Offset(0f, h - (i + 1) * (ledH + spacing)),
                        size = Size(w, ledH),
                        cornerRadius = CornerRadius(1.dp.toPx())
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 5.sp, // Fuente ultra-compacta para responsividad
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

// ELIMINACIÓN DE FUNCIONES ANTIGUAS E INNECESARIAS PARA LIMPIEZA
// (Se mantienen el resto de modos visuales solicitados)


@Composable
fun BandGroupLabel(text: String) {
    androidx.compose.material3.Text(
        text = text, 
        color = Color.White.copy(alpha = 0.3f), 
        fontSize = 7.sp, 
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
fun AnalogVU(l: Float, r: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height; 
        val phosphor = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
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
fun ChunkyBars(bandsL: List<Float>, bandsR: List<Float>, isMagnetActive: Boolean, beatAlpha: Float) {
    Canvas(Modifier.fillMaxSize().padding(2.dp)) {
        val w = size.width; val h = size.height; val barW = w / 10f
        val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        for (i in 0 until 5) {
            val level = bandsL[i]
            val barH = h * level
            drawRect(color.copy(alpha = 0.6f), Offset(i * barW, h - barH), Size(barW - 2.dp.toPx(), barH))
        }
        for (i in 0 until 5) {
            val level = bandsR[i]
            val barH = h * level
            drawRect(color.copy(alpha = 0.85f), Offset((i + 5) * barW, h - barH), Size(barW - 2.dp.toPx(), barH))
        }
    }
}

@Composable
fun NeonWave(isPlaying: Boolean, waveform: FloatArray, energy: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    Canvas(Modifier.fillMaxSize()) {
        if (!isPlaying) return@Canvas
        val path = Path(); val w = size.width; val h = size.height
        val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        for (i in waveform.indices) {
            val x = (w / waveform.size) * i
            val y = (h/2) + waveform[i] * h * 0.48f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, color.copy(alpha = 0.25f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun RadialPips(l: Float, r: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val count = 36
        val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        for (i in 0 until count) {
            val angle = (i.toFloat() / count) * 360f
            // MÁS APERTURA Y ZOOM EN RADIAL PIPS
            val dist = 20.dp.toPx() + (if (i % 2 == 0) l else r) * 80.dp.toPx()
            val rad = Math.toRadians(angle.toDouble())
            drawCircle(color.copy(alpha = 0.8f), 2.dp.toPx(), Offset(center.x + cos(rad).toFloat() * dist, center.y + sin(rad).toFloat() * dist))
        }
    }
}

@Composable
fun MirrorWave(waveform: FloatArray, energy: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height; val midY = h / 2f
        val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        for (i in waveform.indices step 2) {
            val x = (w / waveform.size) * i
            val lineH = abs(waveform[i]) * h * 0.65f
            drawLine(color.copy(alpha = 0.9f), Offset(x, midY - lineH), Offset(x, midY + lineH), 1.8.dp.toPx())
        }
    }
}

@Composable
fun MatrixRain(isPlaying: Boolean, energy: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    Canvas(Modifier.fillMaxSize()) {
        if (!isPlaying) return@Canvas
        val color = (if (isMagnetActive) Color.Cyan else Color.Green).copy(alpha = beatAlpha)
        for (i in 0 until 20) {
            val x = Random.nextFloat() * size.width
            val y = Random.nextFloat() * size.height
            val alpha = (y / size.height) * energy.coerceAtLeast(0.1f)
            drawCircle(color.copy(alpha = alpha), 1.2.dp.toPx(), Offset(x, y))
            drawLine(color.copy(alpha = alpha * 0.5f), Offset(x, y - 10.dp.toPx()), Offset(x, y), 0.8.dp.toPx())
        }
    }
}

@Composable
fun LazerSpikes(waveform: FloatArray, energy: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height; val midY = h / 2f
        val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        for (i in waveform.indices step 2) {
            val x = (w / waveform.size) * i
            val spike = waveform[i] * h * 0.95f
            drawLine(color, Offset(x, midY), Offset(x, midY - spike), 0.8.dp.toPx())
        }
    }
}

@Composable
fun DotMatrix(bandsL: List<Float>, bandsR: List<Float>, isMagnetActive: Boolean, beatAlpha: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val rows = 12; val cols = 10
        val cellW = size.width / cols; val cellH = size.height / rows
        val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        for (c in 0 until cols) {
            val level = if (c < 5) bandsL[c] else bandsR[c-5]
            val activeRows = (level * rows).toInt()
            for (rt in 0 until activeRows) {
                drawCircle(color, 3.dp.toPx(), Offset(c * cellW + cellW/2, size.height - rt * cellH - cellH/2))
            }
        }
    }
}

@Composable
fun FluidCurve(isPlaying: Boolean, l: Float, r: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    val anim = rememberInfiniteTransition()
    val phase by anim.animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(1200, easing = LinearEasing)))
    Canvas(Modifier.fillMaxSize()) {
        val path = Path(); val w = size.width; val h = size.height
        val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        path.moveTo(0f, h/2)
        for (i in 0..20) {
            val x = (w / 20) * i
            val y = (h/2) + sin(i * 0.45f + phase) * h * 0.45f * (l + r)
            path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun RGBGlow(l: Float, r: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        // RGB GLOW MÁS EXPANSIVO (ZOOM)
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = l), Color.Transparent)), l * 120.dp.toPx(), center.copy(x = center.x - 60.dp.toPx()))
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = r), Color.Transparent)), r * 120.dp.toPx(), center.copy(x = center.x + 60.dp.toPx()))
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = (l+r)/2f), Color.Transparent)), (l+r) * 80.dp.toPx(), center)
    }
}

@Composable
fun CyberGrid(isPlaying: Boolean, energy: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        for (i in 0..10) {
            val y = (h/10) * i
            drawLine(color.copy(alpha = 0.1f + 0.4f * energy), Offset(0f, y), Offset(w, y), 0.5f)
            val x = (w/10) * i
            drawLine(color.copy(alpha = 0.1f + 0.4f * energy), Offset(x, 0f), Offset(x, h), 0.5f)
        }
        drawCircle(color.copy(alpha = energy), 20.dp.toPx() * energy, Offset(w/2, h/2), style = Stroke(2.dp.toPx()))
    }
}

@Composable
fun PlasmaAura(l: Float, r: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val color1 = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        val color2 = (if (isMagnetActive) Color.Blue else Color(0xFF008F11)).copy(alpha = beatAlpha)
        drawRect(Brush.sweepGradient(listOf(color1.copy(alpha = l), color2.copy(alpha = r), color1.copy(alpha = l))))
    }
}

@Composable
fun TapeDeckBars(bandsL: List<Float>, bandsR: List<Float>, isMagnetActive: Boolean, beatAlpha: Float) {
    Canvas(Modifier.fillMaxSize().padding(4.dp)) {
        val barH = size.height / 12f
        val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        for (i in 0 until 5) {
            val y = i * (barH + 2.dp.toPx())
            drawRect(Color.DarkGray.copy(alpha = 0.3f), Offset(0f, y), Size(size.width, barH))
            drawRect(color.copy(alpha = 0.7f), Offset(0f, y), Size(size.width * bandsL[i], barH))
        }
        for (i in 0 until 5) {
            val y = (i + 6) * (barH + 2.dp.toPx())
            drawRect(Color.DarkGray.copy(alpha = 0.3f), Offset(0f, y), Size(size.width, barH))
            drawRect(color, Offset(0f, y), Size(size.width * bandsR[i], barH))
        }
    }
}

@Composable
fun StrobeHit(isPlaying: Boolean, energy: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
    Box(Modifier.fillMaxSize().background(if (energy > 0.82f) color.copy(alpha = 0.3f * energy) else Color.Transparent))
}

@Composable
fun Blocks3D(l: Float, r: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        drawRect(color.copy(alpha = 0.08f), Offset(w*0.05f, h*0.05f), Size(w*0.9f, h*0.9f))
        drawRect(color.copy(alpha = l), Offset(w*0.2f, h - h*0.85f*l), Size(w*0.25f, h*0.85f * l))
        drawRect(color.copy(alpha = r), Offset(w*0.55f, h - h*0.85f*r), Size(w*0.25f, h*0.85f * r))
    }
}

@Composable
fun HeartPulse(l: Float, r: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    val energy = (l + r) / 2f
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        // CORAZÓN MÁS EXPLOSIVO Y GRANDE
        drawCircle(color.copy(alpha = 0.45f * energy), 10.dp.toPx() + 100.dp.toPx() * energy, center)
        drawCircle(color, 6.dp.toPx() + 30.dp.toPx() * energy, center)
    }
}

@Composable
fun SonarRadar(isPlaying: Boolean, energy: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    val anim = rememberInfiniteTransition()
    val sweep by anim.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)))
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val color = (if (isMagnetActive) Color.Cyan else Color.Green).copy(alpha = beatAlpha)
        // RADAR MÁS GRANDE
        drawCircle(color.copy(alpha = 0.25f), size.height / 1.5f, center, style = Stroke(1.dp.toPx()))
        val rad = Math.toRadians(sweep.toDouble())
        drawLine(color.copy(alpha = energy.coerceAtLeast(0.3f)), center, Offset(center.x + cos(rad).toFloat() * 180f, center.y + sin(rad).toFloat() * 180f), 2.dp.toPx())
    }
}

@Composable
fun Oscilloscope(isPlaying: Boolean, waveform: FloatArray, isMagnetActive: Boolean, beatAlpha: Float) {
    val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
    Canvas(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(Color(0xFF010501)).border(0.5.dp, color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))) {
        val w = size.width; val h = size.height; val midY = h / 2f
        if (isPlaying && waveform.isNotEmpty()) {
            val path = Path()
            for (i in waveform.indices) {
                val x = (w / (waveform.size - 1)) * i
                val y = (midY + (waveform[i] * h * 0.92f)).coerceIn(2.dp.toPx(), h - 2.dp.toPx())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color.copy(alpha = 0.35f), style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, color, style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round))
        } else {
            drawLine(color.copy(alpha = 0.25f), Offset(0f, midY), Offset(w, midY), 1.2.dp.toPx())
        }
    }
}

@Composable
fun VintageOscillator(isPlaying: Boolean, levelL: Float, levelR: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "oscillator")
    val phase by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 2f * PI.toFloat(), animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val phosphorColor = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
    Canvas(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(Color(0xFF030803)).border(1.dp, Color(0xFF1B3A1B), RoundedCornerShape(4.dp))) {
        val w = size.width; val h = size.height; val midY = h / 2f
        if (isPlaying) {
            val path = Path()
            val combinedEnergy = (levelL + levelR) / 2f
            for (i in 0..100) {
                val x = (w / 100) * i
                val progress = i.toFloat() / 100
                val noise = (Random.nextFloat() - 0.5f) * 18f * combinedEnergy
                val sine1 = sin(progress * 14f + phase) * 28f * levelL
                val sine2 = sin(progress * 32f - phase * 2.2f) * 15f * levelR
                val y = (midY + sine1 + sine2 + noise).coerceIn(4.dp.toPx(), h - 4.dp.toPx())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
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
        androidx.compose.material3.Text(
            text = label, 
            color = Color.White.copy(alpha = 0.6f), 
            fontSize = 7.sp, 
            fontWeight = FontWeight.Bold, 
            modifier = Modifier.width(32.dp),
            maxLines = 1,
            softWrap = false
        )
        Canvas(modifier = Modifier.weight(1f).height(9.dp)) {
            val width = size.width; val height = size.height
            val ledWidth = (width / ledCount) * 0.7f; val spacing = (width / ledCount) * 0.3f
            for (i in 0 until ledCount) {
                val ledLevel = i.toFloat() / ledCount
                val isActive = level > ledLevel; val isPeak = (peakLevel > ledLevel && peakLevel < ledLevel + (1f / ledCount))
                val baseColor = when { i < ledCount * 0.5 -> Color(0xFF00FF00); i < ledCount * 0.8 -> Color(0xFFFFFF00); else -> Color(0xFFFF0000) }
                val finalColor = if (isActive) baseColor else if (isPeak) baseColor.copy(alpha = 0.95f) else baseColor.copy(alpha = 0.15f)
                drawRoundRect(color = finalColor, topLeft = Offset(i * (ledWidth + spacing), 0f), size = Size(ledWidth, height), cornerRadius = CornerRadius(1.dp.toPx()))
            }
        }
    }
}

@Composable
fun GalaxyVortex(isPlaying: Boolean, l: Float, r: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing)))
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val energy = (l + r) / 2f
        val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        for (i in 0 until 12) {
            val angle = rotation + (i * 30f)
            val rad = Math.toRadians(angle.toDouble())
            // VORTEX MÁS ABIERTO Y DINÁMICO
            val dist = 25.dp.toPx() + energy * 80.dp.toPx()
            drawCircle(
                color = color.copy(alpha = 0.6f * energy),
                radius = 4.dp.toPx() + energy * 15.dp.toPx(),
                center = Offset(center.x + cos(rad).toFloat() * dist, center.y + sin(rad).toFloat() * dist)
            )
        }
    }
}

@Composable
fun SpectrumPeaks(bandsL: List<Float>, peaksL: List<Float>, bandsR: List<Float>, peaksR: List<Float>, isMagnetActive: Boolean, beatAlpha: Float) {
    Canvas(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp)) {
        val w = size.width; val h = size.height
        val barW = w / 10f
        val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        for (i in 0 until 5) {
            val level = bandsL[i]
            val peak = peaksL[i]
            drawRect(color.copy(alpha = 0.3f), Offset(i * barW, h - h * level), Size(barW - 4.dp.toPx(), h * level))
            drawLine(color, Offset(i * barW, h - h * peak), Offset(i * barW + barW - 4.dp.toPx(), h - h * peak), 2.dp.toPx())
        }
        for (i in 0 until 5) {
            val level = bandsR[i]
            val peak = peaksR[i]
            drawRect(color.copy(alpha = 0.5f), Offset((i + 5) * barW, h - h * level), Size(barW - 4.dp.toPx(), h * level))
            drawLine(color, Offset((i + 5) * barW, h - h * peak), Offset((i + 5) * barW + barW - 4.dp.toPx(), h - h * peak), 2.dp.toPx())
        }
    }
}

@Composable
fun LimbikFlow(isPlaying: Boolean, l: Float, r: Float, isMagnetActive: Boolean, beatAlpha: Float) {
    val infiniteTransition = rememberInfiniteTransition()
    val phase by infiniteTransition.animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(2000, easing = LinearEasing)))
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
        val path = Path()
        path.moveTo(0f, h/2f)
        val energy = (l + r) / 2f
        for (i in 0..w.toInt() step 5) {
            val relX = i / w
            val y = (h/2f + sin(relX * 10f + phase) * h * 0.4f * energy).coerceIn(4.dp.toPx(), h - 4.dp.toPx())
            path.lineTo(i.toFloat(), y)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx()))
        drawPath(path, color.copy(alpha = 0.2f), style = Stroke(8.dp.toPx()))
    }
}

@Composable
fun MilkdropEvolution(isPlaying: Boolean, l: Float, r: Float, waveform: FloatArray, isMagnetActive: Boolean, beatAlpha: Float) {
    val energy = (l + r) / 2f
    val color = (if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)).copy(alpha = beatAlpha)
    
    // Animación de rotación y zoom MUCHO MÁS AGRESIVA
    val infiniteTransition = rememberInfiniteTransition(label = "milkdrop")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, 
        targetValue = 360f, 
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)), // Aún más rápido
        label = "rotation"
    )
    val zoom by infiniteTransition.animateFloat(
        initialValue = 0.8f, 
        targetValue = 3.5f, // Zoom extremo
        animationSpec = infiniteRepeatable(tween(2000, easing = SineWaveEasing), RepeatMode.Reverse),
        label = "zoom"
    )

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val centerX = w / 2f; val centerY = h / 2f

        withTransform({
            rotate(rotation, Offset(centerX, centerY))
            scale(zoom, zoom, Offset(centerX, centerY))
        }) {
            // 1. TÚNEL INFINITO EXPANDIDO
            for (i in 0 until 16) { // Más líneas
                val angle = (i * 22.5f)
                val rad = Math.toRadians(angle.toDouble())
                val startDist = 10.dp.toPx() * (1f + energy)
                val endDist = w.coerceAtLeast(h) * 2f // Líneas que salen de la pantalla
                drawLine(
                    color = color.copy(alpha = 0.25f * beatAlpha),
                    start = Offset(centerX + cos(rad).toFloat() * startDist, centerY + sin(rad).toFloat() * startDist),
                    end = Offset(centerX + cos(rad).toFloat() * endDist, centerY + sin(rad).toFloat() * endDist),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // 2. ONDA LÍQUIDA GIGANTE (Extremadamente reactiva)
            if (isPlaying && waveform.isNotEmpty()) {
                val path = Path()
                // Radio base más grande y reactivo
                val radius = 60.dp.toPx() + (80.dp.toPx() * energy) 
                for (i in waveform.indices) {
                    val angle = (i.toFloat() / waveform.size) * 360f
                    val rad = Math.toRadians(angle.toDouble())
                    // Factor de onda mucho más agresivo
                    val waveFactor = waveform[i] * 50.dp.toPx() * beatAlpha
                    val x = centerX + cos(rad).toFloat() * (radius + waveFactor)
                    val y = centerY + sin(rad).toFloat() * (radius + waveFactor)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, color.copy(alpha = 0.4f), style = Stroke(15.dp.toPx(), cap = StrokeCap.Round))
            }

            // 3. NÚCLEO EXPLOSIVO
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.8f * energy), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = 120.dp.toPx() * (energy + 0.5f) * beatAlpha
                ),
                radius = 120.dp.toPx() * (energy + 0.5f) * beatAlpha,
                center = Offset(centerX, centerY)
            )
        }
    }
}

@Composable
fun StarfieldVisualizer(isPlaying: Boolean, energy: Float, isMagnetActive: Boolean, beatAlpha: Float, tiltX: Float, tiltY: Float, modifier: Modifier = Modifier.fillMaxSize()) {
    val stars = remember { List(500) { Star() } }
    val starColor = if (isMagnetActive) Color.Cyan else Color.White
    
    // Suavizado y Zona Muerta para el sensor
    val deadZone = 0.5f
    val smoothTiltX = if (abs(tiltX) < deadZone) 0f else tiltX
    val smoothTiltY = if (abs(tiltY) < deadZone) 0f else tiltY

    if (isPlaying) {
        LaunchedEffect(Unit) {
            var lastTime = System.nanoTime()
            while (true) {
                withFrameNanos { time ->
                    val delta = (time - lastTime) / 1_000_000_000f
                    lastTime = time
                    stars.forEach { it.update(delta, energy) }
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val centerY = h / 2f

        // CÁLCULO DEL PUNTO DE FUGA DINÁMICO (PERSPECTIVA 3D)
        // Limitamos el movimiento del punto de fuga al 35% de la pantalla
        val vpx = centerX + (smoothTiltX.coerceIn(-8f, 8f) / 8f) * (w * 0.35f)
        val vpy = centerY - (smoothTiltY.coerceIn(-8f, 8f) / 8f) * (h * 0.35f)

        stars.forEach { star ->
            val pos = star.getPosition(vpx, vpy, w, h)
            if (pos != null) {
                val alpha = star.alpha * beatAlpha
                drawCircle(
                    color = starColor.copy(alpha = alpha.coerceIn(0f, 1f)),
                    radius = star.size.dp.toPx(),
                    center = pos
                )
                
                // Rastro 3D (Motion Blur)
                if (energy > 0.3f) {
                    val prevPos = star.getPreviousPosition(vpx, vpy, w, h)
                    if (prevPos != null) {
                        drawLine(
                            color = starColor.copy(alpha = alpha * 0.4f),
                            start = pos,
                            end = prevPos,
                            strokeWidth = (star.size * 0.6f).dp.toPx()
                        )
                    }
                }
            }
        }
    }
}

class Star {
    var x = (Random.nextFloat() * 4 - 2) // Rango más amplio para cubrir los bordes al girar
    var y = (Random.nextFloat() * 4 - 2)
    var z = Random.nextFloat()
    var pz = z
    var alpha = 0f
    var size = 0.5f

    fun update(delta: Float, energy: Float) {
        pz = z
        // La estrella solo avanza en Z (hacia el usuario) - VELOCIDAD AUMENTADA
        val speed = 0.35f + energy * 4.5f
        z -= delta * speed
        
        if (z <= 0) {
            z = 1f
            pz = z
            x = (Random.nextFloat() * 4 - 2)
            y = (Random.nextFloat() * 4 - 2)
        }
        
        // Fade in desde el fondo - MÁS BRILLO
        alpha = ((1f - z) * 2.0f).coerceIn(0f, 1f)
        // Zoom de perspectiva - MÁS TAMAÑO
        size = 0.3f + (1f - z) * 4.5f
    }

    fun getPosition(vpx: Float, vpy: Float, width: Float, height: Float): Offset? {
        if (z <= 0) return null
        // Proyección 3D: el punto de fuga (vpx, vpy) actúa como el centro de la cámara
        val screenX = vpx + (x / z) * (width / 2f)
        val screenY = vpy + (y / z) * (height / 2f)
        
        // No dibujamos si está muy fuera (optimización)
        if (screenX < -width || screenX > width * 2 || screenY < -height || screenY > height * 2) {
            return null
        }
        return Offset(screenX, screenY)
    }

    fun getPreviousPosition(vpx: Float, vpy: Float, width: Float, height: Float): Offset? {
        if (pz <= 0) return null
        val screenX = vpx + (x / pz) * (width / 2f)
        val screenY = vpy + (y / pz) * (height / 2f)
        return Offset(screenX, screenY)
    }
}

@Composable
fun SpeedometerVisualizer(speed: Float, isMagnetActive: Boolean, beatAlpha: Float, bandsL: List<Float>, bandsR: List<Float>) {
    val color = if (isMagnetActive) Color.Cyan else Color(0xFF00FF41)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    val digitWidth = if (isLandscape) 100.dp else 50.dp
    val digitHeight = if (isLandscape) 160.dp else 80.dp
    val spacerWidth = if (isLandscape) 80.dp else 40.dp
    val textPadding = if (isLandscape) 24.dp else 12.dp
    val fontSize = if (isLandscape) 24.sp else 12.sp
    
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val speedInt = speed.toInt().coerceIn(0, 999)
            val digits = speedInt.toString().padStart(3, ' ').toCharArray()
            
            digits.forEach { char ->
                if (char == ' ') {
                    Spacer(Modifier.width(spacerWidth))
                } else {
                    SevenSegmentDigit(
                        digit = char.toString().toInt(),
                        color = color,
                        modifier = Modifier.size(width = digitWidth, height = digitHeight).padding(horizontal = if (isLandscape) 8.dp else 4.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(textPadding))
            
            Text(
                text = "KM/H",
                color = color.copy(alpha = 0.6f),
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Bottom)
            )
        }
    }
}

@Composable
fun SevenSegmentDigit(digit: Int, color: Color, modifier: Modifier) {
    val segments = when (digit) {
        0 -> listOf(true, true, true, true, true, true, false)
        1 -> listOf(false, true, true, false, false, false, false)
        2 -> listOf(true, true, false, true, true, false, true)
        3 -> listOf(true, true, true, true, false, false, true)
        4 -> listOf(false, true, true, false, false, true, true)
        5 -> listOf(true, false, true, true, false, true, true)
        6 -> listOf(true, false, true, true, true, true, true)
        7 -> listOf(true, true, true, false, false, false, false)
        8 -> listOf(true, true, true, true, true, true, true)
        9 -> listOf(true, true, true, true, false, true, true)
        else -> List(7) { false }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val thickness = w * 0.15f
        val shadowAlpha = 0.05f

        // Segmentos: a, b, c, d, e, f, g
        // a (arriba)
        drawSegment(segments[0], color, shadowAlpha, Offset(thickness, 0f), Size(w - 2 * thickness, thickness))
        // b (derecha arriba)
        drawSegment(segments[1], color, shadowAlpha, Offset(w - thickness, thickness), Size(thickness, (h / 2) - 1.5f * thickness))
        // c (derecha abajo)
        drawSegment(segments[2], color, shadowAlpha, Offset(w - thickness, (h / 2) + 0.5f * thickness), Size(thickness, (h / 2) - 1.5f * thickness))
        // d (abajo)
        drawSegment(segments[3], color, shadowAlpha, Offset(thickness, h - thickness), Size(w - 2 * thickness, thickness))
        // e (izquierda abajo)
        drawSegment(segments[4], color, shadowAlpha, Offset(0f, (h / 2) + 0.5f * thickness), Size(thickness, (h / 2) - 1.5f * thickness))
        // f (izquierda arriba)
        drawSegment(segments[5], color, shadowAlpha, Offset(0f, thickness), Size(thickness, (h / 2) - 1.5f * thickness))
        // g (centro)
        drawSegment(segments[6], color, shadowAlpha, Offset(thickness, (h / 2) - thickness / 2), Size(w - 2 * thickness, thickness))
    }
}

private fun DrawScope.drawSegment(
    active: Boolean,
    color: Color,
    shadowAlpha: Float,
    offset: Offset,
    size: Size
) {
    val finalColor = if (active) color else color.copy(alpha = shadowAlpha)
    drawRoundRect(
        color = finalColor,
        topLeft = offset,
        size = size,
        cornerRadius = CornerRadius(2.dp.toPx())
    )
    if (active) {
        // Brillo sutil (Glow)
        drawRoundRect(
            color = color.copy(alpha = 0.3f),
            topLeft = offset.copy(x = offset.x - 2.dp.toPx(), y = offset.y - 2.dp.toPx()),
            size = Size(size.width + 4.dp.toPx(), size.height + 4.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

val SineWaveEasing = Easing { f ->
    (1f - cos(f * PI.toFloat())) / 2f
}
