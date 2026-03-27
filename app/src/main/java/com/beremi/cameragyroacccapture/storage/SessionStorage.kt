package com.beremi.cameragyroacccapture.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.OutputOptions
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.beremi.cameragyroacccapture.session.CompletedSessionSummary
import com.beremi.cameragyroacccapture.session.SessionManifest
import com.beremi.cameragyroacccapture.util.ClockProvider
import com.beremi.cameragyroacccapture.util.SystemClockProvider
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.json.Json

data class CaptureRootDetails(
    val label: String,
    val description: String,
    val isCustom: Boolean,
    val treeUri: Uri? = null,
)

data class SessionArtifact(
    val fileName: String,
    val mimeType: String,
    val file: File? = null,
    val uri: Uri? = null,
) {
    fun displayLocation(): String = file?.absolutePath ?: uri?.toString().orEmpty()
}

data class SessionArtifacts(
    val sessionId: String,
    val sessionLocationLabel: String,
    val captureRootDetails: CaptureRootDetails,
    val videoArtifact: SessionArtifact,
    val imuArtifact: SessionArtifact,
    val framesArtifact: SessionArtifact,
    val manifestArtifact: SessionArtifact,
    val startedAtUtc: Instant,
    val monotonicStartElapsedRealtimeNanos: Long,
)

data class PreparedVideoOutput(
    val fileOutputOptions: FileOutputOptions? = null,
    val fileDescriptorOutputOptions: FileDescriptorOutputOptions? = null,
    private val closeable: Closeable? = null,
) : Closeable {
    override fun close() {
        closeable?.close()
    }
}

class SessionStorage(
    private val context: Context,
    private val clock: ClockProvider = SystemClockProvider,
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun currentCaptureRootDetails(): CaptureRootDetails {
        val customRootUri = customCaptureRootUri()
        if (customRootUri != null) {
            val documentRoot = DocumentFile.fromTreeUri(context, customRootUri)
            if (documentRoot != null && documentRoot.canWrite()) {
                val label = documentRoot.name ?: "Selected folder"
                return CaptureRootDetails(
                    label = label,
                    description = documentRoot.uri.toString(),
                    isCustom = true,
                    treeUri = customRootUri,
                )
            }
            clearCustomCaptureRoot()
        }

        val defaultRoot = defaultSessionRoot()
        return CaptureRootDetails(
            label = "App documents",
            description = defaultRoot.absolutePath,
            isCustom = false,
        )
    }

    fun persistCustomCaptureRoot(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(treeUri, flags)
        preferences()
            .edit()
            .putString(KEY_CUSTOM_CAPTURE_ROOT_URI, treeUri.toString())
            .apply()
    }

    fun clearCustomCaptureRoot() {
        val treeUri = customCaptureRootUri()
        if (treeUri != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        preferences().edit().remove(KEY_CUSTOM_CAPTURE_ROOT_URI).apply()
    }

    fun createSessionArtifacts(): SessionArtifacts {
        val startedAtUtc = clock.currentInstant()
        val monotonicStart = clock.elapsedRealtimeNanos()
        val sessionId = buildSessionId(startedAtUtc)
        val rootDetails = currentCaptureRootDetails()

        return if (rootDetails.isCustom) {
            createCustomRootArtifacts(
                rootDetails = rootDetails,
                sessionId = sessionId,
                startedAtUtc = startedAtUtc,
                monotonicStart = monotonicStart,
            )
        } else {
            createDefaultArtifacts(
                rootDetails = rootDetails,
                sessionId = sessionId,
                startedAtUtc = startedAtUtc,
                monotonicStart = monotonicStart,
            )
        }
    }

    fun prepareVideoOutput(artifact: SessionArtifact): PreparedVideoOutput {
        artifact.file?.let { file ->
            file.parentFile?.mkdirs()
            return PreparedVideoOutput(
                fileOutputOptions = FileOutputOptions.Builder(file).build(),
            )
        }
        val uri = requireNotNull(artifact.uri) { "Video output requires a file or a URI" }
        val descriptor = requireNotNull(
            context.contentResolver.openFileDescriptor(uri, "rw"),
        ) {
            "Unable to open the selected output file for recording."
        }
        return PreparedVideoOutput(
            fileDescriptorOutputOptions = FileDescriptorOutputOptions.Builder(descriptor).build(),
            closeable = descriptor,
        )
    }

    fun openBufferedWriter(artifact: SessionArtifact): BufferedWriter {
        artifact.file?.let { file ->
            file.parentFile?.mkdirs()
            return file.outputStream().bufferedWriter()
        }
        val uri = requireNotNull(artifact.uri) { "Text output requires a file or a URI" }
        val outputStream = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
            "Unable to open the selected output document."
        }
        return outputStream.bufferedWriter()
    }

    fun writeManifest(manifest: SessionManifest, manifestArtifact: SessionArtifact) {
        openBufferedWriter(manifestArtifact).use { writer ->
            writer.write(json.encodeToString(SessionManifest.serializer(), manifest))
            writer.flush()
        }
    }

    fun queryLength(artifact: SessionArtifact): Long? {
        artifact.file?.let { file ->
            return if (file.exists()) file.length() else null
        }
        val uri = artifact.uri ?: return null
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (columnIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(columnIndex)) {
                return cursor.getLong(columnIndex)
            }
        }
        return null
    }

    fun buildShareIntent(summary: CompletedSessionSummary): Intent {
        val uris = ArrayList<Uri>(summary.shareTargets.size)
        summary.shareTargets.forEach { artifact ->
            val shareUri = when {
                artifact.file != null -> FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    artifact.file,
                )
                artifact.uri != null -> artifact.uri
                else -> error("Share target must have a file or URI")
            }
            uris += shareUri
        }
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun createDefaultArtifacts(
        rootDetails: CaptureRootDetails,
        sessionId: String,
        startedAtUtc: Instant,
        monotonicStart: Long,
    ): SessionArtifacts {
        val sessionDirectory = defaultSessionRoot().resolve(sessionId)
        check(sessionDirectory.mkdirs() || sessionDirectory.exists()) {
            "Failed to create session directory at ${sessionDirectory.absolutePath}"
        }
        return SessionArtifacts(
            sessionId = sessionId,
            sessionLocationLabel = sessionDirectory.absolutePath,
            captureRootDetails = rootDetails,
            videoArtifact = SessionArtifact(
                fileName = "video.mp4",
                mimeType = "video/mp4",
                file = sessionDirectory.resolve("video.mp4"),
            ),
            imuArtifact = SessionArtifact(
                fileName = "imu.csv",
                mimeType = "text/csv",
                file = sessionDirectory.resolve("imu.csv"),
            ),
            framesArtifact = SessionArtifact(
                fileName = "frames.csv",
                mimeType = "text/csv",
                file = sessionDirectory.resolve("frames.csv"),
            ),
            manifestArtifact = SessionArtifact(
                fileName = "session.json",
                mimeType = "application/json",
                file = sessionDirectory.resolve("session.json"),
            ),
            startedAtUtc = startedAtUtc,
            monotonicStartElapsedRealtimeNanos = monotonicStart,
        )
    }

    private fun createCustomRootArtifacts(
        rootDetails: CaptureRootDetails,
        sessionId: String,
        startedAtUtc: Instant,
        monotonicStart: Long,
    ): SessionArtifacts {
        val rootUri = requireNotNull(rootDetails.treeUri)
        val documentRoot = requireNotNull(DocumentFile.fromTreeUri(context, rootUri)) {
            "Selected capture root is no longer available."
        }
        val sessionDirectory = requireNotNull(documentRoot.createDirectory(sessionId)) {
            "Failed to create a session directory in the selected capture root."
        }
        return SessionArtifacts(
            sessionId = sessionId,
            sessionLocationLabel = "${rootDetails.label}/$sessionId",
            captureRootDetails = rootDetails,
            videoArtifact = SessionArtifact(
                fileName = "video.mp4",
                mimeType = "video/mp4",
                uri = requireNotNull(sessionDirectory.createFile("video/mp4", "video.mp4")?.uri) {
                    "Failed to create video output in the selected capture root."
                },
            ),
            imuArtifact = SessionArtifact(
                fileName = "imu.csv",
                mimeType = "text/csv",
                uri = requireNotNull(sessionDirectory.createFile("text/csv", "imu.csv")?.uri) {
                    "Failed to create IMU output in the selected capture root."
                },
            ),
            framesArtifact = SessionArtifact(
                fileName = "frames.csv",
                mimeType = "text/csv",
                uri = requireNotNull(sessionDirectory.createFile("text/csv", "frames.csv")?.uri) {
                    "Failed to create frame output in the selected capture root."
                },
            ),
            manifestArtifact = SessionArtifact(
                fileName = "session.json",
                mimeType = "application/json",
                uri = requireNotNull(sessionDirectory.createFile("application/json", "session.json")?.uri) {
                    "Failed to create manifest output in the selected capture root."
                },
            ),
            startedAtUtc = startedAtUtc,
            monotonicStartElapsedRealtimeNanos = monotonicStart,
        )
    }

    private fun defaultSessionRoot(): File {
        val externalDocuments = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val baseDirectory = externalDocuments ?: context.filesDir
        val root = baseDirectory.resolve("capture_sessions")
        if (!root.exists()) {
            root.mkdirs()
        }
        return root
    }

    private fun preferences() = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun customCaptureRootUri(): Uri? {
        return preferences()
            .getString(KEY_CUSTOM_CAPTURE_ROOT_URI, null)
            ?.let(Uri::parse)
    }

    companion object {
        private const val PREFERENCES_NAME = "capture_storage"
        private const val KEY_CUSTOM_CAPTURE_ROOT_URI = "custom_capture_root_uri"

        private val SessionTimeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss_SSS'Z'")
                .withZone(ZoneOffset.UTC)

        fun buildSessionId(instant: Instant): String {
            val suffix = UUID.randomUUID().toString().take(8)
            return "session_${SessionTimeFormatter.format(instant)}_$suffix"
        }
    }
}
