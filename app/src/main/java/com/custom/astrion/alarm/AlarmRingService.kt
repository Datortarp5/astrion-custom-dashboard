package com.custom.astrion.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.custom.astrion.R
import com.custom.astrion.config.ClockFormatSetting
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Rings alarms: plays the sound ([AlarmPlayer]), keeps the remote awake,
 * turns the screen on and opens [AlarmRingActivity], with a notification
 * carrying Snooze/Dismiss as the way back to it. A foreground service, so
 * the ringing doesn't depend on any screen staying open.
 *
 * Snooze sets each ringing alarm to ring again after its own snooze length;
 * dismiss just stops (an alarm that doesn't repeat already turned itself off
 * when it started ringing, see [AlarmStore.onRang]). After [SILENCE_AFTER_MS]
 * of nobody answering it stops on its own, like a dismiss. An alarm that
 * goes off while another is ringing joins it: one screen, one answer for both.
 */
class AlarmRingService : Service() {
    companion object {
        private const val TAG = "AlarmRingService"
        private const val ACTION_RING = "com.custom.astrion.alarm.RING"
        private const val ACTION_SNOOZE = "com.custom.astrion.alarm.SNOOZE"
        private const val ACTION_DISMISS = "com.custom.astrion.alarm.DISMISS"
        private const val EXTRA_IDS = "ids"
        private const val CHANNEL_ID = "alarms"
        private const val NOTIFICATION_ID = 5
        private const val SILENCE_AFTER_MS = 10 * 60_000L
        private const val START_WAKE_MS = 30_000L
        private const val SCREEN_WAKE_MS = 10_000L

        private val ringingFlow = MutableStateFlow<List<Int>>(emptyList())

        /** Ids of the alarms ringing right now; empty when none is. */
        val ringing: StateFlow<List<Int>> = ringingFlow

        fun ring(context: Context, ids: List<Int>) {
            // Keeps the CPU up between this broadcast returning and the service taking over.
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "astrion:alarm-start")
                ?.acquire(START_WAKE_MS)
            ContextCompat.startForegroundService(context, command(context, ACTION_RING).putExtra(EXTRA_IDS, ids.toIntArray()))
        }

        fun snooze(context: Context) {
            if (ringingFlow.value.isNotEmpty()) context.startService(command(context, ACTION_SNOOZE))
        }

        fun dismiss(context: Context) {
            if (ringingFlow.value.isNotEmpty()) context.startService(command(context, ACTION_DISMISS))
        }

        private fun command(context: Context, action: String) = Intent(context, AlarmRingService::class.java).setAction(action)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val silence = Runnable { stopRinging(snooze = false) }
    private lateinit var player: AlarmPlayer
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        player = AlarmPlayer(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> startRinging(intent.getIntArrayExtra(EXTRA_IDS)?.toList().orEmpty())
            ACTION_SNOOZE -> stopRinging(snooze = true)
            ACTION_DISMISS -> stopRinging(snooze = false)
            else -> if (ringingFlow.value.isEmpty()) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(ids: List<Int>) {
        val alarms = ids.mapNotNull { AlarmStore.get(this, it) }
        val all = (ringingFlow.value + alarms.map { it.id }).distinct()
        // Started with startForegroundService: has to go foreground even when there's nothing left to ring.
        goForeground(all.mapNotNull { AlarmStore.get(this, it) })
        if (all.isEmpty()) {
            stopRinging(snooze = false)
            return
        }
        ringingFlow.value = all
        alarms.lastOrNull()?.let { player.play(it.sound, it.volume) }
        if (wakeLock == null) {
            wakeLock =
                (getSystemService(Context.POWER_SERVICE) as? PowerManager)
                    ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "astrion:alarm-ringing")
                    ?.apply {
                        setReferenceCounted(false)
                        acquire(SILENCE_AFTER_MS + START_WAKE_MS)
                    }
        }
        handler.removeCallbacks(silence)
        handler.postDelayed(silence, SILENCE_AFTER_MS)
        wakeScreen()
        runCatching { startActivity(ringScreenIntent()) }
            .onFailure { Log.w(TAG, "Couldn't open the ringing screen, the notification still can", it) }
    }

    private fun stopRinging(snooze: Boolean) {
        if (snooze) {
            val now = System.currentTimeMillis()
            ringingFlow.value.forEach { id ->
                AlarmStore.get(this, id)?.let { AlarmStore.snooze(this, id, now + it.snoozeMinutes * 60_000L) }
            }
        }
        Log.i(TAG, "Alarm ${ringingFlow.value} ${if (snooze) "snoozed" else "stopped"}")
        ringingFlow.value = emptyList()
        handler.removeCallbacks(silence)
        player.stop()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (ringingFlow.value.isNotEmpty()) stopRinging(snooze = false)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** Turns the screen on even if the ringing screen can't (it also asks, via its window flags). */
    @Suppress("DEPRECATION") // SCREEN_BRIGHT_WAKE_LOCK: the only way to light the screen from a service on Android 8.1.
    private fun wakeScreen() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        pm
            .newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "astrion:alarm-screen"
            ).acquire(SCREEN_WAKE_MS)
    }

    private fun goForeground(alarms: List<Alarm>) {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.alarms_title), NotificationManager.IMPORTANCE_HIGH).apply {
                    // The service plays the sound itself, at the alarm's own volume.
                    setSound(null, null)
                }
            )
        }
        val now = ClockFormatSetting.formatTime(this, Date())
        val labels = alarms.map { it.label }.filter { it.isNotBlank() }
        val notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(if (labels.isEmpty()) getString(R.string.alarm_ringing) else labels.joinToString(", "))
                .setContentText(now)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setContentIntent(pending(0, ringScreenIntent(), activity = true))
                .setFullScreenIntent(pending(1, ringScreenIntent(), activity = true), true)
                .addAction(0, getString(R.string.alarm_snooze), pending(2, command(this, ACTION_SNOOZE), activity = false))
                .addAction(0, getString(R.string.alarm_dismiss), pending(3, command(this, ACTION_DISMISS), activity = false))
                .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun ringScreenIntent(): Intent = Intent(this, AlarmRingActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

    private fun pending(requestCode: Int, intent: Intent, activity: Boolean): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (activity) {
            PendingIntent.getActivity(this, requestCode, intent, flags)
        } else {
            PendingIntent.getService(this, requestCode, intent, flags)
        }
    }
}
