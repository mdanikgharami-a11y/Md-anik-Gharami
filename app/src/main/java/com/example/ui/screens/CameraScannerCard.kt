package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.biometric.FaceRecognitionService
import com.example.biometric.ImageBitmapUtils
import com.example.biometric.LiveFaceTrackingState
import com.example.data.model.AttendanceMode
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun FaceScanCameraArea(
    isScannerActive: Boolean,
    isVerifying: Boolean,
    selectedMode: AttendanceMode,
    faceService: FaceRecognitionService,
    onActivateScanner: () -> Unit,
    onCaptureFrameForVerification: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val semantic = LocalSemanticColors.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            onActivateScanner()
        }
    }

    var useFrontCamera by remember { mutableStateOf(true) }
    var trackingState by remember { mutableStateOf(LiveFaceTrackingState()) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }

    val currentIsVerifying by rememberUpdatedState(isVerifying)
    val currentOnCapture by rememberUpdatedState(onCaptureFrameForVerification)

    val initiateFaceRecognition: () -> Unit = remember {
        {
            val capture = imageCaptureRef
            if (capture != null && !currentIsVerifying) {
                capture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bmp = ImageBitmapUtils.imageProxyToUprightBitmap(
                                imageProxy = image,
                                mirrorHorizontally = useFrontCamera
                            )
                            image.close()
                            if (bmp != null) {
                                currentOnCapture(bmp)
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("FaceScanCamera", "ImageCapture failed", exception)
                        }
                    }
                )
            }
        }
    }

    val buttonContainerColor by animateColorAsState(
        targetValue = when {
            !isScannerActive || !hasCameraPermission -> MaterialTheme.colorScheme.primary
            trackingState.isFaceCentered -> semantic.success
            else -> MaterialTheme.colorScheme.primary
        },
        label = "recognitionButtonColor"
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("face_scan_camera_area")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Live CameraX Preview Viewport
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .testTag("camerax_preview_container")
            ) {
                if (!isScannerActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FaceRetouchingNatural,
                                    contentDescription = "Camera standby",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Camera Preview Paused",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Switch the top-right toggle ON or tap the button below to resume the live CameraX preview for ${selectedMode.label}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (!hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Camera permission needed",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Camera Access Required",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Grant camera permission to display the live CameraX face preview and start recognition.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val previewView = remember {
                        PreviewView(context).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    }
                    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

                    DisposableEffect(Unit) {
                        onDispose {
                            analysisExecutor.shutdown()
                        }
                    }

                    DisposableEffect(lifecycleOwner, useFrontCamera, isScannerActive) {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        val mainExecutor = ContextCompat.getMainExecutor(context)
                        var boundProvider: ProcessCameraProvider? = null

                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                boundProvider = cameraProvider
                                cameraProvider.unbindAll()

                                val preferredSelector = if (useFrontCamera) {
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                } else {
                                    CameraSelector.DEFAULT_BACK_CAMERA
                                }
                                val cameraSelector = when {
                                    cameraProvider.hasCamera(preferredSelector) -> preferredSelector
                                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> {
                                        useFrontCamera = false
                                        CameraSelector.DEFAULT_BACK_CAMERA
                                    }
                                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> {
                                        useFrontCamera = true
                                        CameraSelector.DEFAULT_FRONT_CAMERA
                                    }
                                    else -> null
                                }

                                if (cameraSelector != null) {
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .build()
                                    imageCaptureRef = capture

                                    var isAnalyzing = false
                                    val analysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { imageAnalysis ->
                                            imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
                                                if (isAnalyzing) {
                                                    proxy.close()
                                                    return@setAnalyzer
                                                }
                                                isAnalyzing = true
                                                scope.launch {
                                                    try {
                                                        val state = faceService.detectLiveFaceFromProxy(
                                                            imageProxy = proxy,
                                                            isFrontCamera = useFrontCamera
                                                        )
                                                        trackingState = state
                                                    } catch (e: Exception) {
                                                        Log.w("FaceScanCamera", "Analysis frame skipped", e)
                                                    } finally {
                                                        proxy.close()
                                                        isAnalyzing = false
                                                    }
                                                }
                                            }
                                        }

                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        capture,
                                        analysis
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e("FaceScanCamera", "CameraX bind failed", e)
                            }
                        }, mainExecutor)

                        onDispose {
                            boundProvider?.unbindAll()
                            imageCaptureRef = null
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )

                        BiometricReticleOverlay(
                            trackingState = trackingState,
                            isVerifying = isVerifying,
                            activeColor = if (trackingState.isFaceCentered) semantic.success else MaterialTheme.colorScheme.tertiary
                        )

                        // Top overlay controls: live detection pill + flip camera button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xBF070D18),
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (trackingState.isFaceCentered) semantic.success
                                                else Color(0xFF38BDF8)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isVerifying) "Verifying face match..." else trackingState.statusHint,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = { useFrontCamera = !useFrontCamera },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("flip_camera_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FlipCameraAndroid,
                                    contentDescription = "Switch front or rear camera"
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dedicated primary action button to initiate face recognition
            Button(
                onClick = {
                    when {
                        !hasCameraPermission -> permissionLauncher.launch(Manifest.permission.CAMERA)
                        !isScannerActive -> onActivateScanner()
                        else -> initiateFaceRecognition()
                    }
                },
                enabled = !isVerifying,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonContainerColor,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("initiate_face_recognition_button")
            ) {
                when {
                    isVerifying -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Recognizing Face...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    !hasCameraPermission -> {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Allow Camera & Start Recognition",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    !isScannerActive -> {
                        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Turn On Camera & Start Recognition",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    else -> {
                        Icon(Icons.Rounded.FaceRetouchingNatural, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Start Face Recognition • ${selectedMode.label}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BiometricReticleOverlay(
    trackingState: LiveFaceTrackingState,
    isVerifying: Boolean,
    activeColor: Color
) {
    val frameColor by animateColorAsState(
        targetValue = activeColor,
        animationSpec = tween(250),
        label = "reticleColor"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "scanSweep")
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweepY"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val frameW = size.width * 0.56f
        val frameH = size.height * 0.64f
        val left = (size.width - frameW) / 2f
        val top = (size.height - frameH) / 2f
        val cornerLen = 28.dp.toPx()
        val strokeW = 3.5.dp.toPx()

        // Subtle dark vignette outside guide frame
        drawRoundRect(
            color = frameColor.copy(alpha = if (isVerifying) 0.22f else 0.08f),
            topLeft = Offset(left, top),
            size = Size(frameW, frameH),
            cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx())
        )

        drawRoundRect(
            color = frameColor.copy(alpha = 0.45f),
            topLeft = Offset(left, top),
            size = Size(frameW, frameH),
            cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Four corner biometric brackets
        // Top-left
        drawLine(frameColor, Offset(left, top + cornerLen), Offset(left, top), strokeW)
        drawLine(frameColor, Offset(left, top), Offset(left + cornerLen, top), strokeW)
        // Top-right
        drawLine(frameColor, Offset(left + frameW - cornerLen, top), Offset(left + frameW, top), strokeW)
        drawLine(frameColor, Offset(left + frameW, top), Offset(left + frameW, top + cornerLen), strokeW)
        // Bottom-left
        drawLine(frameColor, Offset(left, top + frameH - cornerLen), Offset(left, top + frameH), strokeW)
        drawLine(frameColor, Offset(left, top + frameH), Offset(left + cornerLen, top + frameH), strokeW)
        // Bottom-right
        drawLine(frameColor, Offset(left + frameW - cornerLen, top + frameH), Offset(left + frameW, top + frameH), strokeW)
        drawLine(frameColor, Offset(left + frameW, top + frameH - cornerLen), Offset(left + frameW, top + frameH), strokeW)

        // Animated laser sweep line
        val sweepY = top + 16.dp.toPx() + (frameH - 32.dp.toPx()) * sweepProgress
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, frameColor, Color.Transparent),
                startX = left,
                endX = left + frameW
            ),
            start = Offset(left + 8.dp.toPx(), sweepY),
            end = Offset(left + frameW - 8.dp.toPx(), sweepY),
            strokeWidth = 2.5.dp.toPx()
        )

        // Live detected face bounding indicator if ML Kit tracks a face
        if (trackingState.faceCount == 1 && trackingState.normalizedWidth > 0f) {
            val boxW = trackingState.normalizedWidth * size.width
            val boxH = trackingState.normalizedHeight * size.height
            val bx = trackingState.normalizedCenterX * size.width - boxW / 2f
            val by = trackingState.normalizedCenterY * size.height - boxH / 2f
            drawRoundRect(
                color = frameColor.copy(alpha = 0.7f),
                topLeft = Offset(bx, by),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
fun SelfieCaptureOverlay(
    onCaptured: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var useFrontCamera by remember { mutableStateOf(true) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner, useFrontCamera) {
        val future = ProcessCameraProvider.getInstance(context)
        var providerRef: ProcessCameraProvider? = null
        future.addListener({
            try {
                val provider = future.get()
                providerRef = provider
                provider.unbindAll()
                val preferred = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val selector = if (provider.hasCamera(preferred)) preferred else CameraSelector.DEFAULT_BACK_CAMERA
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder().build()
                imageCapture = capture
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            } catch (e: Exception) {
                Log.e("SelfieOverlay", "Failed to bind selfie camera", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            providerRef?.unbindAll()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x88000000))
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close selfie camera", tint = Color.White)
            }

            IconButton(
                onClick = { useFrontCamera = !useFrontCamera },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x88000000))
            ) {
                Icon(Icons.Rounded.FlipCameraAndroid, contentDescription = "Flip camera", tint = Color.White)
            }
        }

        Button(
            onClick = {
                imageCapture?.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bmp = ImageBitmapUtils.imageProxyToUprightBitmap(
                                imageProxy = image,
                                mirrorHorizontally = useFrontCamera
                            )
                            image.close()
                            if (bmp != null) onCaptured(bmp)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("SelfieOverlay", "Selfie capture failed", exception)
                        }
                    }
                )
            },
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .height(56.dp)
        ) {
            Icon(Icons.Rounded.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Capture Reference Photo")
        }
    }
}
