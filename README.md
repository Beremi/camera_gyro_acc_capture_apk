# camera_gyro_acc_capture_apk

Android app for synchronized video capture plus accelerometer/gyroscope logging for scientific data collection. The primary use is to support Bayesian calibration workflows for robotic arms and related laboratory rigs.

## Scope

This repository is for research data acquisition, not a consumer camera app and not a robotics control system.

- Experimental status: APIs, file layout, and UI may change without backward compatibility.
- Non-safety-critical: captured data must be independently validated before use in published results or closed-loop systems.
- Capture-only v1: no audio, no live streaming, no onboard analysis, no remote control.
- Scientific emphasis: the repository is mainly intended to produce repeatable multimodal datasets for downstream Bayesian calibration and system-identification work.

## Expected Outputs

Each recording session should produce a per-session directory containing:

- `video.mp4`
- `imu.csv`
- `session.json`

The session manifest is the authoritative description of how to interpret the video and sensor log together.

On-device, sessions are written to the app-specific documents area under `capture_sessions/`, so recordings stay local to the application unless you explicitly export them.

## Repository Map

The initial implementation is expected to follow a single-module Android layout:

- `app/`: Android application code
- `app/src/main/.../capture`: camera recording flow and timing hooks
- `app/src/main/.../sensors`: accelerometer and gyroscope collection
- `app/src/main/.../session`: session orchestration and state machine
- `app/src/main/.../storage`: CSV/JSON writing and export packaging
- `app/src/main/.../ui`: Compose screens for permissions and capture
- `app/src/test/...`: JVM tests for state transitions, timestamps, and manifest serialization
- `app/src/androidTest/...`: Android tests for app launch and session storage wiring
- `docs/`: data-format notes and scientific usage guidance
- `.github/workflows/android.yml`: basic CI for unit tests and debug builds

## Quick Start

1. Open the repository in Android Studio, or run `./gradlew assembleDebug` locally.
2. Install the app on an Android 10+ device.
3. Grant camera permission.
4. Open Settings to choose the video resolution, target FPS, IMU sampling preset, and an optional session label.
5. Start a short capture session and stop it when the calibration motion finishes.
6. Share or inspect the generated session directory containing `video.mp4`, `imu.csv`, and `session.json`.

## Scientific Use Notes

This app is designed to create repeatable multimodal datasets for calibration and system-identification work. For robotic-arm experiments, record the device model, mounting geometry, app version, and any external ground-truth or robot-state logs alongside the session output.

Timing is best-effort on commodity Android hardware. Use the recorded monotonic timestamps and manifest metadata for downstream alignment, and verify sample rates, dropped-frame behavior, and mount repeatability on each device class before relying on the data.

## Reproducibility Guidance

- Keep the device model and mounting configuration fixed within an experiment series.
- Record the exact app version or git commit used for each dataset.
- Validate actual sensor rate and camera behavior on every target device.
- Store calibration metadata and robot state logs externally if they are part of the analysis pipeline.

## Development Notes

The codebase is intended to stay simple at first: one app module, explicit session state, raw data capture, and file-based outputs. Prefer backward-compatible changes to the session manifest and output schema once recordings exist.

Useful local commands:

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- `./gradlew assembleDebugAndroidTest`

## Limits

- No claim of measurement-grade synchronization.
- No claim of production robotics safety.
- No guarantee of compatibility across app versions unless the on-disk format is versioned.
