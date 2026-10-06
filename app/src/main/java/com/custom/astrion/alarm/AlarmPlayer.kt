package com.custom.astrion.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Plays an alarm's sound, looping, on Android's alarm stream with that
 * stream turned to the alarm's own volume (put back afterwards). Other
 * audio is asked to pause meanwhile. Used by the ringing alarm and by the
 * editor's preview; main thread only.
 *
 * If the chosen sound can't be played (deleted, or a URI from a different
 * device) it falls back to the remote's default alarm, notification and
 * ringtone sounds, and finally to a generated beep, so an alarm is never
 * silent just because a sound file went missing.
 */
class AlarmPlayer(context: Context) {
    private val context = context.applicationContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var focus: AudioFocusRequest? = null
    private var restoreVolume: Int? = null

    fun play(sound: String, volumePercent: Int) {
        stop()
        setAlarmVolume(volumePercent)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(ATTRIBUTES).build().also {
            audio?.requestAudioFocus(it)
        }
        player = if (sound == Alarm.SOUND_BEEP) null else candidates(sound).firstNotNullOfOrNull(::start)
        if (player == null) beep()
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        handler.removeCallbacksAndMessages(null)
        tone?.release()
        tone = null
        focus?.let { audio?.abandonAudioFocusRequest(it) }
        focus = null
        restoreVolume?.let { runCatching { audio?.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) } }
        restoreVolume = null
    }

    private fun setAlarmVolume(percent: Int) {
        val audio = audio ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        restoreVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val index = ((percent.coerceIn(Alarm.MIN_VOLUME, Alarm.MAX_VOLUME) * max + 50) / 100).coerceIn(1, max)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, index, 0) }
            .onFailure { Log.w(TAG, "Couldn't set alarm volume", it) }
    }

    private fun candidates(sound: String): List<Uri> = listOfNotNull(
        sound.takeIf { it != Alarm.SOUND_DEFAULT }?.let(Uri::parse),
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION),
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
    ).distinct()

    private fun start(uri: Uri): MediaPlayer? {
        val media = MediaPlayer()
        return try {
            media.setAudioAttributes(ATTRIBUTES)
            media.setDataSource(context, uri)
            media.isLooping = true
            media.prepare()
            media.start()
            media
        } catch (e: Exception) {
            Log.w(TAG, "Can't play $uri", e)
            media.release()
            null
        }
    }

    /** Double beep every 1.2 s, generated rather than read from a file. */
    private fun beep() {
        val generator = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull() ?: return
        tone = generator
        handler.post(
            object : Runnable {
                override fun run() {
                    generator.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_MS)
                    handler.postDelayed(this, BEEP_EVERY_MS)
                }
            }
        )
    }

    private companion object {
        const val TAG = "AlarmPlayer"
        const val BEEP_MS = 400
        const val BEEP_EVERY_MS = 1200L
        val ATTRIBUTES: AudioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
    }
}
