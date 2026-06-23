package com.xiddoc.playintegrityalert

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Receives detection events broadcast from hooked app processes and raises the
 * alert notification from our own app (our icon, our channel, our permission).
 */
class DetectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Constants.ACTION_DETECTED) return

        val packageName = intent.getStringExtra(Constants.EXTRA_PACKAGE) ?: return
        val detail = intent.getStringExtra(Constants.EXTRA_DETAIL).orEmpty()
        val timestamp = intent.getLongExtra(Constants.EXTRA_TIMESTAMP, System.currentTimeMillis())
        val label = labelFor(context, packageName)

        DetectionStore.add(context, Detection(timestamp, packageName, label, detail))
        postNotification(context, packageName, label)
    }

    private fun labelFor(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun postNotification(context: Context, packageName: String, label: String) {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
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
