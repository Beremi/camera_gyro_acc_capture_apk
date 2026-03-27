package com.beremi.cameragyroacccapture.ui

import android.app.Application
import android.content.Intent
import androidx.camera.video.VideoRecordEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.camera.view.PreviewView
import com.beremi.cameragyroacccapture.capture.CameraCaptureManager
import com.beremi.cameragyroacccapture.sensors.ImuRecorder
import com.beremi.cameragyroacccapture.session.CameraConfigurationManifest
import com.beremi.cameragyroacccapture.session.CaptureSettings
import com.beremi.cameragyroacccapture.session.CompletedSessionSummary
import com.beremi.cameragyroacccapture.session.SessionFinalStatus
import com.beremi.cameragyroacccapture.session.SessionFilesManifest
import com.beremi.cameragyroacccapture.session.SessionManifest
import com.beremi.cameragyroacccapture.session.SessionPhase
import com.beremi.cameragyroacccapture.session.SessionStateMachine
import com.beremi.cameragyroacccapture.storage.SessionArtifacts
import com.beremi.cameragyroacccapture.storage.SessionStorage
import com.beremi.cameragyroacccapture.util.ClockProvider
import com.beremi.cameragyroacccapture.util.DeviceInfoProvider
import com.beremi.cameragyroacccapture.util.SystemClockProvider
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CaptureUiState(
    val hasCameraPermission: Boolean = false,
    val settings: CaptureSettings = CaptureSettings(),
    val sessionPhase: SessionPhase = SessionPhase.Idle,
    val isSettingsSheetVisible: Boolean = false,
    val statusMessage: String = "Ready to record a short calibration clip.",
    val errorMessage: String? = null,
    val lastCompletedSession: CompletedSessionSummary? = null,
)

private data class ActiveSession(
    val artifacts: SessionArtifacts,
    val settings: CaptureSettings,
    val sensorRegistrationElapsedRealtimeNanos: Long,
    var videoStartElapsedRealtimeNanos: Long? = null,
    var stopRequestedElapsedRealtimeNanos: Long? = null,
    var imuStopElapsedRealtimeNanos: Long? = null,
    var videoFinalizeElapsedRealtimeNanos: Long? = null,
    var sampleCounts: Map<String, Int> = emptyMap(),
    var failureMessage: String? = null,
)

class CaptureViewModel private constructor(
    application: Application,
    private val clock: ClockProvider,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, SystemClockProvider)

    private val sessionStorage = SessionStorage(application, clock)
    private val stateMachine = SessionStateMachine()
    private val imuRecorder = ImuRecorder(application, clock)
    private val cameraCaptureManager = CameraCaptureManager(application)

    private var activeSession: ActiveSession? = null

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun onCameraPermissionChanged(granted: Boolean) {
        _uiState.update { current ->
            current.copy(
                hasCameraPermission = granted,
                statusMessage = if (granted) {
                    "Ready to record a short calibration clip."
                } else {
                    "Camera access is required to record synchronized video and IMU data."
                },
            )
        }
    }

    fun attachPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ) {
        cameraCaptureManager.attachPreview(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            settings = _uiState.value.settings,
            onError = ::handleFailure,
        )
    }

    fun openSettingsSheet() {
        _uiState.update { it.copy(isSettingsSheetVisible = true) }
    }

    fun closeSettingsSheet() {
        _uiState.update { it.copy(isSettingsSheetVisible = false) }
    }

    fun updateResolutionPreset(resolutionPreset: com.beremi.cameragyroacccapture.session.VideoResolutionPreset) {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(resolutionPreset = resolutionPreset))
        }
    }

    fun updateFrameRatePreset(frameRatePreset: com.beremi.cameragyroacccapture.session.FrameRatePreset) {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(frameRatePreset = frameRatePreset))
        }
    }

    fun updateImuSamplingPreset(imuSamplingPreset: com.beremi.cameragyroacccapture.session.ImuSamplingPreset) {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(imuSamplingPreset = imuSamplingPreset))
        }
    }

    fun updateSessionLabel(sessionLabel: String) {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(sessionLabel = sessionLabel.take(80)))
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun startCapture() {
        if (!_uiState.value.hasCameraPermission) {
            handleFailure("Camera permission is required before a capture can start.")
            return
        }
        if (activeSession != null) {
            return
        }

        viewModelScope.launch {
            val artifacts = runCatching { sessionStorage.createSessionArtifacts() }
                .getOrElse { error ->
                    handleFailure(error.message ?: "Unable to create a session directory.")
                    return@launch
                }
            val settings = _uiState.value.settings
            val preparingPhase = runCatching { stateMachine.prepare(artifacts.sessionId) }
                .getOrElse { error ->
                    handleFailure(error.message ?: "Unable to enter the preparing state.")
                    return@launch
                }
            _uiState.update { current ->
                current.copy(
                    sessionPhase = preparingPhase,
                    statusMessage = "Preparing IMU logging and camera recording.",
                    errorMessage = null,
                    lastCompletedSession = null,
                )
            }

            val imuStart = imuRecorder.start(artifacts.imuFile, settings.imuSamplingPreset)
                .getOrElse { error ->
                    writeFailureManifest(
                        artifacts = artifacts,
                        settings = settings,
                        sensorRegistrationElapsedRealtimeNanos = null,
                        failureMessage = error.message ?: "Failed to start the IMU recorder.",
                    )
                    handleFailure(error.message ?: "Failed to start the IMU recorder.", artifacts.sessionId)
                    return@launch
                }

            activeSession = ActiveSession(
                artifacts = artifacts,
                settings = settings,
                sensorRegistrationElapsedRealtimeNanos = imuStart.sensorRegistrationElapsedRealtimeNanos,
            )

            cameraCaptureManager.startRecording(artifacts.videoFile, ::handleVideoRecordEvent)
                .onFailure { error ->
                    val imuStop = imuRecorder.stop()
                    activeSession = activeSession?.copy(
                        imuStopElapsedRealtimeNanos = imuStop.imuStopElapsedRealtimeNanos,
                        sampleCounts = imuStop.sampleCounts,
                    )
                    writeFailureManifest(
                        artifacts = artifacts,
                        settings = settings,
                        sensorRegistrationElapsedRealtimeNanos = imuStart.sensorRegistrationElapsedRealtimeNanos,
                        failureMessage = error.message ?: "Failed to start video capture.",
                    )
                    activeSession = null
                    handleFailure(error.message ?: "Failed to start video capture.", artifacts.sessionId)
                }
        }
    }

    fun stopCapture(reason: String? = null) {
        val session = activeSession ?: return
        val stoppingPhase = runCatching { stateMachine.stop(session.artifacts.sessionId) }
            .getOrElse { error ->
                handleFailure(error.message ?: "Unable to enter the stopping state.", session.artifacts.sessionId)
                return
            }
        val stopRequestedAt = clock.elapsedRealtimeNanos()
        val imuStop = imuRecorder.stop()
        activeSession = session.copy(
            stopRequestedElapsedRealtimeNanos = stopRequestedAt,
            imuStopElapsedRealtimeNanos = imuStop.imuStopElapsedRealtimeNanos,
            sampleCounts = imuStop.sampleCounts,
            failureMessage = reason,
        )
        _uiState.update { current ->
            current.copy(
                sessionPhase = stoppingPhase,
                statusMessage = "Finalizing video and writing the session manifest.",
                errorMessage = null,
            )
        }
        cameraCaptureManager.stopRecording()
    }

    fun onHostStopped() {
        val phase = _uiState.value.sessionPhase
        if (phase is SessionPhase.Preparing || phase is SessionPhase.Recording) {
            stopCapture("Capture stopped because the app moved to the background.")
        }
    }

    fun resetToIdle() {
        stateMachine.reset()
        _uiState.update {
            it.copy(
                sessionPhase = SessionPhase.Idle,
                statusMessage = "Ready to record a short calibration clip.",
                errorMessage = null,
            )
        }
    }

    fun buildShareIntent(): Intent? {
        val summary = _uiState.value.lastCompletedSession ?: return null
        return sessionStorage.buildShareIntent(summary)
    }

    override fun onCleared() {
        if (imuRecorder.isActive()) {
            imuRecorder.stop()
        }
        cameraCaptureManager.release()
        super.onCleared()
    }

    private fun handleVideoRecordEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                val session = activeSession ?: return
                val startedAt = clock.elapsedRealtimeNanos()
                activeSession = session.copy(videoStartElapsedRealtimeNanos = startedAt)
                if (stateMachine.current() is SessionPhase.Stopping) {
                    return
                }
                val recordingPhase = runCatching {
                    stateMachine.record(session.artifacts.sessionId, startedAt)
                }.getOrElse { error ->
                    handleFailure(error.message ?: "Unable to enter the recording state.", session.artifacts.sessionId)
                    return
                }
                _uiState.update { current ->
                    current.copy(
                        sessionPhase = recordingPhase,
                        statusMessage = "Recording video and IMU samples on a shared monotonic timeline.",
                        errorMessage = null,
                    )
                }
            }

            is VideoRecordEvent.Finalize -> finalizeCapture(event)
            else -> Unit
        }
    }

    private fun finalizeCapture(event: VideoRecordEvent.Finalize) {
        val session = activeSession ?: return
        if (imuRecorder.isActive()) {
            val imuStop = imuRecorder.stop()
            activeSession = session.copy(
                imuStopElapsedRealtimeNanos = imuStop.imuStopElapsedRealtimeNanos,
                sampleCounts = imuStop.sampleCounts,
            )
        }
        val latestSession = requireNotNull(activeSession).copy(
            videoFinalizeElapsedRealtimeNanos = clock.elapsedRealtimeNanos(),
            failureMessage = when {
                event.hasError() -> event.cause?.message ?: "CameraX reported a finalize error."
                else -> activeSession?.failureMessage
            },
        )
        activeSession = latestSession

        viewModelScope.launch {
            val manifestStatus = if (event.hasError()) SessionFinalStatus.FAILED else SessionFinalStatus.COMPLETED
            val manifest = buildManifest(latestSession, manifestStatus)
            withContext(Dispatchers.IO) {
                sessionStorage.writeManifest(manifest, latestSession.artifacts.manifestFile)
            }
            if (manifestStatus == SessionFinalStatus.COMPLETED) {
                val summary = CompletedSessionSummary(
                    sessionId = latestSession.artifacts.sessionId,
                    sessionDirectory = latestSession.artifacts.sessionDirectory,
                    videoFile = latestSession.artifacts.videoFile,
                    imuFile = latestSession.artifacts.imuFile,
                    manifestFile = latestSession.artifacts.manifestFile,
                    label = latestSession.settings.sessionLabel.ifBlank { null },
                    completedAt = clock.currentInstant(),
                )
                val completedPhase = runCatching { stateMachine.complete(summary) }
                    .getOrElse { error ->
                        handleFailure(
                            error.message ?: "Unable to complete the session state machine.",
                            latestSession.artifacts.sessionId,
                        )
                        activeSession = null
                        return@launch
                    }
                _uiState.update { current ->
                    current.copy(
                        sessionPhase = completedPhase,
                        statusMessage = "Session ready for export. Files were stored locally as MP4, CSV, and JSON.",
                        errorMessage = null,
                        lastCompletedSession = summary,
                    )
                }
            } else {
                handleFailure(
                    latestSession.failureMessage ?: "Recording finalized with an error.",
                    latestSession.artifacts.sessionId,
                )
            }
            activeSession = null
        }
    }

    private fun writeFailureManifest(
        artifacts: SessionArtifacts,
        settings: CaptureSettings,
        sensorRegistrationElapsedRealtimeNanos: Long?,
        failureMessage: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val manifest = SessionManifest(
                sessionId = artifacts.sessionId,
                sessionLabel = settings.sessionLabel.ifBlank { null },
                status = SessionFinalStatus.FAILED,
                startedAtUtc = artifacts.startedAtUtc.toString(),
                completedAtUtc = clock.currentInstant().toString(),
                monotonicSessionStartElapsedRealtimeNanos = artifacts.monotonicStartElapsedRealtimeNanos,
                sensorRegistrationElapsedRealtimeNanos = sensorRegistrationElapsedRealtimeNanos,
                failureMessage = failureMessage,
                camera = CameraConfigurationManifest(
                    resolutionPreset = settings.resolutionPreset,
                    targetFramesPerSecond = settings.frameRatePreset.framesPerSecond,
                ),
                sensors = com.beremi.cameragyroacccapture.session.SensorConfigurationManifest(
                    samplingPreset = settings.imuSamplingPreset,
                    expectedRateHz = settings.imuSamplingPreset.expectedRateHz,
                ),
                device = DeviceInfoProvider.capture(getApplication()),
                files = SessionFilesManifest(
                    videoFileName = artifacts.videoFile.name,
                    imuFileName = artifacts.imuFile.name,
                    manifestFileName = artifacts.manifestFile.name,
                    videoBytes = artifacts.videoFile.takeIf { it.exists() }?.length(),
                    imuBytes = artifacts.imuFile.takeIf { it.exists() }?.length(),
                    manifestBytes = null,
                ),
                notes = buildNotes(settings),
            )
            sessionStorage.writeManifest(manifest, artifacts.manifestFile)
        }
    }

    private suspend fun buildManifest(
        session: ActiveSession,
        status: SessionFinalStatus,
    ): SessionManifest = withContext(Dispatchers.IO) {
        SessionManifest(
            sessionId = session.artifacts.sessionId,
            sessionLabel = session.settings.sessionLabel.ifBlank { null },
            status = status,
            startedAtUtc = session.artifacts.startedAtUtc.toString(),
            completedAtUtc = clock.currentInstant().toString(),
            monotonicSessionStartElapsedRealtimeNanos = session.artifacts.monotonicStartElapsedRealtimeNanos,
            sensorRegistrationElapsedRealtimeNanos = session.sensorRegistrationElapsedRealtimeNanos,
            videoStartElapsedRealtimeNanos = session.videoStartElapsedRealtimeNanos,
            stopRequestedElapsedRealtimeNanos = session.stopRequestedElapsedRealtimeNanos,
            imuStopElapsedRealtimeNanos = session.imuStopElapsedRealtimeNanos,
            videoFinalizeElapsedRealtimeNanos = session.videoFinalizeElapsedRealtimeNanos,
            failureMessage = session.failureMessage,
            camera = CameraConfigurationManifest(
                resolutionPreset = session.settings.resolutionPreset,
                targetFramesPerSecond = session.settings.frameRatePreset.framesPerSecond,
            ),
            sensors = com.beremi.cameragyroacccapture.session.SensorConfigurationManifest(
                samplingPreset = session.settings.imuSamplingPreset,
                expectedRateHz = session.settings.imuSamplingPreset.expectedRateHz,
            ),
            device = DeviceInfoProvider.capture(getApplication()),
            files = SessionFilesManifest(
                videoFileName = session.artifacts.videoFile.name,
                imuFileName = session.artifacts.imuFile.name,
                manifestFileName = session.artifacts.manifestFile.name,
                videoBytes = session.artifacts.videoFile.takeIf { it.exists() }?.length(),
                imuBytes = session.artifacts.imuFile.takeIf { it.exists() }?.length(),
                manifestBytes = session.artifacts.manifestFile.takeIf { it.exists() }?.length(),
            ),
            sampleCounts = session.sampleCounts,
            notes = buildNotes(session.settings),
        )
    }

    private fun buildNotes(settings: CaptureSettings): List<String> = buildList {
        add("Best-effort synchronization using monotonic timestamps from SystemClock.elapsedRealtimeNanos and SensorEvent.timestamp.")
        add("This prototype records raw accelerometer and gyroscope samples without filtering or fusion.")
        if (settings.sessionLabel.isNotBlank()) {
            add("Operator label: ${settings.sessionLabel.trim()}")
        }
    }

    private fun handleFailure(
        message: String,
        sessionId: String? = null,
    ) {
        val failedPhase = stateMachine.fail(message, sessionId)
        _uiState.update { current ->
            current.copy(
                sessionPhase = failedPhase,
                statusMessage = "Capture failed. Review the error details and try again.",
                errorMessage = message,
            )
        }
    }
}
