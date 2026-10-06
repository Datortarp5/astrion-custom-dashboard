package com.custom.astrion.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Android's alarm clock went off (see [AlarmScheduler]): mark the alarms as
 * rung, schedule whatever comes after them, and start the ringing.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
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
