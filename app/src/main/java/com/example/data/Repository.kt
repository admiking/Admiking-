package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import java.util.TimeZone

class HayatyRepository(
    private val context: Context,
    private val taskDao: TaskDao,
    private val habitDao: HabitDao,
    private val appUsageDao: AppUsageDao
) {
    // --- Task APIs ---
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    val activeTasks: Flow<List<Task>> = taskDao.getActiveTasks()

    suspend fun insertTask(task: Task): Long {
        val insertedId = taskDao.insertTask(task)
        // If it's an appointment, sync to Google Calendar natively if permission is granted
        if (task.isAppointment) {
            val updatedTask = task.copy(id = insertedId.toInt())
            val eventId = syncTaskToAndroidCalendar(updatedTask)
            if (eventId != null) {
                taskDao.updateTask(updatedTask.copy(googleEventId = eventId))
            }
        }
        com.example.widget.HayatyWidgetProvider.triggerUpdate(context)
        com.example.widget.HayatyTasksWidgetProvider.triggerUpdate(context)
        return insertedId
    }

    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task)
        com.example.widget.HayatyWidgetProvider.triggerUpdate(context)
        com.example.widget.HayatyTasksWidgetProvider.triggerUpdate(context)
    }

    suspend fun deleteTask(task: Task) {
        // Natively delete from calendar if event ID exists
        task.googleEventId?.let { eventId ->
            deleteFromAndroidCalendar(eventId)
        }
        taskDao.deleteTaskById(task.id)
        com.example.widget.HayatyWidgetProvider.triggerUpdate(context)
        com.example.widget.HayatyTasksWidgetProvider.triggerUpdate(context)
    }

    // --- Habit APIs ---
    val allHabits: Flow<List<Habit>> = habitDao.getAllHabits()

    suspend fun insertHabit(habit: Habit): Long {
        return habitDao.insertHabit(habit)
    }

    suspend fun deleteHabit(habitId: Int) {
        habitDao.deleteHabitById(habitId)
    }

    fun getLogsForHabit(habitId: Int): Flow<List<HabitLog>> = habitDao.getLogsForHabit(habitId)
    
    fun getLogsForDate(date: String): Flow<List<HabitLog>> = habitDao.getLogsForDate(date)

    val allHabitLogs: Flow<List<HabitLog>> = habitDao.getAllHabitLogs()

    suspend fun toggleHabitCompletion(habit: Habit, date: String, isCompleted: Boolean) {
        if (isCompleted) {
            habitDao.insertHabitLog(HabitLog(habitId = habit.id, date = date))
            // Update streak
            val newStreak = if (habit.lastCompletedDate == null) {
                1
            } else {
                habit.streak + 1
            }
            habitDao.insertHabit(habit.copy(streak = newStreak, lastCompletedDate = date))
        } else {
            habitDao.deleteHabitLog(habit.id, date)
            val newStreak = maxOf(0, habit.streak - 1)
            habitDao.insertHabit(habit.copy(streak = newStreak, lastCompletedDate = null))
        }
    }

    // --- Usage Record APIs ---
    fun getUsageForDate(date: String): Flow<List<AppUsageRecord>> = appUsageDao.getUsageForDate(date)

    suspend fun insertUsageRecord(record: AppUsageRecord) {
        appUsageDao.insertUsageRecord(record)
    }

    suspend fun deleteUsageForDate(date: String) {
        appUsageDao.deleteUsageForDate(date)
    }

    // --- Native Google Calendar Interface via CalendarContract ---
    
    private fun syncTaskToAndroidCalendar(task: Task): String? {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) 
            != PackageManager.PERMISSION_GRANTED) return null

        return try {
            val cr = context.contentResolver
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, task.dueDate)
                put(CalendarContract.Events.DTEND, task.dueDate + (60 * 60 * 1000)) // Default 1 Hour
                put(CalendarContract.Events.TITLE, task.title)
                put(CalendarContract.Events.DESCRIPTION, task.description)
                put(CalendarContract.Events.CALENDAR_ID, 1) // Default primary system calendar
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            val uri = cr.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun deleteFromAndroidCalendar(eventId: String) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) 
            != PackageManager.PERMISSION_GRANTED) return

        try {
            val cr = context.contentResolver
            val deleteUri = CalendarContract.Events.CONTENT_URI
            cr.delete(deleteUri, "${CalendarContract.Events._ID} = ?", arrayOf(eventId))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
