package com.xiddoc.playintegrityalert

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.widget.ListView
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RoboConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class AppPickerActivityTest {

    private val app: android.app.Application get() = ApplicationProvider.getApplicationContext()
    private var originalRunner: (() -> Unit) -> Unit = AppPickerActivity.runInBackground

    @Before
    fun setUp() {
        originalRunner = AppPickerActivity.runInBackground
        AppPickerActivity.runInBackground = { it() } // run the scan synchronously

        val spm = shadowOf(app.packageManager)
        spm.installPackage(makePackage("com.beta", "Beta"))
        spm.installPackage(makePackage("com.alpha", "Alpha"))
    }

    @After
    fun tearDown() {
        AppPickerActivity.runInBackground = originalRunner
    }

    private fun makePackage(pkg: String, label: String): PackageInfo {
        val ai = ApplicationInfo().apply {
            packageName = pkg
            name = label
            nonLocalizedLabel = label
        }
        return PackageInfo().apply {
            packageName = pkg
            applicationInfo = ai
        }
    }

    @Test
    @RoboConfig(sdk = [33])
    fun populatesListReflectsWatchedAndSavesSelection() {
        Config.setWatched(app, setOf("com.alpha"))

        val activity = Robolectric.buildActivity(AppPickerActivity::class.java).setup().get()
        val list = activity.findViewById<ListView>(R.id.list)

        assertEquals(ListView.CHOICE_MODE_MULTIPLE, list.choiceMode)

        val labels = (0 until list.adapter.count).map { list.adapter.getItem(it).toString() }
        val alphaIdx = labels.indexOfFirst { it.contains("com.alpha") }
        val betaIdx = labels.indexOfFirst { it.contains("com.beta") }
        assertTrue(alphaIdx >= 0 && betaIdx >= 0)

        // The watched package starts checked; the other does not.
        assertTrue(list.isItemChecked(alphaIdx))
        assertFalse(list.isItemChecked(betaIdx))

        // Checking beta and firing the click listener persists the new selection.
        list.setItemChecked(betaIdx, true)
        list.onItemClickListener!!.onItemClick(list, null, betaIdx, betaIdx.toLong())

        val watched = Config.watched(activity)
        assertTrue(watched.contains("com.alpha"))
        assertTrue(watched.contains("com.beta"))
    }

    @Test
    fun defaultRunnerExecutesOnBackgroundThread() {
        val latch = CountDownLatch(1)
        AppPickerActivity.spawnThread { latch.countDown() }
        assertTrue(latch.await(3, TimeUnit.SECONDS))
    }
}
