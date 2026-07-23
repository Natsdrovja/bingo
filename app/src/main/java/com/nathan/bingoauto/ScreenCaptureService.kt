package com.nathan.bingoauto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that mirrors the screen through MediaProjection, OCRs each
 * frame, and asks [BingoEngine] which cells to daub. Taps are performed by
 * [AutoClickService]. A floating overlay ([OverlayController]) lets the user
 * play/pause and reset without leaving the game.
 */
class ScreenCaptureService : Service() {

    private lateinit var projectionManager: MediaProjectionManager
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ocr = OcrProcessor()
    private lateinit var engine: BingoEngine
    private lateinit var overlay: OverlayController

    private var width = 0
    private var height = 0
    private var density = 0

    /** Toggled by the overlay play/pause button. */
    @Volatile private var daubing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.getParcelableExtraCompat<Intent>(EXTRA_DATA)
        if (resultCode == 0 || data == null) {
            Log.e(TAG, "Missing projection result; stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        readMetrics()
        engine = BingoEngine(width, height)
        startProjection(resultCode, data)
        setupOverlay()
        startLoop()
        return START_STICKY
    }

    private fun readMetrics() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
            density = resources.displayMetrics.densityDpi
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
            width = metrics.widthPixels
            height = metrics.heightPixels
            density = metrics.densityDpi
        }
        Log.i(TAG, "Screen ${width}x$height @${density}dpi")
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val projection = projectionManager.getMediaProjection(resultCode, data).also {
            this.projection = it
        }
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection stopped")
                stopEverything()
            }
        }, null)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection.createVirtualDisplay(
            "BingoAutoCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, null
        )
    }

    private fun setupOverlay() {
        overlay = OverlayController(
            context = this,
            onToggle = { running ->
                daubing = running
                overlay.setRunning(running)
            },
            onReset = {
                engine.reset()
                overlay.setStatus("Réinitialisé")
            },
            onClose = { stopEverything() }
        )
        overlay.show()
        overlay.setRunning(false)
        overlay.setStatus("En pause")
    }

    private fun startLoop() {
        scope.launch {
            while (isActive) {
                if (daubing) {
                    try {
                        processFrame()
                    } catch (t: Throwable) {
                        Log.w(TAG, "frame error", t)
                    }
                }
                delay(FRAME_INTERVAL_MS)
            }
        }
    }

    private suspend fun processFrame() {
        val reader = imageReader ?: return
        val image = reader.acquireLatestImage() ?: return
        val bitmap: Bitmap
        try {
            bitmap = image.toBitmap(width)
        } finally {
            image.close()
        }

        val tokens = ocr.recognize(bitmap)
        val targets = engine.onFrame(tokens)
        bitmap.recycle()

        val clicker = AutoClickService.instance
        if (clicker == null) {
            overlay.setStatus("⚠ Service d'accessibilité coupé")
            return
        }
        for (t in targets) {
            clicker.tap(t.x, t.y)
            delay(TAP_SPACING_MS)
        }
        overlay.setStatus("Annoncés ${engine.lastCalledCount} · Daubs ${engine.lastTapCount}")
    }

    private fun startForegroundNotification() {
        val channelId = "bingo_capture"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Bingo Auto", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Bingo Auto actif")
            .setContentText("Capture d'écran en cours")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopEverything() {
        daubing = false
        scope.cancel()
        if (this::overlay.isInitialized) overlay.hide()
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
        ocr.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIF_ID = 42
        private const val FRAME_INTERVAL_MS = 900L
        private const val TAP_SPACING_MS = 120L

        const val ACTION_STOP = "com.nathan.bingoauto.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "result_data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
