package com.custom.astrion.alarm

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTest {
    private val riga = ZoneId.of("Europe/Riga")

    /** 2026-10-06 is a Tuesday. */
    private fun at(day: Int, hour: Int, minute: Int, month: Int = 10, zone: ZoneId = riga): ZonedDateTime =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone)

    @Test
    fun oneTimeAlarmRingsLaterToday() {
        assertEquals(at(6, 7, 30), Alarm(hour = 7, minute = 30).nextTrigger(at(6, 6, 0)))
    }

    @Test
    fun oneTimeAlarmWhoseTimePassedRingsTomorrow() {
        assertEquals(at(7, 7, 30), Alarm(hour = 7, minute = 30).nextTrigger(at(6, 8, 0)))
    }

    @Test
    fun alarmJustRungIsNotPickedAgain() {
        assertEquals(at(7, 7, 30), Alarm(hour = 7, minute = 30).nextTrigger(at(6, 7, 30)))
    }

    @Test
    fun repeatingAlarmSkipsToNextRepeatDay() {
        val weekdays = Alarm(hour = 7, minute = 0, days = listOf("mon", "tue", "wed", "thu", "fri"))
        // Friday evening -> Monday morning.
        assertEquals(at(12, 7, 0), weekdays.nextTrigger(at(9, 20, 0)))
    }

    @Test
    fun repeatingOnlyTodayWithTimePassedRingsInAWeek() {
        val tuesdays = Alarm(hour = 7, minute = 0, days = listOf("tue"))
        assertEquals(at(13, 7, 0), tuesdays.nextTrigger(at(6, 9, 0)))
    }

    @Test
    fun timeInDaylightSavingGapMovesForward() {
        // Riga springs forward on 2026-03-29 from 03:00 to 04:00.
        val alarm = Alarm(hour = 3, minute = 30)
        assertEquals(ZonedDateTime.of(2026, 3, 29, 4, 30, 0, 0, riga), alarm.nextTrigger(at(29, 1, 0, month = 3)))
    }

    @Test
    fun earliestEnabledAlarmOrSnoozeIsNext() {
        val now = at(6, 6, 0)
        val alarms =
            listOf(
                Alarm(id = 1, hour = 8, minute = 0),
                Alarm(id = 2, hour = 6, minute = 30, enabled = false),
                Alarm(id = 3, hour = 7, minute = 0)
            )
        assertEquals(AlarmEvent(at(6, 7, 0).toInstant().toEpochMilli(), listOf(3)), nextAlarmEvent(alarms, emptyMap(), now))

        val snoozeMs = at(6, 6, 10).toInstant().toEpochMilli()
        // A snooze rings even though its alarm (a one-time alarm that already rang) is off now.
        assertEquals(AlarmEvent(snoozeMs, listOf(2)), nextAlarmEvent(alarms, mapOf(2 to snoozeMs), now))
    }

    @Test
    fun alarmsAtTheSameMinuteRingTogether() {
        val alarms = listOf(Alarm(id = 1, hour = 7, minute = 0), Alarm(id = 2, hour = 7, minute = 0, days = listOf("tue")))
        assertEquals(listOf(1, 2), nextAlarmEvent(alarms, emptyMap(), at(6, 6, 0))?.ids)
    }

    @Test
    fun staleOrOrphanedSnoozesAreIgnored() {
        val now = at(6, 6, 0)
        val alarms = listOf(Alarm(id = 1, hour = 7, minute = 0, enabled = false))
        val past = at(6, 5, 0).toInstant().toEpochMilli()
        val orphan = at(6, 6, 5).toInstant().toEpochMilli()
        assertNull(nextAlarmEvent(alarms, mapOf(1 to past, 9 to orphan), now))
    }

    @Test
    fun jsonRoundTripKeepsEveryField() {
        val alarm =
            Alarm(
                id = 4,
                hour = 6,
                minute = 45,
                days = listOf("mon", "fri"),
                enabled = false,
                label = "Work",
                sound = "content://media/internal/audio/media/12",
                volume = 40,
                snoozeMinutes = 5
            )
        val json = encodeAlarms(listOf(alarm))
        assertEquals(listOf(alarm), decodeAlarms(json))
        // The names Home Assistant will see.
        listOf("\"days\":[\"mon\",\"fri\"]", "\"snooze_minutes\":5", "\"volume\":40").forEach {
            assertTrue("$it missing from $json", it in json)
        }
    }

    @Test
    fun decodingCleansUpOutOfRangeValues() {
        val json = """[{"id":1,"hour":25,"minute":-3,"days":["FRI","xyz","mon"],"volume":0,"snooze_minutes":500,"extra":1}]"""
        val alarm = decodeAlarms(json).single()
        assertEquals(23, alarm.hour)
        assertEquals(0, alarm.minute)
        assertEquals(listOf("mon", "fri"), alarm.days)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), alarm.repeatDays)
        assertEquals(Alarm.MIN_VOLUME, alarm.volume)
        assertEquals(Alarm.MAX_SNOOZE_MINUTES, alarm.snoozeMinutes)
    }

    @Test
    fun snoozesRoundTrip() {
        val snoozes = mapOf(1 to 1_790_000_000_000L, 7 to 1_790_000_600_000L)
        assertEquals(snoozes, decodeSnoozes(encodeSnoozes(snoozes)))
    }
}
