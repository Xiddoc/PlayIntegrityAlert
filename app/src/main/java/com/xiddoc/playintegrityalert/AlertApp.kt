package com.xiddoc.playintegrityalert

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class AlertApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    private fun createChannel() {
        // minSdk is 30 (Android 11), so notification channels (API 26+) always exist.
        val channel = NotificationChannel(
            Constants.CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
