package com.xiddoc.playintegrityalert

import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntegrityRequestInspectorTest {

    private fun bundle(build: Bundle.() -> Unit) = Bundle().apply(build)

    @Test
    fun nullArgsYieldNull() {
        assertNull(IntegrityRequestInspector.callerPackage(null))
    }

    @Test
    fun nonBundleArgsAreNotARequest() {
        assertNull(IntegrityRequestInspector.callerPackage(arrayOf<Any?>("x", 1, null)))
    }

    @Test
    fun responseBundlesAreNotRequests() {
        val withToken = bundle { putString("token", "t"); putString("package.name", "com.a.b") }
        val withError = bundle { putString("error", "e"); putString("package.name", "com.a.b") }
        assertNull(IntegrityRequestInspector.callerPackage(arrayOf<Any?>(withToken)))
        assertNull(IntegrityRequestInspector.callerPackage(arrayOf<Any?>(withError)))
    }

    @Test
    fun bundleWithoutPackageOrNonceIsNotARequest() {
        val b = bundle { putString("foo", "bar") }
        assertNull(IntegrityRequestInspector.callerPackage(arrayOf<Any?>(b)))
    }

    @Test
    fun extractsCallerFromKnownKeySkippingNonBundleArgs() {
        val b = bundle { putString("package.name", "com.target.app") }
        assertEquals(
            "com.target.app",
            IntegrityRequestInspector.callerPackage(arrayOf<Any?>("ignored", b)),
        )
    }

    @Test
    fun nonceRequestFallsBackToPackageShapedValue() {
        val b = bundle {
            putString("requestNonce", "abc")        // makes it a request via the nonce key
            putInt("flags", 1)                       // non-string value -> getString returns null
            putString("origin", "com.fallback.pkg")  // package-shaped value found by the fallback
        }
        assertEquals("com.fallback.pkg", IntegrityRequestInspector.callerPackage(arrayOf<Any?>(b)))
    }

    @Test
    fun nonceRequestWithNoPackageShapedValueScansEveryKeyAndReturnsNull() {
        // No caller key and no package-shaped value, so the fallback scans every
        // entry — including a non-string value whose getString() is null.
        val b = bundle {
            putString("requestNonce", "plain-text")  // non-null, not package-shaped
            putInt("count", 5)                         // getString() returns null
        }
        assertNull(IntegrityRequestInspector.callerPackage(arrayOf<Any?>(b)))
    }

    @Test
    fun blankCallerKeyWithNoFallbackYieldsNull() {
        val b = bundle {
            putString("packageName", "   ")     // known key but blank
            putString("note", "not a package")  // contains spaces -> not package-shaped
        }
        assertNull(IntegrityRequestInspector.callerPackage(arrayOf<Any?>(b)))
    }

    @Test
    fun externalAppUidIsAnExternalCaller() {
        assertTrue(IntegrityRequestInspector.isExternalAppCaller(callingUid = 10_042, ownUid = 10_001))
    }

    @Test
    fun systemUidIsNotAnExternalCaller() {
        assertFalse(IntegrityRequestInspector.isExternalAppCaller(callingUid = 1_000, ownUid = 10_001))
    }

    @Test
    fun hostProcessUidIsNotAnExternalCaller() {
        // Same UID as the Play Store host process — a self/internal call, not a request.
        assertFalse(IntegrityRequestInspector.isExternalAppCaller(callingUid = 10_001, ownUid = 10_001))
    }

    @Test
    fun keySetScanFailureIsTreatedAsNotARequest() {
        // Forces the defensive runCatching around keySet() into its failure path.
        val b = mockk<Bundle>()
        every { b.containsKey("token") } returns false
        every { b.containsKey("error") } returns false
        Constants.CALLER_PACKAGE_KEYS.forEach { every { b.containsKey(it) } returns false }
        every { b.keySet() } throws RuntimeException("boom")
        assertNull(IntegrityRequestInspector.callerPackage(arrayOf<Any?>(b)))
    }
}
