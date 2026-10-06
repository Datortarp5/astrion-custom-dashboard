package com.custom.astrion.alarm

import android.content.Context
import android.util.Log
import com.custom.astrion.config.RemoteSettings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Tells Home Assistant the moment the alarms change on the remote (edited on
 * the Alarms screen, changed from HA itself, a one-time alarm turning itself
 * off as it rings, a snooze) by posting [snapshot] with `"type": "alarms"` to
 * the integration's push webhook, the same one page and Activity changes use
 * (see RemoteSettings.haWebhookId). Without a webhook HA still catches up on
 * its next poll of GET /alarms.
 */
object AlarmHaPush {
    private const val TAG = "AlarmHaPush"

    /** One save writes the alarms and then the snoozes; wait this long so they go out as one push. */
    private const val SETTLE_MS = 300L
    private const val TIMEOUT_S = 10L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http =
        OkHttpClient
            .Builder()
            .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    private var started = false

    /** What GET /alarms answers right now; see [alarmsJson]. Reads the media database, so not on the main thread. */
    fun snapshot(context: Context): JsonObject = alarmsJson(
        AlarmStore.alarms(context).value,
        AlarmStore.snoozes(context).value,
        AlarmSounds.recent(context),
        zonedAt(System.currentTimeMillis())
    )

    /** Starts watching the alarms for the rest of the app's life; once per process. */
    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch {
            combine(AlarmStore.alarms(app), AlarmStore.snoozes(app)) { alarms, snoozes -> alarms to snoozes }
                // The first value is what was saved before the app started: HA already has it from its polls.
                .drop(1)
                .collectLatest {
                    delay(SETTLE_MS)
                    push(app)
                }
        }
    }

    private fun push(context: Context) {
        val haUrl = RemoteSettings.haUrl(context).trimEnd('/')
        val webhookId = RemoteSettings.haWebhookId(context)
        if (haUrl.isBlank() || webhookId.isBlank()) return
        val payload = JsonObject(mapOf("type" to JsonPrimitive("alarms")) + snapshot(context))
        runCatching {
            val request =
                Request
                    .Builder()
                    .url("$haUrl/api/webhook/$webhookId")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            http.newCall(request).execute().use { Log.d(TAG, "Pushed alarms -> HTTP ${it.code}") }
        }.onFailure { Log.w(TAG, "Couldn't push alarms to Home Assistant", it) }
    }
}
