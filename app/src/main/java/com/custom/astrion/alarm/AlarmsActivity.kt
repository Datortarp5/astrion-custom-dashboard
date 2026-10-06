package com.custom.astrion.alarm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.custom.astrion.R
import com.custom.astrion.config.ClockFormatSetting
import com.custom.astrion.ui.LocalTheme
import com.custom.astrion.ui.ProvideTheme
import com.custom.astrion.ui.SettingRow
import com.custom.astrion.ui.alarmText
import com.custom.astrion.ui.tapClickable
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Alarms screen, opened from the Settings panel ([AlarmsSettingRow]) or
 * by tapping Android's next-alarm indicator: the list of alarms with an
 * on/off switch each, an Add row, and [AlarmEditor] for one alarm at a time.
 * Its own activity rather than another overlay inside MainActivity, so
 * BACK and the remote's D-pad behave like on any other settings screen.
 */
class AlarmsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        val theme = dashboardTheme()
        setContent {
            ProvideTheme(theme) {
                MaterialTheme(colorScheme = theme.toColorScheme()) {
                    AlarmsScreen(onClose = ::finish)
                }
            }
        }
    }
}

@Composable
private fun AlarmsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val alarms by AlarmStore.alarms(context).collectAsState()
    val snoozes by AlarmStore.snoozes(context).collectAsState()
    val sounds by produceState(emptyList<AlarmSound>()) {
        value = withContext(Dispatchers.IO) { AlarmSounds.available(context) }
    }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    BackHandler(enabled = editing != null) { editing = null }

    Box(modifier = Modifier.fillMaxSize().background(LocalTheme.current.background)) {
        val current = editing
        if (current == null) {
            AlarmList(
                alarms = alarms,
                snoozes = snoozes,
                onAdd = { editing = Alarm() },
                onEdit = { editing = it },
                onClose = onClose
            )
        } else {
            // Keyed so switching straight to another alarm starts the editor fresh.
            key(current.id) { AlarmEditor(current, sounds, onDone = { editing = null }) }
        }
    }
}

@Composable
private fun AlarmList(alarms: List<Alarm>, snoozes: Map<Int, Long>, onAdd: () -> Unit, onEdit: (Alarm) -> Unit, onClose: () -> Unit) {
    val theme = LocalTheme.current
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenHeader(stringResource(R.string.alarms_title), stringResource(R.string.close), onClose)
        if (alarms.isEmpty()) {
            Text(stringResource(R.string.alarms_empty), color = theme.mutedText, fontSize = 13.sp)
        }
        alarms.forEach { alarm ->
            key(alarm.id) { AlarmRow(alarm, snoozes[alarm.id], onEdit) }
        }
        SettingRow(icon = Icons.Filled.Add, label = stringResource(R.string.alarm_add), onClick = onAdd)
    }
}

/** A screen title with a "✕ [closeLabel]" button on the right, like the Settings panel's. */
@Composable
internal fun ScreenHeader(title: String, closeLabel: String, onClose: () -> Unit) {
    val theme = LocalTheme.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = theme.primaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(
            "✕ $closeLabel",
            color = theme.mutedText,
            fontSize = 13.sp,
            modifier =
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .tapClickable(onClick = onClose)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun AlarmRow(alarm: Alarm, snoozedUntil: Long?, onEdit: (Alarm) -> Unit) {
    val theme = LocalTheme.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val details =
        listOfNotNull(
            alarm.label.takeIf { it.isNotBlank() },
            alarm.daysText(context),
            snoozedUntil?.takeIf { it > System.currentTimeMillis() }?.let {
                stringResource(R.string.alarm_snoozed_until, ClockFormatSetting.formatTime(context, Date(it)))
            }
        ).joinToString(" · ")

    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.controlBackground)
            .tapClickable(focusShape = shape) { onEdit(alarm) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                alarm.timeText(context),
                color = if (alarm.enabled) theme.primaryText else theme.mutedText,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light
            )
            Text(details, color = theme.mutedText, fontSize = 12.sp)
        }
        Switch(checked = alarm.enabled, onCheckedChange = { AlarmStore.setEnabled(context, alarm.id, it) })
    }
}

/**
 * The Settings panel's way into the Alarms screen, showing when the next one
 * rings ("Tue 7:30") or "None".
 */
@Composable
fun AlarmsSettingRow() {
    val theme = LocalTheme.current
    val context = LocalContext.current
    val alarms by AlarmStore.alarms(context).collectAsState()
    val snoozes by AlarmStore.snoozes(context).collectAsState()
    val is24Hour = ClockFormatSetting.is24Hour(context)
    val next = remember(alarms, snoozes, is24Hour) { AlarmScheduler.next(context)?.let { alarmText(context, it.atMs) } }
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.controlBackground)
            .tapClickable(focusShape = shape) {
                context.startActivity(Intent(context, AlarmsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Filled.Alarm, contentDescription = null, tint = theme.mutedText)
        Text(stringResource(R.string.alarms_title), color = theme.primaryText, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(next ?: stringResource(R.string.alarms_none), color = theme.mutedText, fontSize = 13.sp)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = theme.mutedText)
    }
}
