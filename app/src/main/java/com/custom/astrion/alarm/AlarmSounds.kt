package com.custom.astrion.alarm

import android.content.Context
import android.media.RingtoneManager
import android.os.SystemClock
import android.util.Log
import com.custom.astrion.R

/** One choice in the alarm editor's sound list; [id] is what [Alarm.sound] stores. */
data class AlarmSound(val id: String, val title: String)

/**
 * The sounds an alarm can play: Android's default alarm sound, a plain beep
 * that works even on a remote with no sound files, then every alarm sound
 * and every notification chime installed on the remote. Reads the media
 * database, so call it off the main thread.
 */
object AlarmSounds {
    private const val RECENT_MS = 5 * 60_000L

    @Volatile
    private var cached: Pair<Long, List<AlarmSound>>? = null

    /**
     * [available], reused for a few minutes: Home Assistant asks for the list
     * on every poll, and installed sounds hardly ever change.
     */
    fun recent(context: Context): List<AlarmSound> {
        val now = SystemClock.elapsedRealtime()
        cached?.let { (at, sounds) -> if (now - at < RECENT_MS) return sounds }
        return available(context).also { cached = now to it }
    }

    fun available(context: Context): List<AlarmSound> {
        val builtIn =
            listOf(
                AlarmSound(Alarm.SOUND_DEFAULT, context.getString(R.string.alarm_sound_default)),
                AlarmSound(Alarm.SOUND_BEEP, context.getString(R.string.alarm_sound_beep))
            )
        val installed = installed(context, RingtoneManager.TYPE_ALARM) + installed(context, RingtoneManager.TYPE_NOTIFICATION)
        return (builtIn + installed).distinctBy { it.id }
    }

    private fun installed(context: Context, type: Int): List<AlarmSound> = runCatching {
        val manager = RingtoneManager(context).apply { setType(type) }
        // RingtoneManager owns this cursor and hands the same one back each call: not ours to close.
        val cursor = manager.cursor
        buildList {
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
                add(AlarmSound(manager.getRingtoneUri(cursor.position).toString(), title))
            }
        }
    }.onFailure { Log.w("AlarmSounds", "Couldn't list sounds of type $type", it) }
        .getOrDefault(emptyList())
}
