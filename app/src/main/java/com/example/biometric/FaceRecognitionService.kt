package com.example.biometric

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.example.data.model.FaceVerificationResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.max

data class LiveFaceTrackingState(
    val faceCount: Int = 0,
    val isFaceCentered: Boolean = false,
    val normalizedCenterX: Float = 0.5f,
    val normalizedCenterY: Float = 0.5f,
    val normalizedWidth: Float = 0f,
    val normalizedHeight: Float = 0f,
    val statusHint: String = "Align your face inside the scan frame"
)

data class RegistrationFaceCheck(
    val hasValidFace: Boolean,
    val message: String
)

object ImageBitmapUtils {

    fun decodeSampledBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 512): Bitmap? {
        return try {
            val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val maxSide = max(info.size.width, info.size.height)
                    if (maxSide > maxDimension) {
                        val scale = maxDimension.toFloat() / maxSide.toFloat()
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1)
                        )
                    }
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } ?: return null

            scaleDownBitmap(rawBitmap, maxDimension)
        } catch (e: Exception) {
            Log.e("ImageBitmapUtils", "Failed to decode URI bitmap", e)
            null
        }
    }

    fun scaleDownBitmap(bitmap: Bitmap, maxDimension: Int = 512): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxSide = max(width, height)
        if (maxSide <= maxDimension) {
            return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
            else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (width * ratio).toInt().coerceAtLeast(1)
        val targetH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    fun bitmapToJpegBase64(bitmap: Bitmap, maxDimension: Int = 360, quality: Int = 78): String {
        val scaled = scaleDownBitmap(bitmap, maxDimension)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            if (base64.isBlank()) return null
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e("ImageBitmapUtils", "Failed to decode base64 bitmap", e)
            null
        }
    }

    fun imageProxyToUprightBitmap(imageProxy: ImageProxy, mirrorHorizontally: Boolean): Bitmap? {
        return try {
            val bitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
            val matrix = Matrix().apply {
                if (rotation != 0f) {
                    postRotate(rotation)
                }
                if (mirrorHorizontally) {
                    postScale(-1f, 1f)
                }
            }
            val rotated = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
            scaleDownBitmap(rotated, 512)
        } catch (e: Exception) {
            Log.e("ImageBitmapUtils", "Failed to convert ImageProxy to Bitmap", e)
            null
        }
    }
}

class FaceRecognitionService {

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.16f)
            .build()
        FaceDetection.getClient(options)
    }

    private val verificationSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "faceDetectedInLive" to Schema.boolean(
                    description = "True if Image 2 contains a clearly visible human face"
                ),
                "isSamePerson" to Schema.boolean(
                    description = "True only if Image 1 and Image 2 depict the exact same individual"
                ),
                "confidenceScore" to Schema.integer(
                    description = "Identity match confidence from 0 to 100"
                ),
                "summary" to Schema.string(
                    description = "Concise one-sentence biometric comparison explanation"
                )
            )
        )
    }

    private val generativeModel by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = verificationSchema
            temperature = 0.1f
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    suspend fun validateRegistrationPhoto(bitmap: Bitmap): RegistrationFaceCheck {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = faceDetector.process(inputImage).await()
            when {
                faces.isEmpty() -> RegistrationFaceCheck(
                    hasValidFace = false,
                    message = "No face detected. Please upload or take a clear front-facing photo."
                )
                faces.size > 1 -> RegistrationFaceCheck(
                    hasValidFace = false,
                    message = "Multiple faces detected. Please use a photo with only your face."
                )
                else -> RegistrationFaceCheck(
                    hasValidFace = true,
                    message = "Face detected and ready for biometric enrollment."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit registration face check failed", e)
            RegistrationFaceCheck(
                hasValidFace = false,
                message = "Could not analyze photo face: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    @OptIn(ExperimentalGetImage::class)
    suspend fun detectLiveFaceFromProxy(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ): LiveFaceTrackingState {
        val mediaImage = imageProxy.image ?: return LiveFaceTrackingState()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        val faces = faceDetector.process(inputImage).await()
        if (faces.isEmpty()) {
            return LiveFaceTrackingState(
                faceCount = 0,
                isFaceCentered = false,
                statusHint = "Position your face inside the scan frame"
            )
        }
        if (faces.size > 1) {
            return LiveFaceTrackingState(
                faceCount = faces.size,
                isFaceCentered = false,
                statusHint = "Multiple faces detected — scan one person at a time"
            )
        }

        val face: Face = faces.first()
        val rotatedWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val rotatedHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
        val box = face.boundingBox

        val rawCenterX = box.centerX().toFloat() / rotatedWidth.coerceAtLeast(1).toFloat()
        val centerX = if (isFrontCamera) 1f - rawCenterX else rawCenterX
        val centerY = box.centerY().toFloat() / rotatedHeight.coerceAtLeast(1).toFloat()
        val normW = box.width().toFloat() / rotatedWidth.coerceAtLeast(1).toFloat()
        val normH = box.height().toFloat() / rotatedHeight.coerceAtLeast(1).toFloat()

        val centered = centerX in 0.24f..0.76f &&
            centerY in 0.20f..0.80f &&
            normW >= 0.16f

        val hint = when {
            normW < 0.16f -> "Move closer to the camera"
            !centered -> "Center your face inside the green frame"
            else -> "Face locked — verifying identity..."
        }

        return LiveFaceTrackingState(
            faceCount = 1,
            isFaceCentered = centered,
            normalizedCenterX = centerX.coerceIn(0f, 1f),
            normalizedCenterY = centerY.coerceIn(0f, 1f),
            normalizedWidth = normW.coerceIn(0f, 1f),
            normalizedHeight = normH.coerceIn(0f, 1f),
            statusHint = hint
        )
    }

    suspend fun verifyFaces(
        registeredProfileBitmap: Bitmap,
        liveCameraBitmap: Bitmap
    ): Result<FaceVerificationResult> = withContext(Dispatchers.IO) {
        try {
            val refScaled = ImageBitmapUtils.scaleDownBitmap(registeredProfileBitmap, 480)
            val liveScaled = ImageBitmapUtils.scaleDownBitmap(liveCameraBitmap, 480)

            val response = withTimeout(60_000L) {
                generativeModel.generateContent(
                    content {
                        text(
                            """
                            You are a strict biometric attendance face verification engine.
                            Image 1 is the REGISTERED REFERENCE PHOTO for this user account.
                            Image 2 is the LIVE CAMERA CAPTURE for attendance verification.

                            Task:
                            1. Confirm Image 2 shows a real, clear human face (not blank, covered, or an unrelated object).
                            2. Compare facial geometry, eyes, eyebrows, nose bridge, mouth, and jawline between Image 1 and Image 2.
                            3. Set isSamePerson=true ONLY if both photos clearly depict the same individual. Allow normal differences in lighting, expression, glasses, or slight head angle, but reject different people.
                            4. Provide confidenceScore (0-100) and a concise one-sentence summary.
                            """.trimIndent()
                        )
                        image(refScaled)
                        image(liveScaled)
                    }
                )
            }

            val jsonText = response.text
                ?: return@withContext Result.failure(
                    IllegalStateException("Face verification returned an empty response.")
                )

            val json = JSONObject(jsonText)
            val faceDetectedInLive = json.optBoolean("faceDetectedInLive", false)
            val isSamePerson = json.optBoolean("isSamePerson", false)
            val confidenceScore = json.optInt("confidenceScore", 0).coerceIn(0, 100)
            val summary = json.optString("summary", "Biometric comparison completed.")

            Result.success(
                FaceVerificationResult(
                    faceDetectedInLive = faceDetectedInLive,
                    isSamePerson = isSamePerson,
                    confidenceScore = confidenceScore,
                    summary = summary
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Face verification timed out", e)
            Result.failure(IllegalStateException("Face verification timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Face verification failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "FaceRecognitionService"
    }
}
