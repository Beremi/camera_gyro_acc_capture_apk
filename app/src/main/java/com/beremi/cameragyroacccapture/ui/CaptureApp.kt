package com.beremi.cameragyroacccapture.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beremi.cameragyroacccapture.session.CaptureSettings
import com.beremi.cameragyroacccapture.session.FrameRatePreset
import com.beremi.cameragyroacccapture.session.ImuSamplingPreset
import com.beremi.cameragyroacccapture.session.SessionPhase
import com.beremi.cameragyroacccapture.session.VideoResolutionPreset
import com.beremi.cameragyroacccapture.ui.theme.CameraGyroAccCaptureTheme

@Composable
fun CaptureApp(
    viewModel: CaptureViewModel = viewModel(),
) {
    CameraGyroAccCaptureTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            CaptureRoute(viewModel = viewModel)
        }
    }
}

@Composable
private fun CaptureRoute(viewModel: CaptureViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onCameraPermissionChanged,
    )

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onCameraPermissionChanged(granted)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onHostStopped()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    CaptureScreen(
        uiState = uiState,
        onRequestCameraPermission = {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onAttachPreview = { previewView ->
            viewModel.attachPreview(lifecycleOwner, previewView)
        },
        onOpenSettings = viewModel::openSettingsSheet,
        onDismissSettings = viewModel::closeSettingsSheet,
        onResolutionSelected = viewModel::updateResolutionPreset,
        onFrameRateSelected = viewModel::updateFrameRatePreset,
        onImuSamplingSelected = viewModel::updateImuSamplingPreset,
        onSessionLabelChanged = viewModel::updateSessionLabel,
        onStartCapture = viewModel::startCapture,
        onStopCapture = { viewModel.stopCapture() },
        onShareLatest = {
            viewModel.buildShareIntent()?.let { intent ->
                context.startActivity(Intent.createChooser(intent, "Share session files"))
            }
        },
        onResetState = viewModel::resetToIdle,
        onDismissError = viewModel::clearError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureScreen(
    uiState: CaptureUiState,
    onRequestCameraPermission: () -> Unit,
    onAttachPreview: (PreviewView) -> Unit,
    onOpenSettings: () -> Unit,
    onDismissSettings: () -> Unit,
    onResolutionSelected: (VideoResolutionPreset) -> Unit,
    onFrameRateSelected: (FrameRatePreset) -> Unit,
    onImuSamplingSelected: (ImuSamplingPreset) -> Unit,
    onSessionLabelChanged: (String) -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onShareLatest: () -> Unit,
    onResetState: () -> Unit,
    onDismissError: () -> Unit,
) {
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isRecording = uiState.sessionPhase is SessionPhase.Recording || uiState.sessionPhase is SessionPhase.Preparing
    val isStopping = uiState.sessionPhase is SessionPhase.Stopping

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    top = topPadding + 12.dp,
                    end = 20.dp,
                    bottom = bottomPadding + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderCard()
            PreviewCard(onAttachPreview = onAttachPreview)
            StatusCard(
                statusMessage = uiState.statusMessage,
                sessionPhase = uiState.sessionPhase,
                errorMessage = uiState.errorMessage,
                onDismissError = onDismissError,
            )
            ActionRow(
                hasCameraPermission = uiState.hasCameraPermission,
                isRecording = isRecording,
                isStopping = isStopping,
                onRequestCameraPermission = onRequestCameraPermission,
                onOpenSettings = onOpenSettings,
                onStartCapture = onStartCapture,
                onStopCapture = onStopCapture,
            )
            LastSessionCard(
                summary = uiState.lastCompletedSession,
                onShareLatest = onShareLatest,
                onResetState = onResetState,
            )
        }

        if (uiState.isSettingsSheetVisible) {
            ModalBottomSheet(onDismissRequest = onDismissSettings) {
                SettingsSheet(
                    settings = uiState.settings,
                    onResolutionSelected = onResolutionSelected,
                    onFrameRateSelected = onFrameRateSelected,
                    onImuSamplingSelected = onImuSamplingSelected,
                    onSessionLabelChanged = onSessionLabelChanged,
                    onDismiss = onDismissSettings,
                )
            }
        }
    }
}

@Composable
private fun HeaderCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Scientific Capture Prototype",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Collect synchronized video, accelerometer, and gyroscope observations for research workflows aimed at Bayesian calibration of robotic arms.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.92f),
            )
            Text(
                text = "The app records observations only. It is not a validated production measurement instrument or robotics-control system.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun PreviewCard(
    onAttachPreview: (PreviewView) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Preview",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface),
                factory = { context ->
                    PreviewView(context).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        onAttachPreview(this)
                    }
                },
                update = onAttachPreview,
            )
        }
    }
}

@Composable
private fun StatusCard(
    statusMessage: String,
    sessionPhase: SessionPhase,
    errorMessage: String?,
    onDismissError: () -> Unit,
) {
    val phaseLabel = when (sessionPhase) {
        SessionPhase.Idle -> "Idle"
        is SessionPhase.Preparing -> "Preparing ${sessionPhase.sessionId}"
        is SessionPhase.Recording -> "Recording ${sessionPhase.sessionId}"
        is SessionPhase.Stopping -> "Stopping ${sessionPhase.sessionId}"
        is SessionPhase.Completed -> "Completed ${sessionPhase.summary.sessionId}"
        is SessionPhase.Failed -> "Failed ${sessionPhase.sessionId ?: ""}".trim()
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = phaseLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyLarge,
            )
            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onDismissError, contentPadding = PaddingValues(0.dp)) {
                    Text("Dismiss error")
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    hasCameraPermission: Boolean,
    isRecording: Boolean,
    isStopping: Boolean,
    onRequestCameraPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Operator actions",
                style = MaterialTheme.typography.titleLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    enabled = !isRecording && !isStopping,
                ) {
                    Text("Settings")
                }
                if (!hasCameraPermission) {
                    Button(onClick = onRequestCameraPermission) {
                        Text("Grant camera access")
                    }
                } else if (isRecording) {
                    Button(onClick = onStopCapture) {
                        Text("Stop capture")
                    }
                } else {
                    Button(
                        onClick = onStartCapture,
                        enabled = !isStopping,
                    ) {
                        Text("Start capture")
                    }
                }
            }
            Text(
                text = "Video is stored without audio. IMU capture is raw and uses the device monotonic timeline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LastSessionCard(
    summary: com.beremi.cameragyroacccapture.session.CompletedSessionSummary?,
    onShareLatest: () -> Unit,
    onResetState: () -> Unit,
) {
    if (summary == null) {
        return
    }
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Latest session ready",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = summary.sessionDirectory.absolutePath,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.84f),
            )
            summary.label?.let { label ->
                Text(
                    text = "Label: $label",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.92f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onShareLatest) {
                    Text("Share files")
                }
                OutlinedButton(onClick = onResetState) {
                    Text("Reset status")
                }
            }
        }
    }
}

@Composable
private fun SettingsSheet(
    settings: CaptureSettings,
    onResolutionSelected: (VideoResolutionPreset) -> Unit,
    onFrameRateSelected: (FrameRatePreset) -> Unit,
    onImuSamplingSelected: (ImuSamplingPreset) -> Unit,
    onSessionLabelChanged: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "Capture settings",
            style = MaterialTheme.typography.headlineSmall,
        )
        ChipGroup(
            title = "Video resolution",
            selected = settings.resolutionPreset,
            options = VideoResolutionPreset.entries.toList(),
            optionLabel = { it.label },
            onSelected = onResolutionSelected,
        )
        ChipGroup(
            title = "Target frame rate",
            selected = settings.frameRatePreset,
            options = FrameRatePreset.entries.toList(),
            optionLabel = { it.label },
            onSelected = onFrameRateSelected,
        )
        ChipGroup(
            title = "IMU sampling",
            selected = settings.imuSamplingPreset,
            options = ImuSamplingPreset.entries.toList(),
            optionLabel = { it.label },
            onSelected = onImuSamplingSelected,
        )
        OutlinedTextField(
            value = settings.sessionLabel,
            onValueChange = onSessionLabelChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Session label") },
            supportingText = {
                Text("Optional note for calibration run identifiers or operator remarks.")
            },
            maxLines = 2,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun <T> ChipGroup(
    title: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(optionLabel(option)) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
    }
}

