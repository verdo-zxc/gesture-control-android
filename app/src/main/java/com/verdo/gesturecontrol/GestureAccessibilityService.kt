package com.verdo.gesturecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class GestureAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: GestureAccessibilityService? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun execute(action: GestureAction) {
        when (action) {
            GestureAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            GestureAction.RECENT -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            GestureAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            GestureAction.TAP -> tap(0.5f, 0.5f)
            GestureAction.SWIPE_LEFT -> swipe(0.80f, 0.50f, 0.20f, 0.50f, 180)
            GestureAction.SWIPE_RIGHT -> swipe(0.20f, 0.50f, 0.80f, 0.50f, 180)
            GestureAction.SCROLL_UP -> swipe(0.50f, 0.72f, 0.50f, 0.28f, 220)
            GestureAction.SCROLL_DOWN -> swipe(0.50f, 0.28f, 0.50f, 0.72f, 220)
        }
    }

    private fun tap(nx: Float, ny: Float) {
        val dm = resources.displayMetrics
        val path = Path().apply { moveTo(nx * dm.widthPixels, ny * dm.heightPixels) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 35)).build()
        dispatchGesture(gesture, null, mainHandler)
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        val dm = resources.displayMetrics
        val path = Path().apply {
            moveTo(x1 * dm.widthPixels, y1 * dm.heightPixels)
            lineTo(x2 * dm.widthPixels, y2 * dm.heightPixels)
        }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
        dispatchGesture(gesture, null, mainHandler)
    }
}

enum class GestureAction { TAP, HOME, RECENT, SWIPE_LEFT, SWIPE_RIGHT, BACK, SCROLL_UP, SCROLL_DOWN }
