package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.R
import com.example.data.AppDatabase
import com.example.data.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class HayatyTasksWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateTasksWidget(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult?.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        
        if (action == "com.example.widget.UPDATE_WIDGET_DATA") {
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, HayatyTasksWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    for (appWidgetId in appWidgetIds) {
                        updateTasksWidget(context, appWidgetManager, appWidgetId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult?.finish()
                }
            }
        } else if (action == "com.example.widget.TOGGLE_TASK_COMPLETE") {
            val taskId = intent.getIntExtra("task_id", -1)
            if (taskId != -1) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val task = db.taskDao().getTaskById(taskId)
                        if (task != null) {
                            val updatedTask = task.copy(isCompleted = !task.isCompleted)
                            db.taskDao().updateTask(updatedTask)
                            
                            // Trigger update to all widgets (both prayer and checklist widget)
                            triggerUpdate(context)
                            HayatyWidgetProvider.triggerUpdate(context)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult?.finish()
                    }
                }
            }
        } else if (action == "com.example.widget.DELETE_TASK") {
            val taskId = intent.getIntExtra("task_id", -1)
            if (taskId != -1) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val task = db.taskDao().getTaskById(taskId)
                        if (task != null) {
                            db.taskDao().deleteTaskById(taskId)
                            
                            // Trigger update to all widgets
                            triggerUpdate(context)
                            HayatyWidgetProvider.triggerUpdate(context)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult?.finish()
                    }
                }
            }
        }
    }

    companion object {
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, HayatyTasksWidgetProvider::class.java).apply {
                action = "com.example.widget.UPDATE_WIDGET_DATA"
            }
            context.sendBroadcast(intent)
        }
    }
}

private suspend fun updateTasksWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val db = AppDatabase.getDatabase(context)
    val allTasks = db.taskDao().getAllTasks().firstOrNull() ?: emptyList()
    
    // Select the top 3 uncompleted tasks (or recent completed ones) to render
    // Let us grab top 3 uncompleted, if less, populate with a few completed ones
    val pending = allTasks.filter { !it.isCompleted }.take(3)
    val remainingSlots = 3 - pending.size
    val completed = if (remainingSlots > 0) allTasks.filter { it.isCompleted }.take(remainingSlots) else emptyList()
    val tasksToRender = pending + completed

    val views = RemoteViews(context.packageName, R.layout.hayaty_tasks_widget)

    if (tasksToRender.isEmpty()) {
        views.setViewVisibility(R.id.widget_row_1, View.GONE)
        views.setViewVisibility(R.id.widget_row_2, View.GONE)
        views.setViewVisibility(R.id.widget_row_3, View.GONE)
        views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE)
    } else {
        views.setViewVisibility(R.id.widget_empty_state, View.GONE)
        
        val rowIds = listOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3)
        val titleIds = listOf(R.id.widget_task_title_1, R.id.widget_task_title_2, R.id.widget_task_title_3)
        val catIds = listOf(R.id.widget_task_cat_1, R.id.widget_task_cat_2, R.id.widget_task_cat_3)
        val checkIds = listOf(R.id.widget_task_check_1, R.id.widget_task_check_2, R.id.widget_task_check_3)
        val deleteIds = listOf(R.id.widget_task_delete_1, R.id.widget_task_delete_2, R.id.widget_task_delete_3)

        for (i in 0 until 3) {
            if (i < tasksToRender.size) {
                val task = tasksToRender[i]
                views.setViewVisibility(rowIds[i], View.VISIBLE)
                views.setTextViewText(titleIds[i], task.title)
                views.setTextViewText(catIds[i], task.category)
                views.setTextViewText(checkIds[i], if (task.isCompleted) "✅" else "⬜")

                // Click Intent for the checkmark button
                val checkIntent = Intent(context, HayatyTasksWidgetProvider::class.java).apply {
                    action = "com.example.widget.TOGGLE_TASK_COMPLETE"
                    putExtra("task_id", task.id)
                }
                
                // Be careful to use unique requestCode per slot to prevent Android from caching identical intents
                val pendingCheckIntent = PendingIntent.getBroadcast(
                    context,
                    task.id,
                    checkIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(checkIds[i], pendingCheckIntent)

                // Click Intent for the delete button
                val deleteIntent = Intent(context, HayatyTasksWidgetProvider::class.java).apply {
                    action = "com.example.widget.DELETE_TASK"
                    putExtra("task_id", task.id)
                }
                val pendingDeleteIntent = PendingIntent.getBroadcast(
                    context,
                    task.id + 10000,
                    deleteIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(deleteIds[i], pendingDeleteIntent)
            } else {
                views.setViewVisibility(rowIds[i], View.GONE)
            }
        }
    }

    // Click Intent for the "Add task" button in the widget header
    val addTaskIntent = Intent(context, com.example.MainActivity::class.java).apply {
        action = "com.example.action.ADD_TASK"
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingAddTaskIntent = PendingIntent.getActivity(
        context,
        101,
        addTaskIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_add_task, pendingAddTaskIntent)

    // click pending intent on the entire widget or title card to launch the main app
    val appIntent = Intent(context, com.example.MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingAppIntent = PendingIntent.getActivity(
        context,
        99,
        appIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, pendingAppIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}
