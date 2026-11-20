package app.alarm.dusk.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** The design's confirmation buzzes, played through the vibrator directly so they work on every phone. */
object Haptics {
    private fun vibrator(ctx: Context): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
    }

    private fun play(ctx: Context, timings: LongArray, amps: IntArray) {
        val v = vibrator(ctx) ?: return
        if (!v.hasVibrator()) return
        val effect = if (v.hasAmplitudeControl()) VibrationEffect.createWaveform(timings, amps, -1)
        else VibrationEffect.createWaveform(timings, -1)
        runCatching { v.vibrate(effect) }
    }

    /** Alarm saved: two short taps (design: buzz([18, 70, 18])). */
    fun saved(ctx: Context) = play(ctx, longArrayOf(0, 18, 70, 18), intArrayOf(0, 255, 0, 255))

    /** Alarm deleted: one firmer pulse (design: buzz(40)). */
    fun deleted(ctx: Context) = play(ctx, longArrayOf(0, 40), intArrayOf(0, 255))

    /** Alarm dismissed on the ring screen (design: vibrate(30)). */
    fun dismissed(ctx: Context) = play(ctx, longArrayOf(0, 30), intArrayOf(0, 255))
}
