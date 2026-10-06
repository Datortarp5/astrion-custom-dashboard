package com.custom.astrion.alarm

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * One alarm on the remote, as plain data: [AlarmStore] keeps the list,
 * [AlarmScheduler] hands the next one to Android, AlarmRingService rings it.
 *
 * The JSON form ([encodeAlarms]) is what gets saved, and it is meant to be
 * the shape Home Assistant reads and writes too once the integration syncs
 * alarms: plain fields, days as "mon".."sun" like HA's own time conditions.
 * Nothing here touches Android, so the rules below are unit-tested on the JVM.
 */
@Serializable
data class Alarm(
    /** Unique on this remote; 0 means "not saved yet" and [AlarmStore.save] assigns one. */
    val id: Int = 0,
    val hour: Int = 7,
    val minute: Int = 0,
    /** Days it repeats on, "mon".."sun". Empty: it rings once, then turns itself off. */
    val days: List<String> = emptyList(),
    val enabled: Boolean = true,
    val label: String = "",
    /** What it plays: [SOUND_DEFAULT] (Android's alarm sound), [SOUND_BEEP], or an Android sound URI (see AlarmSounds). */
    val sound: String = SOUND_DEFAULT,
    /** Alarm volume while it rings, in percent of the remote's maximum. */
    val volume: Int = DEFAULT_VOLUME,
    @SerialName("snooze_minutes")
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES
) {
    val repeatDays: Set<DayOfWeek> get() = days.mapNotNull(::dayFromKey).toSet()

    val repeats: Boolean get() = repeatDays.isNotEmpty()

    /**
     * The first time strictly after [after] that this alarm rings, in [after]'s
     * time zone, whether or not it is [enabled]. A time that falls in a
     * daylight-saving gap moves forward by the gap, like any wall clock.
     */
    fun nextTrigger(after: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        val repeat = repeatDays
        // Today plus a full week covers "today's time already passed and today is the only repeat day".
        return (0L..7L)
            .asSequence()
            .map { ZonedDateTime.of(after.toLocalDate().plusDays(it), time, after.zone) }
            .first { it.isAfter(after) && (repeat.isEmpty() || it.dayOfWeek in repeat) }
    }

    /** This alarm with every field inside its allowed range, days in Monday-first order. */
    fun normalized(): Alarm {
        val repeat = repeatDays
        return copy(
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            days = DayOfWeek.entries.filter { it in repeat }.map(::dayKey),
            label = label.trim().take(MAX_LABEL_LENGTH),
            volume = volume.coerceIn(MIN_VOLUME, MAX_VOLUME),
            snoozeMinutes = snoozeMinutes.coerceIn(MIN_SNOOZE_MINUTES, MAX_SNOOZE_MINUTES)
        )
    }

    companion object {
        const val SOUND_DEFAULT = ""
        const val SOUND_BEEP = "beep"
        const val DEFAULT_VOLUME = 70
        const val MIN_VOLUME = 5
        const val MAX_VOLUME = 100
        const val DEFAULT_SNOOZE_MINUTES = 10
        const val MIN_SNOOZE_MINUTES = 1
        const val MAX_SNOOZE_MINUTES = 60
        const val MAX_LABEL_LENGTH = 40

        /** The editor's snooze slider stops, in minutes. */
        val SNOOZE_CHOICES = listOf(1, 5, 10, 15, 20, 30)

        /** MONDAY -> "mon". */
        fun dayKey(day: DayOfWeek): String = day.name.take(3).lowercase()

        fun dayFromKey(key: String): DayOfWeek? = DayOfWeek.entries.firstOrNull { dayKey(it) == key.trim().lowercase() }
    }
}

/** When something rings next: these alarms, at [atMs]. Several share one event when they're set for the same minute. */
data class AlarmEvent(val atMs: Long, val ids: List<Int>)

/**
 * The next thing to ring strictly after [after]: an enabled alarm's next
 * time, or a snooze. Snoozes of alarms that no longer exist, or that are
 * already in the past (the remote was off), don't count.
 */
fun nextAlarmEvent(alarms: List<Alarm>, snoozes: Map<Int, Long>, after: ZonedDateTime): AlarmEvent? {
    val afterMs = after.toInstant().toEpochMilli()
    val ids = alarms.map { it.id }.toSet()
    val due =
        alarms.filter { it.enabled }.map { it.id to it.nextTrigger(after).toInstant().toEpochMilli() } +
            snoozes.filter { (id, at) -> id in ids && at > afterMs }.map { (id, at) -> id to at }
    val atMs = due.minOfOrNull { it.second } ?: return null
    return AlarmEvent(atMs, due.filter { it.second == atMs }.map { it.first }.distinct())
}

/** [ms] as a [ZonedDateTime] in the remote's current time zone. */
fun zonedAt(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

private val alarmJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

private val alarmListSerializer = ListSerializer(Alarm.serializer())
private val snoozeMapSerializer = MapSerializer(Int.serializer(), Long.serializer())

fun encodeAlarms(alarms: List<Alarm>): String = alarmJson.encodeToString(alarmListSerializer, alarms)

/** Throws on malformed JSON, so the caller decides what a broken save means. */
fun decodeAlarms(text: String): List<Alarm> = alarmJson.decodeFromString(alarmListSerializer, text).map { it.normalized() }

/** Snoozes as alarm id -> epoch ms it rings again. */
fun encodeSnoozes(snoozes: Map<Int, Long>): String = alarmJson.encodeToString(snoozeMapSerializer, snoozes)

fun decodeSnoozes(text: String): Map<Int, Long> = alarmJson.decodeFromString(snoozeMapSerializer, text)
