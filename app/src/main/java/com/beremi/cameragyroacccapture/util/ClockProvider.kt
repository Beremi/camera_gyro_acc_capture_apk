package com.beremi.cameragyroacccapture.util

import android.os.SystemClock
import java.time.Instant

interface ClockProvider {
    fun currentInstant(): Instant
    fun elapsedRealtimeNanos(): Long
}

object SystemClockProvider : ClockProvider {
    override fun currentInstant(): Instant = Instant.now()

    override fun elapsedRealtimeNanos(): Long = SystemClock.elapsedRealtimeNanos()
}

