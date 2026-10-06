package com.custom.astrion.web

import android.content.Context
import com.custom.astrion.alarm.Alarm
import com.custom.astrion.alarm.AlarmHaPush
import com.custom.astrion.alarm.AlarmSounds
import com.custom.astrion.alarm.AlarmStore
import com.custom.astrion.alarm.applyAlarmFields
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * ConfigServer's alarm routes, so the Home Assistant integration can show
 * and change the remote's alarms. The remote stays the one place alarms
 * live: every change goes through [AlarmStore], which reschedules the next
 * Android alarm and updates the Alarms screen at once.
 *
 *  GET  /alarms         every alarm, when each rings next, the next ring
 *                       overall and the sounds an alarm can play (see
 *                       alarmsJson for the shape)
 *  POST /alarms         add an alarm from form fields (see
 *                       applyAlarmFields); `time` is required, other
 *                       unposted fields get the Alarms screen's defaults
 *  POST /alarms/update  change alarm `id`; only the posted fields change
 *  POST /alarms/delete  delete alarm `id`
 *
 * Every POST answers with the same JSON as GET /alarms, so the caller has
 * the new state without asking again; add and update also put the saved
 * alarm's own id in `id`. A bad field is a 400 and an unknown id a 404, both
 * with an `error` message.
 */
internal class AlarmRoutes(private val context: Context) {
    fun list(): Response = json(Response.Status.OK, AlarmHaPush.snapshot(context))

    fun add(session: IHTTPSession): Response {
        val fields = formFields(session)
        if ("time" !in fields) return error(Response.Status.BAD_REQUEST, "time is required")
        return save(Alarm(), fields)
    }

    fun update(session: IHTTPSession): Response {
        val fields = formFields(session)
        val current = alarmFor(fields) ?: return unknownAlarm(fields)
        return save(current, fields)
    }

    fun delete(session: IHTTPSession): Response {
        val fields = formFields(session)
        val alarm = alarmFor(fields) ?: return unknownAlarm(fields)
        AlarmStore.delete(context, alarm.id)
        return list()
    }

    /** The sounds a posted `sound` may be: read fresh, so one installed a minute ago counts. */
    private fun soundIds(fields: Map<String, String>): List<String> =
        if ("sound" in fields) AlarmSounds.available(context).map { it.id } else emptyList()

    private fun alarmFor(fields: Map<String, String>): Alarm? = fields["id"]?.trim()?.toIntOrNull()?.let { AlarmStore.get(context, it) }

    private fun unknownAlarm(fields: Map<String, String>): Response =
        error(Response.Status.NOT_FOUND, "no alarm with id '${fields["id"].orEmpty()}'")

    /** [base] with the posted [fields] applied, saved. */
    private fun save(base: Alarm, fields: Map<String, String>): Response {
        val alarm =
            try {
                applyAlarmFields(base, fields, soundIds(fields))
            } catch (e: IllegalArgumentException) {
                return error(Response.Status.BAD_REQUEST, e.message.orEmpty())
            }
        val id = AlarmStore.save(context, alarm).id
        return json(Response.Status.OK, JsonObject(mapOf("id" to JsonPrimitive(id)) + AlarmHaPush.snapshot(context)))
    }

    /** The urlencoded form body, one value per field (the first, if a field is sent twice). */
    private fun formFields(session: IHTTPSession): Map<String, String> {
        session.parseBody(HashMap())
        return session.parameters.mapNotNull { (key, values) -> values.firstOrNull()?.let { key to it } }.toMap()
    }

    private fun error(status: Response.Status, message: String): Response = json(status, buildJsonObject { put("error", message) })

    private fun json(status: Response.Status, body: JsonObject): Response =
        NanoHTTPD.newFixedLengthResponse(status, "application/json", body.toString())
}
