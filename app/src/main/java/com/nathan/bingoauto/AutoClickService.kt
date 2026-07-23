package com.nathan.bingoauto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service whose only job is to dispatch tap gestures at absolute
 * screen coordinates. A game rendered by a game engine does not expose its
 * content through the accessibility tree, so we never read nodes here — we only
 * use [dispatchGesture] to daub cells detected via screen capture + OCR.
 */
class AutoClickService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "AutoClickService connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* unused */ }

    override fun onInterrupt() { /* unused */ }

    /** Taps once at the given absolute screen pixel coordinates. */
    fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = dispatchGesture(gesture, null, null)
        if (!dispatched) Log.w(TAG, "tap not dispatched at ($x, $y)")
    }

    companion object {
        private const val TAG = "AutoClickService"
        private const val TAP_DURATION_MS = 40L

        /** The single live instance, or null when the service is not enabled. */
        @Volatile
        var instance: AutoClickService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
