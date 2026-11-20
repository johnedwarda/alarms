package app.alarm.dusk.ring

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.alarm.dusk.data.Sound
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays the app's alarm sounds. Each sound is synthesized into one loop of PCM,
 * then streamed on the alarm audio channel, so nothing depends on bundled audio files.
 */
class ToneEngine(private val sound: Sound) {
    private val rate = 44100
    @Volatile private var running = false
    private var thread: Thread? = null

    /** [rampMs]: fade in from quiet to full volume (gentle wake). [limitMs]: stop after this long (previews). */
    fun start(rampMs: Long = 45_000, limitMs: Long = Long.MAX_VALUE, usageAlarm: Boolean = true) {
        if (sound == Sound.SILENT || running) return
        running = true
        val loop = buildLoop(sound)
        thread = Thread {
            val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(if (usageAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder().setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                )
                .setBufferSizeInBytes(maxOf(minBuf, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            val chunk = ShortArray(2048)
            var pos = 0
            var written = 0L
            val rampSamples = rampMs * rate / 1000
            val limitSamples = if (limitMs == Long.MAX_VALUE) Long.MAX_VALUE else limitMs * rate / 1000
            try {
                while (running && written < limitSamples) {
                    for (i in chunk.indices) {
                        val g = if (rampSamples <= 0) 1f else (0.12f + 0.88f * min(1f, (written + i).toFloat() / rampSamples))
                        chunk[i] = (loop[pos] * g).toInt().toShort()
                        pos = (pos + 1) % loop.size
                    }
                    track.write(chunk, 0, chunk.size)
                    written += chunk.size
                }
            } finally {
                runCatching { track.pause(); track.flush(); track.stop() }
                track.release()
                running = false
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.join(300)
        thread = null
    }

    private fun buildLoop(s: Sound): FloatArray {
        val notes: Pattern = when (s) {
            // Warm rising arpeggio with a long rest: easy to wake to.
            Sound.SUNRISE -> listOf(
                Note(523.25, 0.0, 0.55), Note(659.25, 0.28, 0.55), Note(783.99, 0.56, 0.55),
                Note(1046.5, 0.84, 1.1),
            ).withLength(2.4)
            // Brisker, brighter pattern.
            Sound.BRIGHT -> listOf(
                Note(783.99, 0.0, 0.18, 2), Note(987.77, 0.16, 0.18, 2), Note(1174.66, 0.32, 0.18, 2),
                Note(1567.98, 0.48, 0.35, 2), Note(1174.66, 0.80, 0.18, 2), Note(1567.98, 0.96, 0.45, 2),
            ).withLength(1.7)
            // Bell partials with long decays.
            Sound.CHIMES -> listOf(
                Note(1318.5, 0.0, 1.6, 3), Note(987.77, 0.55, 1.6, 3), Note(830.61, 1.1, 2.0, 3),
            ).withLength(3.4)
            // Old mechanical bell: fast alternating strikes.
            Sound.BELL -> (0 until 24).map { i -> Note(if (i % 2 == 0) 1400.0 else 1750.0, i * 0.042, 0.08, 4) }.withLength(1.6)
            Sound.SILENT -> listOf<Note>().withLength(1.0)
        }
        val total = (notes.length * rate).toInt()
        val out = FloatArray(total)
        for (n in notes.items) {
            val start = (n.at * rate).toInt()
            val len = (n.dur * rate).toInt()
            for (i in 0 until len) {
                val idx = start + i
                if (idx >= total) break
                val t = i.toDouble() / rate
                val attack = min(1.0, t / 0.012)
                val env = attack * when (n.timbre) {
                    3 -> exp(-t * 2.2)
                    4 -> exp(-t * 18)
                    else -> exp(-t * 3.0)
                }
                val w = 2 * PI * n.f * t
                val v = when (n.timbre) {
                    2 -> sin(w) * 0.75 + sin(2 * w) * 0.2 + sin(3 * w) * 0.05
                    3 -> sin(w) * 0.6 + sin(2.76 * w) * 0.25 * exp(-t * 3) + sin(5.4 * w) * 0.15 * exp(-t * 6)
                    4 -> sin(w) * 0.7 + sin(2.0 * w) * 0.3
                    else -> sin(w) * 0.85 + sin(2 * w) * 0.15
                }
                out[idx] += (v * env).toFloat()
            }
        }
        val peak = out.maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 0f } ?: 1f
        val scale = 0.8f * Short.MAX_VALUE / peak
        for (i in out.indices) out[i] *= scale
        return out
    }

    private data class Note(val f: Double, val at: Double, val dur: Double, val timbre: Int = 1)
    private class Pattern(val items: List<Note>, val length: Double)
    private fun List<Note>.withLength(sec: Double) = Pattern(this, sec)
}
