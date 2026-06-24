package com.xiddoc.playintegrityalert

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-caller debounce so a single burst of integrity requests yields one alert.
 *
 * The clock is injectable so the time-based behaviour is deterministically
 * testable without sleeping.
 */
class AlertThrottle(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val lastAlertAt = ConcurrentHashMap<String, Long>()

    /**
     * True if an alert for [caller] should fire now — i.e. it is the first time we
     * have seen this caller, or at least [windowMs] has elapsed since the last
     * time. The caller's timestamp is always refreshed.
     */
    fun allow(caller: String): Boolean {
        val now = clock()
        val previous = lastAlertAt.put(caller, now)
        return previous == null || now - previous >= windowMs
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 4_000L
    }
}
