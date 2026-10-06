package com.custom.astrion.ui

import android.app.AlarmManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.custom.astrion.ScreensaverController
import com.custom.astrion.config.ClockFormatSetting
import com.custom.astrion.config.ScreensaverSetting
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private const val MINUTE_MS = 60_000L

/**
 * The idle screensaver: a big clock, today's date, and the next alarm when
 * one is set. Shown by [ScreensaverLayer]; waking it is handled in
 * [ScreensaverController], so this composable only draws.
 *
 * The clock follows the Settings 12h/24h switch via [ClockFormatSetting].
 * "Next alarm" is Android's own next alarm clock (whatever app set it, via
 * [AlarmManager.getNextAlarmClock]); the line is hidden when there's none.
 */
@Composable
private fun Screensaver() {
    val theme = LocalTheme.current
    val context = LocalContext.current
    val now = rememberMinuteTick()
    val nextAlarmMs = remember(now) { nextAlarmClockMs(context) }
    // "9:41 PM" -> "9:41" big with a small "PM" beside it; a 24h time has no
    // space and stays whole.
    val time = ClockFormatSetting.formatTime(context, Date(now))
    val marker = time.substringAfter(' ', "")
    val dateFmt = remember { SimpleDateFormat(datePattern(), Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row {
            Text(
                time.substringBefore(' '),
                color = theme.primaryText,
                fontSize = 96.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                modifier = Modifier.alignByBaseline()
            )
            if (marker.isNotEmpty()) {
                Text(
                    marker,
                    color = theme.mutedText,
                    fontSize = 28.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = 8.dp)
                )
            }
        }
        Text(
            dateFmt.format(Date(now)),
            color = theme.mutedText,
            fontSize = 20.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        nextAlarmMs?.let { ms ->
            Row(
                modifier = Modifier.padding(top = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Alarm, contentDescription = null, tint = theme.accent, modifier = Modifier.size(24.dp))
                Text(
                    alarmText(context, ms),
                    color = theme.primaryText,
                    fontSize = 22.sp
                )
            }
        }
    }
}

/**
 * The screensaver on top of everything else (MainActivity places it last,
 * over the dashboard and the charging screen) while [controller] says it's
 * up. Also: a new idle time picked in Settings restarts the countdown right
 * away, and while the screensaver is enabled and the remote is [charging]
 * the screen stays on (as long as this app is in front with no dialog
 * open), so a docked remote shows the clock instead of going dark. On
 * battery Android's own screen timeout still applies.
 */
@Composable
fun ScreensaverLayer(controller: ScreensaverController, theme: ThemeColors, charging: Boolean) {
    val idleS = ScreensaverSetting.idleSeconds(LocalContext.current)
    LaunchedEffect(idleS) { controller.restartCountdown() }
    KeepScreenOn(idleS > 0 && charging && controller.active)
    if (controller.showing) {
        ProvideTheme(theme) { Screensaver() }
    }
}

/**
 * Holds the screen on while [enabled] (see [ScreensaverLayer] for when).
 * Uses the Compose view's flag, separate from the decor view's one that
 * MainActivity's motion-wake toggles, so neither clears the other's.
 */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** Current time, refreshed right on each minute boundary. Also used by the alarm ringing screen. */
@Composable
internal fun rememberMinuteTick(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MINUTE_MS - System.currentTimeMillis() % MINUTE_MS)
            now = System.currentTimeMillis()
        }
    }
    return now
}

private fun nextAlarmClockMs(context: Context): Long? = runCatching {
    (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.nextAlarmClock?.triggerTime
}.getOrNull()

/** "Tue 7:30 AM" / "mar. 07:30" — the weekday in the device language, the
 * time per the 12h/24h switch. Also shown on the Settings panel's Alarms row. */
internal fun alarmText(context: Context, ms: Long): String {
    val day = SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ms))
    return "$day ${ClockFormatSetting.formatTime(context, Date(ms))}"
}

/** "Tuesday, October 6" / "mardi 6 octobre", ordered for the device locale. */
private fun datePattern(): String = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
