package com.verdo.gesturecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val permissionRequest = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        val status = TextView(this).apply { textSize = 18f; text = "Gesture Control\n\n1. Izinkan kamera\n2. Aktifkan Accessibility Service\n3. Tekan Start" }
        val camera = Button(this).apply { text = "Izinkan Kamera"; setOnClickListener { requestCamera() } }
        val accessibility = Button(this).apply { text = "Buka Accessibility"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
        val start = Button(this).apply { text = "START GESTURE"; setOnClickListener { GestureService.start(this@MainActivity); status.text = "AKTIF — gesture berjalan di background" } }
        val stop = Button(this).apply { text = "STOP"; setOnClickListener { GestureService.stop(this@MainActivity); status.text = "STOPPED" } }
        layout.addView(status); layout.addView(camera); layout.addView(accessibility); layout.addView(start); layout.addView(stop)
        setContentView(layout)
    }

    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), permissionRequest)
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), permissionRequest + 1)
        }
    }
}
