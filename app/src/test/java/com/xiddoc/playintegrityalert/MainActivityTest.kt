package com.xiddoc.playintegrityalert

import android.Manifest
import android.app.Application
import android.os.Build
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

        // Status reflects the (false) module-active state.
        assertEquals(
            activity.getString(R.string.status_inactive),
            activity.findViewById<TextView>(R.id.status).text,
        )
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
    fun statusTextReflectsModuleActiveState() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(activity.getString(R.string.status_active), activity.statusText(true))
        assertEquals(activity.getString(R.string.status_inactive), activity.statusText(false))
    }
}
