package com.beremi.cameragyroacccapture.storage

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class SessionStorageIdTest {
    @Test
    fun `buildSessionId uses UTC timestamp prefix and random suffix`() {
        val sessionId = SessionStorage.buildSessionId(Instant.parse("2026-03-27T12:05:12.123Z"))

        assertThat(sessionId).startsWith("session_20260327T120512_123Z_")
        assertThat(sessionId.substringAfterLast("_")).hasLength(8)
    }
}

