package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.R
import com.example.data.AppDatabase
import com.example.util.PrayerTimesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HayatyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult: android.content.BroadcastReceiver.PendingResult? = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    pendingResult?.finish()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        try {
            for (appWidgetId in appWidgetIds) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                appWidgetManager.updateAppWidget(appWidgetId, RemoteViews(context.packageName, R.layout.hayaty_widget))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == "com.example.widget.UPDATE_WIDGET_DATA") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, HayatyWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            val pendingResult: android.content.BroadcastReceiver.PendingResult? = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        pendingResult?.finish()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } else if (action == "com.example.widget.TOGGLE_CITY") {
            val pendingResult: android.content.BroadcastReceiver.PendingResult? = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = context.getSharedPreferences("AzkarPrefs", Context.MODE_PRIVATE)
                    val currentCity = prefs.getString("SelectedCity", "مكة المكرمة") ?: "مكة المكرمة"
                    val cities = com.example.util.PrayerTimesHelper.cities
                    val currentIndex = cities.indexOf(currentCity)
                    val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % cities.size
                    val nextCity = cities[nextIndex]
                    
                    prefs.edit().putString("SelectedCity", nextCity).commit()
                    
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val componentName = ComponentName(context, HayatyWidgetProvider::class.java)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        pendingResult?.finish()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } else if (action == "com.example.widget.INCREMENT_TASBIH") {
            val pendingResult: android.content.BroadcastReceiver.PendingResult? = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = context.getSharedPreferences("AzkarPrefs", Context.MODE_PRIVATE)
                    val currentCount = prefs.getInt("WidgetTasbihCount", 0)
                    prefs.edit().putInt("WidgetTasbihCount", currentCount + 1).commit()
                    
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val componentName = ComponentName(context, HayatyWidgetProvider::class.java)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        pendingResult?.finish()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    companion object {
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, HayatyWidgetProvider::class.java).apply {
                action = "com.example.widget.UPDATE_WIDGET_DATA"
            }
            context.sendBroadcast(intent)
        }
    }
}

private suspend fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    try {
        val prefs = context.getSharedPreferences("AzkarPrefs", Context.MODE_PRIVATE)
        val city = prefs.getString("SelectedCity", "مكة المكرمة") ?: "مكة المكرمة"
        val isUsingGps = prefs.getBoolean("IsUsingGps", false)
        val widgetTasbihCount = prefs.getInt("WidgetTasbihCount", 0)

        // 1. Calculate next prayer - with error handling
        val nextPrayer = try {
            getNextPrayerTime(context)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair("خطأ", "غير متاح")
        }

        // 2. Query nearest task from Room Database - with null safety
        var nearestTask: com.example.data.Task? = null
        try {
            val db = AppDatabase.getDatabase(context)
            val activeTasks = db.taskDao().getActiveTasks().firstOrNull() ?: emptyList()
            nearestTask = activeTasks.firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Build Remote Views
        val views = RemoteViews(context.packageName, R.layout.hayaty_widget)
        val displayLocation = if (isUsingGps) "موقعي الحالي 📍" else "$city 📍"
        views.setTextViewText(R.id.widget_location, displayLocation)
        views.setTextViewText(R.id.widget_prayer_name, nextPrayer.first)
        views.setTextViewText(R.id.widget_prayer_time, nextPrayer.second)

        // Format Tasbih count to Arabic digits
        val arabicTasbih = toArabicDigits(widgetTasbihCount)
        views.setTextViewText(R.id.widget_tasbih_text, "📿 تسبيح سريع: $arabicTasbih (اضغط للزيادة)")

        if (nearestTask != null) {
            views.setTextViewText(R.id.widget_task_title, nearestTask.title)
            views.setTextViewText(R.id.widget_task_sub, "المجموعة: ${nearestTask.category}")
        } else {
            views.setTextViewText(R.id.widget_task_title, "لا توجد مهام قادمة 🌿")
            views.setTextViewText(R.id.widget_task_sub, "يومك مبارك وسعيد!")
        }

        // click pending intent to launch main activity on widget root
        val intent = Intent(context, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        // Interactive city click intent
        val cityIntent = Intent(context, HayatyWidgetProvider::class.java).apply {
            action = "com.example.widget.TOGGLE_CITY"
        }
        val pendingCity = PendingIntent.getBroadcast(
            context,
            appWidgetId + 60000,
            cityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_location, pendingCity)

        // Interactive Tasbih click intent
        val tasbihIntent = Intent(context, HayatyWidgetProvider::class.java).apply {
            action = "com.example.widget.INCREMENT_TASBIH"
        }
        val pendingTasbih = PendingIntent.getBroadcast(
            context,
            appWidgetId + 70000,
            tasbihIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_tasbih_row, pendingTasbih)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun toArabicDigits(number: Int): String {
    val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    return number.toString().map { if (it.isDigit()) arabicDigits[it - '0'] else it }.joinToString("")
}

private fun getNextPrayerTime(context: Context): Pair<String, String> {
    return try {
        val prefs = context.getSharedPreferences("AzkarPrefs", Context.MODE_PRIVATE)
        val city = prefs.getString("SelectedCity", "مكة المكرمة") ?: "مكة المكرمة"
        val isUsingGps = prefs.getBoolean("IsUsingGps", false)
        val lat = if (prefs.contains("GpsLatitude")) prefs.getFloat("GpsLatitude", 0f).toDouble() else null
        val lon = if (prefs.contains("GpsLongitude")) prefs.getFloat("GpsLongitude", 0f).toDouble() else null

        val now = Calendar.getInstance()
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        val currentTimeStr = sdf.format(now.time)

        val todayTimes = if (isUsingGps && lat != null && lon != null) {
            PrayerTimesHelper.getPrayerTimesForCoordinates(lat, lon, now.time)
        } else {
            PrayerTimesHelper.getPrayerTimesForCity(city, now.time)
        }

        // FIX: Use proper time comparison instead of string comparison
        var nextPrayer = todayTimes.firstOrNull { compareTime(it.time, currentTimeStr) > 0 }
        
        if (nextPrayer == null) {
            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val tomorrowTimes = if (isUsingGps && lat != null && lon != null) {
                PrayerTimesHelper.getPrayerTimesForCoordinates(lat, lon, tomorrow.time)
            } else {
                PrayerTimesHelper.getPrayerTimesForCity(city, tomorrow.time)
            }
            val tomorrowFajr = tomorrowTimes.firstOrNull { it.name == "Fajr" } ?: tomorrowTimes.firstOrNull()
            if (tomorrowFajr != null) {
                Pair("الفجر صباحاً", "غداً ${formatTimeToTwelveHour(tomorrowFajr.time)}")
            } else {
                Pair("الفجر", "غداً")
            }
        } else {
            Pair(nextPrayer.arabicName, formatTimeToTwelveHour(nextPrayer.time))
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Pair("خطأ", "غير متاح")
    }
}

// Helper function to properly compare times
private fun compareTime(time1: String, time2: String): Int {
    return try {
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        val date1 = sdf.parse(time1)
        val date2 = sdf.parse(time2)
        if (date1 != null && date2 != null) {
            date1.compareTo(date2)
        } else {
            0
        }
    } catch (e: Exception) {
        0
    }
}

private fun formatTimeToTwelveHour(time24: String): String {
    return try {
        val sdf24 = SimpleDateFormat("HH:mm", Locale.US)
        val sdf12 = SimpleDateFormat("hh:mm a", Locale("ar"))
        val date = sdf24.parse(time24)
        if (date != null) {
            val formatted = sdf12.format(date)
            formatted.replace("AM", "ص").replace("PM", "م")
        } else {
            time24
        }
    } catch (e: Exception) {
        time24
    }
}
