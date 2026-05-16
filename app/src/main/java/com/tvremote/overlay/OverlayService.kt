package com.tvremote.overlay

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Foreground service that shows a floating D-pad remote overlay.
 * Buttons communicate with RemoteAccessibilityService via its companion object.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "com.tvremote.overlay.SHOW"
        const val ACTION_HIDE = "com.tvremote.overlay.HIDE"
        const val NOTIF_CHANNEL_ID = "tv_remote_overlay"
        private const val NOTIF_ID = 1001
    }

    private var windowManager: WindowManager? = null
    private var overlayRoot: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // For drag tracking
    private var dragStartX = 0
    private var dragStartY = 0
    private var touchStartRawX = 0f
    private var touchStartRawY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> {
                removeOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> showOverlay()
        }
        return START_STICKY
    }

    // ─── Overlay lifecycle ────────────────────────────────────────────────────

    private fun showOverlay() {
        if (overlayRoot != null) return   // already showing

        val view = buildRemoteView()
        overlayRoot = view

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        windowManager?.addView(view, layoutParams)
    }

    private fun removeOverlay() {
        overlayRoot?.let {
            runCatching { windowManager?.removeView(it) }
            overlayRoot = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    // ─── View construction ────────────────────────────────────────────────────

    private fun buildRemoteView(): View {
        // Outer container with rounded corners and semi-transparent dark background
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(color = 0xEE1a1a2e.toInt(), radius = dp(18f))
            elevation = dp(10f).toFloat()
        }

        container.addView(buildDragHandle(), lp(dp(210f), dp(46f)))
        container.addView(buildDpad(), lp(dp(210f), dp(210f)))
        container.addView(buildBottomRow(), lp(dp(210f), dp(56f)))

        return container
    }

    /** Top bar — drag handle + close button */
    private fun buildDragHandle(): View {
        val handle = RelativeLayout(this).apply {
            background = roundedTopBg(color = 0xFF0f3460.toInt(), radius = dp(18f))
            setPadding(dp(14f), 0, dp(10f), 0)
        }

        val title = TextView(this).apply {
            text = "📺  TV Remote"
            setTextColor(Color.WHITE)
            textSize = 13f
            id = View.generateViewId()
        }

        val closeBtn = buildIconButton("✕", 0xAAFF4444.toInt(), cornerRadius = dp(6f)).apply {
            id = View.generateViewId()
            setOnClickListener {
                removeOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        handle.addView(title, RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).also { it.addRule(RelativeLayout.CENTER_IN_PARENT) })

        handle.addView(closeBtn, RelativeLayout.LayoutParams(dp(34f), dp(34f)).also {
            it.addRule(RelativeLayout.ALIGN_PARENT_END)
            it.addRule(RelativeLayout.CENTER_VERTICAL)
        })

        // Make handle draggable
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = layoutParams?.x ?: 0
                    dragStartY = layoutParams?.y ?: 0
                    touchStartRawX = event.rawX
                    touchStartRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams?.apply {
                        x = dragStartX + (event.rawX - touchStartRawX).toInt()
                        y = dragStartY + (event.rawY - touchStartRawY).toInt()
                    }
                    windowManager?.updateViewLayout(overlayRoot, layoutParams)
                    true
                }
                else -> false
            }
        }

        return handle
    }

    /** D-pad: ▲ ◄ ● ► ▼ arranged in a cross */
    private fun buildDpad(): View {
        val area = RelativeLayout(this).apply {
            setBackgroundColor(0x221a1a2e.toInt())
        }

        // Directional buttons
        val btnUp    = buildDpadButton("▲")
        val btnDown  = buildDpadButton("▼")
        val btnLeft  = buildDpadButton("◄")
        val btnRight = buildDpadButton("►")

        // Centre OK button — circular purple
        val btnOk = Button(this).apply {
            text = "OK"
            setTextColor(Color.WHITE)
            textSize = 15f
            background = ovalBg(0xFF6c3fc4.toInt())
            setPadding(0, 0, 0, 0)
            setOnClickListener { RemoteAccessibilityService.performClick() }
        }

        val size = dp(58f)
        val margin = dp(12f)
        val centreOffset = (size + margin * 2) / 2   // half of (button + two gaps)

        area.addView(btnUp, RelativeLayout.LayoutParams(size, size).also {
            it.addRule(RelativeLayout.CENTER_HORIZONTAL)
            it.topMargin = margin
        })
        area.addView(btnDown, RelativeLayout.LayoutParams(size, size).also {
            it.addRule(RelativeLayout.CENTER_HORIZONTAL)
            it.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            it.bottomMargin = margin
        })
        area.addView(btnLeft, RelativeLayout.LayoutParams(size, size).also {
            it.addRule(RelativeLayout.CENTER_VERTICAL)
            it.addRule(RelativeLayout.ALIGN_PARENT_START)
            it.marginStart = margin
        })
        area.addView(btnRight, RelativeLayout.LayoutParams(size, size).also {
            it.addRule(RelativeLayout.CENTER_VERTICAL)
            it.addRule(RelativeLayout.ALIGN_PARENT_END)
            it.marginEnd = margin
        })
        area.addView(btnOk, RelativeLayout.LayoutParams(size, size).also {
            it.addRule(RelativeLayout.CENTER_IN_PARENT)
        })

        // Wire navigation
        btnUp.setOnClickListener    { RemoteAccessibilityService.navigate(View.FOCUS_UP) }
        btnDown.setOnClickListener  { RemoteAccessibilityService.navigate(View.FOCUS_DOWN) }
        btnLeft.setOnClickListener  { RemoteAccessibilityService.navigate(View.FOCUS_LEFT) }
        btnRight.setOnClickListener { RemoteAccessibilityService.navigate(View.FOCUS_RIGHT) }

        return area
    }

    /** Bottom row: Home | Menu/Recents | Back */
    private fun buildBottomRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedBottomBg(0xFF0f3460.toInt(), dp(18f))
            setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
        }

        val btnHome  = buildActionButton("⌂")
        val btnMenu  = buildActionButton("☰")
        val btnBack  = buildActionButton("↩")

        btnHome.setOnClickListener  { RemoteAccessibilityService.performHome() }
        btnMenu.setOnClickListener  { RemoteAccessibilityService.performMenu() }
        btnBack.setOnClickListener  { RemoteAccessibilityService.performBack() }

        val btnLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).also {
            it.marginStart = dp(4f)
            it.marginEnd   = dp(4f)
        }

        row.addView(btnHome, btnLp)
        row.addView(btnMenu, btnLp)
        row.addView(btnBack,  btnLp)

        return row
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun buildDpadButton(symbol: String) = Button(this).apply {
        text = symbol
        setTextColor(Color.WHITE)
        textSize = 20f
        background = roundedBg(0xFF16213e.toInt(), dp(10f)).also {
            (it as? GradientDrawable)?.setStroke(dp(1.5f), 0xFF533483.toInt())
        }
        setPadding(0, 0, 0, 0)
    }

    private fun buildActionButton(symbol: String) = Button(this).apply {
        text = symbol
        setTextColor(Color.WHITE)
        textSize = 18f
        background = roundedBg(0xFF533483.toInt(), dp(8f))
        setPadding(0, 0, 0, 0)
    }

    private fun buildIconButton(symbol: String, bgColor: Int, cornerRadius: Int) = Button(this).apply {
        text = symbol
        setTextColor(Color.WHITE)
        textSize = 14f
        background = roundedBg(bgColor, cornerRadius)
        setPadding(0, 0, 0, 0)
    }

    // ─── Drawable factories ───────────────────────────────────────────────────

    private fun roundedBg(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun roundedTopBg(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadii = floatArrayOf(
                radius.toFloat(), radius.toFloat(),   // top-left
                radius.toFloat(), radius.toFloat(),   // top-right
                0f, 0f,                               // bottom-right
                0f, 0f                                // bottom-left
            )
        }

    private fun roundedBottomBg(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadii = floatArrayOf(
                0f, 0f,                               // top-left
                0f, 0f,                               // top-right
                radius.toFloat(), radius.toFloat(),   // bottom-right
                radius.toFloat(), radius.toFloat()    // bottom-left
            )
        }

    private fun ovalBg(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2f), 0xFFa78bfa.toInt())
        }

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val hideIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).apply { action = ACTION_HIDE },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("TV Remote Active")
            .setContentText("Floating D-pad remote is showing")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hide Remote", hideIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ─── Layout param / dp helpers ────────────────────────────────────────────

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
            .toInt()
}
