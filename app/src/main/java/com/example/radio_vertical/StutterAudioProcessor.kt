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

/**
 * Motor de Audio Radio Vertical - Ultra Hi-Fi Pro Looper (V18).
 * Sincronización rítmica perfecta con interpolación de alta calidad.
 * Corrección del analizador de espectro (Normalización dinámica).
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
    
    private var energySumL = 0f
    private var energySumR = 0f
    private var sampleCount = 0
    private var peakDetected = false 
    
    private val beatIntervals = mutableListOf<Long>()
    private var lastPeakTime = 0L
    private var averageEnergy = 1000f
    private var lastMeasuredIntervalShorts = 0
    private var lastPeakPos = 0

    // Nueva variable para normalización automática "Estilo Limbik"
    private var visualPeakL = 5000f
    private var visualPeakR = 5000f

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
            
            magnetActiveFlow.value = peakDetected && isCalibratedFlow.value
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

        while (shorts.hasRemaining()) {
            val sampleL = abs(shorts.get().toInt()).toFloat()
            if (sampleL > maxL) maxL = sampleL
            
            val sampleR = if (channels > 1 && shorts.hasRemaining()) {
                abs(shorts.get().toInt()).toFloat()
            } else sampleL
            if (sampleR > maxR) maxR = sampleR
            
            energySumL += sampleL
            energySumR += sampleR
            sampleCount++
            
            if (sampleCount >= 256) {
                val curAvg = (energySumL + energySumR) / (sampleCount * 2f)
                if (curAvg > averageEnergy * 1.5f) { 
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
                }
                averageEnergy = averageEnergy * 0.94f + curAvg * 0.06f 
                energySumL = 0f; energySumR = 0f; sampleCount = 0
            }
        }
        
        // NORMALIZACIÓN "CALIBRACIÓN LIMBIK" (V84)
        // Usamos un seguimiento de picos más lento y estable (0.996f)
        // Esto crea un divisor más sólido que no "rebota" tanto con el ruido
        visualPeakL = max(visualPeakL * 0.996f, maxL)
        visualPeakR = max(visualPeakR * 0.996f, maxR)
        
        val minDivisor = 4500f
        // Mapeo lineal pero con una base de referencia muy estable
        // Esto permite que el movimiento sea fluido y no se quede pegado arriba
        energyPeakLFlow.value = (maxL / visualPeakL.coerceAtLeast(minDivisor)).coerceIn(0f, 1.0f)
        energyPeakRFlow.value = (maxR / visualPeakR.coerceAtLeast(minDivisor)).coerceIn(0f, 1.0f)
        
        magnetActiveFlow.value = peakDetected && isCalibratedFlow.value
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
