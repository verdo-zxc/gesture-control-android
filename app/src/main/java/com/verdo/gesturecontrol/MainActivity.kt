package com.verdo.gesturecontrol

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val cameraGranted = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted) {
            status.text = "KAMERA: izin OK\nACCESSIBILITY: cek di bawah\nTekan START GESTURE"
            requestNotificationsIfNeeded()
        } else {
            status.text = "KAMERA: DITOLAK\nBerikan izin kamera terlebih dahulu."
        }
    }

    private val statusTicker = object : Runnable {
        override fun run() {
            renderStatus()
            handler.postDelayed(this, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            textSize = 17f
            setPadding(0, 0, 0, 24)
            text = "Gesture Control\n\nKAMERA: belum dicek\nMEDIAPIPE: STOPPED\nHAND: --\nGESTURE: --\nACCESSIBILITY: --"
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            addView(status)
            addView(Button(context).apply {
                text = "IZINKAN KAMERA"
                setOnClickListener { requestPermissionsIfNeeded() }
            })
            addView(Button(context).apply {
                text = "BUKA ACCESSIBILITY"
                setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            })
            addView(Button(context).apply {
                text = "START GESTURE"
                setOnClickListener {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestPermissionsIfNeeded()
                        return@setOnClickListener
                    }
                    if (!isAccessibilityEnabled()) {
                        status.text = "ACCESSIBILITY: BELUM AKTIF\nAktifkan Gesture Control di Settings terlebih dahulu."
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        return@setOnClickListener
                    }
                    GestureService.start(this@MainActivity)
                }
            })
            addView(Button(context).apply {
                text = "STOP"
                setOnClickListener { GestureService.stop(this@MainActivity) }
            })
        }
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(statusTicker)
        handler.post(statusTicker)
    }

    override fun onPause() {
        handler.removeCallbacks(statusTicker)
        super.onPause()
    }

    private fun renderStatus() {
        val prefs = getSharedPreferences(GestureService.PREFS, Context.MODE_PRIVATE)
        val running = prefs.getBoolean("running", false)
        val camera = prefs.getString("camera", if (running) "STARTING" else "STOPPED") ?: "--"
        val hand = prefs.getBoolean("hand", false)
        val pose = prefs.getString("pose", "NONE") ?: "NONE"
        val confidence = prefs.getFloat("confidence", 0f)
        val fps = prefs.getFloat("fps", 0f)
        val error = prefs.getString("error", "") ?: ""
        val access = isAccessibilityEnabled()
        val lastAction = prefs.getString("action", "NONE") ?: "NONE"

        status.text = buildString {
            append("GESTURE CONTROL\n\n")
            append("CAMERA: ").append(camera).append('\n')
            append("MEDIAPIPE: ").append(if (running) "RUNNING" else "STOPPED").append('\n')
            append("HAND: ").append(if (hand) "DETECTED" else "NO HAND").append('\n')
            append("GESTURE: ").append(pose).append('\n')
            append("CONFIDENCE: ").append("%.0f%%".format(confidence * 100f)).append('\n')
            append("FPS: ").append("%.1f".format(fps)).append('\n')
            append("ACCESSIBILITY: ").append(if (access) "ACTIVE" else "OFF").append('\n')
            append("LAST ACTION: ").append(lastAction)
            if (error.isNotBlank()) append("\n\nERROR: ").append(error)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.CAMERA
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (permissions.isEmpty()) {
            status.text = "KAMERA: izin OK\nACCESSIBILITY: ${if (isAccessibilityEnabled()) "ACTIVE" else "OFF"}"
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }
}
