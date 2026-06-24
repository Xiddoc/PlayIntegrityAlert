package com.xiddoc.playintegrityalert

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AlertAppTest {

    @Test
    @Config(sdk = [33])
    fun createsNotificationChannel() {
        val app = ApplicationProvider.getApplicationContext<AlertApp>()
        val nm = app.getSystemService(NotificationManager::class.java)
        assertNotNull(nm.getNotificationChannel(Constants.CHANNEL_ID))
    }
}
