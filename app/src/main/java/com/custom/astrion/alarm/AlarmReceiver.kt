package com.custom.astrion.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Android's alarm clock went off (see [AlarmScheduler]): mark the alarms as
 * rung, schedule whatever comes after them, and start the ringing. Also
 * takes the Snooze/Dismiss buttons of the ringing notification ([answer]).
 */
class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val ACTION_SNOOZE = "com.custom.astrion.alarm.SNOOZE_PRESSED"
        private const val ACTION_DISMISS = "com.custom.astrion.alarm.DISMISS_PRESSED"

        /** The broadcast a notification button sends to snooze or dismiss what's ringing. */
        fun answer(context: Context, snooze: Boolean): Intent =
            Intent(context, AlarmReceiver::class.java).setAction(if (snooze) ACTION_SNOOZE else ACTION_DISMISS)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_FIRE -> fire(context, intent)
            ACTION_SNOOZE -> AlarmRingService.snooze(context)
            ACTION_DISMISS -> AlarmRingService.dismiss(context)
        }
    }

    private fun fire(context: Context, intent: Intent) {
        val dueAt = intent.getLongExtra(AlarmScheduler.EXTRA_AT, 0L)
        val ids = intent.getIntArrayExtra(AlarmScheduler.EXTRA_IDS)?.filter { AlarmStore.get(context, it) != null }.orEmpty()
        Log.i("AlarmReceiver", "Alarm $ids due at $dueAt")
        AlarmStore.onRang(context, ids)
        AlarmScheduler.reschedule(context, notBeforeMs = dueAt)
        if (ids.isNotEmpty()) AlarmRingService.ring(context, ids)
    }
}

/**
 * Puts the next alarm back after Android drops or misplaces it: a reboot
 * clears scheduled alarms, and a clock or time-zone change moves the moment
 * "7:00" falls on. Also after an app update, to be safe.
 */
class AlarmRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> AlarmScheduler.reschedule(context)
        }
    }
}
