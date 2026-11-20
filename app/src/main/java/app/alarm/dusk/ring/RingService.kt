package app.alarm.dusk.ring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.alarm.dusk.R
import app.alarm.dusk.data.Alarm
import app.alarm.dusk.data.Rec
import app.alarm.dusk.data.Repo
import app.alarm.dusk.schedule.Scheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.format.DateTimeFormatter

/** Keeps the alarm ringing (sound + vibration) in the foreground until it's snoozed or dismissed. */
class RingService : Service() {

    private var tone: ToneEngine? = null
    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { snooze() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> ring(intent.getIntExtra(Scheduler.EXTRA_ID, -1))
            ACTION_SNOOZE -> snooze()
            ACTION_DISMISS -> dismiss()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ring(id: Int) {
        val repo = Repo.get(this)
        val alarm = repo.alarm(id)
        // Whatever happens, the next occurrence of every alarm gets scheduled again.
        Scheduler.rescheduleAll(this)
        ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
        if (alarm == null || !repo.data.value.settings.allOn) {
            // Started with startForegroundService, so it must enter the foreground before leaving.
            if (_ringing.value == null) {
                ServiceCompat.startForeground(
                    this, NOTIF_ID,
                    NotificationCompat.Builder(this, CHANNEL_INFO).setSmallIcon(R.drawable.ic_stat_alarm)
                        .setContentTitle("Alarms").build(),
                    type,
                )
                finish()
            }
            return
        }
        if (_ringing.value != null) stopOutputs() // a second alarm takes over
        _ringing.value = alarm

        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(alarm), type)

        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alarm:ring").apply { acquire(11 * 60_000L) }

        val s = repo.data.value.settings
        tone = ToneEngine(s.sound).also { it.start(rampMs = if (s.sound.name == "SUNRISE") 60_000 else 20_000) }
        if (s.vibrate) startVibration()

        // A forgotten alarm doesn't ring forever: after 10 minutes it snoozes itself.
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 10 * 60_000L)

        // Try to open the ring screen directly too (works when the app is allowed to); the
        // full-screen notification covers the lock screen case.
        runCatching { startActivity(ringActivityIntent(this)) }
    }

    private fun snooze() {
        val alarm = _ringing.value ?: return finish()
        val minutes = Repo.get(this).data.value.settings.snooze
        val at = Scheduler.snooze(this, alarm.id, minutes)
        stopOutputs()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            SNOOZE_NOTIF_BASE + alarm.id,
            NotificationCompat.Builder(this, CHANNEL_INFO)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle("Snoozed until " + at.format(DateTimeFormatter.ofPattern("HH:mm")))
                .setContentText(alarm.name.ifBlank { "Alarm" })
                .setTimeoutAfter(minutes * 60_000L)
                .setOngoing(false)
                .build(),
        )
        finish()
    }

    private fun dismiss() {
        val alarm = _ringing.value
        stopOutputs()
        if (alarm != null) {
            Scheduler.cancelSnooze(this, alarm.id)
            getSystemService(NotificationManager::class.java).cancel(SNOOZE_NOTIF_BASE + alarm.id)
            // A one-time alarm is done once it has rung.
            if (alarm.rec == Rec.ONCE) {
                Repo.get(this).update { d -> d.copy(alarms = d.alarms.filterNot { it.id == alarm.id }) }
            }
        }
        finish()
    }

    private fun finish() {
        _ringing.value = null
        handler.removeCallbacks(timeout)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopOutputs() {
        tone?.stop(); tone = null
        vibrator?.cancel(); vibrator = null
        wake?.let { if (it.isHeld) it.release() }; wake = null
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        }
        vibrator = v
        val pattern = longArrayOf(0, 600, 400, 600, 1200)
        v.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun buildNotification(a: Alarm): Notification {
        val full = PendingIntent.getActivity(
            this, 1, ringActivityIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snoozePI = PendingIntent.getService(
            this, 2, Intent(this, RingService::class.java).setAction(ACTION_SNOOZE), PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissPI = PendingIntent.getService(
            this, 3, Intent(this, RingService::class.java).setAction(ACTION_DISMISS), PendingIntent.FLAG_IMMUTABLE,
        )
        val time = "%02d:%02d".format(a.time / 60, a.time % 60)
        return NotificationCompat.Builder(this, CHANNEL_RING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(time)
            .setContentText(a.name.ifBlank { "Alarm" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setColor(0xFFC618C4.toInt())
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Snooze", snoozePI)
            .addAction(0, "Dismiss", dismissPI)
            .build()
    }

    override fun onDestroy() {
        stopOutputs()
        handler.removeCallbacks(timeout)
        _ringing.value = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_RING = "app.alarm.dusk.RING"
        const val ACTION_SNOOZE = "app.alarm.dusk.SNOOZE"
        const val ACTION_DISMISS = "app.alarm.dusk.DISMISS"
        private const val CHANNEL_RING = "ringing"
        private const val CHANNEL_INFO = "info"
        private const val NOTIF_ID = 42
        private const val SNOOZE_NOTIF_BASE = 1000

        private val _ringing = MutableStateFlow<Alarm?>(null)
        /** The alarm ringing right now, or null. The ring screen closes itself when this clears. */
        val ringing: StateFlow<Alarm?> = _ringing

        fun ringActivityIntent(ctx: Context) = Intent(ctx, RingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

        fun send(ctx: Context, action: String) {
            ctx.startService(Intent(ctx, RingService::class.java).setAction(action))
        }

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_RING) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_RING, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                        setSound(null, null) // the service plays the sound itself
                        enableVibration(false)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    },
                )
            }
            if (nm.getNotificationChannel(CHANNEL_INFO) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_INFO, "Snoozed alarms", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }
}
