package com.custom.astrion.config

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The settings page's 12h/24h switch — one remote-wide choice that every
 * clock in the app follows (status bar, clock_weather cards, and anything
 * that shows a time later, like a screensaver), instead of each place
 * deciding on its own. Stored in the same "astrion_settings" prefs as
 * MainActivity's other switches. Until the switch is touched it follows
 * Android's own 12/24-hour setting, so a fresh install matches the system.
 *
 * The value is held in Compose state: a composable that formats through
 * [is24Hour] or [formatTime] redraws as soon as the switch flips, no
 * restart. Code outside Compose can call the same functions.
 */
object ClockFormatSetting {
    private const val PREFS = "astrion_settings"
    private const val KEY = "clock_24_hour"

    // Created on first use, so the first reader's context picks the starting value.
    private var cached: MutableState<Boolean>? = null

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun state(context: Context): MutableState<Boolean> = cached ?: mutableStateOf(load(context)).also { cached = it }

    private fun load(context: Context): Boolean {
        val prefs = prefs(context)
        return if (prefs.contains(KEY)) prefs.getBoolean(KEY, false) else DateFormat.is24HourFormat(context)
    }

    fun is24Hour(context: Context): Boolean = state(context).value

    fun set24Hour(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY, enabled) }
        state(context).value = enabled
    }

    /** `HH:mm` (21:41) or `h:mm a` (9:41 PM). */
    fun pattern(is24Hour: Boolean): String = if (is24Hour) "HH:mm" else "h:mm a"

    /** [date]'s hours and minutes in the format the switch is set to. */
    fun formatTime(context: Context, date: Date): String = SimpleDateFormat(pattern(is24Hour(context)), Locale.getDefault()).format(date)
}
