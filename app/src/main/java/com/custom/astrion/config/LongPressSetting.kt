package com.custom.astrion.config

import android.content.Context
import androidx.core.content.edit

/**
 * The settings page's long-press slider — how long a physical button must
 * be held before its long-press hotkey fires instead of its short one.
 * Stored in the same "astrion_settings" prefs as MainActivity's other
 * switches. MainActivity reads it on every key press (SharedPreferences
 * keeps it in memory), so a change applies on the next press, no restart.
 */
object LongPressSetting {
    const val DEFAULT_MS = 1000L
    const val MIN_MS = 300L
    const val MAX_MS = 3000L
    private const val PREFS = "astrion_settings"
    private const val KEY = "long_press_ms"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): Long = prefs(context).getLong(KEY, DEFAULT_MS).coerceIn(MIN_MS, MAX_MS)

    /** Saves [ms] clamped to [MIN_MS]..[MAX_MS] and returns the stored value. */
    fun save(context: Context, ms: Long): Long {
        val clamped = ms.coerceIn(MIN_MS, MAX_MS)
        prefs(context).edit { putLong(KEY, clamped) }
        return clamped
    }
}
