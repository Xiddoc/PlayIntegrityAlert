package com.xiddoc.playintegrityalert

import android.content.Context

/** A detected Play Integrity usage event. */
data class Detection(
    val timestamp: Long,
    val packageName: String,
    val label: String,
    val detail: String,
)

/**
 * Tiny persistent ring buffer of recent detections, backed by SharedPreferences
 * so the in-app history survives the app being killed between alerts.
 */
object DetectionStore {
    private const val PREFS = "detections"
    private const val KEY = "log"
    private const val SEPARATOR = "" // unit separator, safe inside labels
    private const val MAX_ENTRIES = 100

    fun add(context: Context, detection: Detection) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val line = listOf(
            detection.timestamp.toString(),
            detection.packageName,
            detection.label,
            detection.detail,
        ).joinToString(SEPARATOR) { it.replace(SEPARATOR, " ").replace("\n", " ") }

        val updated = (listOf(line) + read(prefs)).take(MAX_ENTRIES)
        prefs.edit().putString(KEY, updated.joinToString("\n")).apply()
    }

    fun list(context: Context): List<Detection> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return read(prefs).mapNotNull { line ->
            val parts = line.split(SEPARATOR)
            if (parts.size < 4) return@mapNotNull null
            Detection(
                timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null,
                packageName = parts[1],
                label = parts[2],
                detail = parts[3],
            )
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun read(prefs: android.content.SharedPreferences): List<String> =
        prefs.getString(KEY, "")?.takeIf { it.isNotEmpty() }?.split("\n").orEmpty()
}
