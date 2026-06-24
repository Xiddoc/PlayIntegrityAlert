package com.xiddoc.playintegrityalert

/** Shared constants for the app and the Xposed hook. */
object Constants {
    const val TAG = "PlayIntegrityAlert"
    const val OWN_PACKAGE = "com.xiddoc.playintegrityalert"

    /** Play Store / Finsky, which hosts the Play Integrity verdict service. */
    const val VENDING_PACKAGE = "com.android.vending"

    /** Notification channel for Play Integrity usage alerts. */
    const val CHANNEL_ID = "play_integrity_alerts"

    /** Broadcast from the Finsky-hosted hook to our app, which raises the alert. */
    const val ACTION_DETECTED = "com.xiddoc.playintegrityalert.action.INTEGRITY_DETECTED"

    /**
     * Heartbeat broadcast the hook fires (from the Play Store process) the first
     * time a Play Integrity request runs through the hook. Our app records the
     * timestamp so the UI can prove the hook is live inside Play Store — a check
     * that does not depend on our own app being in the module's scope (it can't be;
     * LSPosed won't let a module scope itself).
     */
    const val ACTION_HOOK_ALIVE = "com.xiddoc.playintegrityalert.action.HOOK_ALIVE"

    const val EXTRA_PACKAGE = "package"
    const val EXTRA_DETAIL = "detail"
    const val EXTRA_TIMESTAMP = "timestamp"

    /**
     * Marks a detection broadcast as coming from the real Play Store hook (vs. the
     * in-app "send test notification" button), so only genuine hook traffic is
     * allowed to refresh the "watching" heartbeat.
     */
    const val EXTRA_FROM_HOOK = "from_hook"

    /** Markers logged through XposedBridge so LSPosed logs (and CI) can assert behaviour. */
    const val LOG_MODULE_LOADED = "PIA_MODULE_LOADED"
    const val LOG_INSTALLED = "PIA_HOOK_INSTALLED"
    const val LOG_DETECTED = "PIA_DETECTED"

    /**
     * Finsky services that fulfil Play Integrity requests. We hook these inside the
     * Play Store process and read the caller's package from the request Bundle, so a
     * single injected process sees every app's request — far lighter than injecting
     * into each watched app. (Names from ElDavoo/PlayIntegrityBreak.)
     */
    val INTEGRITY_SERVICE_CLASSES = listOf(
        "com.google.android.finsky.integrityservice.IntegrityService",
        "com.google.android.finsky.integrityservice.BackgroundIntegrityService",
        "com.google.android.finsky.expressintegrityservice.ExpressIntegrityService",
    )

    /** Bundle keys Play carries the requesting app's package under. */
    val CALLER_PACKAGE_KEYS = listOf("package.name", "packageName", "package_name")

    /** Config shared from the app to the hook via XSharedPreferences. */
    const val PREFS_CONFIG = "config"
    const val KEY_WATCH_ALL = "watch_all"
    const val KEY_WATCHED = "watched"

    /** Last time the hook was confirmed alive in the Play Store process (epoch millis). */
    const val KEY_HOOK_SEEN_AT = "hook_seen_at"
}
