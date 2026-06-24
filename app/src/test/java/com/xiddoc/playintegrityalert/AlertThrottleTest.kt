package com.xiddoc.playintegrityalert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertThrottleTest {

    @Test
    fun firstCallForCallerIsAllowed() {
        val throttle = AlertThrottle(windowMs = 1_000L, clock = { 0L })
        assertTrue(throttle.allow("a"))
    }

    @Test
    fun repeatWithinWindowIsBlocked() {
        var now = 0L
        val throttle = AlertThrottle(windowMs = 1_000L, clock = { now })
        assertTrue(throttle.allow("a"))
        now = 999L
        assertFalse(throttle.allow("a"))
    }

    @Test
    fun repeatAtOrAfterWindowIsAllowedAgain() {
        var now = 0L
        val throttle = AlertThrottle(windowMs = 1_000L, clock = { now })
        assertTrue(throttle.allow("a"))
        now = 1_000L
        assertTrue(throttle.allow("a"))
    }

    @Test
    fun callersAreThrottledIndependently() {
        val throttle = AlertThrottle(windowMs = 1_000L, clock = { 0L })
        assertTrue(throttle.allow("a"))
        assertTrue(throttle.allow("b"))
    }

    @Test
    fun defaultConstructorUsesRealClockAndWindow() {
        // Exercises the default windowMs and the default System.currentTimeMillis clock.
        val throttle = AlertThrottle()
        assertTrue(throttle.allow("once"))
        assertFalse(throttle.allow("once"))
        assertEquals(4_000L, AlertThrottle.DEFAULT_WINDOW_MS)
    }
}
