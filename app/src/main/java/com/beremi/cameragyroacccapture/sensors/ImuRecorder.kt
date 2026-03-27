package com.beremi.cameragyroacccapture.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.beremi.cameragyroacccapture.session.ImuSamplingPreset
import com.beremi.cameragyroacccapture.session.SensorConfigurationManifest
import com.beremi.cameragyroacccapture.storage.SessionArtifact
import com.beremi.cameragyroacccapture.storage.SessionStorage
import com.beremi.cameragyroacccapture.util.ClockProvider
import com.beremi.cameragyroacccapture.util.SystemClockProvider
import java.io.BufferedWriter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class ImuStartResult(
    val sensorRegistrationElapsedRealtimeNanos: Long,
    val configuration: SensorConfigurationManifest,
)

data class ImuStopResult(
    val imuStopElapsedRealtimeNanos: Long,
    val sampleCounts: Map<String, Int>,
)

class ImuRecorder(
    context: Context,
    private val clock: ClockProvider = SystemClockProvider,
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sessionStorage = SessionStorage(context, clock)
    private val lock = Any()
    private val sampleCounts = ConcurrentHashMap<String, Int>()

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var writer: BufferedWriter? = null
    private var active = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val sensorName = when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> "accelerometer"
                Sensor.TYPE_GYROSCOPE -> "gyroscope"
                else -> return
            }
            sampleCounts.merge(sensorName, 1, Int::plus)
            val line = String.format(
                Locale.US,
                "%d,%s,%.9f,%.9f,%.9f,%d\n",
                event.timestamp,
                sensorName,
                event.values.getOrElse(0) { 0f }.toDouble(),
                event.values.getOrElse(1) { 0f }.toDouble(),
                event.values.getOrElse(2) { 0f }.toDouble(),
                event.accuracy,
            )
            synchronized(lock) {
                writer?.write(line)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start(outputArtifact: SessionArtifact, preset: ImuSamplingPreset): Result<ImuStartResult> {
        if (active) {
            return Result.failure(IllegalStateException("IMU recorder is already active"))
        }
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return Result.failure(IllegalStateException("Accelerometer is unavailable on this device"))
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            ?: return Result.failure(IllegalStateException("Gyroscope is unavailable on this device"))

        return runCatching {
            sampleCounts.clear()
            writer = sessionStorage.openBufferedWriter(outputArtifact).apply {
                write("elapsed_realtime_nanos,sensor_type,x,y,z,accuracy\n")
                flush()
            }
            handlerThread = HandlerThread("imu-recorder").apply { start() }
            handler = Handler(requireNotNull(handlerThread).looper)
            val registrationTime = clock.elapsedRealtimeNanos()
            sensorManager.registerListener(listener, accelerometer, preset.sensorDelay, handler)
            sensorManager.registerListener(listener, gyroscope, preset.sensorDelay, handler)
            active = true
            ImuStartResult(
                sensorRegistrationElapsedRealtimeNanos = registrationTime,
                configuration = SensorConfigurationManifest(
                    samplingPreset = preset,
                    expectedRateHz = preset.expectedRateHz,
                ),
            )
        }
    }

    fun stop(): ImuStopResult {
        val stoppedAt = clock.elapsedRealtimeNanos()
        if (!active) {
            return ImuStopResult(stoppedAt, emptyMap())
        }
        sensorManager.unregisterListener(listener)
        synchronized(lock) {
            writer?.flush()
            writer?.close()
            writer = null
        }
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        active = false
        return ImuStopResult(stoppedAt, sampleCounts.toMap())
    }

    fun isActive(): Boolean = active
}
