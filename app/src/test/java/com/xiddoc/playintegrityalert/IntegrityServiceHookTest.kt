package com.xiddoc.playintegrityalert

import android.app.AndroidAppHelper
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBinder

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
        IntegrityServiceHook.reportedAlive = false
        mockkObject(Notifier)
        every { Notifier.notifyDetection(any(), any(), any()) } just Runs
        every { Notifier.reportHookAlive(any()) } just Runs
    }

    @After
    fun tearDown() {
        WatchList.source = originalSource
        IntegrityServiceHook.throttle = originalThrottle
        IntegrityServiceHook.reportedAlive = false
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

    // ---- callerFromBinder() (Standard/Express API: no package Bundle) ----

    private val externalUid = 10_042

    private fun contextWithPm(pm: PackageManager): Context =
        mockk<Context>(relaxed = true).also { every { it.packageManager } returns pm }

    @Test
    fun callbackFallsBackToBinderCallerWhenNoPackageBundle() {
        // A request with no caller package in the args (the Express API shape) is still
        // attributed via the binder calling UID resolved through the PackageManager.
        IntegrityServiceHook.install(loaderResolvingIntegrityClasses())
        val callback = XposedBridge.lastCallback!!
        ShadowBinder.setCallingUid(externalUid)
        val pm = mockk<PackageManager>()
        every { pm.getPackagesForUid(externalUid) } returns arrayOf("com.express.app")
        val context = contextWithPm(pm)
        val param = XC_MethodHook.MethodHookParam().apply {
            args = arrayOf<Any?>("a parcelable request, not a bundle")
            thisObject = context
        }

        callback.callBeforeHookedMethod(param)

        verify { Notifier.notifyDetection(context, "com.express.app", any()) }
    }

    @Test
    fun binderCallerIgnoresSystemUid() {
        ShadowBinder.setCallingUid(1_000) // system, not a third-party app
        assertNull(IntegrityServiceHook.callerFromBinder(mockk<Context>(relaxed = true)))
    }

    @Test
    fun binderCallerYieldsNullWithoutAContext() {
        ShadowBinder.setCallingUid(externalUid)
        AndroidAppHelper.currentApplication = null
        assertNull(IntegrityServiceHook.callerFromBinder(serviceObject = null))
    }

    @Test
    fun binderCallerYieldsNullWhenUidResolvesToNothing() {
        ShadowBinder.setCallingUid(externalUid)
        val pm = mockk<PackageManager>()
        every { pm.getPackagesForUid(externalUid) } returns null
        assertNull(IntegrityServiceHook.callerFromBinder(contextWithPm(pm)))
    }

    @Test
    fun binderCallerYieldsNullWhenPackageManagerThrows() {
        ShadowBinder.setCallingUid(externalUid)
        val pm = mockk<PackageManager>()
        every { pm.getPackagesForUid(externalUid) } throws RuntimeException("boom")
        assertNull(IntegrityServiceHook.callerFromBinder(contextWithPm(pm)))
    }

    @Test
    fun binderCallerIgnoresPlayStoreItself() {
        ShadowBinder.setCallingUid(externalUid)
        val pm = mockk<PackageManager>()
        every { pm.getPackagesForUid(externalUid) } returns arrayOf(Constants.VENDING_PACKAGE)
        assertNull(IntegrityServiceHook.callerFromBinder(contextWithPm(pm)))
    }

    @Test
    fun binderCallerIgnoresOurOwnApp() {
        ShadowBinder.setCallingUid(externalUid)
        val pm = mockk<PackageManager>()
        every { pm.getPackagesForUid(externalUid) } returns arrayOf(Constants.OWN_PACKAGE)
        assertNull(IntegrityServiceHook.callerFromBinder(contextWithPm(pm)))
    }

    @Test
    fun binderCallerResolvesThirdPartyApp() {
        ShadowBinder.setCallingUid(externalUid)
        val pm = mockk<PackageManager>()
        every { pm.getPackagesForUid(externalUid) } returns arrayOf("com.third.party")
        assertEquals("com.third.party", IntegrityServiceHook.callerFromBinder(contextWithPm(pm)))
    }

    // ---- handleHookedCall(): attribution, diagnostics, context resolution ----

    private fun bundleArgs(build: Bundle.() -> Unit) =
        arrayOf<Any?>(Bundle().apply(build))

    @Test
    fun handlesCallByAttributingBundleCaller() {
        val context = mockk<Context>(relaxed = true)
        IntegrityServiceHook.handleHookedCall(
            method = null,
            args = bundleArgs { putString("package.name", "com.watched.app") },
            serviceObject = context,
        )
        verify { Notifier.notifyDetection(context, "com.watched.app", any()) }
    }

    @Test
    fun fallsBackToAppContextWhenServiceObjectIsNotAContext() {
        val app = mockk<Application>(relaxed = true)
        AndroidAppHelper.currentApplication = app

        IntegrityServiceHook.handleHookedCall(
            method = null,
            args = bundleArgs { putString("package.name", "com.watched.app") },
            serviceObject = "not a context",
        )

        verify { Notifier.notifyDetection(app, "com.watched.app", any()) }
    }

    @Test
    fun logsUnattributedCallWithMethodNameAndArgShapes() {
        // No caller anywhere -> the call is logged (with method + arg shapes) for diagnosis.
        ShadowBinder.setCallingUid(1_000) // system, so the binder fallback yields nothing
        val method = FakeIntegrityService::class.java.getDeclaredMethod("warmUpIntegrityToken", Int::class.java)

        IntegrityServiceHook.handleHookedCall(
            method = method,
            args = bundleArgs { putString("token", "verdict") },
            serviceObject = mockk<Context>(relaxed = true),
        )

        verify(exactly = 0) { Notifier.notifyDetection(any(), any(), any()) }
        assertTrue(
            XposedBridge.logs.any {
                it.contains("unattributed integrity call warmUpIntegrityToken") &&
                    it.contains("Bundle{token}")
            },
        )
    }

    // ---- describeArgs() ----

    @Test
    fun describeArgsRendersNullArrayBundlesAndOtherTypes() {
        assertEquals("null", IntegrityServiceHook.describeArgs(null))
        // One key keeps the rendering deterministic (keySet() order isn't guaranteed).
        assertEquals(
            "[null, Bundle{nonce}, java.lang.String]",
            IntegrityServiceHook.describeArgs(
                arrayOf<Any?>(null, Bundle().apply { putString("nonce", "1") }, "s"),
            ),
        )
    }

    @Test
    fun describeArgsToleratesABundleWhoseKeysetThrows() {
        val bundle = mockk<Bundle>()
        every { bundle.keySet() } throws RuntimeException("boom")
        assertEquals("[Bundle{?}]", IntegrityServiceHook.describeArgs(arrayOf<Any?>(bundle)))
    }

    // ---- onIntegrityRequest(): watch-list + throttle ----

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
    fun skipsNotificationWhenNoContextAvailable() {
        IntegrityServiceHook.onIntegrityRequest("com.x", context = null)
        verify(exactly = 0) { Notifier.notifyDetection(any(), any(), any()) }
    }

    // ---- hook-alive heartbeat (any hooked call proves liveness) ----

    @Test
    fun reportsHookAliveOnceEvenAcrossManyCalls() {
        // Even a call we can't attribute proves the hook is live; report it exactly once.
        ShadowBinder.setCallingUid(1_000) // unattributable, so only the heartbeat fires
        val context = mockk<Context>(relaxed = true)

        assertFalse(IntegrityServiceHook.reportedAlive)
        IntegrityServiceHook.handleHookedCall(method = null, args = null, serviceObject = context)
        assertTrue(IntegrityServiceHook.reportedAlive)
        IntegrityServiceHook.handleHookedCall(method = null, args = null, serviceObject = context)

        verify(exactly = 1) { Notifier.reportHookAlive(context) }
    }

    @Test
    fun doesNotReportHookAliveWithoutAContext() {
        AndroidAppHelper.currentApplication = null
        IntegrityServiceHook.handleHookedCall(method = null, args = null, serviceObject = null)
        verify(exactly = 0) { Notifier.reportHookAlive(any()) }
    }
}
