package com.xiddoc.playintegrityalert

import android.Manifest
import android.app.Application
import android.os.Build
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RoboConfig

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    @RoboConfig(sdk = [33])
    fun rendersAndWiresControls() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val watchAll = activity.findViewById<SwitchCompat>(R.id.watch_all)
        val choose = activity.findViewById<Button>(R.id.btn_choose)
        assertTrue(watchAll.isChecked) // default watch-all = true
        assertFalse(choose.isEnabled)

        // Toggle off -> persisted, picker button enabled.
        watchAll.isChecked = false
        assertFalse(Config.isWatchAll(activity))
        assertTrue(choose.isEnabled)

        // Picker button launches AppPickerActivity.
        choose.performClick()
        assertEquals(
            AppPickerActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )

        // Toggle back on.
        watchAll.isChecked = true
        assertTrue(Config.isWatchAll(activity))
        assertFalse(choose.isEnabled)

        // Test button broadcasts a detection event (must not crash).
        activity.findViewById<Button>(R.id.btn_test).performClick()

        // Clear button empties the history.
        DetectionStore.add(activity, Detection(1L, "com.a", "A", "x"))
        activity.findViewById<Button>(R.id.btn_clear).performClick()
        assertTrue(DetectionStore.list(activity).isEmpty())

        // Status reflects the (false) module-active state with no hook heartbeat yet.
        assertEquals(
            activity.getString(R.string.status_inactive),
            activity.findViewById<TextView>(R.id.status).text,
        )
    }

    @Test
    @RoboConfig(sdk = [33])
    fun restartPlayStoreButtonOpensPlayStoreAppInfo() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<Button>(R.id.btn_restart_play_store).performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertEquals("package:${Constants.VENDING_PACKAGE}", started.data.toString())
    }

    @Test
    @RoboConfig(sdk = [33])
    fun statusStaysInactiveWhenModuleNotLoadedDespiteStaleHeartbeat() {
        // A leftover heartbeat must NOT show "watching" once the module is no longer
        // loaded (isModuleActivated() is false off-device), so the status reads inactive.
        Config.setHookSeenAt(app, 1_700_000_000_000L)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val status = activity.findViewById<TextView>(R.id.status).text.toString()
        assertEquals(activity.getString(R.string.status_inactive), status)
    }

    @Test
    @RoboConfig(sdk = [33])
    fun rendersHistoryWhenPresent() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        DetectionStore.add(app, Detection(1_700_000_000_000L, "com.a", "Alpha", "verdict"))

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val history = activity.findViewById<TextView>(R.id.history).text.toString()
        assertTrue(history.contains("Alpha"))
        assertTrue(history.contains("com.a"))
    }

    @Test
    @RoboConfig(sdk = [33])
    fun requestsNotificationPermissionWhenMissing() {
        // Permission not granted -> onCreate takes the request branch (must not crash).
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNotNull(activity)
    }

    @Test
    @RoboConfig(sdk = [Build.VERSION_CODES.S])
    fun skipsPermissionRequestBeforeTiramisu() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNotNull(activity)
    }

    @Test
    @RoboConfig(sdk = [33])
    fun pickerEnabledWhenWatchAllDisabledAtLaunch() {
        // Launching with watch-all already off exercises the enabled-on-create branch.
        Config.setWatchAll(app, false)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertFalse(activity.findViewById<SwitchCompat>(R.id.watch_all).isChecked)
        assertTrue(activity.findViewById<Button>(R.id.btn_choose).isEnabled)
    }

    @Test
    @RoboConfig(sdk = [33])
    fun statusTextCoversAllThreeStates() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        // "Watching" needs BOTH the module loaded now AND a hook heartbeat.
        assertTrue(activity.statusText(true, 1_700_000_000_000L).contains("Watching Google Play Store"))
        // Module loaded into our process, but Play Store not yet hit.
        assertEquals(activity.getString(R.string.status_loaded), activity.statusText(true, 0L))
        // Not loaded -> inactive, even with a stale heartbeat from a previous run.
        assertEquals(activity.getString(R.string.status_inactive), activity.statusText(false, 0L))
        assertEquals(activity.getString(R.string.status_inactive), activity.statusText(false, 1_700_000_000_000L))
    }
}
