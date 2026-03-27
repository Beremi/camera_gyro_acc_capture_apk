package com.beremi.cameragyroacccapture.capture

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.beremi.cameragyroacccapture.session.CameraTimestampSource
import com.beremi.cameragyroacccapture.session.CaptureSettings
import com.beremi.cameragyroacccapture.storage.PreparedVideoOutput
import java.util.concurrent.Executors

class CameraCaptureManager(
    private val context: Context,
) {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val analyzerExecutor = Executors.newSingleThreadExecutor()

    private var lifecycleOwner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var activeRecording: Recording? = null
    private var activeVideoOutput: PreparedVideoOutput? = null
    private var boundSettings: CaptureSettings? = null
    private var frameListener: ((FrameMetadata) -> Unit)? = null
    private var timestampSource: CameraTimestampSource = CameraTimestampSource.UNAVAILABLE

    @ExperimentalCamera2Interop
    fun attachPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        settings: CaptureSettings,
        onError: (String) -> Unit,
    ) {
        this.lifecycleOwner = lifecycleOwner
        this.previewView = previewView
        if (boundSettings == settings && cameraProvider != null && videoCapture != null) {
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                runCatching {
                    val provider = cameraProviderFuture.get()
                    val lifecycle = requireNotNull(this.lifecycleOwner)
                    val previewTarget = requireNotNull(this.previewView)
                    val previewBuilder = Preview.Builder()
                    val analysisBuilder = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    Camera2Interop.Extender(previewBuilder)
                        .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                            Range(
                                settings.frameRatePreset.framesPerSecond,
                                settings.frameRatePreset.framesPerSecond,
                            ),
                        )
                    Camera2Interop.Extender(analysisBuilder)
                        .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                            Range(
                                settings.frameRatePreset.framesPerSecond,
                                settings.frameRatePreset.framesPerSecond,
                            ),
                        )
                    val preview = previewBuilder.build().apply {
                        setSurfaceProvider(previewTarget.surfaceProvider)
                    }
                    val imageAnalysis = analysisBuilder.build().apply {
                        setAnalyzer(analyzerExecutor) { image ->
                            try {
                                frameListener?.invoke(
                                    FrameMetadata(
                                        frameTimestampNanos = image.imageInfo.timestamp,
                                        width = image.width,
                                        height = image.height,
                                        rotationDegrees = image.imageInfo.rotationDegrees,
                                    ),
                                )
                            } finally {
                                image.close()
                            }
                        }
                    }
                    val recorder = Recorder.Builder()
                        .setQualitySelector(settings.resolutionPreset.toQualitySelector())
                        .build()
                    val videoCapture = VideoCapture.withOutput(recorder)
                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(
                        lifecycle,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                        videoCapture,
                    )
                    this.cameraProvider = provider
                    this.videoCapture = videoCapture
                    this.imageAnalysis = imageAnalysis
                    this.boundSettings = settings
                    this.timestampSource = mapTimestampSource(
                        Camera2CameraInfo.from(camera.cameraInfo)
                            .getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE),
                    )
                }.onFailure { throwable ->
                    onError(
                        throwable.message
                            ?: "Camera preview failed to attach to the lifecycle owner.",
                    )
                }
            },
            mainExecutor,
        )
    }

    fun startRecording(
        preparedVideoOutput: PreparedVideoOutput,
        onEvent: (VideoRecordEvent) -> Unit,
    ): Result<Unit> {
        if (activeRecording != null) {
            preparedVideoOutput.close()
            return Result.failure(IllegalStateException("A recording is already in progress"))
        }
        val videoCapture = videoCapture
            ?: run {
                preparedVideoOutput.close()
                return Result.failure(IllegalStateException("Camera preview is not ready"))
            }
        val pendingRecording: PendingRecording = when {
            preparedVideoOutput.fileOutputOptions != null -> {
                videoCapture.output.prepareRecording(context, preparedVideoOutput.fileOutputOptions)
            }
            preparedVideoOutput.fileDescriptorOutputOptions != null -> {
                videoCapture.output.prepareRecording(
                    context,
                    preparedVideoOutput.fileDescriptorOutputOptions,
                )
            }
            else -> {
                preparedVideoOutput.close()
                return Result.failure(IllegalStateException("No valid video output was prepared"))
            }
        }
        val recording = pendingRecording
            .start(mainExecutor) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    activeVideoOutput?.close()
                    activeVideoOutput = null
                    activeRecording = null
                }
                onEvent(event)
            }
        activeVideoOutput = preparedVideoOutput
        activeRecording = recording
        return Result.success(Unit)
    }

    fun setFrameListener(listener: ((FrameMetadata) -> Unit)?) {
        frameListener = listener
    }

    fun currentTimestampSource(): CameraTimestampSource = timestampSource

    fun stopRecording() {
        activeRecording?.stop()
    }

    fun release() {
        activeRecording?.close()
        activeVideoOutput?.close()
        activeRecording = null
        activeVideoOutput = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        videoCapture = null
        imageAnalysis = null
        boundSettings = null
        frameListener = null
        analyzerExecutor.shutdown()
    }

    private fun mapTimestampSource(source: Int?): CameraTimestampSource {
        return when (source) {
            CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME -> CameraTimestampSource.REALTIME
            CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN -> CameraTimestampSource.UNKNOWN
            else -> CameraTimestampSource.UNAVAILABLE
        }
    }
}
