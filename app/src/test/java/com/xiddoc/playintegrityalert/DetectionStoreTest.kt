package com.xiddoc.playintegrityalert

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DetectionStoreTest {

    private val sep = ""
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun listIsEmptyByDefault() {
        assertTrue(DetectionStore.list(context).isEmpty())
    }

    @Test
    fun addThenListRoundTripsNewestFirst() {
        DetectionStore.add(context, Detection(1L, "com.a", "A", "first"))
        DetectionStore.add(context, Detection(2L, "com.b", "B", "second"))

        val list = DetectionStore.list(context)
        assertEquals(2, list.size)
        assertEquals(Detection(2L, "com.b", "B", "second"), list[0])
        assertEquals(Detection(1L, "com.a", "A", "first"), list[1])
    }

    @Test
    fun sanitizesSeparatorsAndNewlines() {
        DetectionStore.add(context, Detection(5L, "com.c", "lab${sep}el", "de\ntail"))
        val d = DetectionStore.list(context).single()
        assertEquals("lab el", d.label)
        assertEquals("de tail", d.detail)
    }

    @Test
    fun capsAtOneHundredEntries() {
        for (i in 1..101) {
            DetectionStore.add(context, Detection(i.toLong(), "com.$i", "L$i", "d$i"))
        }
        val list = DetectionStore.list(context)
        assertEquals(100, list.size)
        assertEquals(101L, list.first().timestamp) // newest kept
        assertEquals(2L, list.last().timestamp)     // oldest (entry #1) dropped
    }

    @Test
    fun skipsMalformedLines() {
        val prefs = context.getSharedPreferences("detections", Context.MODE_PRIVATE)
        val good = listOf("123", "com.ok", "Label", "detail").joinToString(sep)
        val tooFewParts = "onlyonepart"
        val badTimestamp = listOf("notanumber", "com.x", "L", "d").joinToString(sep)
        prefs.edit()
            .putString("log", listOf(tooFewParts, badTimestamp, good).joinToString("\n"))
            .commit()

        val list = DetectionStore.list(context)
        assertEquals(1, list.size)
        assertEquals("com.ok", list.single().packageName)
    }

    @Test
    fun listReturnsEmptyWhenStoredValueIsNull() {
        // SharedPreferences.getString is @Nullable; exercise the null-handling path.
        val prefs = mockk<SharedPreferences>()
        every { prefs.getString("log", "") } returns null
        val ctx = mockk<Context>()
        every { ctx.getSharedPreferences("detections", Context.MODE_PRIVATE) } returns prefs

        assertTrue(DetectionStore.list(ctx).isEmpty())
    }

    @Test
    fun clearEmptiesTheLog() {
        DetectionStore.add(context, Detection(1L, "com.a", "A", "x"))
        assertTrue(DetectionStore.list(context).isNotEmpty())
        DetectionStore.clear(context)
        assertTrue(DetectionStore.list(context).isEmpty())
    }
}
