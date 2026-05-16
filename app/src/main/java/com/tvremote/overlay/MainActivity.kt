package com.tvremote.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.tvremote.overlay.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Create notification channel for foreground service
        createNotificationChannel()

        supportActionBar?.title = "📺 TV Remote Overlay"

        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUI()
    }

    private fun setupClickListeners() {
        // Step 1: Overlay draw permission
        binding.btnGrantOverlay.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        // Step 2: Accessibility service
        binding.btnGrantAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Step 3: Show / Hide overlay
        binding.btnShowOverlay.setOnClickListener {
            if (canDrawOverlays() && isAccessibilityEnabled()) {
                val intent = Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_SHOW
                }
                startForegroundService(intent)
                // Minimise app so user can see their TV app with overlay on top
                moveTaskToBack(true)
            } else {
                updatePermissionUI()
                binding.tvStatus.text = "⚠️ Please complete all steps above first."
            }
        }

        binding.btnHideOverlay.setOnClickListener {
            startService(Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_HIDE
            })
        }
    }

    private fun updatePermissionUI() {
        val overlayOk = canDrawOverlays()
        val accessibilityOk = isAccessibilityEnabled()

        // Overlay permission card
        setCardState(
            binding.cardOverlayPermission,
            binding.tvOverlayStatus,
            overlayOk,
            "✅  Overlay Permission — Granted",
            "❌  Overlay Permission — Tap button to grant"
        )
        binding.btnGrantOverlay.isEnabled = !overlayOk
        binding.btnGrantOverlay.alpha = if (overlayOk) 0.4f else 1f

        // Accessibility card
        setCardState(
            binding.cardAccessibilityPermission,
            binding.tvAccessibilityStatus,
            accessibilityOk,
            "✅  Accessibility Service — Enabled",
            "❌  Accessibility Service — Tap button to enable"
        )
        binding.btnGrantAccessibility.isEnabled = !accessibilityOk
        binding.btnGrantAccessibility.alpha = if (accessibilityOk) 0.4f else 1f

        // Show remote button — only active when both permissions are done
        val allReady = overlayOk && accessibilityOk
        binding.btnShowOverlay.isEnabled = allReady
        binding.btnShowOverlay.alpha = if (allReady) 1f else 0.4f

        binding.tvStatus.text = when {
            !overlayOk -> "Step 1: Grant overlay permission to show the floating remote."
            !accessibilityOk -> "Step 2: Enable the accessibility service for D-pad navigation."
            else -> "✅ All set! Tap 'Show Remote' then switch to your TV app."
        }
    }

    private fun setCardState(
        card: MaterialCardView,
        label: TextView,
        isOk: Boolean,
        okText: String,
        failText: String
    ) {
        val successColor = ContextCompat.getColor(this, R.color.status_success)
        val errorColor = ContextCompat.getColor(this, R.color.status_error)
        card.setCardBackgroundColor(if (isOk) successColor else errorColor)
        label.text = if (isOk) okText else failText
    }

    private fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(this)

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (!am.isEnabled) return false

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val target = ComponentName(this, RemoteAccessibilityService::class.java)
        return enabledServices.split(':').any { flat ->
            try {
                ComponentName.unflattenFromString(flat.trim()) == target
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            OverlayService.NOTIF_CHANNEL_ID,
            "TV Remote Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while the floating TV remote is active"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
