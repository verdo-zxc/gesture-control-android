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
import kotlin.math.max

class GestureService : LifecycleService() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var landmarker: HandLandmarker? = null
    private val busy = AtomicBoolean(false)
    private var previousX: Float? = null
    private var previousY: Float? = null
    private var previousResultTime = 0L
    private var lastAction: GestureAction? = null
    private var lastActionTime = 0L
    private var stablePose = GestureClassifier.Pose.NONE
    private var stableSince = 0L
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
            .setMinHandDetectionConfidence(0.60f).setMinHandPresenceConfidence(0.60f).setMinTrackingConfidence(0.60f)
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
                    .setTargetResolution(android.util.Size(640, 480)).build()
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
            stablePose = GestureClassifier.Pose.NONE
            writeState(hand = false, pose = "NONE", confidence = 0f)
            return
        }
        val raw = result.landmarks()[0].map { LandmarkPoint(it.x(), it.y(), it.z()) }
        val dt = if (previousResultTime == 0L) 100L else (now - previousResultTime).coerceIn(10L, 350L)
        previousResultTime = now
        val r = GestureClassifier.classify(raw, previousX, previousY, dt)
        previousX = r.x; previousY = r.y

        if (r.pose != stablePose) { stablePose = r.pose; stableSince = now }
        writeState(hand = true, pose = r.pose.name, confidence = r.confidence)

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

        // Require a stable pose before acting. A pose change also resets the cooldown,
        // preventing a shaky hand from generating repeated actions.
        val hold = if (action == GestureAction.TAP) 260L else 320L
        val cooldown = if (action == GestureAction.TAP) 900L else 800L
        val accessibility = GestureAccessibilityService.instance
        if (action != null && r.confidence >= 0.78f && now - stableSince >= hold &&
            (action != lastAction || now - lastActionTime >= cooldown)) {
            lastAction = action; lastActionTime = now
            if (accessibility != null) {
                accessibility.execute(action, r.tapX, r.tapY)
                writeState(action = action.name, error = "")
                updateNotification("${r.pose.name} → ${action.name}")
            } else {
                writeState(action = "BLOCKED: ACCESSIBILITY OFF", error = "Enable Gesture Control Accessibility")
                updateNotification("Accessibility OFF")
            }
        }
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
