package com.custom.astrion.config

import android.content.Context
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit

/**
 * The settings page's screensaver choice — how long the remote sits with no
 * touch or button press before the big-clock screensaver (see
 * ui/Screensaver.kt) covers the screen, or 0 for never. Stored in the same
 * "astrion_settings" prefs as MainActivity's other switches.
 *
 * The value is held in Compose state like [ClockFormatSetting]'s, so
 * MainActivity's composition notices a change right away and restarts the
 * idle countdown with the new delay, no restart.
 */
object ScreensaverSetting {
    /** The slider's stops, in seconds; 0 is "Off". */
    val CHOICES_S = listOf(0, 15, 30, 60, 120, 300, 600, 1800)
    const val DEFAULT_S = 60
    private const val PREFS = "astrion_settings"
    private const val KEY = "screensaver_idle_s"

    // Created on first use, so the first reader's context picks the starting value.
    private var cached: MutableIntState? = null

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun state(context: Context): MutableIntState = cached ?: mutableIntStateOf(load(context)).also { cached = it }

    private fun load(context: Context): Int = prefs(context).getInt(KEY, DEFAULT_S).takeIf { it in CHOICES_S } ?: DEFAULT_S

    /** Idle seconds before the screensaver shows; 0 means it's off. */
    fun idleSeconds(context: Context): Int = state(context).intValue

    fun isEnabled(context: Context): Boolean = idleSeconds(context) > 0

    fun setIdleSeconds(context: Context, seconds: Int) {
        prefs(context).edit { putInt(KEY, seconds) }
        state(context).intValue = seconds
    }
}
