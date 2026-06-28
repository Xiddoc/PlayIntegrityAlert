package com.xiddoc.playintegrityalert

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmapOrNull

/**
 * Receives detection events broadcast from hooked app processes and raises the
 * alert notification from our own app (our icon, our channel, our permission).
 */
class DetectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Plain == comparisons (not a `when` on the String, which compiles to a
        // hashCode switch with an unreachable equals branch the coverage gate flags).
        if (intent.action == Constants.ACTION_HOOK_ALIVE) {
            markHookSeen(context, intent)
        } else if (intent.action == Constants.ACTION_DETECTED) {
            handleDetection(context, intent)
        }
    }

    /** Record that the hook pinged us from the Play Store process. */
    private fun markHookSeen(context: Context, intent: Intent) {
        Config.setHookSeenAt(
            context,
            intent.getLongExtra(Constants.EXTRA_TIMESTAMP, System.currentTimeMillis()),
        )
    }

    private fun handleDetection(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(Constants.EXTRA_PACKAGE) ?: return
        val detail = intent.getStringExtra(Constants.EXTRA_DETAIL).orEmpty()
        val timestamp = intent.getLongExtra(Constants.EXTRA_TIMESTAMP, System.currentTimeMillis())
        val label = labelFor(context, packageName)

        // A real hook detection also proves the hook is live, so keep the heartbeat
        // current — but the in-app test button must NOT flip the status to "watching".
        if (intent.getBooleanExtra(Constants.EXTRA_FROM_HOOK, false)) {
            Config.setHookSeenAt(context, timestamp)
        }
        DetectionStore.add(context, Detection(timestamp, packageName, label, detail))
        postNotification(context, packageName, label)
    }

    private fun labelFor(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /**
     * The requesting app's own icon, shown as the notification's large icon so the
     * alert is recognisable at a glance. Falls back to our launcher icon if the app
     * can't be resolved (e.g. uninstalled since the request). The small status-bar
     * icon stays our own monochrome glyph — Android tints small icons flat, so an
     * app icon can't be used there.
     */
    internal fun largeIcon(context: Context, packageName: String): Bitmap {
        val callerIcon = runCatching { context.packageManager.getApplicationIcon(packageName) }
            .getOrNull()
            ?.toBitmapOrNull()
        return callerIcon ?: BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
    }

    private fun postNotification(context: Context, packageName: String, label: String) {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(largeIcon(context, packageName))
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text, label))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notification_big_text, label, packageName)),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        runCatching {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED ||
                android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU
            ) {
                // One notification slot per app; repeated usage refreshes it.
                NotificationManagerCompat.from(context).notify(packageName.hashCode(), notification)
            }
        }
    }
}
