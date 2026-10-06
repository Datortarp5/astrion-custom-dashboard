package com.custom.astrion.input

import com.custom.astrion.config.HotkeyConfig
import com.custom.astrion.config.PageConfig
import com.custom.astrion.ha.EntityMap
import com.custom.astrion.ha.EntityState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MuteToggleTest {
    private val bedroom = "media_player.bedroom"
    private var clock = 0L
    private val toggle = MuteToggle { clock }
    private val muteService = "media_player.volume_mute"
    private val muteKey = HotkeyConfig("MUTE", service = muteService, entityId = bedroom)

    private fun reporting(muted: Boolean?): EntityMap {
        val attrs = if (muted == null) JsonObject(emptyMap()) else JsonObject(mapOf("is_volume_muted" to JsonPrimitive(muted)))
        return mapOf(bedroom to EntityState(bedroom, "playing", attrs))
    }

    private fun press(hk: HotkeyConfig, entities: EntityMap): Boolean =
        toggle.callFor(hk, muteService, entities).data.getValue("is_volume_muted").jsonPrimitive.boolean

    @Test
    fun muteKeyFlipsTheReportedState() {
        assertTrue(press(muteKey, reporting(false)))
        clock += 5000
        assertFalse(press(muteKey, reporting(true)))
    }

    @Test
    fun configuredValueIsIgnored() {
        // The web builder always writes is_volume_muted: true.
        val built = muteKey.copy(data = mapOf("is_volume_muted" to true))
        assertFalse(press(built, reporting(true)))
    }

    @Test
    fun unknownStateMutes() {
        assertTrue(press(muteKey, reporting(null)))
        clock += 5000
        assertTrue(press(muteKey, emptyMap()))
    }

    @Test
    fun quickSecondPressUndoesTheFirstBeforeHaReportsIt() {
        assertTrue(press(muteKey, reporting(false)))
        clock += 500
        assertFalse(press(muteKey, reporting(false)))
    }

    @Test
    fun reportedStateWinsOnceHaHasHadTimeToReport() {
        assertTrue(press(muteKey, reporting(false)))
        clock += 3000
        // HA never applied the first call (player off, say): mute again.
        assertTrue(press(muteKey, reporting(false)))
    }

    @Test
    fun callGoesToTheBoundPlayer() {
        val call = toggle.callFor(muteKey, muteService, reporting(false))
        assertEquals("media_player", call.domain)
        assertEquals("volume_mute", call.service)
        assertEquals(bedroom, call.entityId)
    }

    @Test
    fun otherServicesKeepTheirOwnData() {
        val hk = HotkeyConfig("VOLUME_UP", entityId = "remote.tv", data = mapOf("command" to "VOLUME_UP"))
        val call = toggle.callFor(hk, "remote.send_command", emptyMap())
        assertEquals("remote", call.domain)
        assertEquals("send_command", call.service)
        assertEquals(mapOf("command" to JsonPrimitive("VOLUME_UP")), call.data)
        assertEquals("remote.tv", call.entityId)
    }

    @Test
    fun unboundMuteTogglesThePlayerTheVolumeKeysDrive() {
        val hotkeys =
            listOf(
                HotkeyConfig("VOLUME_UP", service = "media_player.volume_up", entityId = "media_player.living_room"),
                HotkeyConfig("HOME", page = "Main")
            )
        val mute = MuteToggle.withDefault(hotkeys, page = null).single { it.key == "MUTE" }
        assertEquals("media_player.volume_mute", mute.service)
        assertEquals("media_player.living_room", mute.entityId)
    }

    @Test
    fun unboundMuteFallsBackToBedroom() {
        val hotkeys = listOf(HotkeyConfig("VOLUME_UP", service = "remote.send_command", entityId = "remote.tv"))
        val mute = MuteToggle.withDefault(hotkeys, page = null).single { it.key == "MUTE" }
        assertEquals(MuteToggle.DEFAULT_ENTITY, mute.entityId)
        assertEquals("media_player.bedroom", MuteToggle.DEFAULT_ENTITY)
    }

    @Test
    fun configuredMuteIsKept() {
        val hotkeys = listOf(HotkeyConfig("mute", service = "remote.send_command", entityId = "remote.tv"))
        assertSame(hotkeys, MuteToggle.withDefault(hotkeys, page = null))
    }

    @Test
    fun pageThatLeavesOnMuteKeepsIt() {
        val page = PageConfig(name = "Sub", cards = emptyList(), parent = "Main", parentKey = "MUTE")
        assertTrue(MuteToggle.withDefault(emptyList(), page).isEmpty())
    }
}
