package com.verdo.gesturecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val cameraGranted = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted) {
            status.text = "Kamera diizinkan. Pastikan Accessibility Service aktif."
            requestNotificationsIfNeeded()
        } else {
            status.text = "Izin kamera ditolak."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            textSize = 18f
            text = "Gesture Control\n\n1. Izinkan kamera\n2. Aktifkan Accessibility Service\n3. Tekan Start"
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            addView(status)
            addView(Button(context).apply {
                text = "Izinkan Kamera"
                setOnClickListener { requestPermissionsIfNeeded() }
            })
            addView(Button(context).apply {
                text = "Buka Accessibility"
                setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            })
            addView(Button(context).apply {
                text = "START GESTURE"
                setOnClickListener {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        status.text = "Kamera belum diizinkan."
                        requestPermissionsIfNeeded()
                        return@setOnClickListener
                    }
                    GestureService.start(this@MainActivity)
                    status.text = "AKTIF — gesture berjalan di background."
                }
            })
            addView(Button(context).apply {
                text = "STOP"
                setOnClickListener {
                    GestureService.stop(this@MainActivity)
                    status.text = "STOPPED"
                }
            })
        }
        setContentView(layout)
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
            status.text = "Semua izin sudah aktif."
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
