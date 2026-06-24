package com.xiddoc.playintegrityalert

import de.robv.android.xposed.XSharedPreferences

/**
 * Default [WatchList.Source], backed by [XSharedPreferences] — LSPosed's supported
 * channel for a module to read its own app's preferences across processes; LSPosed
 * permits the app to write the file world-readable for exactly this.
 *
 * Thin Xposed-runtime glue with no decision logic of its own, so it is exercised
 * by the e2e rather than the unit suite.
 */
internal class XSharedConfigSource : WatchList.Source {

    private val prefs by lazy {
        XSharedPreferences(Constants.OWN_PACKAGE, Constants.PREFS_CONFIG)
    }

    override fun reload() {
        runCatching { prefs.reload() }
    }

    override fun watchAll(): Boolean =
        prefs.getBoolean(Constants.KEY_WATCH_ALL, true)

    override fun watched(): Set<String> =
        prefs.getStringSet(Constants.KEY_WATCHED, emptySet()).orEmpty()
}
