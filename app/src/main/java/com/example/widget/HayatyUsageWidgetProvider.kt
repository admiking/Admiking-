package com.example.widget

import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.RemoteViews
import com.example.R
import com.example.data.AppDatabase
import com.example.data.AppUsageRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HayatyUsageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult: android.content.BroadcastReceiver.PendingResult? = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateUsageWidget(context, appWidgetManager, appWidgetId)
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == "com.example.widget.UPDATE_WIDGET_DATA") {
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, HayatyUsageWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            val pendingResult: android.content.BroadcastReceiver.PendingResult? = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    for (appWidgetId in appWidgetIds) {
                        updateUsageWidget(context, appWidgetManager, appWidgetId)
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
        } else if (action == "com.example.widget.REFRESH_USAGE_STATS") {
            val pendingResult: android.content.BroadcastReceiver.PendingResult? = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    refreshDeviceUsageStats(context)
                    
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val componentName = ComponentName(context, HayatyUsageWidgetProvider::class.java)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                    for (appWidgetId in appWidgetIds) {
                        updateUsageWidget(context, appWidgetManager, appWidgetId)
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // Clean up when widget is removed from home screen
        super.onDeleted(context, appWidgetIds)
        try {
            for (appWidgetId in appWidgetIds) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                appWidgetManager.updateAppWidget(appWidgetId, RemoteViews(context.packageName, R.layout.hayaty_usage_widget))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDisabled(context: Context) {
        // Called when the last widget instance of this provider is deleted
        super.onDisabled(context)
        try {
            // Clean up any global resources or listeners here
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, HayatyUsageWidgetProvider::class.java).apply {
                action = "com.example.widget.UPDATE_WIDGET_DATA"
            }
            context.sendBroadcast(intent)
        }
    }
}

private suspend fun updateUsageWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.hayaty_usage_widget)
    val db = AppDatabase.getDatabase(context)
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    
    val records = db.appUsageDao().getUsageForDate(todayStr).firstOrNull() ?: emptyList()

    if (records.isEmpty()) {
        views.setViewVisibility(R.id.widget_use_row_1, View.GONE)
        views.setViewVisibility(R.id.widget_use_row_2, View.GONE)
        views.setViewVisibility(R.id.widget_use_row_3, View.GONE)
        views.setViewVisibility(R.id.widget_use_empty, View.VISIBLE)
        views.setTextViewText(R.id.widget_total_usage_text, "إجمالي الوقت: لا توجد بيانات")
    } else {
        views.setViewVisibility(R.id.widget_use_empty, View.GONE)
        
        val totalMs = records.sumOf { it.durationMs }
        val totalMinutes = totalMs / 60000
        val totalHours = totalMinutes / 60
        val remainingMin = totalMinutes % 60
        
        val hoursStr = toArabicDigits(totalHours.toInt())
        val minsStr = toArabicDigits(remainingMin.toInt())
        views.setTextViewText(
            R.id.widget_total_usage_text,
            "إجمالي الوقت: $hoursStr س و $minsStr د"
        )

        val rowIds = listOf(R.id.widget_use_row_1, R.id.widget_use_row_2, R.id.widget_use_row_3)
        val appIds = listOf(R.id.widget_use_app_1, R.id.widget_use_app_2, R.id.widget_use_app_3)
        val timeIds = listOf(R.id.widget_use_time_1, R.id.widget_use_time_2, R.id.widget_use_time_3)

        for (i in 0 until 3) {
            if (i < records.size) {
                val record = records[i]
                views.setViewVisibility(rowIds[i], View.VISIBLE)
                views.setTextViewText(appIds[i], record.appName)
                
                val appMin = record.durationMs / 60000
                views.setTextViewText(timeIds[i], "${toArabicDigits(appMin.toInt())} د")
            } else {
                views.setViewVisibility(rowIds[i], View.GONE)
            }
        }
    }

    // Launch main app when clicked
    val appIntent = Intent(context, com.example.MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("ROUTE", "usage")
    }
    val pendingApp = PendingIntent.getActivity(
        context,
        appWidgetId + 40000,
        appIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, pendingApp)

    // Refresh action
    val refreshIntent = Intent(context, HayatyUsageWidgetProvider::class.java).apply {
        action = "com.example.widget.REFRESH_USAGE_STATS"
    }
    val pendingRefresh = PendingIntent.getBroadcast(
        context,
        appWidgetId + 50000,
        refreshIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_usage_refresh, pendingRefresh)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

private fun toArabicDigits(number: Int): String {
    val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    return number.toString().map { if (it.isDigit()) arabicDigits[it - '0'] else it }.joinToString("")
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
        if (appOps != null) {
            val mode = appOps.noteOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}

private suspend fun refreshDeviceUsageStats(context: Context) {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
    if (!hasUsageStatsPermission(context)) return

    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startTime = cal.timeInMillis
    val endTime = System.currentTimeMillis()

    val stats = usageStatsManager.queryUsageStats(
        UsageStatsManager.INTERVAL_DAILY,
        startTime,
        endTime
    ) ?: return

    val db = AppDatabase.getDatabase(context)
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    
    val filteredStats = stats.filter { it.totalTimeInForeground > 60000 }
    val sortedStats = filteredStats.sortedByDescending { it.totalTimeInForeground }

    if (sortedStats.isNotEmpty()) {
        db.appUsageDao().deleteUsageForDate(todayStr)
        val pm = context.packageManager
        
        for (usage in sortedStats) {
            val appLabel = try {
                pm.getApplicationLabel(pm.getApplicationInfo(usage.packageName, 0)).toString()
            } catch (e: Exception) {
                usage.packageName.substringAfterLast('.')
            }
            db.appUsageDao().insertUsageRecord(
                AppUsageRecord(
                    packageName = usage.packageName,
                    appName = appLabel,
                    durationMs = usage.totalTimeInForeground,
                    date = todayStr
                )
            )
        }
    }
}
