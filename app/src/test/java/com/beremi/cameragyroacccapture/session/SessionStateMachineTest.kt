package com.beremi.cameragyroacccapture.session

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class SessionStateMachineTest {
    @Test
    fun `happy path transitions through prepare record stop complete`() {
        val machine = SessionStateMachine()

        assertThat(machine.prepare("session-1")).isEqualTo(SessionPhase.Preparing("session-1"))
        assertThat(machine.record("session-1", 1234L))
            .isEqualTo(SessionPhase.Recording("session-1", 1234L))
        assertThat(machine.stop("session-1")).isEqualTo(SessionPhase.Stopping("session-1"))

        val summary = CompletedSessionSummary(
            sessionId = "session-1",
            sessionLocationLabel = "/tmp/session-1",
            shareTargets = emptyList(),
            label = "lab-run",
            completedAt = Instant.parse("2026-03-27T12:00:00Z"),
        )
        assertThat(machine.complete(summary)).isEqualTo(SessionPhase.Completed(summary))
    }

    @Test
    fun `fail transitions from any phase`() {
        val machine = SessionStateMachine()
        machine.prepare("session-2")

        assertThat(machine.fail("camera unavailable", "session-2"))
            .isEqualTo(SessionPhase.Failed("camera unavailable", "session-2"))
    }
}
