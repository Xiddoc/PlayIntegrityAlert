package com.xiddoc.playintegrityalert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstantsTest {

    @Test
    fun exposesStableValues() {
        assertEquals("com.xiddoc.playintegrityalert", Constants.OWN_PACKAGE)
        assertEquals("com.android.vending", Constants.VENDING_PACKAGE)
        assertEquals("config", Constants.PREFS_CONFIG)

        // Reference the collection fields so the object initializer is fully run.
        assertTrue(Constants.INTEGRITY_SERVICE_CLASSES.isNotEmpty())
        assertTrue(Constants.CALLER_PACKAGE_KEYS.contains("package.name"))

        // Touch the remaining string constants.
        assertTrue(
            listOf(
                Constants.TAG,
                Constants.CHANNEL_ID,
                Constants.ACTION_DETECTED,
                Constants.ACTION_HOOK_ALIVE,
                Constants.EXTRA_PACKAGE,
                Constants.EXTRA_DETAIL,
                Constants.EXTRA_TIMESTAMP,
                Constants.LOG_MODULE_LOADED,
                Constants.LOG_INSTALLED,
                Constants.LOG_DETECTED,
                Constants.KEY_WATCH_ALL,
                Constants.KEY_WATCHED,
                Constants.KEY_HOOK_SEEN_AT,
            ).all { it.isNotEmpty() },
        )
    }
}
