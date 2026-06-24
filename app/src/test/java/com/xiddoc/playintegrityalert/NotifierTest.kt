package com.xiddoc.playintegrityalert

import android.content.Context
import android.content.Intent
import de.robv.android.xposed.XposedBridge
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotifierTest {

    @Before
    fun resetBridge() = XposedBridge.reset()

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun buildsAndSendsAnExplicitBroadcastToOurReceiver() {
        val appContext = mockk<Context>()
        val context = mockk<Context>()
        every { context.applicationContext } returns appContext
        val sent = slot<Intent>()
        every { appContext.sendBroadcast(capture(sent)) } just Runs

        Notifier.notifyDetection(context, "com.caller.app", "Play Integrity verdict requested")

        val intent = sent.captured
        assertEquals(Constants.ACTION_DETECTED, intent.action)
        assertEquals(Constants.OWN_PACKAGE, intent.component?.packageName)
        assertEquals("${Constants.OWN_PACKAGE}.DetectionReceiver", intent.component?.className)
        assertEquals("com.caller.app", intent.getStringExtra(Constants.EXTRA_PACKAGE))
        assertEquals(
            "Play Integrity verdict requested",
            intent.getStringExtra(Constants.EXTRA_DETAIL),
        )
        assertTrue(intent.getLongExtra(Constants.EXTRA_TIMESTAMP, 0L) > 0L)
        assertTrue(intent.flags and Intent.FLAG_INCLUDE_STOPPED_PACKAGES != 0)
        // Marks this as real hook traffic so the receiver may refresh the heartbeat.
        assertTrue(intent.getBooleanExtra(Constants.EXTRA_FROM_HOOK, false))
    }

    @Test
    fun reportHookAliveSendsExplicitHeartbeatToOurReceiver() {
        val appContext = mockk<Context>()
        val context = mockk<Context>()
        every { context.applicationContext } returns appContext
        val sent = slot<Intent>()
        every { appContext.sendBroadcast(capture(sent)) } just Runs

        Notifier.reportHookAlive(context)

        val intent = sent.captured
        assertEquals(Constants.ACTION_HOOK_ALIVE, intent.action)
        assertEquals(Constants.OWN_PACKAGE, intent.component?.packageName)
        assertEquals("${Constants.OWN_PACKAGE}.DetectionReceiver", intent.component?.className)
        assertTrue(intent.getLongExtra(Constants.EXTRA_TIMESTAMP, 0L) > 0L)
        assertTrue(intent.flags and Intent.FLAG_INCLUDE_STOPPED_PACKAGES != 0)
    }

    @Test
    fun logsThroughXposedWhenDeliveryFails() {
        val context = mockk<Context>()
        every { context.applicationContext } throws RuntimeException("no context")

        // Must not propagate: the failure is caught and logged via XposedBridge.
        Notifier.notifyDetection(context, "com.caller.app", "detail")

        assertTrue(XposedBridge.logs.any { it.contains("com.caller.app") })
    }
}
