package com.xiddoc.playintegrityalert

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun watchAllDefaultsToTrue() {
        assertTrue(Config.isWatchAll(context))
    }

    @Test
    fun watchAllRoundTrips() {
        Config.setWatchAll(context, false)
        assertFalse(Config.isWatchAll(context))
        Config.setWatchAll(context, true)
        assertTrue(Config.isWatchAll(context))
    }

    @Test
    fun watchedDefaultsToEmptyAndRoundTrips() {
        assertTrue(Config.watched(context).isEmpty())
        Config.setWatched(context, setOf("com.a", "com.b"))
        assertEquals(setOf("com.a", "com.b"), Config.watched(context))
    }

    @Test
    fun fallsBackToPrivateModeWhenWorldReadableUnsupported() {
        val realPrivate = context.getSharedPreferences("config_fallback", Context.MODE_PRIVATE)
        val ctx = mockk<Context>()
        every {
            ctx.getSharedPreferences(Constants.PREFS_CONFIG, Context.MODE_WORLD_READABLE)
        } throws SecurityException("world-readable not permitted")
        every {
            ctx.getSharedPreferences(Constants.PREFS_CONFIG, Context.MODE_PRIVATE)
        } returns realPrivate

        // Must not throw: it falls back to private mode and returns the default.
        assertTrue(Config.isWatchAll(ctx))
    }

    @Test
    fun watchedReturnsEmptyWhenStoreReturnsNull() {
        // The platform getStringSet is @Nullable; exercise the null-handling path.
        val prefs = mockk<SharedPreferences>()
        every { prefs.getStringSet(Constants.KEY_WATCHED, emptySet()) } returns null
        val ctx = mockk<Context>()
        every {
            ctx.getSharedPreferences(Constants.PREFS_CONFIG, Context.MODE_WORLD_READABLE)
        } returns prefs

        assertTrue(Config.watched(ctx).isEmpty())
    }
}
