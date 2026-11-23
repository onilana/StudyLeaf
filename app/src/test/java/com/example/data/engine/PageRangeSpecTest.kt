package com.example.data.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PageRangeSpecTest {

    @Test
    fun parsePageRangeSpec_handlesRangesSinglesAndOpenEndedRanges() {
        // 1-based "1-3, 5, 8-" on a 10-page document => 0-based [0, 1, 2, 4, 7, 8, 9]
        val result = parsePageRangeSpec("1-3, 5, 8-", totalPages = 10)
        assertEquals(listOf(0, 1, 2, 4, 7, 8, 9), result)
    }

    @Test
    fun parsePageRangeSpec_handlesReverseRangeAndClampsBounds() {
        val result = parsePageRangeSpec("4-2, 99, 1", totalPages = 5)
        assertEquals(listOf(3, 2, 1, 0), result)
    }

    @Test
    fun parsePageRangeSpec_returnsEmptyOnInvalidOrBlank() {
        assertEquals(emptyList<Int>(), parsePageRangeSpec("   ", totalPages = 5))
        assertEquals(emptyList<Int>(), parsePageRangeSpec("abc", totalPages = 5))
    }
}
