package com.xiddoc.playintegrityalert

import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XposedEntryTest {

    private val classLoader: ClassLoader = javaClass.classLoader!!

    private fun lpparam(pkg: String) = XC_LoadPackage.LoadPackageParam().apply {
        packageName = pkg
        classLoader = this@XposedEntryTest.classLoader
    }

    @Before
    fun reset() {
        XposedBridge.reset()
        XposedHelpers.reset()
    }

    @After
    fun cleanup() {
        unmockkAll()
        XposedBridge.reset()
        XposedHelpers.reset()
    }

    @Test
    fun logsModuleLoadedAndDoesNotSelfHookForOtherPackages() {
        XposedEntry().handleLoadPackage(lpparam("com.random.app"))
        assertTrue(XposedBridge.logs.any { it.contains(Constants.LOG_MODULE_LOADED) })
        assertTrue(XposedHelpers.hookedMethods.isEmpty())
    }

    @Test
    fun ownPackageMarksSelfActivated() {
        XposedEntry().handleLoadPackage(lpparam(Constants.OWN_PACKAGE))
        assertTrue(XposedHelpers.hookedMethods.any { it.contains("MainActivity#isModuleActivated") })
    }

    @Test
    fun ownPackageSwallowsSelfHookFailure() {
        XposedHelpers.error = RuntimeException("cannot hook")
        XposedEntry().handleLoadPackage(lpparam(Constants.OWN_PACKAGE)) // must not throw
    }

    @Test
    fun vendingPackageInstallsTheIntegrityHook() {
        mockkObject(IntegrityServiceHook)
        every { IntegrityServiceHook.install(any()) } just Runs

        XposedEntry().handleLoadPackage(lpparam(Constants.VENDING_PACKAGE))

        verify { IntegrityServiceHook.install(classLoader) }
    }

    @Test
    fun vendingPackageLogsWhenInstallFails() {
        mockkObject(IntegrityServiceHook)
        every { IntegrityServiceHook.install(any()) } throws RuntimeException("boom")

        XposedEntry().handleLoadPackage(lpparam(Constants.VENDING_PACKAGE))

        assertTrue(XposedBridge.logs.any { it.contains("failed to install") })
    }
}
