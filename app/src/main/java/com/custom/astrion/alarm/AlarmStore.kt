package com.custom.astrion.alarm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The remote's alarms and their pending snoozes, saved in the
 * "astrion_alarms" prefs as JSON (see [encodeAlarms]) and held in
 * [StateFlow]s so the Alarms screen, the Settings row and anything else
 * (Home Assistant sync later) see every change as it happens.
 *
 * Every change made through here reschedules the next Android alarm clock
 * ([AlarmScheduler.reschedule]), so callers only ever edit alarms, never
 * schedule them. Any change to an alarm also drops its snooze, the way a
 * phone's clock app does.
 */
object AlarmStore {
    private const val TAG = "AlarmStore"
    private const val PREFS = "astrion_alarms"
    private const val KEY_ALARMS = "alarms"
    private const val KEY_SNOOZES = "snoozes"

    private val alarmsFlow = MutableStateFlow<List<Alarm>>(emptyList())
    private val snoozesFlow = MutableStateFlow<Map<Int, Long>>(emptyMap())
    private var loaded = false

    /** Every alarm, by time of day. */
    fun alarms(context: Context): StateFlow<List<Alarm>> {
        ensureLoaded(context)
        return alarmsFlow
    }

    /** Alarm id -> epoch ms its snooze ends. */
    fun snoozes(context: Context): StateFlow<Map<Int, Long>> {
        ensureLoaded(context)
        return snoozesFlow
    }

    fun get(context: Context, id: Int): Alarm? = alarms(context).value.firstOrNull { it.id == id }

    /** Adds [alarm] (id 0 gets a new id) or replaces the alarm with its id. Returns what was saved. */
    @Synchronized
    fun save(context: Context, alarm: Alarm): Alarm {
        val current = alarms(context).value
        val id = if (alarm.id > 0) alarm.id else (current.maxOfOrNull { it.id } ?: 0) + 1
        val saved = alarm.copy(id = id).normalized()
        write(context, current.filterNot { it.id == id } + saved, snoozesFlow.value - id)
        return saved
    }

    @Synchronized
    fun delete(context: Context, id: Int) {
        write(context, alarms(context).value.filterNot { it.id == id }, snoozesFlow.value - id)
    }

    @Synchronized
    fun setEnabled(context: Context, id: Int, enabled: Boolean) {
        val alarm = get(context, id) ?: return
        save(context, alarm.copy(enabled = enabled))
    }

    /** Rings [id] again at [untilMs] — whether or not the alarm itself is still enabled. */
    @Synchronized
    fun snooze(context: Context, id: Int, untilMs: Long) {
        if (get(context, id) == null) return
        write(context, alarmsFlow.value, snoozesFlow.value + (id to untilMs))
    }

    /**
     * [ids] just started ringing: their snoozes are used up, and alarms that
     * don't repeat turn themselves off. Doesn't reschedule; AlarmReceiver
     * does that right after, from the time this ring was due.
     */
    @Synchronized
    internal fun onRang(context: Context, ids: Collection<Int>) {
        val alarms = alarms(context).value.map { if (it.id in ids && !it.repeats) it.copy(enabled = false) else it }
        persist(context, alarms, snoozesFlow.value - ids.toSet())
    }

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        val prefs = prefs(context)
        alarmsFlow.value = read(prefs, KEY_ALARMS, emptyList(), ::decodeAlarms).let(::byTime)
        snoozesFlow.value = read(prefs, KEY_SNOOZES, emptyMap(), ::decodeSnoozes)
        loaded = true
    }

    private fun <T> read(prefs: SharedPreferences, key: String, empty: T, decode: (String) -> T): T {
        val text = prefs.getString(key, null) ?: return empty
        return runCatching { decode(text) }
            .onFailure { Log.e(TAG, "Saved $key unreadable, starting empty", it) }
            .getOrDefault(empty)
    }

    private fun write(context: Context, alarms: List<Alarm>, snoozes: Map<Int, Long>) {
        persist(context, alarms, snoozes)
        AlarmScheduler.reschedule(context)
    }

    private fun persist(context: Context, alarms: List<Alarm>, snoozes: Map<Int, Long>) {
        val sortedAlarms = byTime(alarms)
        prefs(context).edit {
            putString(KEY_ALARMS, encodeAlarms(sortedAlarms))
            putString(KEY_SNOOZES, encodeSnoozes(snoozes))
        }
        alarmsFlow.value = sortedAlarms
        snoozesFlow.value = snoozes
    }

    private fun byTime(alarms: List<Alarm>): List<Alarm> = alarms.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))

    private fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
