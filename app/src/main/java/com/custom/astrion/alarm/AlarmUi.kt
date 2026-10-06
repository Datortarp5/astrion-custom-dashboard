package com.custom.astrion.alarm

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import com.custom.astrion.R
import com.custom.astrion.config.ClockFormatSetting
import com.custom.astrion.config.DashboardLoader
import com.custom.astrion.ui.ThemeColors
import com.custom.astrion.ui.toColors
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Calendar
import java.util.Locale

// Small pieces shared by the alarm screens, which run as their own activities outside MainActivity.

private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** The dashboard's colors (dashboard.json's `theme`), so the alarm screens match it. */
internal fun dashboardTheme(): ThemeColors =
    runCatching { DashboardLoader.load().config.theme.toColors() }.getOrDefault(ThemeColors.Default)

/**
 * The same colors as a Material color scheme, for the stock Material pieces
 * (time picker dial, text field, switches) the alarm editor uses.
 */
internal fun ThemeColors.toColorScheme(): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = primaryText,
    primaryContainer = accent,
    onPrimaryContainer = primaryText,
    tertiaryContainer = accent,
    onTertiaryContainer = primaryText,
    background = background,
    onBackground = primaryText,
    surface = background,
    onSurface = primaryText,
    surfaceVariant = controlBackground,
    onSurfaceVariant = mutedText,
    surfaceContainer = cardSurface,
    surfaceContainerHigh = controlBackground,
    surfaceContainerHighest = controlBackground,
    outline = mutedText
)

/** Full screen like MainActivity: the remote's status and navigation bars stay hidden. */
@Suppress("DEPRECATION") // Same flags as MainActivity; WindowInsetsController needs Android 11.
internal fun Activity.hideSystemBars() {
    window.decorView.systemUiVisibility =
        View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
}

/** The alarm's time per the Settings 12h/24h switch: "7:30 AM" or "07:30". */
internal fun Alarm.timeText(context: Context): String {
    val time =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.time
    return ClockFormatSetting.formatTime(context, time)
}

/** "Once", "Every day", "Weekdays", "Weekends", or the days themselves: "Mon, Wed, Fri". */
internal fun Alarm.daysText(context: Context): String {
    val repeat = repeatDays
    return when {
        repeat.isEmpty() -> context.getString(R.string.alarm_once)
        repeat.size == DayOfWeek.entries.size -> context.getString(R.string.alarm_every_day)
        repeat == WEEKDAYS -> context.getString(R.string.alarm_weekdays)
        repeat == WEEKEND -> context.getString(R.string.alarm_weekends)
        else -> weekInLocaleOrder().filter { it in repeat }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
}

/** The seven days starting from the locale's first day of the week (Monday in most of Europe, Sunday in the US). */
internal fun weekInLocaleOrder(): List<DayOfWeek> {
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    return (0L until DayOfWeek.entries.size).map { first.plus(it) }
}
