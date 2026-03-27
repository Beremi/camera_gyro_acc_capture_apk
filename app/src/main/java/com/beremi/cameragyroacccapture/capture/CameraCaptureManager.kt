package com.beremi.cameragyroacccapture.capture

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.beremi.cameragyroacccapture.session.CaptureSettings
import java.io.File

class CameraCaptureManager(
    private val context: Context,
) {
    private val mainExecutor = ContextCompat.getMainExecutor(context)

    private var lifecycleOwner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var boundSettings: CaptureSettings? = null

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
                    Camera2Interop.Extender(previewBuilder)
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
                    val recorder = Recorder.Builder()
                        .setQualitySelector(settings.resolutionPreset.toQualitySelector())
                        .build()
                    val videoCapture = VideoCapture.withOutput(recorder)
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycle,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        videoCapture,
                    )
                    this.cameraProvider = provider
                    this.videoCapture = videoCapture
                    this.boundSettings = settings
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
        outputFile: File,
        onEvent: (VideoRecordEvent) -> Unit,
    ): Result<Unit> {
        if (activeRecording != null) {
            return Result.failure(IllegalStateException("A recording is already in progress"))
        }
        val videoCapture = videoCapture
            ?: return Result.failure(IllegalStateException("Camera preview is not ready"))
        outputFile.parentFile?.mkdirs()
        val recording = videoCapture.output
            .prepareRecording(context, FileOutputOptions.Builder(outputFile).build())
            .start(mainExecutor) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    activeRecording = null
                }
                onEvent(event)
            }
        activeRecording = recording
        return Result.success(Unit)
    }

    fun stopRecording() {
        activeRecording?.stop()
    }

    fun release() {
        activeRecording?.close()
        activeRecording = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        videoCapture = null
        boundSettings = null
    }
}
