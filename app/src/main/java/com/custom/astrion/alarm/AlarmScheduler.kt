package com.custom.astrion.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlin.math.max

/**
 * Hands the single next alarm (or snooze) to Android as an alarm clock
 * ([AlarmManager.setAlarmClock]): it fires on time even in deep sleep, shows
 * as the system's next alarm (which is what the clock screensaver reads),
 * and lets the app start its ringing screen from the background.
 *
 * Only one is ever scheduled. When it fires, AlarmReceiver rings it and calls
 * [reschedule] again; every edit in [AlarmStore] does the same. Recomputing
 * from the saved alarms each time means nothing can be left scheduled for an
 * alarm that was changed or deleted.
 */
object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    internal const val ACTION_FIRE = "com.custom.astrion.alarm.FIRE"
    internal const val EXTRA_IDS = "ids"
    internal const val EXTRA_AT = "at"

    /** What rings next from now, or null when nothing is set. */
    fun next(context: Context): AlarmEvent? = nextAlarmEvent(
        AlarmStore.alarms(context).value,
        AlarmStore.snoozes(context).value,
        zonedAt(System.currentTimeMillis())
    )

    /**
     * Schedules whatever rings next after now — or after [notBeforeMs] if
     * that is later, so an alarm that just fired a moment early isn't picked
     * again — or cancels the pending one when nothing is set.
     */
    fun reschedule(context: Context, notBeforeMs: Long = 0L) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val event =
            nextAlarmEvent(
                AlarmStore.alarms(app).value,
                AlarmStore.snoozes(app).value,
                zonedAt(max(System.currentTimeMillis(), notBeforeMs))
            )
        if (event == null) {
            alarmManager.cancel(fireIntent(app, null))
            Log.i(TAG, "No alarm set")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarms not allowed for this app, alarm ${event.ids} can't be scheduled")
            return
        }
        try {
            val info = AlarmManager.AlarmClockInfo(event.atMs, showAlarmsIntent(app))
            alarmManager.setAlarmClock(info, fireIntent(app, event))
            Log.i(TAG, "Next alarm ${event.ids} at ${zonedAt(event.atMs)}")
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to set alarm ${event.ids}", e)
        }
    }

    /** Same request code and action every time, so each one replaces the last; extras say what rings. */
    private fun fireIntent(context: Context, event: AlarmEvent?): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE)
        if (event != null) {
            intent.putExtra(EXTRA_IDS, event.ids.toIntArray()).putExtra(EXTRA_AT, event.atMs)
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Opened when the system's next-alarm indicator is tapped. */
    private fun showAlarmsIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, AlarmsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
