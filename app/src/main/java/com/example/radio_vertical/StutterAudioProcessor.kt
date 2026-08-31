package com.example.radio_vertical

import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Motor de Audio Radio Vertical - Estabilizado y calibrado (V21).
 */
@UnstableApi
class StutterAudioProcessor : BaseAudioProcessor() {

    private val lock = Any() 

    val magnetActiveFlow = MutableStateFlow(false)
    val isCalibratedFlow = MutableStateFlow(false)
    val calibrationCountdownFlow = MutableStateFlow(5)

    var streamingVolume = 1.0f
    
    @Volatile var isScratching = false
    @Volatile var scratchSpeed = 0f 
    private var scratchReadPositionShorts = 0.0f

    @Volatile var effectMix = 0f 
    @Volatile private var effectMixTarget = 0f
    
    @Volatile private var isLooping = false
    @Volatile private var pendingCapture = false
    private var staticLoopBuffer: FloatArray? = null
    private var loopReadPos = 0f 
    private var loopLength = 0
    
    private var flutterPhase = 0f
    private val flutterSpeed = 0.00002f 
    private val flutterDepth = 1.0f 
    
    private var reverbBufferL: FloatArray? = null
    private var reverbBufferR: FloatArray? = null
    private var reverbPosL = 0
    private var reverbPosR = 0
    private val reverbFeedback = 0.82f 
    
    private var rollingBuffer: ShortBuffer? = null
    private var totalBufferSizeShorts = 0
    private var writePositionShorts = 0
    private val maxLoopDurationMs = 8000L

    val bpmFlow = MutableStateFlow(0)
    val energyPeakLFlow = MutableStateFlow(0f)
    val energyPeakRFlow = MutableStateFlow(0f)
    val bandEnergyLFlow = MutableStateFlow(FloatArray(5) { 0f })
    val bandEnergyRFlow = MutableStateFlow(FloatArray(5) { 0f })
    val waveformFlow = MutableStateFlow(FloatArray(128) { 0f })
    
    val isMonoFlow = MutableStateFlow(true)
    
    private var energySumL = 0f
    private var energySumR = 0f
    private var diffSum = 0f
    private var bandSumsL = FloatArray(5) { 0f }
    private var bandSumsR = FloatArray(5) { 0f }
    private var sampleCount = 0
    private var peakDetected = false 
    
    private val beatIntervals = mutableListOf<Long>()
    private var lastPeakTime = 0L
    private var averageEnergy = 1000f
    private var lastMeasuredIntervalShorts = 0
    private var lastPeakPos = 0

    private var visualPeakL = 5000f
    private var visualPeakR = 5000f
    private val bandPeaksL = FloatArray(5) { 4000f }
    private val bandPeaksR = FloatArray(5) { 4000f }

    // ESTADOS DE FILTROS IIR (BANCO DE FRECUENCIAS)
    private var lp80L = 0f; private var lp80R = 0f
    private var lp250L = 0f; private var lp250R = 0f
    private var lp2500L = 0f; private var lp2500R = 0f
    private var lp7000L = 0f; private var lp7000R = 0f

    fun resetVisualPeaks() {
        synchronized(lock) {
            visualPeakL = 5000f
            visualPeakR = 5000f
            bandPeaksL.fill(4000f)
            bandPeaksR.fill(4000f)
            energyPeakLFlow.value = 0f
            energyPeakRFlow.value = 0f
            bandEnergyLFlow.value = FloatArray(5) { 0f }
            bandEnergyRFlow.value = FloatArray(5) { 0f }
            waveformFlow.value = FloatArray(128) { 0f }
            magnetActiveFlow.value = false
            lastMagnetActiveTime = 0L
            diffSum = 0f
            isMonoFlow.value = true
            // Reset filtros
            lp80L = 0f; lp80R = 0f; lp250L = 0f; lp250R = 0f
            lp2500L = 0f; lp2500R = 0f; lp7000L = 0f; lp7000R = 0f
        }
    }

    private var lastMagnetActiveTime = 0L
    private var calibrationStartTime = 0L

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat == AudioFormat.NOT_SET) return AudioFormat.NOT_SET
        synchronized(lock) {
            val sampleRate = inputAudioFormat.sampleRate
            val channels = inputAudioFormat.channelCount
            
            totalBufferSizeShorts = (sampleRate * channels * (maxLoopDurationMs / 1000f)).toInt()
            val byteBuffer = ByteBuffer.allocateDirect(totalBufferSizeShorts * 2 + 1024).order(ByteOrder.nativeOrder())
            rollingBuffer = byteBuffer.asShortBuffer()
            writePositionShorts = 0
            scratchReadPositionShorts = 0f
            
            staticLoopBuffer = FloatArray(sampleRate * channels * 10) 
            
            reverbBufferL = FloatArray((sampleRate * 0.70).toInt())
            reverbBufferR = FloatArray((sampleRate * 0.85).toInt())
            reverbPosL = 0
            reverbPosR = 0
            
            isCalibratedFlow.value = false
            beatIntervals.clear()
            bpmFlow.value = 0
            averageEnergy = 1000f
            visualPeakL = 5000f
            visualPeakR = 5000f
            energyPeakLFlow.value = 0f
            energyPeakRFlow.value = 0f
        }
        return inputAudioFormat
    }

    fun setFxActive(active: Boolean) {
        synchronized(lock) {
            if (active) {
                pendingCapture = true
                effectMixTarget = 1.0f 
            } else {
                isLooping = false
                pendingCapture = false
                effectMixTarget = 0f
                effectMix = 0f
            }
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val shortsBuffer = rollingBuffer ?: return
        val staticLp = staticLoopBuffer ?: return
        val revL = reverbBufferL ?: return
        val revR = reverbBufferR ?: return
        if (!inputBuffer.hasRemaining()) return

        synchronized(lock) {
            detectBpmAndPeakInternal(inputBuffer.duplicate())

            if (!isCalibratedFlow.value) {
                val elapsed = (System.currentTimeMillis() - calibrationStartTime) / 1000
                val remaining = (5 - elapsed).toInt().coerceAtLeast(0)
                calibrationCountdownFlow.value = remaining
                if (remaining <= 0 || beatIntervals.size >= 8) {
                    isCalibratedFlow.value = true
                }
            }

            // CAPTURA
            if (pendingCapture && (peakDetected || !isCalibratedFlow.value)) {
                val channels = inputAudioFormat.channelCount
                val bpm = bpmFlow.value.coerceIn(60, 200).toFloat()
                val oneBeatShorts = if (lastMeasuredIntervalShorts > 0) {
                    lastMeasuredIntervalShorts
                } else {
                    ((inputAudioFormat.sampleRate * 60f) / bpm).toInt() * channels
                }
                
                val beatsToCapture = if (bpm > 110) 8 else 4
                val targetLength = oneBeatShorts * beatsToCapture
                
                if (targetLength > 0) {
                    loopLength = targetLength.coerceAtMost(staticLp.size)
                    val startIdx = (lastPeakPos - loopLength + totalBufferSizeShorts) % totalBufferSizeShorts
                    for (i in 0 until loopLength) {
                        val readIdx = (startIdx + i) % totalBufferSizeShorts
                        staticLp[i] = shortsBuffer.get(readIdx).toFloat() * streamingVolume
                    }
                    loopReadPos = 0f
                    isLooping = true 
                    pendingCapture = false
                    revL.fill(0f); revR.fill(0f)
                }
            }

            val format = inputAudioFormat
            val channels = format.channelCount
            inputBuffer.order(ByteOrder.nativeOrder())
            val inputView = inputBuffer.asShortBuffer()
            val totalShorts = inputView.remaining()
            
            val tempView = inputView.duplicate()
            while (tempView.hasRemaining()) {
                shortsBuffer.put(writePositionShorts, tempView.get())
                writePositionShorts = (writePositionShorts + 1) % totalBufferSizeShorts
            }

            if (!isScratching) {
                scratchReadPositionShorts = (writePositionShorts - totalShorts).toFloat()
                if (scratchReadPositionShorts < 0) scratchReadPositionShorts += totalBufferSizeShorts
            }

            val output = replaceOutputBuffer(totalShorts * 2).order(ByteOrder.nativeOrder())

            var shortsProcessed = 0
            while (shortsProcessed < totalShorts) {
                if (effectMix < effectMixTarget) effectMix += 0.1f 
                else if (effectMix > effectMixTarget) effectMix -= 0.1f 
                effectMix = effectMix.coerceIn(0f, 1f)

                val frameLive = FloatArray(channels)
                for (c in 0 until channels) {
                    if (isScratching) {
                        val readIdx = (scratchReadPositionShorts.toInt() / channels) * channels + c
                        val safeReadIdx = (readIdx % totalBufferSizeShorts + totalBufferSizeShorts) % totalBufferSizeShorts
                        frameLive[c] = shortsBuffer.get(safeReadIdx).toFloat() * streamingVolume
                    } else if (inputView.hasRemaining()) {
                        frameLive[c] = inputView.get().toFloat() * streamingVolume
                    }
                }

                if (isScratching) {
                    scratchReadPositionShorts += scratchSpeed * channels
                    if (scratchReadPositionShorts >= totalBufferSizeShorts) scratchReadPositionShorts -= totalBufferSizeShorts
                    if (scratchReadPositionShorts < 0) scratchReadPositionShorts += totalBufferSizeShorts
                }

                val frameFinal = FloatArray(channels)
                if (isLooping && loopLength > 0) {
                    val wobble = sin(flutterPhase) * flutterDepth
                    flutterPhase += flutterSpeed
                    if (flutterPhase > Math.PI * 2) flutterPhase -= (Math.PI * 2).toFloat()

                    for (c in 0 until channels) {
                        val exactPos = (loopReadPos + c + (wobble * channels) + loopLength * 10) % loopLength
                        val i0 = exactPos.toInt()
                        val i1 = (i0 + channels) % loopLength
                        val frac = exactPos - i0
                        
                        val s0 = staticLp[i0]
                        val s1 = staticLp[i1]
                        val sampled = s0 + frac * (s1 - s0)
                        
                        val dry = sampled
                        
                        if (c == 0) {
                            val delayedL = revL[reverbPosL]
                            revL[reverbPosL] = dry + delayedL * reverbFeedback
                            frameFinal[0] = (dry + delayedL * 0.2f) * effectMix + frameLive[0] * (1.0f - effectMix)
                            reverbPosL = (reverbPosL + 1) % revL.size
                        } else {
                            val delayedR = revR[reverbPosR]
                            revR[reverbPosR] = dry + delayedR * reverbFeedback
                            frameFinal[1] = (dry + delayedR * 0.2f) * effectMix + frameLive[1] * (1.0f - effectMix)
                            reverbPosR = (reverbPosR + 1) % revR.size
                        }
                    }
                    loopReadPos = (loopReadPos + channels) % loopLength
                } else {
                    for (c in 0 until channels) {
                        frameFinal[c] = frameLive[c]
                    }
                }

                for (c in 0 until channels) {
                    val finalSample = frameFinal[c].coerceIn(-32768f, 32767f).toInt().toShort()
                    output.putShort(finalSample)
                }
                shortsProcessed += channels
            }
            output.flip()
            inputBuffer.position(inputBuffer.limit())
            
            magnetActiveFlow.value = (System.currentTimeMillis() - lastMagnetActiveTime < 120) && isCalibratedFlow.value
        }
    }

    private fun detectBpmAndPeakInternal(buffer: ByteBuffer) {
        buffer.order(ByteOrder.nativeOrder())
        val shorts = buffer.asShortBuffer()
        val format = inputAudioFormat
        val channels = format.channelCount
        peakDetected = false 

        var maxL = 0f
        var maxR = 0f
        
        bandSumsL.fill(0f)
        bandSumsR.fill(0f)
        var bufferSamples = 0

        while (shorts.hasRemaining()) {
            val rawL = shorts.get().toFloat() * streamingVolume
            val absL = abs(rawL)
            if (absL > maxL) maxL = absL
            
            val rawR = if (channels > 1 && shorts.hasRemaining()) {
                shorts.get().toFloat() * streamingVolume
            } else rawL
            val absR = abs(rawR)
            if (absR > maxR) maxR = absR

            // BANCO DE FILTROS IIR (Análisis de Frecuencia Real)
            // Coeficientes alpha para 44.1kHz: 80Hz(0.011), 250Hz(0.034), 2.5kHz(0.26), 7kHz(0.50)
            lp80L += 0.011f * (rawL - lp80L); lp80R += 0.011f * (rawR - lp80R)
            lp250L += 0.034f * (rawL - lp250L); lp250R += 0.034f * (rawR - lp250R)
            lp2500L += 0.262f * (rawL - lp2500L); lp2500R += 0.262f * (rawR - lp2500R)
            lp7000L += 0.499f * (rawL - lp7000L); lp7000R += 0.499f * (rawR - lp7000R)

            // Extracción de bandas por sustracción
            bandSumsL[0] += abs(lp80L)                     // SUB
            bandSumsL[1] += abs(lp250L - lp80L)            // LOW
            bandSumsL[2] += abs(lp2500L - lp250L)          // MID
            bandSumsL[3] += abs(lp7000L - lp2500L)         // HIGH
            bandSumsL[4] += abs(rawL - lp7000L)            // TREBLE

            bandSumsR[0] += abs(lp80R)
            bandSumsR[1] += abs(lp250R - lp80R)
            bandSumsR[2] += abs(lp2500R - lp250R)
            bandSumsR[3] += abs(lp7000R - lp2500R)
            bandSumsR[4] += abs(rawR - lp7000R)
            
            energySumL += absL
            energySumR += absR
            diffSum += abs(rawL - rawR)
            sampleCount++
            bufferSamples++
            
            if (sampleCount >= 256) {
                val curAvg = (energySumL + energySumR) / (sampleCount * 2f)
                if (curAvg > averageEnergy * 1.35f) { 
                    val now = System.currentTimeMillis()
                    val interval = now - lastPeakTime
                    if (interval in 300..1200) {
                        lastMeasuredIntervalShorts = ((inputAudioFormat.sampleRate * interval) / 1000).toInt() * channels
                        bpmFlow.value = (60000 / interval).toInt()
                        beatIntervals.add(interval)
                        if (beatIntervals.size > 32) beatIntervals.removeAt(0)
                    }
                    lastPeakTime = now
                    lastPeakPos = writePositionShorts
                    peakDetected = true
                    lastMagnetActiveTime = now
                }
                averageEnergy = averageEnergy * 0.94f + curAvg * 0.06f 
                energySumL = 0f; energySumR = 0f; sampleCount = 0
            }
        }

        // ACTUALIZACIÓN DE BANDAS AL FINAL DEL BUFFER (Estabilidad V22)
        if (bufferSamples > 0) {
            val outL = FloatArray(5)
            val outR = FloatArray(5)
            
            // Detección de Mono (Si la diferencia es menor al 1% de la energía media)
            val monoThreshold = (energySumL + energySumR) / (sampleCount * 100f)
            isMonoFlow.value = (diffSum / sampleCount) < monoThreshold.coerceAtLeast(10f)

            for (i in 0 until 5) {
                val instL = bandSumsL[i] / bufferSamples
                val instR = bandSumsR[i] / bufferSamples
                
                // Normalización inteligente (Decaimiento lento del pico para conservar dinámica)
                bandPeaksL[i] = max(bandPeaksL[i] * 0.9998f, instL)
                bandPeaksR[i] = max(bandPeaksR[i] * 0.9998f, instR)
                
                // Divisor con Headroom del 20% (1.2f) para evitar que MID/SUB se peguen arriba
                val divL = bandPeaksL[i].coerceAtLeast(800f) * 1.2f
                val divR = bandPeaksR[i].coerceAtLeast(800f) * 1.2f
                
                // Escala lineal pura para fidelidad profesional
                outL[i] = (instL / divL).coerceIn(0f, 1f)
                outR[i] = (instR / divR).coerceIn(0f, 1f)
            }
            bandEnergyLFlow.value = outL
            bandEnergyRFlow.value = outR
            diffSum = 0f
        }
        
        // NORMALIZACIÓN ANTI-SATURACIÓN EXTREMA (V96)
        visualPeakL = max(visualPeakL * 0.9992f, maxL)
        visualPeakR = max(visualPeakR * 0.9992f, maxR)
        
        val rms = averageEnergy // Usamos el promedio histórico para mayor estabilidad
        
        // COMPRESIÓN DINÁMICA ULTRA-AGRESIVA PARA RADIOS "HOT"
        val autoHeadroom = when {
            rms > 12000f -> 2.5f 
            rms > 8000f -> 2.1f  
            rms > 4000f -> 1.7f  
            else -> 1.4f         
        }
        
        val minDivisor = 10000f 
        
        // El indicador de energía total ahora también es más reactivo
        energyPeakLFlow.value = (maxL / (visualPeakL * autoHeadroom).coerceAtLeast(minDivisor)).coerceIn(0f, 1.0f)
        energyPeakRFlow.value = (maxR / (visualPeakR * autoHeadroom).coerceAtLeast(minDivisor)).coerceIn(0f, 1.0f)

        val waveform = FloatArray(128)
        val shortsView = buffer.asShortBuffer()
        val step = (shortsView.remaining() / (128 * channels)).coerceAtLeast(1)
        for (i in 0 until 128) {
            if (shortsView.hasRemaining()) {
                waveform[i] = shortsView.get().toFloat() / 32768f
                for (s_idx in 1 until step) if (shortsView.hasRemaining()) shortsView.get()
            }
        }
        waveformFlow.value = waveform
    }

    override fun onReset() {
        synchronized(lock) {
            writePositionShorts = 0
            scratchReadPositionShorts = 0f
            effectMix = 0f
            effectMixTarget = 0f
            isLooping = false
            pendingCapture = false
            staticLoopBuffer?.fill(0f)
            reverbBufferL?.fill(0f)
            reverbBufferR?.fill(0f)
            isCalibratedFlow.value = false
            bpmFlow.value = 0
            flutterPhase = 0f
            lastMeasuredIntervalShorts = 0
            lastPeakPos = 0
            averageEnergy = 1000f
            visualPeakL = 5000f
            visualPeakR = 5000f
            energyPeakLFlow.value = 0f
            energyPeakRFlow.value = 0f
        }
        magnetActiveFlow.value = false
    }
}
