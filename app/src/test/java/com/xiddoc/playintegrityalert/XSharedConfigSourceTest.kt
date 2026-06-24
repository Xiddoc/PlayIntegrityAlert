package com.xiddoc.playintegrityalert

import de.robv.android.xposed.XSharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XSharedConfigSourceTest {

    @Before
    fun reset() = XSharedPreferences.reset()

    @After
    fun cleanup() = XSharedPreferences.reset()

    @Test
    fun reloadDelegatesAndSwallowsErrors() {
        val source = XSharedConfigSource()
        source.reload()
        assertEquals(1, XSharedPreferences.reloadCount)

        XSharedPreferences.reloadShouldThrow = true
        source.reload() // must not throw
        assertEquals(2, XSharedPreferences.reloadCount)
    }

    @Test
    fun watchAllReadsBackingValue() {
        XSharedPreferences.watchAll = false
        assertFalse(XSharedConfigSource().watchAll())
        XSharedPreferences.watchAll = true
        assertTrue(XSharedConfigSource().watchAll())
    }

    @Test
    fun watchedReturnsSetOrEmptyWhenNull() {
        XSharedPreferences.watched = setOf("com.x")
        assertEquals(setOf("com.x"), XSharedConfigSource().watched())

        XSharedPreferences.watched = null
        assertTrue(XSharedConfigSource().watched().isEmpty())
    }
}
