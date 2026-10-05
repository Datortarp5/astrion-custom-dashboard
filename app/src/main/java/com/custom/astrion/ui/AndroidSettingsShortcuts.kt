package com.custom.astrion.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness3
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.custom.astrion.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SettingsShortcuts"

/**
 * One Android Settings screen reachable from the pull-down menu. Screens that
 * don't exist on this device (the action resolves to nothing, e.g. a vendor
 * build without Location, or a page newer than the device's Android) are left
 * out of the list instead of crashing on tap.
 */
private class SettingsShortcut(
    @StringRes val label: Int,
    val icon: ImageVector,
    val intent: (Context) -> Intent
)

private fun action(name: String): (Context) -> Intent = { Intent(name) }

private fun actionForThisApp(name: String): (Context) -> Intent = { context ->
    Intent(name, Uri.parse("package:${context.packageName}"))
}

/** Not in the public SDK, but AOSP's Settings app has answered it since Android 4.3. */
private const val ACTION_NOTIFICATION_SETTINGS = "android.settings.NOTIFICATION_SETTINGS"

/** Roughly the order of Android's own Settings home screen. */
private val SHORTCUTS =
    listOf(
        SettingsShortcut(R.string.settings_shortcut_bluetooth, Icons.Filled.Bluetooth, action(Settings.ACTION_BLUETOOTH_SETTINGS)),
        SettingsShortcut(R.string.settings_shortcut_network, Icons.Filled.SettingsEthernet, action(Settings.ACTION_WIRELESS_SETTINGS)),
        SettingsShortcut(R.string.settings_shortcut_display, Icons.Filled.BrightnessHigh, action(Settings.ACTION_DISPLAY_SETTINGS)),
        SettingsShortcut(R.string.settings_shortcut_screensaver, Icons.Filled.Brightness3, action(Settings.ACTION_DREAM_SETTINGS)),
        SettingsShortcut(R.string.settings_shortcut_sound, Icons.AutoMirrored.Filled.VolumeUp, action(Settings.ACTION_SOUND_SETTINGS)),
        SettingsShortcut(R.string.settings_shortcut_notifications, Icons.Filled.Notifications, action(ACTION_NOTIFICATION_SETTINGS)),
        SettingsShortcut(R.string.settings_shortcut_apps, Icons.Filled.Apps, action(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)),
        SettingsShortcut(R.string.settings_shortcut_default_apps, Icons.Filled.Home, action(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)),
        SettingsShortcut(
            R.string.settings_shortcut_app_info,
            Icons.Filled.Info,
            actionForThisApp(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        ),
        SettingsShortcut(
            R.string.settings_shortcut_unknown_sources,
            Icons.Filled.GetApp,
            actionForThisApp(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        ),
        SettingsShortcut(R.string.settings_shortcut_battery, Icons.Filled.BatteryStd, action(Intent.ACTION_POWER_USAGE_SUMMARY)),
        SettingsShortcut(R.string.settings_shortcut_storage, Icons.Filled.Storage, action(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
        SettingsShortcut(R.string.settings_shortcut_security, Icons.Filled.Security, action(Settings.ACTION_SECURITY_SETTINGS)),
        SettingsShortcut(R.string.settings_shortcut_location, Icons.Filled.LocationOn, action(Settings.ACTION_LOCATION_SOURCE_SETTINGS)),
        SettingsShortcut(R.string.settings_shortcut_accounts, Icons.Filled.AccountCircle, action(Settings.ACTION_SYNC_SETTINGS)),
        SettingsShortcut(
            R.string.settings_shortcut_accessibility,
            Icons.Filled.Accessibility,
            action(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        ),
        SettingsShortcut(R.string.settings_shortcut_language, Icons.Filled.Language, action(Settings.ACTION_LOCALE_SETTINGS)),
        SettingsShortcut(R.string.settings_shortcut_keyboard, Icons.Filled.Keyboard, action(Settings.ACTION_INPUT_METHOD_SETTINGS)),
        SettingsShortcut(R.string.settings_shortcut_date_time, Icons.Filled.Schedule, action(Settings.ACTION_DATE_SETTINGS)),
        SettingsShortcut(
            R.string.settings_shortcut_developer,
            Icons.Filled.DeveloperMode,
            action(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        ),
        SettingsShortcut(R.string.settings_shortcut_about, Icons.Filled.PhoneAndroid, action(Settings.ACTION_DEVICE_INFO_SETTINGS))
    )

private fun availableShortcuts(context: Context): List<Pair<SettingsShortcut, Intent>> = SHORTCUTS.mapNotNull { shortcut ->
    val intent = shortcut.intent(context)
    if (intent.resolveActivity(context.packageManager) != null) shortcut to intent else null
}

private fun launch(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Log.w(TAG, "Couldn't open ${intent.component ?: intent.action}", it) }
}

/**
 * The way into the stock Sanytron launcher (HaRemote), which stays installed
 * after Astrion Custom becomes the home app (docs/GETTING_STARTED.md §7).
 * [opensSettings] is false when HaRemote keeps its settings screen private
 * and [intent] can only open the app itself.
 */
private class StockAstrionEntry(val intent: Intent, val opensSettings: Boolean)

/**
 * HaRemote's package name isn't documented, so it's found among the
 * device's home/launcher apps by package name or label. Its settings screen
 * is the exported activity with "Setting" in its name (HaRemote's own is
 * SettingActivity); the shortest such name wins, so SettingActivity beats
 * SettingDisplayActivity.
 */
private object StockAstrionApp {
    private val NAME_HINTS = listOf("haremote", "ha remote", "ha_remote", "sanytron", "astrion")

    /** This app and its `.debug` beta, which would otherwise match "astrion". */
    private const val OWN_PACKAGE_PREFIX = "com.custom.astrion"

    // The Int-flag PackageManager overloads are deprecated from Android 13
    // but still work there, and the HA100 runs Android 8.1.
    @Suppress("DEPRECATION")
    fun find(context: Context): StockAstrionEntry? {
        val pm = context.packageManager
        val candidates =
            listOf(Intent.CATEGORY_HOME, Intent.CATEGORY_LAUNCHER)
                .flatMap { category -> pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }
                .map { it.activityInfo }
                .filter { !it.packageName.startsWith(OWN_PACKAGE_PREFIX) && looksLikeStock(pm, it) }
        val packages = candidates.map { it.packageName }.distinct()

        packages.firstNotNullOfOrNull { settingsActivity(pm, it) }?.let {
            return StockAstrionEntry(it, opensSettings = true)
        }
        val pkg = packages.firstOrNull() ?: return null
        val home = candidates.first { it.packageName == pkg }
        val open = pm.getLaunchIntentForPackage(pkg) ?: Intent(Intent.ACTION_MAIN).setClassName(home.packageName, home.name)
        return StockAstrionEntry(open, opensSettings = false)
    }

    private fun looksLikeStock(pm: PackageManager, info: ActivityInfo): Boolean {
        val label = runCatching { info.applicationInfo.loadLabel(pm).toString() }.getOrDefault("")
        val haystack = "${info.packageName} $label".lowercase()
        return NAME_HINTS.any { it in haystack }
    }

    @Suppress("DEPRECATION")
    private fun settingsActivity(pm: PackageManager, pkg: String): Intent? {
        val activities = runCatching { pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities }.getOrNull() ?: return null
        return activities
            .filter { it.exported && it.enabled && it.permission == null }
            .map { it to it.name.substringAfterLast('.') }
            .filter { (_, simpleName) -> simpleName.contains("setting", ignoreCase = true) }
            .minByOrNull { (_, simpleName) -> simpleName.length }
            ?.let { (activity, _) -> Intent().setClassName(activity.packageName, activity.name) }
    }
}

/**
 * Pull-down menu shortcuts into Android's own Settings screens, plus the stock
 * Astrion (HaRemote) settings when that app is still on the device. The list
 * sits behind a collapsed row so it doesn't push the app's own switches far
 * down the menu. Both lookups run off the main thread, once per menu open.
 */
@Composable
internal fun AndroidSettingsSection() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val stockEntry by produceState<StockAstrionEntry?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { runCatching { StockAstrionApp.find(context) }.getOrNull() }
    }
    val shortcuts by produceState(initialValue = emptyList<Pair<SettingsShortcut, Intent>>()) {
        value = withContext(Dispatchers.IO) { availableShortcuts(context) }
    }

    stockEntry?.let { entry ->
        val label = stringResource(if (entry.opensSettings) R.string.stock_astrion_settings else R.string.stock_astrion_app)
        SettingRow(icon = Icons.Filled.SettingsRemote, label = label) { launch(context, entry.intent) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingRow(
            icon = Icons.Filled.Tune,
            label = stringResource(R.string.android_settings_more),
            trailingIcon = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore
        ) { expanded = !expanded }

        if (expanded) {
            shortcuts.forEach { (shortcut, intent) ->
                SettingRow(icon = shortcut.icon, label = stringResource(shortcut.label)) { launch(context, intent) }
            }
        }
    }
}
