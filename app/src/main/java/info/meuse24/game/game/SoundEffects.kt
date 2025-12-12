package info.meuse24.game.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Optimized sound effects engine.
 * - Uses multiple AudioTracks per sound type for polyphony (overlapping sounds).
 * - Offloads audio commands to a background thread to prevent "braking" the game loop.
 */
class SoundEffects {
    private val sampleRate = 22_050
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val audioFormat = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(sampleRate)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()

    private var audioExecutor: ExecutorService? = null

    // Buffers (generated once)
    private val laserBuffer = toneBuffer(frequency = 880f, durationSeconds = 0.08f)
    private val explosionBuffer = noiseBuffer(durationSeconds = 0.22f)
    private val hitBuffer = toneBuffer(frequency = 180f, durationSeconds = 0.25f, curve = 2f)
    // Kraken: Two frequencies mixed to create a 12Hz "beat" (roughness), higher pitch for phone speakers
    private val krakenLoopBuffer = beatingBuffer(freq1 = 180f, freq2 = 192f, durationSeconds = 1.0f)
    private val krakenExplosionBuffer = noiseBuffer(durationSeconds = 0.4f, pitch = 0.7f)
    // Game Over: Descending Sawtooth
    private val gameOverBuffer = slideBuffer(startFreq = 350f, endFreq = 60f, durationSeconds = 0.8f)
    // Highscore: Ascending Arpeggio (Fast)
    private val highscoreBuffer = arpeggioBuffer(durationSeconds = 0.6f)

    // Pools of tracks
    private var laserTracks: TrackPool? = null
    private var explosionTracks: TrackPool? = null
    private var hitTracks: TrackPool? = null
    private var krakenExplosionTracks: TrackPool? = null
    private var krakenLoopTrack: AudioTrack? = null
    private var gameOverTracks: TrackPool? = null
    private var highscoreTracks: TrackPool? = null

    init {
        initialize()
    }

    @Synchronized
    fun initialize() {
        if (audioExecutor?.isShutdown == false) return // Already initialized

        audioExecutor = Executors.newSingleThreadExecutor()
        laserTracks = TrackPool(3) { createTrack(laserBuffer) }
        explosionTracks = TrackPool(4) { createTrack(explosionBuffer) }
        hitTracks = TrackPool(2) { createTrack(hitBuffer) }
        krakenExplosionTracks = TrackPool(2) { createTrack(krakenExplosionBuffer) }
        gameOverTracks = TrackPool(1) { createTrack(gameOverBuffer) }
        highscoreTracks = TrackPool(1) { createTrack(highscoreBuffer) }
        
        // Create a specific track for the loop
        krakenLoopTrack = createTrack(krakenLoopBuffer).apply {
            setLoopPoints(0, krakenLoopBuffer.size, -1) // Infinite loop
        }
    }

    private fun createTrack(buffer: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(buffer.size * Short.SIZE_BYTES)
            .build()
        track.write(buffer, 0, buffer.size)
        return track
    }

    private fun toneBuffer(frequency: Float, durationSeconds: Float, curve: Float = 1f): ShortArray {
        val samples = (durationSeconds * sampleRate).toInt().coerceAtLeast(1)
        val buffer = ShortArray(samples)
        for (i in 0 until samples) {
            val envelope = 1f - i / samples.toFloat()
            val shapedEnvelope = envelope.powCurve(curve)
            val angle = 2 * PI * frequency * (i / sampleRate.toDouble())
            val value = (sin(angle) * shapedEnvelope * Short.MAX_VALUE * 0.55).toInt()
            buffer[i] = value.toShort()
        }
        return buffer
    }

    private fun beatingBuffer(freq1: Float, freq2: Float, durationSeconds: Float): ShortArray {
        val samples = (durationSeconds * sampleRate).toInt().coerceAtLeast(1)
        val buffer = ShortArray(samples)
        for (i in 0 until samples) {
            val t = i / sampleRate.toDouble()
            // Mix two sine waves
            val val1 = sin(2 * PI * freq1 * t)
            val val2 = sin(2 * PI * freq2 * t)
            // LFO for extra "swelling"
            val lfo = 0.8 + 0.2 * sin(2 * PI * 2.0 * t) 
            
            // Result is a throbbing, rough sound
            val mixed = (val1 + val2) * 0.5 * lfo
            buffer[i] = (mixed * Short.MAX_VALUE * 0.7).toInt().toShort()
        }
        return buffer
    }

    private fun slideBuffer(startFreq: Float, endFreq: Float, durationSeconds: Float): ShortArray {
        val samples = (durationSeconds * sampleRate).toInt().coerceAtLeast(1)
        val buffer = ShortArray(samples)
        var phase = 0.0
        for (i in 0 until samples) {
            val progress = i / samples.toFloat()
            val freq = startFreq + (endFreq - startFreq) * progress
            phase += 2 * PI * freq / sampleRate
            
            // Sawtooth-ish approximation (richer than sine)
            val raw = (phase % (2 * PI)) / PI - 1.0
            val envelope = 1f - progress
            buffer[i] = (raw * envelope * Short.MAX_VALUE * 0.5).toInt().toShort()
        }
        return buffer
    }

    private fun arpeggioBuffer(durationSeconds: Float): ShortArray {
        val samples = (durationSeconds * sampleRate).toInt().coerceAtLeast(1)
        val buffer = ShortArray(samples)
        // C Major Arpeggio: C4, E4, G4, C5
        val freqs = listOf(261.63, 329.63, 392.00, 523.25)
        val noteDuration = samples / freqs.size
        
        var phase = 0.0
        for (i in 0 until samples) {
            val noteIndex = (i / noteDuration).coerceAtMost(freqs.size - 1)
            val freq = freqs[noteIndex]
            phase += 2 * PI * freq / sampleRate
            
            // Square wave for 8-bit win feel
            val raw = if (sin(phase) > 0) 0.5 else -0.5
            
            // Slight decay per note
            val noteProgress = (i % noteDuration) / noteDuration.toFloat()
            val envelope = 1f - noteProgress * 0.5f
            
            buffer[i] = (raw * envelope * Short.MAX_VALUE * 0.6).toInt().toShort()
        }
        return buffer
    }

    private fun noiseBuffer(durationSeconds: Float, pitch: Float = 1f): ShortArray {
        val samples = (durationSeconds * sampleRate).toInt().coerceAtLeast(1)
        val buffer = ShortArray(samples)
        val random = Random(0xC0FFEE)
        for (i in 0 until samples) {
            val envelope = 1f - i / samples.toFloat()
            val shapedEnvelope = envelope.powCurve(2f)
            val noise = random.nextFloat() * 2f - 1f
            val value = (noise * shapedEnvelope * Short.MAX_VALUE * 0.6f * pitch).toInt() 
            buffer[i] = value.toShort()
        }
        return buffer
    }

    private fun Float.powCurve(curve: Float): Float {
        return this.toDouble().pow(curve.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun playLaser() = play(laserTracks)
    fun playExplosion() = play(explosionTracks)
    fun playPlayerHit() = play(hitTracks)
    fun playKrakenExplosion() = play(krakenExplosionTracks)
    fun playGameOver() = play(gameOverTracks)
    fun playHighscore() = play(highscoreTracks)

    fun startKrakenLoop(speedFactor: Float = 1.0f) {
        val track = krakenLoopTrack ?: return
        audioExecutor?.execute {
            try {
                // Adjust pitch and speed based on Kraken movement speed
                // Faster movement = Higher pitch + Faster pulsing beat
                val safeFactor = speedFactor.coerceIn(0.5f, 2.0f)
                val newRate = (sampleRate * safeFactor).toInt()
                
                track.playbackRate = newRate
                
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.setLoopPoints(0, krakenLoopBuffer.size, -1)
                    track.play()
                }
            } catch (_: Exception) {}
        }
    }

    fun stopKrakenLoop() {
        val track = krakenLoopTrack ?: return
        audioExecutor?.execute {
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
            } catch (_: Exception) {}
        }
    }

    private fun play(pool: TrackPool?) {
        val exec = audioExecutor
        if (exec == null || exec.isShutdown) return
        pool?.play(exec)
    }

    @Synchronized
    fun release() {
        stopKrakenLoop()
        audioExecutor?.shutdown()
        audioExecutor = null
        laserTracks?.release()
        explosionTracks?.release()
        hitTracks?.release()
        krakenExplosionTracks?.release()
        gameOverTracks?.release()
        highscoreTracks?.release()
        krakenLoopTrack?.release()
        
        laserTracks = null
        explosionTracks = null
        hitTracks = null
        krakenExplosionTracks = null
        gameOverTracks = null
        highscoreTracks = null
        krakenLoopTrack = null
    }

    /**
     * Manages a pool of AudioTracks for a specific sound effect.
     */
    private class TrackPool(size: Int, creator: () -> AudioTrack) {
        private val tracks = Array(size) { creator() }
        private var index = 0

        fun play(executor: java.util.concurrent.Executor) {
            // Round-robin selection
            val track = tracks[index]
            index = (index + 1) % tracks.size

            executor.execute {
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                        track.reloadStaticData()
                    }
                    track.setPlaybackHeadPosition(0)
                    track.play()
                } catch (_: Exception) {
                    // Safety ignore
                }
            }
        }

        fun release() {
            tracks.forEach { try { it.release() } catch (_: Exception) {} }
        }
    }
}
