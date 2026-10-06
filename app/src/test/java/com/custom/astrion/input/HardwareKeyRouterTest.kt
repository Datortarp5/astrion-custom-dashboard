package com.custom.astrion.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HardwareKeyRouterTest {
    private val router = HardwareKeyRouter()
    private val fired = mutableListOf<String>()

    private fun bind(key: HardwareKey) = router.on(key) { fired.add(key.name) }

    private fun press(code: Int) {
        router.shortHandler(code)?.invoke()
    }

    @Test
    fun everyMuteKeycodeRunsTheMuteBinding() {
        bind(HardwareKey.MUTE)
        press(164)
        press(91)
        press(82)
        assertEquals(listOf("MUTE", "MUTE", "MUTE"), fired)
    }

    @Test
    fun keycode82KeepsAnExplicitMainBinding() {
        bind(HardwareKey.MUTE)
        bind(HardwareKey.MAIN)
        press(82)
        assertEquals(listOf("MAIN"), fired)
    }

    @Test
    fun longPressOnMainAlsoKeepsKeycode82() {
        bind(HardwareKey.MUTE)
        router.onLong(HardwareKey.MAIN) { true }
        assertEquals(HardwareKey.MAIN, router.resolve(82))
        assertNull(router.shortHandler(82))
    }

    @Test
    fun unknownKeycodesStayUnbound() {
        bind(HardwareKey.MUTE)
        assertNull(router.shortHandler(999))
    }
}
