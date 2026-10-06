package com.custom.astrion.alarm

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.custom.astrion.R
import com.custom.astrion.config.ClockFormatSetting
import com.custom.astrion.ui.LocalTheme
import com.custom.astrion.ui.ProvideTheme
import com.custom.astrion.ui.rememberMinuteTick
import com.custom.astrion.ui.tapClickable
import java.util.Date

/**
 * The screen of a ringing alarm, shown over everything (and over the lock
 * screen) with the screen turned on: the time, the alarm's label, and big
 * Snooze / Dismiss buttons. Any button on the remote snoozes (HOME too);
 * dismissing takes a tap on the screen, so a remote knocked in the dark
 * can't switch the alarm off for good. Closes itself once [AlarmRingService] stops
 * ringing, however that happened (notification, timeout).
 */
class AlarmRingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        val theme = dashboardTheme()
        setContent {
            val ringing by AlarmRingService.ringing.collectAsState()
            LaunchedEffect(ringing) { if (ringing.isEmpty()) finish() }
            ProvideTheme(theme) {
                RingScreen(
                    alarms = ringing.mapNotNull { AlarmStore.get(this, it) },
                    onSnooze = { AlarmRingService.snooze(this) },
                    onDismiss = { AlarmRingService.dismiss(this) }
                )
            }
        }
    }

    // Lint flags any dispatchKeyEvent override because androidx.core's ComponentActivity is
    // @RestrictTo; overriding the platform Activity method is fine (MainActivity does the same).
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) AlarmRingService.snooze(this)
        return true
    }

    /** HOME never reaches [dispatchKeyEvent], but it shouldn't leave an alarm ringing behind the dashboard either. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        AlarmRingService.snooze(this)
    }
}

@Composable
private fun RingScreen(alarms: List<Alarm>, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val theme = LocalTheme.current
    val context = LocalContext.current
    val now = rememberMinuteTick()
    val labels = alarms.map { it.label }.filter { it.isNotBlank() }
    val snoozeMinutes = alarms.firstOrNull()?.snoozeMinutes ?: Alarm.DEFAULT_SNOOZE_MINUTES

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Alarm, contentDescription = null, tint = theme.accent, modifier = Modifier.size(48.dp))
        Text(
            ClockFormatSetting.formatTime(context, Date(now)),
            color = theme.primaryText,
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1
        )
        Text(
            if (labels.isEmpty()) stringResource(R.string.alarm_ringing) else labels.joinToString(", "),
            color = theme.mutedText,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        RingButton(stringResource(R.string.alarm_snooze_for, snoozeMinutes), theme.accent, onSnooze)
        Spacer(Modifier.height(16.dp))
        RingButton(stringResource(R.string.alarm_dismiss), theme.controlBackground, onDismiss)
    }
}

@Composable
private fun RingButton(text: String, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        text,
        color = LocalTheme.current.primaryText,
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color)
            .tapClickable(focusShape = shape, onClick = onClick)
            .padding(vertical = 22.dp)
    )
}
