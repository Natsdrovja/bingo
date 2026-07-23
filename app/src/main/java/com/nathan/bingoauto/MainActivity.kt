package com.nathan.bingoauto

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.nathan.bingoauto.databinding.ActivityMainBinding

/**
 * Setup screen. Walks the user through the three permissions the bot needs —
 * overlay, accessibility, screen capture — and starts the capture service.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                ScreenCaptureService.start(this, result.resultCode, data)
                Toast.makeText(this, "Bot démarré — ouvre ton jeu", Toast.LENGTH_LONG).show()
                moveTaskToBack(true)
            } else {
                Toast.makeText(this, "Capture d'écran refusée", Toast.LENGTH_SHORT).show()
            }
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOverlay.setOnClickListener { requestOverlay() }
        binding.btnAccessibility.setOnClickListener { openAccessibilitySettings() }
        binding.btnStart.setOnClickListener { startBot() }
        binding.btnStop.setOnClickListener { ScreenCaptureService.stop(this) }

        maybeRequestNotifications()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val accessibilityOk = isAccessibilityEnabled()
        binding.txtOverlayStatus.text = status("Superposition", overlayOk)
        binding.txtAccessibilityStatus.text = status("Accessibilité", accessibilityOk)
        // The overlay pastille only needs the "draw over other apps" permission.
        // Accessibility is required to actually tap, but we let the user start
        // without it (the overlay shows a warning) so the pastille can be tested.
        binding.btnStart.isEnabled = overlayOk
    }

    private fun status(label: String, ok: Boolean) =
        "$label : ${if (ok) "✓ activé" else "✗ à activer"}"

    private fun requestOverlay() {
        if (Settings.canDrawOverlays(this)) return
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        Toast.makeText(
            this,
            "Si « Bingo Auto » est grisé (paramètres restreints) : Applis → Bingo Auto → ⋮ → Autoriser les paramètres restreints",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun startBot() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this, "Active d'abord la superposition", Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (!isAccessibilityEnabled()) {
            Toast.makeText(
                this,
                "Accessibilité coupée : la pastille s'affichera mais aucun tap ne sera fait",
                Toast.LENGTH_LONG
            ).show()
        }
        val manager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${AutoClickService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }
}
