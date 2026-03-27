package com.beremi.cameragyroacccapture.session

class SessionStateMachine {
    private var phase: SessionPhase = SessionPhase.Idle

    fun current(): SessionPhase = phase

    fun prepare(sessionId: String): SessionPhase.Preparing {
        require(
            phase is SessionPhase.Idle ||
                phase is SessionPhase.Completed ||
                phase is SessionPhase.Failed,
        ) {
            "Cannot prepare from $phase"
        }
        return SessionPhase.Preparing(sessionId).also { phase = it }
    }

    fun record(sessionId: String, videoStartElapsedRealtimeNanos: Long?): SessionPhase.Recording {
        require(phase is SessionPhase.Preparing) { "Cannot record from $phase" }
        return SessionPhase.Recording(sessionId, videoStartElapsedRealtimeNanos).also { phase = it }
    }

    fun stop(sessionId: String): SessionPhase.Stopping {
        require(phase is SessionPhase.Preparing || phase is SessionPhase.Recording) {
            "Cannot stop from $phase"
        }
        return SessionPhase.Stopping(sessionId).also { phase = it }
    }

    fun complete(summary: CompletedSessionSummary): SessionPhase.Completed {
        require(phase is SessionPhase.Stopping || phase is SessionPhase.Recording) {
            "Cannot complete from $phase"
        }
        return SessionPhase.Completed(summary).also { phase = it }
    }

    fun fail(message: String, sessionId: String? = null): SessionPhase.Failed {
        return SessionPhase.Failed(message, sessionId).also { phase = it }
    }

    fun reset() {
        phase = SessionPhase.Idle
    }
}

