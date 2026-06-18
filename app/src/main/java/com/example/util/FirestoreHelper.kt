package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.Habit
import com.example.data.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// Custom .await() extension for play-services Task to avoid extra library dependencies
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = suspendCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(task.exception ?: RuntimeException("Unknown Firebase/Firestore error"))
        }
    }
}

object FirestoreHelper {
    private const val TAG = "FirestoreHelper"
    private var firestoreInstance: FirebaseFirestore? = null

    fun getFirestore(context: Context): FirebaseFirestore {
        if (firestoreInstance == null) {
            try {
                FirebaseApp.getInstance()
            } catch (e: IllegalStateException) {
                // Programmatic backup fallback initialization so it works withoutgoogle-services.json
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:555555555555:android:a1b2c3d4e5f6")
                    .setProjectId("hayaty-app-project")
                    .setApiKey("AIzaSyMockApiKeyForProgrammaticFirestoreInit")
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options)
                Log.d(TAG, "Firebase initialized programmatically.")
            }
            firestoreInstance = FirebaseFirestore.getInstance()
        }
        return firestoreInstance!!
    }

    // --- Task CRUD ---
    suspend fun saveTask(context: Context, task: Task) {
        try {
            val db = getFirestore(context)
            val data = hashMapOf(
                "id" to task.id,
                "title" to task.title,
                "description" to task.description,
                "dueDate" to task.dueDate,
                "isCompleted" to task.isCompleted,
                "isAppointment" to task.isAppointment,
                "category" to task.category,
                "googleEventId" to task.googleEventId
            )
            db.collection("tasks").document(task.id.toString())
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "Task saved successfully to Firestore: ${task.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving task to Firestore", e)
        }
    }

    suspend fun deleteTask(context: Context, taskId: Int) {
        try {
            val db = getFirestore(context)
            db.collection("tasks").document(taskId.toString())
                .delete()
                .await()
            Log.d(TAG, "Task deleted from Firestore: $taskId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting task from Firestore", e)
        }
    }

    suspend fun getTasks(context: Context): List<Task> {
        return try {
            val db = getFirestore(context)
            val snapshot = db.collection("tasks").get().await()
            snapshot.documents.mapNotNull { doc ->
                try {
                    val id = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: 0
                    val title = doc.getString("title") ?: "بدون عنوان"
                    val description = doc.getString("description") ?: ""
                    val dueDate = doc.getLong("dueDate") ?: System.currentTimeMillis()
                    val isCompleted = doc.getBoolean("isCompleted") ?: false
                    val isAppointment = doc.getBoolean("isAppointment") ?: false
                    val category = doc.getString("category") ?: "عام"
                    val googleEventId = doc.getString("googleEventId")
                    Task(id, title, description, dueDate, isCompleted, isAppointment, category, googleEventId)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching tasks from Firestore", e)
            emptyList()
        }
    }

    // --- Habit CRUD ---
    suspend fun saveHabit(context: Context, habit: Habit) {
        try {
            val db = getFirestore(context)
            val data = hashMapOf(
                "id" to habit.id,
                "name" to habit.name,
                "streak" to habit.streak,
                "lastCompletedDate" to habit.lastCompletedDate,
                "icon" to habit.icon,
                "category" to habit.category,
                "targetDurationMinutes" to habit.targetDurationMinutes,
                "reminderTime" to habit.reminderTime,
                "aiExpectedDays" to habit.aiExpectedDays,
                "aiExplanation" to habit.aiExplanation,
                "targetAppPackage" to habit.targetAppPackage
            )
            db.collection("habits").document(habit.id.toString())
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "Habit saved to Firestore: ${habit.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving habit to Firestore", e)
        }
    }

    suspend fun deleteHabit(context: Context, habitId: Int) {
        try {
            val db = getFirestore(context)
            db.collection("habits").document(habitId.toString())
                .delete()
                .await()
            Log.d(TAG, "Habit deleted from Firestore: $habitId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting habit from Firestore", e)
        }
    }

    suspend fun getHabits(context: Context): List<Habit> {
        return try {
            val db = getFirestore(context)
            val snapshot = db.collection("habits").get().await()
            snapshot.documents.mapNotNull { doc ->
                try {
                    val id = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: 0
                    val name = doc.getString("name") ?: "بدون اسم"
                    val streak = doc.getLong("streak")?.toInt() ?: 0
                    val lastCompletedDate = doc.getString("lastCompletedDate")
                    val icon = doc.getString("icon") ?: "✨"
                    val category = doc.getString("category") ?: "عام"
                    val targetDurationMinutes = doc.getLong("targetDurationMinutes")?.toInt() ?: 15
                    val reminderTime = doc.getString("reminderTime")
                    val aiExpectedDays = doc.getLong("aiExpectedDays")?.toInt() ?: 21
                    val aiExplanation = doc.getString("aiExplanation")
                    val targetAppPackage = doc.getString("targetAppPackage")
                    Habit(
                        id = id,
                        name = name,
                        streak = streak,
                        lastCompletedDate = lastCompletedDate,
                        icon = icon,
                        category = category,
                        targetDurationMinutes = targetDurationMinutes,
                        reminderTime = reminderTime,
                        aiExpectedDays = aiExpectedDays,
                        aiExplanation = aiExplanation,
                        targetAppPackage = targetAppPackage
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching habits from Firestore", e)
            emptyList()
        }
    }

    // --- Daily Schedule CRUD ---
    data class DailySchedule(
        val id: String = "",
        val title: String = "",
        val timeSlot: String = "08:00",
        val date: String = "",
        val note: String = ""
    )

    suspend fun saveDailySchedule(context: Context, schedule: DailySchedule) {
        try {
            val db = getFirestore(context)
            val data = hashMapOf(
                "id" to schedule.id,
                "title" to schedule.title,
                "timeSlot" to schedule.timeSlot,
                "date" to schedule.date,
                "note" to schedule.note
            )
            val docRef = if (schedule.id.isBlank()) {
                db.collection("daily_schedules").document()
            } else {
                db.collection("daily_schedules").document(schedule.id)
            }
            val finalId = docRef.id
            if (schedule.id.isBlank()) {
                data["id"] = finalId
            }
            docRef.set(data, SetOptions.merge()).await()
            Log.d(TAG, "Daily schedule saved with ID: $finalId")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving schedule to Firestore", e)
        }
    }

    suspend fun deleteDailySchedule(context: Context, scheduleId: String) {
        try {
            val db = getFirestore(context)
            db.collection("daily_schedules").document(scheduleId)
                .delete()
                .await()
            Log.d(TAG, "Daily schedule deleted from Firestore: $scheduleId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting schedule from Firestore", e)
        }
    }

    suspend fun getDailySchedules(context: Context): List<DailySchedule> {
        return try {
            val db = getFirestore(context)
            val snapshot = db.collection("daily_schedules").get().await()
            snapshot.documents.mapNotNull { doc ->
                try {
                    val id = doc.getString("id") ?: doc.id
                    val title = doc.getString("title") ?: ""
                    val timeSlot = doc.getString("timeSlot") ?: "08:00"
                    val date = doc.getString("date") ?: ""
                    val note = doc.getString("note") ?: ""
                    DailySchedule(id, title, timeSlot, date, note)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching schedules from Firestore", e)
            emptyList()
        }
    }
}
