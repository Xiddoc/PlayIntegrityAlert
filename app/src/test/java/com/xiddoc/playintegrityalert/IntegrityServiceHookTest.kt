package com.xiddoc.playintegrityalert

import android.app.AndroidAppHelper
import android.app.Application
import android.content.Context
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntegrityServiceHookTest {

    private lateinit var originalSource: WatchList.Source
    private lateinit var originalThrottle: AlertThrottle

    @Before
    fun setUp() {
        XposedBridge.reset()
        AndroidAppHelper.reset()
        originalSource = WatchList.source
        originalThrottle = IntegrityServiceHook.throttle
        WatchList.source = fakeSource(watchAll = true)
        IntegrityServiceHook.throttle = AlertThrottle(windowMs = 1_000L, clock = { 0L })
        mockkObject(Notifier)
        every { Notifier.notifyDetection(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        WatchList.source = originalSource
        IntegrityServiceHook.throttle = originalThrottle
        unmockkAll()
        XposedBridge.reset()
        AndroidAppHelper.reset()
    }

    private fun fakeSource(watchAll: Boolean, watched: Set<String> = emptySet()) =
        object : WatchList.Source {
            override fun reload() {}
            override fun watchAll() = watchAll
            override fun watched() = watched
        }

    // ---- install() ----

    class FakeIntegrityService {
        fun requestIntegrityToken() {}
        fun warmUpIntegrityToken(flag: Int) {}
    }

    private fun loaderResolvingIntegrityClasses() = object : ClassLoader(javaClass.classLoader) {
        override fun loadClass(name: String): Class<*> =
            if (name in Constants.INTEGRITY_SERVICE_CLASSES) FakeIntegrityService::class.java
            else super.loadClass(name)
    }

    @Test
    fun installHooksDiscoveredServiceMethods() {
        val expected = Constants.INTEGRITY_SERVICE_CLASSES.size *
            FakeIntegrityService::class.java.declaredMethods.size

        IntegrityServiceHook.install(loaderResolvingIntegrityClasses())

        assertEquals(expected, XposedBridge.hookedMembers.size)
        assertTrue(
            XposedBridge.logs.any {
                it.contains(Constants.LOG_INSTALLED) && it.contains("methods=$expected")
            },
        )
    }

    @Test
    fun installSkipsClassesThatDoNotLoad() {
        IntegrityServiceHook.install(javaClass.classLoader!!) // integrity classes absent
        assertEquals(0, XposedBridge.hookedMembers.size)
        assertTrue(XposedBridge.logs.any { it.contains("methods=0") })
    }

    @Test
    fun installSwallowsHookMethodFailures() {
        XposedBridge.hookError = RuntimeException("cannot hook")
        IntegrityServiceHook.install(loaderResolvingIntegrityClasses())
        assertEquals(0, XposedBridge.hookedMembers.size)
        assertTrue(XposedBridge.logs.any { it.contains("methods=0") })
    }

    @Test
    fun installedCallbackReportsWatchedCaller() {
        IntegrityServiceHook.install(loaderResolvingIntegrityClasses())
        val callback = XposedBridge.lastCallback!!
        val context = mockk<Context>(relaxed = true)
        val param = XC_MethodHook.MethodHookParam().apply {
            args = arrayOf<Any?>(Bundle().apply { putString("package.name", "com.watched.app") })
            thisObject = context
        }

        callback.callBeforeHookedMethod(param)

        verify { Notifier.notifyDetection(context, "com.watched.app", any()) }
    }

    @Test
    fun installedCallbackIgnoresNonRequests() {
        IntegrityServiceHook.install(loaderResolvingIntegrityClasses())
        val callback = XposedBridge.lastCallback!!
        val param = XC_MethodHook.MethodHookParam().apply {
            args = arrayOf<Any?>(Bundle().apply { putString("token", "verdict") })
            thisObject = mockk<Context>(relaxed = true)
        }

        callback.callBeforeHookedMethod(param)

        verify(exactly = 0) { Notifier.notifyDetection(any(), any(), any()) }
    }

    // ---- onIntegrityRequest() ----

    @Test
    fun ignoresUnwatchedCaller() {
        WatchList.source = fakeSource(watchAll = false, watched = emptySet())
        IntegrityServiceHook.onIntegrityRequest("com.x", mockk<Context>(relaxed = true))
        verify(exactly = 0) { Notifier.notifyDetection(any(), any(), any()) }
    }

    @Test
    fun debouncesRepeatedAlerts() {
        IntegrityServiceHook.throttle = AlertThrottle(windowMs = Long.MAX_VALUE, clock = { 0L })
        val context = mockk<Context>(relaxed = true)

        IntegrityServiceHook.onIntegrityRequest("com.x", context) // first -> alert
        IntegrityServiceHook.onIntegrityRequest("com.x", context) // within window -> blocked

        verify(exactly = 1) { Notifier.notifyDetection(context, "com.x", any()) }
    }

    @Test
    fun usesServiceObjectAsContextWhenPossible() {
        val context = mockk<Context>(relaxed = true)
        IntegrityServiceHook.onIntegrityRequest("com.x", context)
        verify { Notifier.notifyDetection(context, "com.x", any()) }
    }

    @Test
    fun fallsBackToAppContextWhenServiceObjectIsNotAContext() {
        val app = mockk<Application>(relaxed = true)
        AndroidAppHelper.currentApplication = app

        IntegrityServiceHook.onIntegrityRequest("com.x", serviceObject = "not a context")

        verify { Notifier.notifyDetection(app, "com.x", any()) }
    }

    @Test
    fun skipsNotificationWhenNoContextAvailable() {
        AndroidAppHelper.currentApplication = null
        IntegrityServiceHook.onIntegrityRequest("com.x", serviceObject = null)
        verify(exactly = 0) { Notifier.notifyDetection(any(), any(), any()) }
    }
}
