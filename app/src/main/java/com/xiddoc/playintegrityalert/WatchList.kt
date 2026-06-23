package com.xiddoc.playintegrityalert

import de.robv.android.xposed.XSharedPreferences

/**
 * Read-only view, from inside the hooked Play Store process, of the watch-list the
 * user configured in our app. Backed by [XSharedPreferences] — LSPosed's supported
 * channel for a module to read its own app's preferences across processes.
 *
 * Fails safe: if the prefs can't be read (e.g. not running under LSPosed, or the
 * file isn't world-readable yet), [KEY_WATCH_ALL] defaults to true, so the user
 * still gets alerts rather than silent failure.
 */
object WatchList {

    private val prefs by lazy {
        XSharedPreferences(Constants.OWN_PACKAGE, Constants.PREFS_CONFIG)
    }

    fun isWatched(packageName: String): Boolean {
        runCatching { prefs.reload() }
        if (prefs.getBoolean(Constants.KEY_WATCH_ALL, true)) return true
        return prefs.getStringSet(Constants.KEY_WATCHED, emptySet())
            ?.contains(packageName) == true
    }
}
