package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.R
import com.example.data.FiqhData
import java.util.Calendar

class HayatyFiqhWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateFiqhWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE || 
            intent.action == "com.example.widget.UPDATE_WIDGET_DATA") {
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, HayatyFiqhWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateFiqhWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    companion object {
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, HayatyFiqhWidgetProvider::class.java).apply {
                action = "com.example.widget.UPDATE_WIDGET_DATA"
            }
            context.sendBroadcast(intent)
        }
    }
}

private fun updateFiqhWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.hayaty_fiqh_widget)

    val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val questions = FiqhData.questions
    val currentQuestion = if (questions.isNotEmpty()) {
        questions[dayOfYear % questions.size]
    } else {
        null
    }

    if (currentQuestion != null) {
        views.setTextViewText(R.id.widget_category, "الباب: ${currentQuestion.category} 📖")
        views.setTextViewText(R.id.widget_fiqh_question, currentQuestion.question)
    } else {
        views.setTextViewText(R.id.widget_category, "العلم الشرعي 📖")
        views.setTextViewText(R.id.widget_fiqh_question, "لا توجد أسئلة فقهية متوفرة اليوم.")
    }

    // Launch main app when clicked
    val appIntent = Intent(context, com.example.MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("ROUTE", "fiqh") // to deep-link or focus on Fiqh questions tab
    }
    val pendingApp = PendingIntent.getActivity(
        context,
        201,
        appIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, pendingApp)
    views.setOnClickPendingIntent(R.id.widget_action_row, pendingApp)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}
