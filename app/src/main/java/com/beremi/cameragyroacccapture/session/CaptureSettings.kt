package com.beremi.cameragyroacccapture.session

import android.hardware.SensorManager
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import kotlinx.serialization.Serializable

@Serializable
enum class VideoResolutionPreset(val label: String) {
    HD("HD 1280x720"),
    FHD("Full HD 1920x1080"),
    UHD("UHD 3840x2160");

    fun toQualitySelector(): QualitySelector {
        val quality = when (this) {
            HD -> Quality.HD
            FHD -> Quality.FHD
            UHD -> Quality.UHD
        }
        return QualitySelector.from(
            quality,
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
        )
    }
}

@Serializable
enum class FrameRatePreset(val label: String, val framesPerSecond: Int) {
    FPS_24("24 fps", 24),
    FPS_30("30 fps", 30),
    FPS_60("60 fps", 60),
}

@Serializable
enum class ImuSamplingPreset(
    val label: String,
    val sensorDelay: Int,
    val expectedRateHz: Int,
) {
    NORMAL("Normal (~5 Hz)", SensorManager.SENSOR_DELAY_NORMAL, 5),
    GAME("Game (~50 Hz)", SensorManager.SENSOR_DELAY_GAME, 50),
    FASTEST("Fastest (~200+ Hz)", SensorManager.SENSOR_DELAY_FASTEST, 200),
}

@Serializable
data class CaptureSettings(
    val resolutionPreset: VideoResolutionPreset = VideoResolutionPreset.FHD,
    val frameRatePreset: FrameRatePreset = FrameRatePreset.FPS_30,
    val imuSamplingPreset: ImuSamplingPreset = ImuSamplingPreset.GAME,
    val sessionLabel: String = "",
)

