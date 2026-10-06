package com.custom.astrion.alarm

import java.time.ZonedDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/*
 * The alarms as the local web API (ConfigServer's /alarms routes) shows and
 * takes them, for the Home Assistant integration. Plain functions with no
 * Android in them, so they're unit-tested on the JVM like Alarm.kt.
 */

private val apiJson = Json { encodeDefaults = true }

/**
 * What GET /alarms answers, and what the remote pushes to Home Assistant
 * when its alarms change:
 *
 *     {"alarms": [<alarm>, ...],
 *      "next": {"at_ms": <epoch ms>, "ids": [<id>, ...]} | null,
 *      "sounds": [{"id": "", "title": "Default alarm sound"}, ...]}
 *
 * Each alarm is its saved JSON (see [Alarm]) plus `next_ring_ms`, when it
 * rings next counting its snooze (null when it's off and not snoozed), and
 * `snoozed_until_ms` (null when not snoozed). Times are epoch milliseconds,
 * worked out in the remote's own time zone.
 */
fun alarmsJson(alarms: List<Alarm>, snoozes: Map<Int, Long>, sounds: List<AlarmSound>, now: ZonedDateTime): JsonObject {
    val nowMs = now.toInstant().toEpochMilli()
    val next = nextAlarmEvent(alarms, snoozes, now)
    return buildJsonObject {
        put(
            "alarms",
            buildJsonArray {
                alarms.forEach { alarm ->
                    val snooze = snoozes[alarm.id]?.takeIf { it > nowMs }
                    val ringsAt = nextAlarmEvent(listOf(alarm), snoozes, now)?.atMs
                    val fields = apiJson.encodeToJsonElement(Alarm.serializer(), alarm).jsonObject
                    add(
                        JsonObject(
                            fields +
                                mapOf(
                                    "next_ring_ms" to (ringsAt?.let(::JsonPrimitive) ?: JsonNull),
                                    "snoozed_until_ms" to (snooze?.let(::JsonPrimitive) ?: JsonNull)
                                )
                        )
                    )
                }
            }
        )
        if (next == null) {
            put("next", JsonNull)
        } else {
            put(
                "next",
                buildJsonObject {
                    put("at_ms", next.atMs)
                    put("ids", JsonArray(next.ids.map(::JsonPrimitive)))
                }
            )
        }
        put(
            "sounds",
            buildJsonArray {
                sounds.forEach { sound ->
                    add(
                        buildJsonObject {
                            put("id", sound.id)
                            put("title", sound.title)
                        }
                    )
                }
            }
        )
    }
}

/**
 * [base] with the posted form [fields] applied; a field that isn't posted
 * keeps [base]'s value, so an edit only sends what changes. Fields:
 * `time` ("7:30" or "07:30:00"), `days` (comma-separated "mon".."sun", empty
 * for a one-time alarm), `enabled` ("true"/"false"), `label`, `sound` (one of
 * [soundIds]), `volume` (5-100) and `snooze_minutes` (1-60).
 *
 * Throws [IllegalArgumentException] naming the first bad field, rather than
 * quietly saving something else than what was asked for.
 */
fun applyAlarmFields(base: Alarm, fields: Map<String, String>, soundIds: Collection<String>): Alarm {
    var alarm = base
    fields["time"]?.let { text ->
        val parts = text.trim().split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()
        val minute = parts.getOrNull(1)?.toIntOrNull()
        require(parts.size in 2..3 && hour != null && minute != null && hour in 0..23 && minute in 0..59) {
            "time must be HH:MM, got '$text'"
        }
        alarm = alarm.copy(hour = hour, minute = minute)
    }
    fields["days"]?.let { text ->
        val keys = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val unknown = keys.filter { Alarm.dayFromKey(it) == null }
        require(unknown.isEmpty()) { "days must be mon, tue, wed, thu, fri, sat or sun, got '${unknown.first()}'" }
        alarm = alarm.copy(days = keys)
    }
    fields["enabled"]?.let { text ->
        val enabled = text.trim().lowercase().toBooleanStrictOrNull()
        require(enabled != null) { "enabled must be true or false, got '$text'" }
        alarm = alarm.copy(enabled = enabled)
    }
    fields["label"]?.let { text ->
        require(text.trim().length <= Alarm.MAX_LABEL_LENGTH) { "label must be at most ${Alarm.MAX_LABEL_LENGTH} characters" }
        alarm = alarm.copy(label = text)
    }
    fields["sound"]?.let { id ->
        require(id in soundIds) { "no sound '$id' on this remote" }
        alarm = alarm.copy(sound = id)
    }
    fields["volume"]?.let { text ->
        alarm = alarm.copy(volume = intField("volume", text, Alarm.MIN_VOLUME..Alarm.MAX_VOLUME))
    }
    fields["snooze_minutes"]?.let { text ->
        alarm = alarm.copy(snoozeMinutes = intField("snooze_minutes", text, Alarm.MIN_SNOOZE_MINUTES..Alarm.MAX_SNOOZE_MINUTES))
    }
    return alarm.normalized()
}

private fun intField(name: String, text: String, range: IntRange): Int {
    val value = text.trim().toIntOrNull()
    require(value != null && value in range) { "$name must be a whole number from ${range.first} to ${range.last}, got '$text'" }
    return value
}
