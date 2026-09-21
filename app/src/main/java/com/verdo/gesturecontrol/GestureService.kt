package com.verdo.gesturecontrol

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Matrix
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
import kotlin.math.abs

class GestureService : LifecycleService() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var landmarker: HandLandmarker? = null
    private val busy = AtomicBoolean(false)
    private var previousX: Float? = null
    private var previousY: Float? = null
    private var previousResultTime = 0L
    private var openHandActive = false
    private var indexPoseActive = false
    private var peacePoseActive = false
    private var fistPoseActive = false
    private var lastIndexFire = 0L
    private var lastPeaceFire = 0L
    private var lastFistFire = 0L
    private var lastActionTime = 0L
    private var frames = 0
    private var fpsStart = 0L
    private var fps = 0f

    override fun onCreate() {
        super.onCreate()
        writeState(running = true, camera = "STARTING", error = "")
        startForegroundCompat()
        try { createLandmarker(); startCamera() }
        catch (t: Throwable) { writeState(camera = "ERROR", error = t.message ?: t.javaClass.simpleName); updateNotification("ERROR: ${t.javaClass.simpleName}") }
    }

    private fun startForegroundCompat() {
        val notification = notification("Starting camera…")
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private fun createLandmarker() {
        val base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base).setRunningMode(RunningMode.LIVE_STREAM).setNumHands(1)
            .setMinHandDetectionConfidence(0.60f).setMinHandPresenceConfidence(0.50f).setMinTrackingConfidence(0.50f)
            .setResultListener { result, _ -> onResult(result) }
            .setErrorListener { error -> writeState(camera = "ERROR", error = error.message ?: "MediaPipe error"); updateNotification("MediaPipe ERROR") }
            .build()
        landmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun startCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            writeState(camera = "NO CAMERA PERMISSION", error = "CAMERA permission missing"); return
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetResolution(android.util.Size(480, 360)).build()
                analysis.setAnalyzer(cameraExecutor) { image -> analyze(image) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                writeState(camera = "ACTIVE", error = "")
                updateNotification("Camera ACTIVE • waiting for hand")
            } catch (t: Throwable) { writeState(camera = "ERROR", error = t.message ?: t.javaClass.simpleName); updateNotification("Camera ERROR") }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        if (busy.getAndSet(true)) { image.close(); return }
        try {
            val source = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = if (rotation == 0) source else Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            val mpImage = BitmapImageBuilder(rotated).build()
            landmarker?.detectAsync(mpImage, SystemClock.uptimeMillis())
            frames++
            val now = SystemClock.uptimeMillis()
            if (fpsStart == 0L) fpsStart = now
            if (now - fpsStart >= 1000L) { fps = frames * 1000f / (now - fpsStart); frames = 0; fpsStart = now; writeState(fps = fps) }
            if (rotated !== source) source.recycle()
        } catch (t: Throwable) { writeState(camera = "ERROR", error = t.message ?: t.javaClass.simpleName) }
        finally { busy.set(false); image.close() }
    }

    private fun onResult(result: HandLandmarkerResult) {
        val now = SystemClock.uptimeMillis()
        if (result.landmarks().isEmpty()) {
            previousX = null; previousY = null; previousResultTime = 0L
            openHandActive = false; indexPoseActive = false; peacePoseActive = false; fistPoseActive = false
            writeState(hand = false, pose = "NONE", confidence = 0f)
            return
        }
        val raw = result.landmarks()[0].map { LandmarkPoint(it.x(), it.y(), it.z()) }
        val dt = if (previousResultTime == 0L) 100L else (now - previousResultTime).coerceIn(10L, 350L)
        previousResultTime = now
        val r = GestureClassifier.classify(raw, previousX, previousY, dt)
        previousX = r.x; previousY = r.y
        writeState(hand = true, pose = r.pose.name, confidence = r.confidence)

        // Match the Macaly demo's edge-triggered static gestures and 600 ms re-fire windows.
        if (r.pose == GestureClassifier.Pose.VICTORY) {
            if (!peacePoseActive && now - lastPeaceFire > 600L) {
                fire(GestureAction.TAP, r.tapX, r.tapY, "TAP / OK (2 jari ✌️)")
                lastPeaceFire = now
            }
            peacePoseActive = true
        } else peacePoseActive = false

        if (r.pose == GestureClassifier.Pose.FIST) {
            if (!fistPoseActive && now - lastFistFire > 600L) {
                fire(GestureAction.RECENT, r.tapX, r.tapY, "RECENT APPS (kepalan ✊)")
                lastFistFire = now
            }
            fistPoseActive = true
        } else fistPoseActive = false

        if (r.pose == GestureClassifier.Pose.POINT) {
            if (!indexPoseActive && now - lastIndexFire > 600L) {
                fire(GestureAction.HOME, r.tapX, r.tapY, "HOME (telunjuk ☝️)")
                lastIndexFire = now
            }
            indexPoseActive = true
        } else indexPoseActive = false

        // Match Macaly's open-palm 220 ms motion window.
        if (r.pose == GestureClassifier.Pose.OPEN) {
            if (!openHandActive) {
                openHandActive = true
                previousX = r.x; previousY = r.y
            } else if (r.swipe != GestureClassifier.Swipe.NONE && now - lastActionTime > 450L) {
                val action = when (r.swipe) {
                    GestureClassifier.Swipe.LEFT -> GestureAction.SWIPE_LEFT
                    GestureClassifier.Swipe.RIGHT -> GestureAction.SWIPE_RIGHT
                    GestureClassifier.Swipe.UP -> GestureAction.SCROLL_UP
                    GestureClassifier.Swipe.DOWN -> GestureAction.SCROLL_DOWN
                    GestureClassifier.Swipe.NONE -> null
                }
                if (action != null) {
                    fire(action, r.tapX, r.tapY, actionLabel(action))
                    lastActionTime = now
                    previousX = r.x; previousY = r.y
                }
            }
        } else openHandActive = false
    }

    private fun fire(action: GestureAction, x: Float, y: Float, message: String) {
        val accessibility = GestureAccessibilityService.instance
        if (accessibility != null) {
            accessibility.execute(action, x, y)
            writeState(action = action.name, error = "")
            updateNotification(message)
        } else {
            writeState(action = "BLOCKED: ACCESSIBILITY OFF", error = "Enable Gesture Control Accessibility")
            updateNotification("Accessibility OFF")
        }
    }

    private fun actionLabel(action: GestureAction) = when (action) {
        GestureAction.SWIPE_LEFT -> "GESER LAYAR kanan→kiri"
        GestureAction.SWIPE_RIGHT -> "BACK (swipe kiri→kanan)"
        GestureAction.SCROLL_UP -> "SCROLL ke atas"
        GestureAction.SCROLL_DOWN -> "SCROLL ke bawah"
        else -> action.name
    }

    private fun writeState(running: Boolean? = null, camera: String? = null, hand: Boolean? = null, pose: String? = null,
                           confidence: Float? = null, fps: Float? = null, action: String? = null, error: String? = null) {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        running?.let { p.putBoolean("running", it) }; camera?.let { p.putString("camera", it) }
        hand?.let { p.putBoolean("hand", it) }; pose?.let { p.putString("pose", it) }
        confidence?.let { p.putFloat("confidence", it) }; fps?.let { p.putFloat("fps", it) }
        action?.let { p.putString("action", it) }; error?.let { p.putString("error", it) }
        p.apply()
    }

    private fun notification(text: String): Notification {
        val channelId = "gesture_control"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(channelId, "Gesture Control", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, channelId).setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Gesture Control").setContentText(text).setOngoing(true).build()
    }

    private fun updateNotification(text: String) = getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))

    override fun onDestroy() {
        writeState(running = false, camera = "STOPPED", hand = false, pose = "NONE")
        landmarker?.close(); landmarker = null; cameraExecutor.shutdownNow(); super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val PREFS = "gesture_runtime"
        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, GestureService::class.java))
        fun stop(context: Context) = context.stopService(Intent(context, GestureService::class.java))
    }
}

enum class GestureAction { TAP, HOME, RECENT, SWIPE_LEFT, SWIPE_RIGHT, BACK, SCROLL_UP, SCROLL_DOWN }
