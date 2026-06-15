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
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE || 
            intent.action == "com.example.widget.UPDATE_WIDGET_DATA") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, HayatyWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
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
    val prefs = context.getSharedPreferences("AzkarPrefs", Context.MODE_PRIVATE)
    val city = prefs.getString("SelectedCity", "مكة المكرمة") ?: "مكة المكرمة"

    // 1. Calculate next prayer
    val nextPrayer = getNextPrayerTime(city)

    // 2. Query nearest task from Room Database
    val db = AppDatabase.getDatabase(context)
    val activeTasks = db.taskDao().getActiveTasks().firstOrNull() ?: emptyList()
    val nearestTask = activeTasks.firstOrNull()

    // 3. Build Remote Views
    val views = RemoteViews(context.packageName, R.layout.hayaty_widget)
    views.setTextViewText(R.id.widget_location, "$city 📍")
    views.setTextViewText(R.id.widget_prayer_name, nextPrayer.first)
    views.setTextViewText(R.id.widget_prayer_time, nextPrayer.second)

    if (nearestTask != null) {
        views.setTextViewText(R.id.widget_task_title, nearestTask.title)
        views.setTextViewText(R.id.widget_task_sub, "المجموعة: ${nearestTask.category}")
    } else {
        views.setTextViewText(R.id.widget_task_title, "لا توجد مهام قادمة 🌿")
        views.setTextViewText(R.id.widget_task_sub, "يومك مبارك وسعيد!")
    }

    // click pending intent to launch main activity
    val intent = Intent(context, com.example.MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

private fun getNextPrayerTime(city: String): Pair<String, String> {
    val now = Calendar.getInstance()
    val sdf = SimpleDateFormat("HH:mm", Locale.US)
    val currentTimeStr = sdf.format(now.time)

    val todayTimes = PrayerTimesHelper.getPrayerTimesForCity(city, now.time)

    var nextPrayer = todayTimes.firstOrNull { it.time > currentTimeStr }
    if (nextPrayer == null) {
        // next prayer is tomorrow's Fajr
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowTimes = PrayerTimesHelper.getPrayerTimesForCity(city, tomorrow.time)
        val tomorrowFajr = tomorrowTimes.firstOrNull { it.name == "Fajr" } ?: tomorrowTimes.first()
        return Pair("الفجر صباحاً", "غداً ${formatTimeToTwelveHour(tomorrowFajr.time)}")
    }

    return Pair(nextPrayer.arabicName, formatTimeToTwelveHour(nextPrayer.time))
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
