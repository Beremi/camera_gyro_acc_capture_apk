package com.beremi.cameragyroacccapture.storage

import com.beremi.cameragyroacccapture.session.CameraConfigurationManifest
import com.beremi.cameragyroacccapture.session.CameraTimestampSource
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
            schemaVersion = 2,
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
                timestampSource = CameraTimestampSource.REALTIME,
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
                framesFileName = "frames.csv",
                manifestFileName = "session.json",
                videoBytes = 128L,
                imuBytes = 256L,
                framesBytes = 64L,
                manifestBytes = 512L,
            ),
            sampleCounts = mapOf("accelerometer" to 120, "gyroscope" to 120),
            frameCount = 230,
            captureRootDescription = "/storage/emulated/0/Documents/calibration-captures",
        )

        val json = Json.encodeToString(SessionManifest.serializer(), manifest)

        assertThat(json).contains("\"sessionId\":\"session_20260327T120000_000Z_deadbeef\"")
        assertThat(json).contains("\"videoFileName\":\"video.mp4\"")
        assertThat(json).contains("\"framesFileName\":\"frames.csv\"")
        assertThat(json).contains("\"samplingPreset\":\"GAME\"")
        assertThat(json).contains("\"timestampSource\":\"REALTIME\"")
    }
}
