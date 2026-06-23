package com.xiddoc.playintegrityalert

import android.content.Context
import android.content.SharedPreferences

/**
 * App-side reader/writer for the watch-list. Writes with [Context.MODE_WORLD_READABLE]
 * so the module's [WatchList] (running in the Play Store process) can read the file;
 * LSPosed permits this mode for module apps. Falls back to private mode off-LSPosed
 * so the settings UI still works when simply browsing the app.
 */
object Config {

    @Suppress("DEPRECATION")
    private fun prefs(context: Context): SharedPreferences =
        runCatching {
            context.getSharedPreferences(Constants.PREFS_CONFIG, Context.MODE_WORLD_READABLE)
        }.getOrElse {
            context.getSharedPreferences(Constants.PREFS_CONFIG, Context.MODE_PRIVATE)
        }

    fun isWatchAll(context: Context): Boolean =
        prefs(context).getBoolean(Constants.KEY_WATCH_ALL, true)

    fun setWatchAll(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(Constants.KEY_WATCH_ALL, value).apply()
    }

    fun watched(context: Context): Set<String> =
        prefs(context).getStringSet(Constants.KEY_WATCHED, emptySet()).orEmpty()

    fun setWatched(context: Context, packages: Set<String>) {
        // Store a copy: SharedPreferences must not be handed its own live Set back.
        prefs(context).edit().putStringSet(Constants.KEY_WATCHED, HashSet(packages)).apply()
    }
}
