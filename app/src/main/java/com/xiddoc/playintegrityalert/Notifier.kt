package com.xiddoc.playintegrityalert

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import de.robv.android.xposed.XposedBridge

/**
 * Bridges a detection from the hooked Play Store process to the Play Integrity
 * Alert app, which raises the actual notification.
 *
 * The alert is posted by our own app rather than from the Play Store process, so
 * it always carries our icon/identity and uses our notification channel and
 * permission. The broadcast is explicit (a named component) and flagged to wake
 * our app even if it has never been launched.
 */
object Notifier {

    fun notifyDetection(context: Context, callerPackage: String, detail: String) {
        val intent = receiverIntent(Constants.ACTION_DETECTED).apply {
            putExtra(Constants.EXTRA_PACKAGE, callerPackage)
            putExtra(Constants.EXTRA_DETAIL, detail)
            // Genuine hook traffic — may refresh the "watching" heartbeat.
            putExtra(Constants.EXTRA_FROM_HOOK, true)
        }
        send(context, intent, "alert for $callerPackage")
    }

    /**
     * Tells our app the hook is live inside the Play Store process. Lets the UI show
     * "watching" without our own app having to be in the module's scope (it can't be).
     */
    fun reportHookAlive(context: Context) {
        send(context, receiverIntent(Constants.ACTION_HOOK_ALIVE), "hook-alive ping")
    }

    /** Explicit, stopped-package-safe broadcast to our [DetectionReceiver], with a timestamp. */
    private fun receiverIntent(action: String): Intent =
        Intent(action).apply {
            component = ComponentName(Constants.OWN_PACKAGE, "${Constants.OWN_PACKAGE}.DetectionReceiver")
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            putExtra(Constants.EXTRA_TIMESTAMP, System.currentTimeMillis())
        }

    private fun send(context: Context, intent: Intent, what: String) {
        runCatching {
            context.applicationContext.sendBroadcast(intent)
        }.onFailure {
            XposedBridge.log("[${Constants.TAG}] could not deliver $what: $it")
        }
    }
}
