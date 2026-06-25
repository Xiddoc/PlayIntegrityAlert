package com.xiddoc.playintegrityalert

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the libsu-backed [LibsuRootShell]. The JVM/Robolectric host has no
 * Android su daemon, so libsu falls back to a plain `sh` shell — which is enough to
 * run both methods end to end. We only assert they return without throwing; the
 * actual root state of the host is irrelevant (and [LibsuRootShell] is branch-free).
 */
@RunWith(RobolectricTestRunner::class)
class RootShellTest {

    @Test
    fun isAvailableReturnsWithoutThrowing() {
        // Boolean by construction; the call must simply not blow up off-device.
        LibsuRootShell.isAvailable()
    }

    @Test
    fun execRunsCommandWithoutThrowing() {
        // `true` is a no-op success in any POSIX shell.
        LibsuRootShell.exec("true")
    }
}
