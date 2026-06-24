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
        runCatching {
            val intent = Intent(Constants.ACTION_DETECTED).apply {
                component = ComponentName(Constants.OWN_PACKAGE, "${Constants.OWN_PACKAGE}.DetectionReceiver")
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(Constants.EXTRA_PACKAGE, callerPackage)
                putExtra(Constants.EXTRA_DETAIL, detail)
                putExtra(Constants.EXTRA_TIMESTAMP, System.currentTimeMillis())
            }
            context.applicationContext.sendBroadcast(intent)
        }.onFailure {
            XposedBridge.log("[${Constants.TAG}] could not deliver alert for $callerPackage: $it")
        }
    }
}
