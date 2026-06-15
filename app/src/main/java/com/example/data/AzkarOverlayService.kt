package com.example.data

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.random.Random

class AzkarOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "AzkarOverlayNotificationChannel"
        const val NOTIFICATION_ID = 5005
        
        // Settings Keys (shared with MainActivity)
        const val PREFS_NAME = "AzkarPrefs"
        const val KEY_ENABLED = "AzkarEnabled"
        const val KEY_INTERVAL = "AzkarInterval" // in minutes
        const val KEY_PREF_MORNING = "PrefMorning"
        const val KEY_PREF_EVENING = "PrefEvening"
        const val KEY_PREF_GENERAL = "PrefGeneral"
        
        fun startService(context: Context) {
            val intent = Intent(context, AzkarOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, AzkarOverlayService::class.java)
            context.stopService(intent)
        }
    }

    private var serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private lateinit var windowManager: WindowManager
    private var activeFloatingView: View? = null
    
    // Shared Preferences
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Create foreground notification to satisfy modern Android target APIs
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // Reset and start scheduler
        serviceJob.cancel()
        serviceJob = Job()
        startAzkarScheduler()
        
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🌾 التذكير بذكر الله طوال النهار نشط")
            .setContentText("نقوم بتذكيرك بالأذكار والأقوال المأثورة تلقائياً")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تنبيهات ذكر الله اليومية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "قناة تنبيهات مستمرة لعرض الأذكار فوق التطبيقات لدوام الأجر"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startAzkarScheduler() {
        val intervalMinutes = prefs.getInt(KEY_INTERVAL, 5) // default 5 minutes
        val isEnabled = prefs.getBoolean(KEY_ENABLED, true)
        
        if (!isEnabled) {
            stopSelf()
            return
        }

        serviceScope.launch {
            while (isActive) {
                // Wait for the specified interval
                delay(intervalMinutes * 60 * 1000L)
                
                // Show floating overlay
                showAzkarOverlay()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showAzkarOverlay() {
        // Check if drawing overlay is permitted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            return
        }

        // Dismiss existing overlay if any
        dismissOverlay()

        val zikrText = getRandomZikr()

        // 1. Layout Params
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 120 // Position slightly down from status bar
            // Narrow width on larger screens for adaptive design
            width = (resources.displayMetrics.widthPixels * 0.92).toInt()
        }

        // 2. Programmatic Layout construction
        val context = this
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val px = dpToPx(16)
            setPadding(px, px, px, px)
            
            // Background with rounded corners and warm glow
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24).toFloat()
                // Warm-slate semi-transparent color matching our application identity
                setColor(0xEE1A1A24.toInt())
                setStroke(2, 0xFF3D3D4E.toInt())
            }
            
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Header Section (Zikr Topic + Drag Handle indicator)
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dpToPx(10))
            }
        }

        val starIcon = TextView(context).apply {
            text = "🌟"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, dpToPx(6), 0)
            }
        }

        val headerText = TextView(context).apply {
            text = "تذكير بذكر الله طوال النهار 🌾"
            setTextColor(0xFFFFD700.toInt()) // beautiful gold color
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.RIGHT
            textDirection = View.TEXT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val dragIndicator = TextView(context).apply {
            text = "✥" // Move overlay indicator
            setTextColor(Color.GRAY)
            textSize = 14f
            gravity = Gravity.LEFT
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        headerLayout.addView(starIcon)
        headerLayout.addView(headerText)
        headerLayout.addView(dragIndicator)

        // Main Zikr Content Text
        val zikrTextView = TextView(context).apply {
            text = zikrText
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setLineSpacing(3f, 1.1f)
            textDirection = View.TEXT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(4), 0, dpToPx(18))
            }
        }

        // Action Buttons Row (Close + Alternative Zikr)
        val buttonsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val closeButton = Button(context).apply {
            text = "إغلاق ✕"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            
            // Style button with nice subtle rounded outline
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(14).toFloat()
                setColor(0x33FF0000.toInt()) // dark red translucent
                setStroke(1, 0x88FF4444.toInt())
            }
            
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(38),
                1f
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            
            setOnClickListener {
                dismissOverlay()
            }
        }

        val anotherButton = Button(context).apply {
            text = "ذكر آخر ⇆"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            
            // Style button with nice subtle rounded outline
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(14).toFloat()
                setColor(0x444CAF50.toInt()) // dark green translucent
                setStroke(1, 0x8881C784.toInt())
            }
            
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(38),
                1.5f
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            
            setOnClickListener {
                zikrTextView.text = getRandomZikr()
            }
        }

        buttonsLayout.addView(closeButton)
        buttonsLayout.addView(anotherButton)

        container.addView(headerLayout)
        container.addView(zikrTextView)
        container.addView(buttonsLayout)

        // Drag handle touch controller
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(container, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(container, params)
            activeFloatingView = container
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dismissOverlay() {
        activeFloatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            activeFloatingView = null
        }
    }

    private fun getRandomZikr(): String {
        val showMorning = prefs.getBoolean(KEY_PREF_MORNING, true)
        val showEvening = prefs.getBoolean(KEY_PREF_EVENING, true)
        val showGeneral = prefs.getBoolean(KEY_PREF_GENERAL, true)
        
        val list = mutableListOf<String>()
        if (showMorning) {
            list.addAll(AzkarData.morningAzkar.map { it.text })
        }
        if (showEvening) {
            list.addAll(AzkarData.eveningAzkar.map { it.text })
        }
        if (showGeneral || list.isEmpty()) {
            list.addAll(AzkarData.generalDaytimeAzkar)
        }
        
        return list[Random.nextInt(list.size)]
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        dismissOverlay()
        serviceJob.cancel()
        super.onDestroy()
    }
}
