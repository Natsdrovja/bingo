package com.nathan.bingoauto

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import kotlin.math.abs

/**
 * A small draggable floating window shown over the game: a play/pause button, a
 * status line, and a close button. Long-pressing the play button resets the
 * engine for a new game.
 */
class OverlayController(
    private val context: Context,
    private val onToggle: (running: Boolean) -> Unit,
    private val onReset: () -> Unit,
    private val onClose: () -> Unit,
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())

    private var root: View? = null
    private var toggleButton: Button? = null
    private var statusView: TextView? = null
    private var running = false

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 24
        y = 160
    }

    fun show() {
        if (root != null) return
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_controls, null)
        root = view
        toggleButton = view.findViewById(R.id.btn_toggle)
        statusView = view.findViewById(R.id.txt_status)

        toggleButton?.setOnClickListener {
            running = !running
            onToggle(running)
        }
        toggleButton?.setOnLongClickListener {
            onReset()
            true
        }
        view.findViewById<Button>(R.id.btn_close).setOnClickListener { onClose() }

        enableDrag(view)
        windowManager.addView(view, layoutParams)
    }

    private fun enableDrag(view: View) {
        val handle = view.findViewById<View>(R.id.drag_handle)
        var startX = 0f
        var startY = 0f
        var initialX = 0
        var initialY = 0
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - startX).toInt()
                    layoutParams.y = initialY + (event.rawY - startY).toInt()
                    root?.let { windowManager.updateViewLayout(it, layoutParams) }
                    true
                }
                else -> abs(event.rawX - startX) < 8 && abs(event.rawY - startY) < 8
            }
        }
    }

    fun setRunning(isRunning: Boolean) = main.post {
        running = isRunning
        toggleButton?.text = if (isRunning) "⏸" else "▶"
    }

    fun setStatus(text: String) = main.post {
        statusView?.text = text
    }

    fun hide() = main.post {
        root?.let {
            runCatching { windowManager.removeView(it) }
            root = null
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
}
