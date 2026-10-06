package com.custom.astrion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.custom.astrion.config.ScreensaverSetting

/**
 * Decides when the big-clock screensaver (ui/Screensaver.kt) is up: after
 * [ScreensaverSetting.idleSeconds] with no touch or button press, while
 * [active]. The first touch or press after it appears only wakes it —
 * [interceptKey] / [interceptTouch] swallow that whole press, so the button
 * or tile underneath never fires.
 *
 * Kept out of MainActivity for the same reason as [ChargeDockMonitor]: one
 * feature's timer and state readable start to finish in one place. It
 * hooks itself up from the Activity's own lifecycle: on create it puts
 * itself in front of the window's callback, so it sees every key, touch
 * and focus change before MainActivity's dispatchKeyEvent and the views
 * do, and listens for screen on/off; MainActivity only has to create it
 * and draw [com.custom.astrion.ui.ScreensaverLayer].
 */
class ScreensaverController(private val activity: ComponentActivity) : DefaultLifecycleObserver {

    var showing by mutableStateOf(false)
        private set

    /**
     * True while the screen is on and this app's window has focus — the only
     * time the countdown runs. A card's dialog, another app or a system
     * panel on top takes focus away, and touches there never pass through
     * this Activity, so counting on would put the screensaver up behind
     * them; and after screen-off the dashboard should come back, not a
     * screensaver that needs an extra tap.
     */
    var active by mutableStateOf(false)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val showRunnable = Runnable { showing = true }

    private var screenOn = true
    private var windowFocused = false
    private var wakeKeyCode = NO_KEY
    private var swallowingTouch = false

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                screenOn = intent?.action != Intent.ACTION_SCREEN_OFF
                updateActive()
            }
        }

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        val window = activity.window
        val inner = window.callback
        window.callback =
            object : Window.Callback by inner {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean = interceptKey(event) || inner.dispatchKeyEvent(event)

                override fun dispatchTouchEvent(event: MotionEvent): Boolean = interceptTouch(event) || inner.dispatchTouchEvent(event)

                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    windowFocused = hasFocus
                    updateActive()
                    inner.onWindowFocusChanged(hasFocus)
                }
            }
        activity.registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        )
        screenOn = (activity.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive != false
        windowFocused = activity.hasWindowFocus()
        updateActive()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        runCatching { activity.unregisterReceiver(screenReceiver) }
        handler.removeCallbacks(showRunnable)
    }

    private fun updateActive() {
        active = screenOn && windowFocused
        restartCountdown()
    }

    /** Hides the screensaver and starts the idle countdown over, with
     * whatever delay the setting holds now. Every key and touch event comes
     * through here, so it stays a couple of Handler calls. */
    fun restartCountdown() {
        showing = false
        handler.removeCallbacks(showRunnable)
        val idleS = ScreensaverSetting.idleSeconds(activity)
        if (active && idleS > 0) handler.postDelayed(showRunnable, idleS * 1000L)
    }

    /** True means the event belongs to the press that woke the screensaver:
     * drop it. */
    private fun interceptKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            wakeKeyCode = if (showing) event.keyCode else NO_KEY
        }
        val swallow = event.keyCode == wakeKeyCode
        if (swallow && event.action == KeyEvent.ACTION_UP) wakeKeyCode = NO_KEY
        restartCountdown()
        return swallow
    }

    /** True means the event belongs to the touch that woke the screensaver
     * (from finger down to finger up): drop it. */
    private fun interceptTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) swallowingTouch = showing
        val swallow = swallowingTouch
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            swallowingTouch = false
        }
        restartCountdown()
        return swallow
    }

    private companion object {
        const val NO_KEY = -1
    }
}
