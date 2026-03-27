package com.beremi.cameragyroacccapture.capture

import com.beremi.cameragyroacccapture.session.CameraTimestampSource
import com.beremi.cameragyroacccapture.storage.SessionArtifact
import com.beremi.cameragyroacccapture.storage.SessionStorage
import java.io.BufferedWriter
import java.util.Locale

data class FrameMetadata(
    val frameTimestampNanos: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
)

data class FrameRecorderStartResult(
    val frameCount: Int = 0,
)

class FrameTimestampRecorder(
    private val sessionStorage: SessionStorage,
) {
    private val lock = Any()
    private var writer: BufferedWriter? = null
    private var frameCount = 0
    private var sessionStartElapsedRealtimeNanos: Long = 0L
    private var cameraTimestampSource: CameraTimestampSource = CameraTimestampSource.UNAVAILABLE

    fun start(
        artifact: SessionArtifact,
        sessionStartElapsedRealtimeNanos: Long,
        cameraTimestampSource: CameraTimestampSource,
    ): FrameRecorderStartResult {
        stop()
        this.sessionStartElapsedRealtimeNanos = sessionStartElapsedRealtimeNanos
        this.cameraTimestampSource = cameraTimestampSource
        this.frameCount = 0
        writer = sessionStorage.openBufferedWriter(artifact).apply {
            write("frame_index,camera_timestamp_nanos,relative_session_nanos,width,height,rotation_degrees\n")
            flush()
        }
        return FrameRecorderStartResult()
    }

    fun onFrame(metadata: FrameMetadata) {
        val writer = writer ?: return
        frameCount += 1
        val relativeSessionNanos = when (cameraTimestampSource) {
            CameraTimestampSource.REALTIME -> metadata.frameTimestampNanos - sessionStartElapsedRealtimeNanos
            CameraTimestampSource.UNKNOWN,
            CameraTimestampSource.UNAVAILABLE,
            -> null
        }
        val line = String.format(
            Locale.US,
            "%d,%d,%s,%d,%d,%d\n",
            frameCount,
            metadata.frameTimestampNanos,
            relativeSessionNanos?.toString() ?: "",
            metadata.width,
            metadata.height,
            metadata.rotationDegrees,
        )
        synchronized(lock) {
            writer.write(line)
        }
    }

    fun stop(): Int {
        synchronized(lock) {
            writer?.flush()
            writer?.close()
            writer = null
        }
        return frameCount
    }
}
