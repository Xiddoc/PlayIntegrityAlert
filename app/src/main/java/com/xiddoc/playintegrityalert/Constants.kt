package com.xiddoc.playintegrityalert

/** Shared constants for the app and the Xposed hook. */
object Constants {
    const val TAG = "PlayIntegrityAlert"
    const val OWN_PACKAGE = "com.xiddoc.playintegrityalert"

    /** Play Store / Finsky, which actually hosts the Play Integrity service. */
    const val VENDING_PACKAGE = "com.android.vending"

    /** Notification channel for Play Integrity usage alerts. */
    const val CHANNEL_ID = "play_integrity_alerts"

    /** Best-effort broadcast from a hooked process to our app's history store. */
    const val ACTION_DETECTED = "com.xiddoc.playintegrityalert.action.INTEGRITY_DETECTED"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_LABEL = "label"
    const val EXTRA_DETAIL = "detail"
    const val EXTRA_TIMESTAMP = "timestamp"

    /** Marker logged through XposedBridge so LSPosed logs (and CI) can assert hooks fired. */
    const val LOG_INSTALLED = "PIA_HOOK_INSTALLED"
    const val LOG_READY = "PIA_HOOK_READY"
    const val LOG_DETECTED = "PIA_DETECTED"

    /**
     * Substrings that mark a [android.content.Intent] as a bind to the Play
     * Integrity service. The Play Core / Play Integrity client libraries bind to
     * Play Store using the AIDL interface name as the intent action, e.g.
     * `com.google.android.play.core.integrity.protocol.IIntegrityService`, and the
     * Express Integrity variant. Matching "integrity" (case-insensitive) catches
     * both the legacy and Standard Integrity APIs without depending on the
     * obfuscated client classes, while staying narrow enough to ignore the
     * sibling review / app-update Play Core services.
     */
    val INTEGRITY_ACTION_HINTS = listOf("integrity")

    /** Finsky service classes that fulfil integrity requests (component-based binds). */
    val INTEGRITY_COMPONENT_HINTS = listOf(
        "integrityservice",
        "expressintegrity",
    )
}
