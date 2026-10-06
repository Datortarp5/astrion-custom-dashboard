package com.custom.astrion.input

import com.custom.astrion.config.HotkeyConfig
import com.custom.astrion.config.JsonPlain
import com.custom.astrion.config.PageConfig
import com.custom.astrion.ha.EntityMap
import com.custom.astrion.ha.ServiceCall
import kotlinx.serialization.json.JsonPrimitive

/**
 * Makes the remote's MUTE key a real mute toggle.
 *
 * A `media_player.volume_mute` hotkey used to send whatever
 * `is_volume_muted` value its config held (the web builder always writes
 * `true`), so the key could mute but never unmute. [callFor] now works the
 * value out at press time from the player's live `is_volume_muted`
 * attribute. A second press that lands before Home Assistant has reported
 * the first one's result flips the value sent last instead, so a quick
 * double press mutes and unmutes rather than muting twice.
 *
 * A MUTE key the config doesn't bind gets [withDefault]'s binding: a mute
 * toggle on the media player the volume keys drive, or [DEFAULT_ENTITY].
 *
 * [now] is a monotonic clock in milliseconds (SystemClock.elapsedRealtime
 * on the device).
 */
class MuteToggle(private val now: () -> Long) {
    private var lastEntity: String? = null
    private var lastSent = false
    private var lastSentAt = 0L

    /** The Home Assistant call [hk] fires for its [service] ("domain.service"),
     * with `is_volume_muted` flipped from [entities]' live state when it is
     * a mute call. Any other call goes out with the hotkey's own data. */
    fun callFor(hk: HotkeyConfig, service: String, entities: EntityMap): ServiceCall {
        val data = hk.data.mapValues { JsonPlain.toJson(it.value) }.toMutableMap()
        val entityId = hk.entityId
        if (service == MUTE_SERVICE && entityId != null) {
            data[MUTED_ATTR] = JsonPrimitive(next(entityId, entities[entityId]?.attrBoolean(MUTED_ATTR)))
        }
        return ServiceCall(service.substringBefore('.'), service.substringAfter('.'), entityId, data)
    }

    /** The `is_volume_muted` value to send for [entityId], whose last
     * reported value is [reported] (null when unknown, treated as unmuted). */
    fun next(entityId: String, reported: Boolean?): Boolean {
        val t = now()
        val waitingForHa = entityId == lastEntity && t - lastSentAt < CATCH_UP_MS
        val current = if (waitingForHa) lastSent else reported == true
        val muted = !current
        lastEntity = entityId
        lastSent = muted
        lastSentAt = t
        return muted
    }

    companion object {
        /** The player MUTE toggles when nothing in the config says otherwise. */
        const val DEFAULT_ENTITY = "media_player.bedroom"
        const val MUTE_SERVICE = "media_player.volume_mute"
        private const val MUTE_KEY = "MUTE"
        private const val MUTED_ATTR = "is_volume_muted"
        private const val MEDIA_PLAYER = "media_player."

        /** How long a sent value counts as newer than Home Assistant's
         * reported one; HA normally reports the change well within this. */
        private const val CATCH_UP_MS = 2000L
        private val VOLUME_KEYS = setOf("VOLUME_UP", "VOLUME_DOWN")

        /** [hotkeys] plus a MUTE binding when they have none: a mute toggle on
         * the media player VOLUME_UP/VOLUME_DOWN target there, else on
         * [DEFAULT_ENTITY]. Left out when [page] already uses MUTE as its
         * parent-navigation key. */
        fun withDefault(hotkeys: List<HotkeyConfig>, page: PageConfig?): List<HotkeyConfig> {
            if (hotkeys.any { it.key.equals(MUTE_KEY, ignoreCase = true) }) return hotkeys
            if (page?.parent != null && page.parentKey.equals(MUTE_KEY, ignoreCase = true)) return hotkeys
            val volumePlayer =
                hotkeys.firstNotNullOfOrNull { hk ->
                    hk.entityId?.takeIf { hk.key.uppercase() in VOLUME_KEYS && it.startsWith(MEDIA_PLAYER) }
                }
            return hotkeys + HotkeyConfig(MUTE_KEY, service = MUTE_SERVICE, entityId = volumePlayer ?: DEFAULT_ENTITY)
        }
    }
}
