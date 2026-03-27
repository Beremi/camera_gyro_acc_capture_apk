package com.beremi.cameragyroacccapture.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionStorageInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val createdDirectories = mutableListOf<java.io.File>()

    @After
    fun tearDown() {
        SessionStorage(context, FakeClockProvider()).clearCustomCaptureRoot()
        createdDirectories.forEach { directory ->
            directory.deleteRecursively()
        }
    }

    @Test
    fun createSessionArtifacts_createsExpectedFilesUnderSessionDirectory() {
        val storage = SessionStorage(context, FakeClockProvider())

        val artifacts = storage.createSessionArtifacts()
        createdDirectories += requireNotNull(artifacts.videoArtifact.file?.parentFile)

        assertThat(requireNotNull(artifacts.videoArtifact.file?.parentFile).exists()).isTrue()
        assertThat(artifacts.videoArtifact.fileName).isEqualTo("video.mp4")
        assertThat(artifacts.imuArtifact.fileName).isEqualTo("imu.csv")
        assertThat(artifacts.framesArtifact.fileName).isEqualTo("frames.csv")
        assertThat(artifacts.manifestArtifact.fileName).isEqualTo("session.json")
        assertThat(artifacts.captureRootDetails.isCustom).isFalse()
    }

    private class FakeClockProvider : com.beremi.cameragyroacccapture.util.ClockProvider {
        override fun currentInstant(): Instant = Instant.parse("2026-03-27T12:00:00Z")

        override fun elapsedRealtimeNanos(): Long = 42L
    }
}
