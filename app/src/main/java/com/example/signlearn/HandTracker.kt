package com.example.signlearn

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/** One processed camera frame. */
class HandFrame(
    /** 21 points, x/y from 0..1 across the (rotated, mirrored) image — for drawing. */
    val screenPoints: List<P3>?,
    /** 21 points in real-world meters — for recognizing the sign. */
    val worldPoints: List<P3>?,
    val imageWidth: Int,
    val imageHeight: Int
)

/**
 * Wraps Google's MediaPipe Hand Landmarker. Feed it CameraX frames with [detect];
 * results arrive on [onFrame] (on a background thread).
 */
class HandTracker(
    context: Context,
    private val onFrame: (HandFrame) -> Unit,
    private val onError: (String) -> Unit
) {
    private var landmarker: HandLandmarker? = null

    init {
        try {
            val base = BaseOptions.builder()
                .setModelAssetPath(MODEL)
                .setDelegate(Delegate.CPU)
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: HandLandmarkerResult, image ->
                    onFrame(toFrame(result, image.width, image.height))
                }
                .setErrorListener { e -> android.util.Log.e("HandTracker", "MediaPipe error", e) }
                .build()
            landmarker = HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            onError("Could not load the hand model ($MODEL). See README step 3.\n${e.message}")
        }
    }

    val isReady: Boolean get() = landmarker != null

    /** Called for every camera frame (on the camera's background thread). */
    fun detect(proxy: ImageProxy, frontCamera: Boolean) {
        val lm = landmarker
        if (lm == null) { proxy.close(); return }
        val bitmap: Bitmap
        try {
            val raw = proxy.toBitmap()
            val matrix = Matrix().apply {
                postRotate(proxy.imageInfo.rotationDegrees.toFloat())
                // Mirror front camera so the points line up with the mirrored preview
                if (frontCamera) postScale(-1f, 1f, proxy.width.toFloat(), proxy.height.toFloat())
            }
            bitmap = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        } catch (e: Exception) {
            proxy.close()
            return
        }
        proxy.close()
        try {
            lm.detectAsync(BitmapImageBuilder(bitmap).build(), SystemClock.uptimeMillis())
        } catch (e: Exception) {
            // Frame dropped (e.g. timestamps out of order) — just skip it.
        }
    }

    private fun toFrame(result: HandLandmarkerResult, w: Int, h: Int): HandFrame {
        val screen = result.landmarks().firstOrNull()?.map { P3(it.x(), it.y(), it.z()) }
        val world = result.worldLandmarks().firstOrNull()?.map { P3(it.x(), it.y(), it.z()) }
        return HandFrame(screen, world, w, h)
    }

    fun close() {
        landmarker?.close()
        landmarker = null
    }

    companion object {
        const val MODEL = "hand_landmarker.task"
    }
}
