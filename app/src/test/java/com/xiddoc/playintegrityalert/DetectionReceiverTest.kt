package com.xiddoc.playintegrityalert

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DetectionReceiverTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val receiver = DetectionReceiver()

    private val notificationManager: NotificationManager
        get() = app.getSystemService(NotificationManager::class.java)

    private fun postedCount(): Int = shadowOf(notificationManager).size()

    private fun detectionIntent(
        packageName: String? = "com.caller.app",
        detail: String? = "verdict requested",
        timestamp: Long? = 123L,
        fromHook: Boolean = false,
    ) = Intent(Constants.ACTION_DETECTED).apply {
        packageName?.let { putExtra(Constants.EXTRA_PACKAGE, it) }
        detail?.let { putExtra(Constants.EXTRA_DETAIL, it) }
        timestamp?.let { putExtra(Constants.EXTRA_TIMESTAMP, it) }
        if (fromHook) putExtra(Constants.EXTRA_FROM_HOOK, true)
    }

    @Test
    fun ignoresIntentWithWrongAction() {
        receiver.onReceive(app, Intent("com.example.SOMETHING_ELSE"))
        assertTrue(DetectionStore.list(app).isEmpty())
        assertEquals(0, postedCount())
        assertEquals(0L, com.xiddoc.playintegrityalert.Config.hookSeenAt(app))
    }

    @Test
    fun hookAliveActionRecordsHeartbeatWithoutDetectionOrNotification() {
        receiver.onReceive(
            app,
            Intent(Constants.ACTION_HOOK_ALIVE).putExtra(Constants.EXTRA_TIMESTAMP, 555L),
        )

        assertEquals(555L, com.xiddoc.playintegrityalert.Config.hookSeenAt(app))
        assertTrue(DetectionStore.list(app).isEmpty()) // heartbeat is not a detection
        assertEquals(0, postedCount())
    }

    @Test
    fun hookAliveActionFallsBackToNowWhenTimestampMissing() {
        receiver.onReceive(app, Intent(Constants.ACTION_HOOK_ALIVE))
        assertTrue(com.xiddoc.playintegrityalert.Config.hookSeenAt(app) > 0L)
    }

    @Test
    @Config(sdk = [33])
    fun realHookDetectionRefreshesTheHeartbeat() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        receiver.onReceive(app, detectionIntent(timestamp = 4242L, fromHook = true))
        assertEquals(4242L, com.xiddoc.playintegrityalert.Config.hookSeenAt(app))
    }

    @Test
    @Config(sdk = [33])
    fun localTestDetectionDoesNotRefreshTheHeartbeat() {
        // The in-app "test notification" broadcast has no from-hook flag, so it must
        // record/notify but NOT light up the authoritative "watching" heartbeat.
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        receiver.onReceive(app, detectionIntent(timestamp = 4242L)) // fromHook = false

        assertEquals(0L, com.xiddoc.playintegrityalert.Config.hookSeenAt(app))
        assertTrue(DetectionStore.list(app).isNotEmpty()) // still recorded
        assertEquals(1, postedCount())                    // still notified
    }

    @Test
    fun ignoresIntentWithoutPackageExtra() {
        receiver.onReceive(app, detectionIntent(packageName = null))
        assertTrue(DetectionStore.list(app).isEmpty())
        assertEquals(0, postedCount())
    }

    @Test
    @Config(sdk = [33])
    fun storesAndNotifiesForKnownAppWhenPermissionGranted() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // Our own installed package resolves to a real, non-empty label.
        receiver.onReceive(app, detectionIntent(packageName = app.packageName))

        val stored = DetectionStore.list(app).single()
        assertEquals(app.packageName, stored.packageName)
        assertEquals("verdict requested", stored.detail)
        assertEquals(123L, stored.timestamp)
        assertTrue(stored.label.isNotEmpty())
        assertEquals(1, postedCount())
    }

    @Test
    @Config(sdk = [33])
    fun unknownPackageFallsBackToPackageNameWithEmptyDetailAndDefaultTimestamp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        receiver.onReceive(
            app,
            detectionIntent(packageName = "com.unknown.pkg", detail = null, timestamp = null),
        )

        val stored = DetectionStore.list(app).single()
        assertEquals("com.unknown.pkg", stored.packageName)
        assertEquals("com.unknown.pkg", stored.label) // label lookup failed -> package name
        assertEquals("", stored.detail)                // missing detail -> empty
        assertTrue(stored.timestamp > 0L)              // missing timestamp -> now
    }

    @Test
    @Config(sdk = [33])
    fun doesNotPostWhenNotificationPermissionDenied() {
        // Permission intentionally not granted on this SDK.
        receiver.onReceive(app, detectionIntent())

        assertTrue(DetectionStore.list(app).isNotEmpty()) // still recorded
        assertEquals(0, postedCount())                    // but no notification
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun postsWithoutRuntimePermissionBeforeTiramisu() {
        receiver.onReceive(app, detectionIntent())

        assertEquals(1, postedCount())
    }
}
