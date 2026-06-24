package com.xiddoc.playintegrityalert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the generated members of the [Detection] data class. */
class DetectionTest {

    private val base = Detection(1L, "com.pkg", "Label", "detail")

    @Test
    fun componentsExposeEachField() {
        assertEquals(1L, base.component1())
        assertEquals("com.pkg", base.component2())
        assertEquals("Label", base.component3())
        assertEquals("detail", base.component4())
    }

    @Test
    fun equalsAndHashCodeCoverEveryField() {
        // Identity, null, and wrong-type branches.
        assertTrue(base == base)
        assertFalse(base.equals(null))
        assertFalse(base.equals("not a detection"))

        // Fully equal copy.
        val same = base.copy()
        assertEquals(base, same)
        assertEquals(base.hashCode(), same.hashCode())

        // One differing field at a time exercises each comparison branch.
        assertNotEquals(base, base.copy(timestamp = 2L))
        assertNotEquals(base, base.copy(packageName = "com.other"))
        assertNotEquals(base, base.copy(label = "Other"))
        assertNotEquals(base, base.copy(detail = "other"))
    }

    @Test
    fun toStringContainsFields() {
        val text = base.toString()
        assertTrue(text.contains("com.pkg"))
        assertTrue(text.contains("Label"))
    }
}
