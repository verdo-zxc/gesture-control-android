package com.verdo.gesturecontrol

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class GestureService : LifecycleService() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var landmarker: HandLandmarker? = null
    private val busy = AtomicBoolean(false)
    private var previousX: Float? = null
    private var previousY: Float? = null
    private var previousTime = 0L
    private var lastAction: GestureAction? = null
    private var lastActionTime = 0L
    private var stablePose = GestureClassifier.Pose.NONE
    private var stableSince = 0L

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification())
        createLandmarker()
        startCamera()
    }

    private fun createLandmarker() {
        val base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.65f)
            .setMinHandPresenceConfidence(0.65f)
            .setMinTrackingConfidence(0.65f)
            .setResultListener { result, _ -> onResult(result) }
            .setErrorListener { }
            .build()
        landmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun startCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(android.util.Size(640, 480))
                .build()
            analysis.setAnalyzer(cameraExecutor) { image -> analyze(image) }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        if (busy.getAndSet(true)) { image.close(); return }
        try {
            val bitmap = image.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            landmarker?.detectAsync(mpImage, SystemClock.uptimeMillis())
        } catch (_: Throwable) {
        } finally {
            busy.set(false)
            image.close()
        }
    }

    private fun onResult(result: HandLandmarkerResult) {
        if (result.landmarks().isEmpty()) {
            previousX = null
            previousY = null
            stablePose = GestureClassifier.Pose.NONE
            return
        }
        val raw = result.landmarks()[0].map { LandmarkPoint(it.x(), it.y(), it.z()) }
        val now = SystemClock.uptimeMillis()
        val dt = max(1L, now - previousTime)
        val r = GestureClassifier.classify(raw, previousX, previousY, dt)
        previousX = r.x; previousY = r.y; previousTime = now

        if (r.pose != stablePose) {
            stablePose = r.pose
            stableSince = now
        }

        val action = when {
            r.swipe != GestureClassifier.Swipe.NONE && r.pose == GestureClassifier.Pose.OPEN -> when (r.swipe) {
                GestureClassifier.Swipe.LEFT -> GestureAction.SWIPE_LEFT
                GestureClassifier.Swipe.RIGHT -> GestureAction.SWIPE_RIGHT
                GestureClassifier.Swipe.UP -> GestureAction.SCROLL_UP
                GestureClassifier.Swipe.DOWN -> GestureAction.SCROLL_DOWN
                else -> null
            }
            r.pose == GestureClassifier.Pose.VICTORY -> GestureAction.TAP
            r.pose == GestureClassifier.Pose.POINT -> GestureAction.HOME
            r.pose == GestureClassifier.Pose.FIST -> GestureAction.RECENT
            else -> null
        }

        val hold = if (action == GestureAction.TAP) 90 else 140
        val cooldown = if (action == GestureAction.TAP) 650 else 500
        if (action != null && r.confidence >= 0.85f && now - stableSince >= hold &&
            (action != lastAction || now - lastActionTime >= cooldown)) {
            lastAction = action
            lastActionTime = now
            GestureAccessibilityService.instance?.execute(action)
        }
    }

    private fun notification(): Notification {
        val channelId = "gesture_control"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(channelId, "Gesture Control", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Gesture Control aktif")
            .setContentText("Kamera gesture berjalan di latar belakang")
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        landmarker?.close(); landmarker = null
        cameraExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, GestureService::class.java))
        fun stop(context: Context) = context.stopService(Intent(context, GestureService::class.java))
    }
}
