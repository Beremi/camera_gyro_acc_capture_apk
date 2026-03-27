package com.beremi.cameragyroacccapture.session

import com.beremi.cameragyroacccapture.storage.SessionArtifact
import java.time.Instant
import kotlinx.serialization.Serializable

sealed interface SessionPhase {
    data object Idle : SessionPhase

    data class Preparing(val sessionId: String) : SessionPhase

    data class Recording(
        val sessionId: String,
        val videoStartedAtElapsedRealtimeNanos: Long?,
    ) : SessionPhase

    data class Stopping(val sessionId: String) : SessionPhase

    data class Completed(val summary: CompletedSessionSummary) : SessionPhase

    data class Failed(
        val message: String,
        val sessionId: String?,
    ) : SessionPhase
}

data class CompletedSessionSummary(
    val sessionId: String,
    val sessionLocationLabel: String,
    val shareTargets: List<SessionArtifact>,
    val label: String?,
    val completedAt: Instant,
)

@Serializable
enum class SessionFinalStatus {
    COMPLETED,
    FAILED,
}

@Serializable
enum class CameraTimestampSource {
    REALTIME,
    UNKNOWN,
    UNAVAILABLE,
}

@Serializable
data class CameraConfigurationManifest(
    val resolutionPreset: VideoResolutionPreset,
    val targetFramesPerSecond: Int,
    val audioEnabled: Boolean = false,
    val timestampSource: CameraTimestampSource = CameraTimestampSource.UNAVAILABLE,
)

@Serializable
data class SensorConfigurationManifest(
    val accelerometerEnabled: Boolean = true,
    val gyroscopeEnabled: Boolean = true,
    val samplingPreset: ImuSamplingPreset,
    val expectedRateHz: Int,
)

@Serializable
data class DeviceInfoManifest(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val apiLevel: Int,
    val androidRelease: String,
    val appVersionName: String,
    val appVersionCode: Long,
)

@Serializable
data class SessionFilesManifest(
    val videoFileName: String,
    val imuFileName: String,
    val framesFileName: String,
    val manifestFileName: String,
    val videoBytes: Long? = null,
    val imuBytes: Long? = null,
    val framesBytes: Long? = null,
    val manifestBytes: Long? = null,
)

@Serializable
data class SessionManifest(
    val schemaVersion: Int = 1,
    val sessionId: String,
    val sessionLabel: String? = null,
    val status: SessionFinalStatus,
    val startedAtUtc: String,
    val completedAtUtc: String,
    val monotonicSessionStartElapsedRealtimeNanos: Long,
    val sensorRegistrationElapsedRealtimeNanos: Long? = null,
    val videoStartElapsedRealtimeNanos: Long? = null,
    val stopRequestedElapsedRealtimeNanos: Long? = null,
    val imuStopElapsedRealtimeNanos: Long? = null,
    val videoFinalizeElapsedRealtimeNanos: Long? = null,
    val failureMessage: String? = null,
    val camera: CameraConfigurationManifest,
    val sensors: SensorConfigurationManifest,
    val device: DeviceInfoManifest,
    val files: SessionFilesManifest,
    val sampleCounts: Map<String, Int> = emptyMap(),
    val frameCount: Int = 0,
    val captureRootDescription: String? = null,
    val notes: List<String> = emptyList(),
)
