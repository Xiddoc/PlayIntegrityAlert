package com.xiddoc.playintegrityalert

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WatchListTest {

    private class FakeSource(
        val watchAll: Boolean,
        val watched: Set<String>,
    ) : WatchList.Source {
        var reloadCount = 0
        override fun reload() { reloadCount++ }
        override fun watchAll() = watchAll
        override fun watched() = watched
    }

    private lateinit var original: WatchList.Source

    @Before
    fun save() {
        original = WatchList.source
    }

    @After
    fun restore() {
        WatchList.source = original
    }

    @Test
    fun watchAllAlertsForEveryPackageAndReloadsFirst() {
        val source = FakeSource(watchAll = true, watched = emptySet())
        WatchList.source = source

        assertTrue(WatchList.isWatched("com.anything"))
        assertEquals(1, source.reloadCount)
    }

    @Test
    fun whenNotWatchAllOnlyListedPackagesMatch() {
        WatchList.source = FakeSource(watchAll = false, watched = setOf("com.watched"))

        assertTrue(WatchList.isWatched("com.watched"))
        assertFalse(WatchList.isWatched("com.unwatched"))
    }
}
