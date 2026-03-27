package com.beremi.cameragyroacccapture.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeAlignmentTest {
    @Test
    fun `relativeOffsetNanos returns offset from reference`() {
        assertThat(TimeAlignment.relativeOffsetNanos(100L, 145L)).isEqualTo(45L)
    }

    @Test
    fun `isMonotonicNonDecreasing returns true for ordered values`() {
        assertThat(TimeAlignment.isMonotonicNonDecreasing(listOf(1L, 1L, 2L, 8L))).isTrue()
    }

    @Test
    fun `isMonotonicNonDecreasing returns false for regression`() {
        assertThat(TimeAlignment.isMonotonicNonDecreasing(listOf(10L, 12L, 11L))).isFalse()
    }
}

