package com.beremi.cameragyroacccapture.storage

import com.beremi.cameragyroacccapture.session.CameraConfigurationManifest
import com.beremi.cameragyroacccapture.session.DeviceInfoManifest
import com.beremi.cameragyroacccapture.session.FrameRatePreset
import com.beremi.cameragyroacccapture.session.ImuSamplingPreset
import com.beremi.cameragyroacccapture.session.SensorConfigurationManifest
import com.beremi.cameragyroacccapture.session.SessionFilesManifest
import com.beremi.cameragyroacccapture.session.SessionFinalStatus
import com.beremi.cameragyroacccapture.session.SessionManifest
import com.beremi.cameragyroacccapture.session.VideoResolutionPreset
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class SessionManifestSerializationTest {
    @Test
    fun `manifest serialization preserves core capture fields`() {
        val manifest = SessionManifest(
            sessionId = "session_20260327T120000_000Z_deadbeef",
            sessionLabel = "arm-calibration-a",
            status = SessionFinalStatus.COMPLETED,
            startedAtUtc = "2026-03-27T12:00:00Z",
            completedAtUtc = "2026-03-27T12:00:12Z",
            monotonicSessionStartElapsedRealtimeNanos = 1000L,
            sensorRegistrationElapsedRealtimeNanos = 1010L,
            videoStartElapsedRealtimeNanos = 1040L,
            stopRequestedElapsedRealtimeNanos = 1200L,
            imuStopElapsedRealtimeNanos = 1202L,
            videoFinalizeElapsedRealtimeNanos = 1215L,
            camera = CameraConfigurationManifest(
                resolutionPreset = VideoResolutionPreset.FHD,
                targetFramesPerSecond = FrameRatePreset.FPS_30.framesPerSecond,
            ),
            sensors = SensorConfigurationManifest(
                samplingPreset = ImuSamplingPreset.GAME,
                expectedRateHz = ImuSamplingPreset.GAME.expectedRateHz,
            ),
            device = DeviceInfoManifest(
                manufacturer = "Google",
                brand = "Pixel",
                model = "Pixel 8",
                device = "husky",
                product = "husky",
                apiLevel = 34,
                androidRelease = "14",
                appVersionName = "0.1.0",
                appVersionCode = 1,
            ),
            files = SessionFilesManifest(
                videoFileName = "video.mp4",
                imuFileName = "imu.csv",
                manifestFileName = "session.json",
                videoBytes = 128L,
                imuBytes = 256L,
                manifestBytes = 512L,
            ),
            sampleCounts = mapOf("accelerometer" to 120, "gyroscope" to 120),
        )

        val json = Json.encodeToString(SessionManifest.serializer(), manifest)

        assertThat(json).contains("\"sessionId\":\"session_20260327T120000_000Z_deadbeef\"")
        assertThat(json).contains("\"videoFileName\":\"video.mp4\"")
        assertThat(json).contains("\"samplingPreset\":\"GAME\"")
    }
}

