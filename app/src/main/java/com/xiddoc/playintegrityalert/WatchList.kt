package com.xiddoc.playintegrityalert

/**
 * Read-only view, from inside the hooked Play Store process, of the watch-list the
 * user configured in our app.
 *
 * The decision logic lives here and is unit tested; the actual cross-process read
 * is delegated to a [Source], whose default implementation ([XSharedConfigSource])
 * is backed by XSharedPreferences.
 *
 * Fails safe: if the prefs can't be read (e.g. not running under LSPosed, or the
 * file isn't world-readable yet), watch-all defaults to true, so the user still
 * gets alerts rather than silent failure.
 */
object WatchList {

    /** Cross-process view of the watch-list config. */
    internal interface Source {
        fun reload()
        fun watchAll(): Boolean
        fun watched(): Set<String>
    }

    /** Swappable so unit tests can drive the decision logic without Xposed. */
    internal var source: Source = XSharedConfigSource()

    fun isWatched(packageName: String): Boolean {
        source.reload()
        if (source.watchAll()) return true
        return packageName in source.watched()
    }
}
