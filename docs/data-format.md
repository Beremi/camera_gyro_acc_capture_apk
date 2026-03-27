# Data Format v1

This document defines the initial session package produced by the app. The package is file-based and intended for direct inspection and downstream scientific processing.

## Session Directory

Each recording should create one directory per session, for example:

```text
capture_sessions/session_20260327T140512_123Z_deadbeef/
  video.mp4
  imu.csv
  session.json
```

The exact random suffix is an implementation detail. The three files below are the v1 contract.

## `video.mp4`

- Contains the recorded camera video.
- Audio is not part of v1.
- The video file is the visual record for the session; do not infer sensor timing from container metadata alone.

## `imu.csv`

Raw inertial samples from the device sensors.

### Required columns

```text
elapsed_realtime_nanos,sensor_type,x,y,z,accuracy
```

### Column semantics

- `elapsed_realtime_nanos`: sample timestamp on the device monotonic clock, aligned to Android `elapsedRealtimeNanos()` semantics.
- `sensor_type`: the source sensor, expected to be accelerometer or gyroscope in v1.
- `x`, `y`, `z`: raw sensor values in the sensor's native units.
- `accuracy`: Android sensor accuracy value captured with the event.

### Notes

- Keep samples in acquisition order.
- Do not filter, smooth, or fuse values in v1.
- Prefer writing one row per sensor event.

## `session.json`

The session manifest is the authoritative metadata record for the directory.

### Required fields

- session identifier
- UTC start time
- monotonic start time
- sensor registration time
- video start callback time
- stop time
- selected camera settings
- selected sensor configuration
- device model
- Android version
- app version
- file names
- status and failure flags

### Purpose

The manifest exists so that downstream analysis can reconstruct the capture timeline and interpret the raw files without guessing device state or app settings.

## Timestamp Semantics

v1 uses the device monotonic timeline as the primary reference for sensor data and session alignment.

- Prefer monotonic timestamps over wall-clock time for ordering and alignment.
- Use UTC wall-clock timestamps only for human-readable logging and dataset bookkeeping.
- Record explicit offsets between:
  - session start
  - sensor subscription start
  - video recording start callback
  - stop request
  - IMU stop
  - video finalize callback

The system should be treated as best-effort synchronized acquisition, not hard real-time capture.

## Compatibility Notes

- Preserve raw values and timestamps in v1.
- If the format changes later, version the manifest and document the migration path.
- Downstream Bayesian calibration code should read the session manifest first, then load the paired `video.mp4` and `imu.csv`.
