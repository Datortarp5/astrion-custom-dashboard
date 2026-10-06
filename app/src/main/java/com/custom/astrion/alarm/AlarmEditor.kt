package com.custom.astrion.alarm

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.custom.astrion.R
import com.custom.astrion.config.ClockFormatSetting
import com.custom.astrion.ui.LocalTheme
import com.custom.astrion.ui.tapClickable
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PREVIEW_MS = 6_000L
private const val VOLUME_STEP = 5

/**
 * Edits one alarm: time (Material dial, following the 12h/24h switch),
 * repeat days, label, sound, volume and snooze length. Picking a sound or
 * letting go of the volume slider plays a few seconds of it at that volume.
 * Save turns the alarm on, like a phone's clock app does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmEditor(initial: Alarm, sounds: List<AlarmSound>, onDone: () -> Unit) {
    val context = LocalContext.current
    val time = rememberTimePickerState(initial.hour, initial.minute, ClockFormatSetting.is24Hour(context))
    var days by remember { mutableStateOf(initial.repeatDays) }
    var label by remember { mutableStateOf(initial.label) }
    var sound by remember { mutableStateOf(initial.sound) }
    var volume by remember { mutableIntStateOf(initial.volume) }
    var snooze by remember { mutableIntStateOf(initial.snoozeMinutes) }
    val preview = rememberSoundPreview()

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader(
            stringResource(if (initial.id == 0) R.string.alarm_new else R.string.alarm_edit),
            stringResource(R.string.cancel),
            onDone
        )
        TimePicker(state = time, modifier = Modifier.align(Alignment.CenterHorizontally))
        DayPicker(days) { days = it }
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(Alarm.MAX_LABEL_LENGTH) },
            label = { Text(stringResource(R.string.alarm_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SoundPicker(sounds, sound) {
            sound = it
            preview.play(context, it, volume)
        }
        VolumeSlider(volume, onChange = { volume = it }, onChangeFinished = { preview.play(context, sound, volume) })
        SnoozeSlider(snooze) { snooze = it }
        EditorButton(stringResource(R.string.alarm_save), LocalTheme.current.accent) {
            val edited =
                initial.copy(
                    hour = time.hour,
                    minute = time.minute,
                    days = days.map { Alarm.dayKey(it) },
                    label = label,
                    sound = sound,
                    volume = volume,
                    snoozeMinutes = snooze,
                    enabled = true
                )
            AlarmStore.save(context, edited)
            onDone()
        }
        if (initial.id != 0) {
            EditorButton(stringResource(R.string.alarm_delete), LocalTheme.current.danger) {
                AlarmStore.delete(context, initial.id)
                onDone()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** One round toggle per day, in the locale's week order; none selected means "once". */
@Composable
private fun DayPicker(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val theme = LocalTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekInLocaleOrder().forEach { day ->
                val selected = day in days
                Box(
                    modifier =
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(if (selected) theme.accent else theme.controlBackground)
                        .tapClickable(focusShape = CircleShape) { onChange(if (selected) days - day else days + day) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        color = if (selected) theme.primaryText else theme.mutedText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Text(
            Alarm(days = days.map { Alarm.dayKey(it) }).daysText(LocalContext.current),
            color = theme.mutedText,
            fontSize = 12.sp
        )
    }
}

/** The current sound; tap to open the list of everything the remote can play. */
@Composable
private fun SoundPicker(sounds: List<AlarmSound>, selected: String, onPick: (String) -> Unit) {
    val theme = LocalTheme.current
    var open by remember { mutableStateOf(false) }
    val title = sounds.firstOrNull { it.id == selected }?.title ?: stringResource(R.string.alarm_sound_default)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val chevron = if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore
        EditorRow(Icons.Filled.MusicNote, stringResource(R.string.alarm_sound), title, chevron) { open = !open }
        if (open) {
            sounds.forEach { option ->
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .tapClickable { onPick(option.id) }
                        .padding(start = 48.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(option.title, color = theme.primaryText, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (option.id == selected) Icon(Icons.Filled.Check, contentDescription = null, tint = theme.accent)
                }
            }
        }
    }
}

@Composable
private fun VolumeSlider(volume: Int, onChange: (Int) -> Unit, onChangeFinished: () -> Unit) {
    LabeledSlider(
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        label = stringResource(R.string.alarm_volume),
        value = "$volume%",
        position = volume.toFloat(),
        range = Alarm.MIN_VOLUME.toFloat()..Alarm.MAX_VOLUME.toFloat(),
        steps = (Alarm.MAX_VOLUME - Alarm.MIN_VOLUME) / VOLUME_STEP - 1,
        onChange = { onChange((it / VOLUME_STEP).roundToInt() * VOLUME_STEP) },
        onChangeFinished = onChangeFinished
    )
}

/** Stops at [Alarm.SNOOZE_CHOICES]; a length set elsewhere (Home Assistant) shows as is until the slider moves. */
@Composable
private fun SnoozeSlider(minutes: Int, onChange: (Int) -> Unit) {
    val choices = Alarm.SNOOZE_CHOICES
    val index = choices.indices.minBy { abs(choices[it] - minutes) }
    LabeledSlider(
        icon = Icons.Filled.Snooze,
        label = stringResource(R.string.alarm_snooze_length),
        value = stringResource(R.string.alarm_minutes, minutes),
        position = index.toFloat(),
        range = 0f..(choices.size - 1).toFloat(),
        steps = choices.size - 2,
        onChange = { onChange(choices[it.roundToInt().coerceIn(choices.indices)]) },
        onChangeFinished = {}
    )
}

@Suppress("LongParameterList") // One slider row: each argument is a distinct visible part of it.
@Composable
private fun LabeledSlider(
    icon: ImageVector,
    label: String,
    value: String,
    position: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit
) {
    val theme = LocalTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = theme.mutedText)
            Text(label, color = theme.primaryText, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(value, color = theme.mutedText, fontSize = 13.sp)
        }
        Slider(
            value = position,
            valueRange = range,
            steps = steps,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            colors =
            SliderDefaults.colors(
                thumbColor = theme.accent,
                activeTrackColor = theme.accent,
                inactiveTrackColor = theme.controlBackground
            )
        )
    }
}

@Composable
private fun EditorRow(icon: ImageVector, label: String, value: String, trailing: ImageVector, onClick: () -> Unit) {
    val theme = LocalTheme.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.controlBackground)
            .tapClickable(focusShape = shape, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = theme.mutedText)
        Text(label, color = theme.primaryText, fontSize = 14.sp)
        Text(value, color = theme.mutedText, fontSize = 13.sp, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1f))
        Icon(trailing, contentDescription = null, tint = theme.mutedText)
    }
}

@Composable
private fun EditorButton(text: String, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        text,
        color = LocalTheme.current.primaryText,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color)
            .tapClickable(focusShape = shape, onClick = onClick)
            .padding(vertical = 14.dp)
    )
}

/** Plays a few seconds of a sound at a volume; stops early on the next play or when the editor closes. */
private class SoundPreview {
    private var player: AlarmPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopper = Runnable { stop() }

    fun play(context: Context, sound: String, volume: Int) {
        // An alarm going off wins: don't talk over it, or restore its volume from under it.
        if (AlarmRingService.ringing.value.isNotEmpty()) return
        val player = player ?: AlarmPlayer(context).also { player = it }
        player.play(sound, volume)
        handler.removeCallbacks(stopper)
        handler.postDelayed(stopper, PREVIEW_MS)
    }

    fun stop() {
        handler.removeCallbacks(stopper)
        player?.stop()
    }
}

@Composable
private fun rememberSoundPreview(): SoundPreview {
    val preview = remember { SoundPreview() }
    DisposableEffect(preview) { onDispose { preview.stop() } }
    return preview
}
