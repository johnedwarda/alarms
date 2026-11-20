package app.alarm.dusk.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import app.alarm.dusk.MainActivity
import app.alarm.dusk.data.Alarm
import app.alarm.dusk.data.AppData
import app.alarm.dusk.data.Holidays
import app.alarm.dusk.data.Repo
import app.alarm.dusk.data.Settings
import app.alarm.dusk.ring.RingService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** When alarms ring: shared by the scheduler and the UI so both always agree. */
object Occurrences {
    fun dow(date: LocalDate) = date.dayOfWeek.value - 1

    fun silentOn(a: Alarm, date: LocalDate, s: Settings): Boolean =
        a.skipDate == date || (a.isRepeating && a.skipHol && Holidays.skippedOn(date, s.holSkip))

    /** The next time [a] rings after [now], honouring skips. */
    fun next(a: Alarm, s: Settings, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val days = a.dayList()
        for (k in 0..21) {
            val date = now.toLocalDate().plusDays(k.toLong())
            if (dow(date) !in days) continue
            val dt = date.atTime(a.time / 60, a.time % 60)
            if (!dt.isAfter(now)) continue
            if (silentOn(a, date, s)) continue
            return dt
        }
        return null
    }

    /** The next date (today or later) on which weekday [d] at [time] is still ahead. */
    fun nextDateOf(d: Int, time: Int, now: LocalDateTime = LocalDateTime.now()): LocalDate {
        for (k in 0..7) {
            val date = now.toLocalDate().plusDays(k.toLong())
            if (dow(date) == d && date.atTime(time / 60, time % 60).isAfter(now)) return date
        }
        return now.toLocalDate().plusDays(7)
    }

    /** The next ring date ignoring skips: what "Skip next" will silence. */
    fun nextUnskipped(a: Alarm, now: LocalDateTime = LocalDateTime.now()): LocalDate? =
        a.dayList().map { nextDateOf(it, a.time, now) }.minOrNull()
}

object Scheduler {
    private const val PREFS = "scheduler"
    const val EXTRA_ID = "alarm_id"

    fun rescheduleAll(ctx: Context, data: AppData = Repo.get(ctx).data.value) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getStringSet("ids", emptySet())!!.mapNotNull { it.toIntOrNull() }
        old.forEach { am.cancel(firePI(ctx, it)) }

        val scheduled = mutableSetOf<String>()
        if (data.settings.allOn) {
            for (a in data.alarms) {
                val at = Occurrences.next(a, data.settings) ?: continue
                setAt(ctx, am, at, firePI(ctx, a.id))
                scheduled += a.id.toString()
            }
        }
        prefs.edit().putStringSet("ids", scheduled).apply()
    }

    fun snooze(ctx: Context, id: Int, minutes: Int): LocalDateTime {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = LocalDateTime.now().withSecond(0).withNano(0).plusMinutes(minutes.toLong())
        setAt(ctx, am, at, snoozePI(ctx, id))
        return at
    }

    fun cancelSnooze(ctx: Context, id: Int) {
        ctx.getSystemService(AlarmManager::class.java).cancel(snoozePI(ctx, id))
    }

    private fun setAt(ctx: Context, am: AlarmManager, at: LocalDateTime, pi: PendingIntent) {
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            val show = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(ms, show), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    private fun firePI(ctx: Context, id: Int) = PendingIntent.getBroadcast(
        ctx, id, Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun snoozePI(ctx: Context, id: Int) = PendingIntent.getBroadcast(
        ctx, -id, Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_ID, id).putExtra("snoozed", true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, -1)
        if (id < 0) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, RingService::class.java).setAction(RingService.ACTION_RING).putExtra(Scheduler.EXTRA_ID, id),
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = Repo.get(context)
        repo.clearStaleSkips()
        Scheduler.rescheduleAll(context)
    }
}
