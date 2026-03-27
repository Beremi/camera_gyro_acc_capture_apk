package com.beremi.cameragyroacccapture.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.beremi.cameragyroacccapture.session.CompletedSessionSummary
import com.beremi.cameragyroacccapture.session.SessionManifest
import com.beremi.cameragyroacccapture.util.ClockProvider
import com.beremi.cameragyroacccapture.util.SystemClockProvider
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.json.Json

data class SessionArtifacts(
    val sessionId: String,
    val sessionDirectory: File,
    val videoFile: File,
    val imuFile: File,
    val manifestFile: File,
    val startedAtUtc: Instant,
    val monotonicStartElapsedRealtimeNanos: Long,
)

class SessionStorage(
    private val context: Context,
    private val clock: ClockProvider = SystemClockProvider,
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun createSessionArtifacts(): SessionArtifacts {
        val startedAtUtc = clock.currentInstant()
        val monotonicStart = clock.elapsedRealtimeNanos()
        val sessionId = buildSessionId(startedAtUtc)
        val sessionDirectory = sessionRoot().resolve(sessionId)
        check(sessionDirectory.mkdirs() || sessionDirectory.exists()) {
            "Failed to create session directory at ${sessionDirectory.absolutePath}"
        }
        return SessionArtifacts(
            sessionId = sessionId,
            sessionDirectory = sessionDirectory,
            videoFile = sessionDirectory.resolve("video.mp4"),
            imuFile = sessionDirectory.resolve("imu.csv"),
            manifestFile = sessionDirectory.resolve("session.json"),
            startedAtUtc = startedAtUtc,
            monotonicStartElapsedRealtimeNanos = monotonicStart,
        )
    }

    fun writeManifest(manifest: SessionManifest, manifestFile: File) {
        manifestFile.writeText(json.encodeToString(SessionManifest.serializer(), manifest))
    }

    fun buildShareIntent(summary: CompletedSessionSummary): Intent {
        val files = listOf(summary.videoFile, summary.imuFile, summary.manifestFile).filter(File::exists)
        val uris = ArrayList<Uri>(files.size)
        files.forEach { file ->
            uris += FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun sessionRoot(): File {
        val externalDocuments = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val baseDirectory = externalDocuments ?: context.filesDir
        val root = baseDirectory.resolve("capture_sessions")
        if (!root.exists()) {
            root.mkdirs()
        }
        return root
    }

    companion object {
        private val SessionTimeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss_SSS'Z'")
                .withZone(ZoneOffset.UTC)

        fun buildSessionId(instant: Instant): String {
            val suffix = UUID.randomUUID().toString().take(8)
            return "session_${SessionTimeFormatter.format(instant)}_$suffix"
        }
    }
}
