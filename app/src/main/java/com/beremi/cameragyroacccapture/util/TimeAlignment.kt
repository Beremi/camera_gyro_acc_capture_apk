package com.beremi.cameragyroacccapture.util

object TimeAlignment {
    fun relativeOffsetNanos(
        referenceElapsedRealtimeNanos: Long,
        eventElapsedRealtimeNanos: Long?,
    ): Long? {
        if (eventElapsedRealtimeNanos == null) {
            return null
        }
        return eventElapsedRealtimeNanos - referenceElapsedRealtimeNanos
    }

    fun isMonotonicNonDecreasing(values: List<Long>): Boolean {
        if (values.isEmpty()) {
            return true
        }
        return values.zipWithNext().all { (previous, next) -> next >= previous }
    }
}

