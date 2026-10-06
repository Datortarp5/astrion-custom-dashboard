package com.custom.astrion.alarm

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmApiTest {
    private val riga = ZoneId.of("Europe/Riga")
    private val sounds = listOf(AlarmSound("", "Default alarm sound"), AlarmSound("beep", "Beep"))
    private val soundIds = sounds.map { it.id }

    /** 2026-10-06 is a Tuesday. */
    private fun at(day: Int, hour: Int, minute: Int): ZonedDateTime = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, riga)

    private fun ms(day: Int, hour: Int, minute: Int): Long = at(day, hour, minute).toInstant().toEpochMilli()

    private fun JsonObject.alarm(id: Int): JsonObject =
        getValue("alarms").jsonArray.map { it.jsonObject }.single { it.getValue("id").jsonPrimitive.int == id }

    @Test
    fun listShowsEachAlarmWithWhenItRingsNext() {
        val alarms =
            listOf(
                Alarm(id = 1, hour = 7, minute = 0, days = listOf("mon", "tue")),
                Alarm(id = 2, hour = 6, minute = 30, enabled = false),
                Alarm(id = 3, hour = 9, minute = 0, enabled = false)
            )
        val json = alarmsJson(alarms, mapOf(3 to ms(6, 6, 10)), sounds, at(6, 6, 0))

        val first = json.alarm(1)
        assertEquals(ms(6, 7, 0), first.getValue("next_ring_ms").jsonPrimitive.long)
        assertEquals(JsonNull, first.getValue("snoozed_until_ms"))
        assertEquals(listOf("mon", "tue"), first.getValue("days").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(10, first.getValue("snooze_minutes").jsonPrimitive.int)

        // Off and not snoozed: never rings.
        assertEquals(JsonNull, json.alarm(2).getValue("next_ring_ms"))

        // Off but snoozed: rings once more, at the snooze.
        val snoozed = json.alarm(3)
        assertEquals(ms(6, 6, 10), snoozed.getValue("next_ring_ms").jsonPrimitive.long)
        assertEquals(ms(6, 6, 10), snoozed.getValue("snoozed_until_ms").jsonPrimitive.long)

        val next = json.getValue("next").jsonObject
        assertEquals(ms(6, 6, 10), next.getValue("at_ms").jsonPrimitive.long)
        assertEquals(listOf(3), next.getValue("ids").jsonArray.map { it.jsonPrimitive.int })

        assertEquals(listOf("", "beep"), json.getValue("sounds").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content })
    }

    @Test
    fun nothingSetMeansNoNextRing() {
        val json = alarmsJson(listOf(Alarm(id = 1, enabled = false)), emptyMap(), sounds, at(6, 6, 0))
        assertEquals(JsonNull, json.getValue("next"))
    }

    @Test
    fun allFieldsApply() {
        val fields =
            mapOf(
                "time" to "06:45:00",
                "days" to "fri, mon",
                "enabled" to "false",
                "label" to "  Work ",
                "sound" to "beep",
                "volume" to "40",
                "snooze_minutes" to "5"
            )
        val alarm = applyAlarmFields(Alarm(id = 4), fields, soundIds)
        assertEquals(
            Alarm(
                id = 4,
                hour = 6,
                minute = 45,
                days = listOf("mon", "fri"),
                enabled = false,
                label = "Work",
                sound = "beep",
                volume = 40,
                snoozeMinutes = 5
            ),
            alarm
        )
    }

    @Test
    fun fieldsNotPostedKeepTheirValue() {
        val before = Alarm(id = 2, hour = 5, minute = 10, days = listOf("sat"), label = "Gym", volume = 90)
        assertEquals(before.copy(enabled = false), applyAlarmFields(before, mapOf("enabled" to "false"), soundIds))
    }

    @Test
    fun emptyDaysMakeItOneTime() {
        val before = Alarm(days = listOf("mon"))
        assertTrue(applyAlarmFields(before, mapOf("days" to ""), soundIds).days.isEmpty())
    }

    @Test
    fun badFieldsAreRefusedNotFixedUp() {
        listOf(
            "time" to "24:00",
            "time" to "7",
            "time" to "seven:30",
            "days" to "mon,funday",
            "enabled" to "yes",
            "label" to "x".repeat(Alarm.MAX_LABEL_LENGTH + 1),
            "sound" to "content://not/installed",
            "volume" to "4",
            "volume" to "loud",
            "snooze_minutes" to "61"
        ).forEach { (name, value) ->
            val error = assertThrows(IllegalArgumentException::class.java) { applyAlarmFields(Alarm(), mapOf(name to value), soundIds) }
            assertTrue("'${error.message}' should name $name", error.message.orEmpty().startsWith(name) || name in error.message.orEmpty())
        }
    }
}
