package com.example.ui

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.api.GeminiClient
import com.example.util.PrayerTime
import com.example.util.PrayerTimesHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HayatyViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = HayatyRepository(
        application,
        database.taskDao(),
        database.habitDao(),
        database.appUsageDao()
    )

    private val _currentDate = MutableStateFlow(getTodayDateString())
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

    // --- Task State ---
    val allTasks: StateFlow<List<Task>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Habit State ---
    val allHabits: StateFlow<List<Habit>> = repository.allHabits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayHabitLogs: StateFlow<List<HabitLog>> = _currentDate
        .flatMapLatest { date -> repository.getLogsForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHabitLogs: StateFlow<List<HabitLog>> = repository.allHabitLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Screen Usage State ---
    val usageRecords: StateFlow<List<AppUsageRecord>> = _currentDate
        .flatMapLatest { date -> repository.getUsageForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val prefs = application.getSharedPreferences("AzkarPrefs", Context.MODE_PRIVATE)

    // --- Theme Mode State ---
    private val _themeMode = MutableStateFlow(prefs.getString("ThemeMode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString("ThemeMode", mode).apply()
    }

    // --- Monthly Habit Alert State ---
    private val _showMonthlyHabitAlert = MutableStateFlow(false)
    val showMonthlyHabitAlert: StateFlow<Boolean> = _showMonthlyHabitAlert.asStateFlow()

    fun updateMonthlyHabitAlertState() {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val lastHabitAddedMonthYear = prefs.getString("LastHabitAddedMonthYear", "")
        _showMonthlyHabitAlert.value = (lastHabitAddedMonthYear != currentMonthStr)
    }

    fun dismissMonthlyHabitAlert() {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        prefs.edit().putString("LastHabitAddedMonthYear", currentMonthStr).apply()
        _showMonthlyHabitAlert.value = false
    }

    // --- Quran Wird State ---
    private val _quranWirdPages = MutableStateFlow(prefs.getInt("QuranWirdPages", 4))
    val quranWirdPages: StateFlow<Int> = _quranWirdPages.asStateFlow()

    private val _quranWirdIncreasePeriod = MutableStateFlow(prefs.getString("QuranWirdIncreasePeriod", "1_month") ?: "1_month")
    val quranWirdIncreasePeriod: StateFlow<String> = _quranWirdIncreasePeriod.asStateFlow()

    private val _quranWirdIncreasePages = MutableStateFlow(prefs.getInt("QuranWirdIncreasePages", 2))
    val quranWirdIncreasePages: StateFlow<Int> = _quranWirdIncreasePages.asStateFlow()

    private val _quranWirdStartDate = MutableStateFlow(prefs.getLong("QuranWirdStartDate", System.currentTimeMillis()))
    val quranWirdStartDate: StateFlow<Long> = _quranWirdStartDate.asStateFlow()

    fun updateQuranWirdConfig(pages: Int, period: String, increasePages: Int, startDate: Long) {
        _quranWirdPages.value = pages
        _quranWirdIncreasePeriod.value = period
        _quranWirdIncreasePages.value = increasePages
        _quranWirdStartDate.value = startDate

        prefs.edit()
            .putInt("QuranWirdPages", pages)
            .putString("QuranWirdIncreasePeriod", period)
            .putInt("QuranWirdIncreasePages", increasePages)
            .putLong("QuranWirdStartDate", startDate)
            .apply()
    }

    // --- Prayer Times State (GPS-Aware Unified Core) ---
    private val _selectedCity = MutableStateFlow(prefs.getString("SelectedCity", "مكة المكرمة") ?: "مكة المكرمة")
    val selectedCity: StateFlow<String> = _selectedCity.asStateFlow()

    private val _isUsingGps = MutableStateFlow(prefs.getBoolean("IsUsingGps", false))
    val isUsingGps: StateFlow<Boolean> = _isUsingGps.asStateFlow()

    private val _gpsLatitude = MutableStateFlow<Double?>(
        if (prefs.contains("GpsLatitude")) prefs.getFloat("GpsLatitude", 0f).toDouble() else null
    )
    val gpsLatitude: StateFlow<Double?> = _gpsLatitude.asStateFlow()

    private val _gpsLongitude = MutableStateFlow<Double?>(
        if (prefs.contains("GpsLongitude")) prefs.getFloat("GpsLongitude", 0f).toDouble() else null
    )
    val gpsLongitude: StateFlow<Double?> = _gpsLongitude.asStateFlow()

    val prayerTimes: StateFlow<List<PrayerTime>> = combine(
        _selectedCity,
        _currentDate,
        _isUsingGps,
        _gpsLatitude,
        _gpsLongitude
    ) { city, _, usingGps, lat, lon ->
        if (usingGps && lat != null && lon != null) {
            PrayerTimesHelper.getPrayerTimesForCoordinates(lat, lon)
        } else {
            PrayerTimesHelper.getPrayerTimesForCity(city)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setUsingGps(enabled: Boolean, context: Context) {
        _isUsingGps.value = enabled
        prefs.edit().putBoolean("IsUsingGps", enabled).apply()
        if (enabled) {
            fetchGpsLocation(context)
        }
        com.example.widget.HayatyWidgetProvider.triggerUpdate(getApplication())
        com.example.widget.HayatyTasksWidgetProvider.triggerUpdate(getApplication())
    }

    fun updateGpsCoordinates(lat: Double, lon: Double) {
        _gpsLatitude.value = lat
        _gpsLongitude.value = lon
        prefs.edit()
            .putFloat("GpsLatitude", lat.toFloat())
            .putFloat("GpsLongitude", lon.toFloat())
            .apply()
        com.example.widget.HayatyWidgetProvider.triggerUpdate(getApplication())
        com.example.widget.HayatyTasksWidgetProvider.triggerUpdate(getApplication())
    }

    fun fetchGpsLocation(context: Context) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            if (locationManager != null) {
                val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

                var bestLocation: android.location.Location? = null
                if (isGpsEnabled) {
                    bestLocation = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                }
                if (bestLocation == null && isNetworkEnabled) {
                    bestLocation = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                }

                if (bestLocation != null) {
                    updateGpsCoordinates(bestLocation.latitude, bestLocation.longitude)
                }

                // Request single quick high fidelity update
                val provider = if (isGpsEnabled) android.location.LocationManager.GPS_PROVIDER else android.location.LocationManager.NETWORK_PROVIDER
                locationManager.requestSingleUpdate(
                    provider,
                    object : android.location.LocationListener {
                        override fun onLocationChanged(location: android.location.Location) {
                            updateGpsCoordinates(location.latitude, location.longitude)
                        }
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    },
                    null
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Quran Achievement Status After Each Prayer (ورد ما بعد الصلاة) ---
    private val _quranPostPrayerStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val quranPostPrayerStatus: StateFlow<Map<String, Boolean>> = _quranPostPrayerStatus.asStateFlow()

    fun loadPostPrayerQuranStatus() {
        val today = getTodayDateString()
        val prayers = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        val map = prayers.associateWith { prayer ->
            prefs.getBoolean("quran_after_${prayer}_$today", false)
        }
        _quranPostPrayerStatus.value = map
    }

    fun togglePostPrayerQuranReading(prayerName: String) {
        val today = getTodayDateString()
        val currentVal = _quranPostPrayerStatus.value[prayerName] ?: false
        val newVal = !currentVal
        
        prefs.edit().putBoolean("quran_after_${prayerName}_$today", newVal).apply()
        
        val updatedMap = _quranPostPrayerStatus.value.toMutableMap().apply {
            put(prayerName, newVal)
        }
        _quranPostPrayerStatus.value = updatedMap
    }

    // --- AI Advising State ---
    private val _aiProductivityAdvice = MutableStateFlow<String?>(null)
    val aiProductivityAdvice: StateFlow<String?> = _aiProductivityAdvice.asStateFlow()

    private val _isAnalyzingProductivity = MutableStateFlow(false)
    val isAnalyzingProductivity: StateFlow<Boolean> = _isAnalyzingProductivity.asStateFlow()

    // --- Focus Mode State ---
    private val _isFocusActive = MutableStateFlow(false)
    val isFocusActive: StateFlow<Boolean> = _isFocusActive.asStateFlow()

    private val _focusRemainingSeconds = MutableStateFlow(0)
    val focusRemainingSeconds: StateFlow<Int> = _focusRemainingSeconds.asStateFlow()

    private var focusTimer: Timer? = null

    init {
        // Hydrate database with initial apps & habits if empty
        viewModelScope.launch {
            repository.allHabits.first().let { currentHabits ->
                if (currentHabits.isEmpty()) {
                    repository.insertHabit(Habit(name = "قراءة الورد اليومي من القرآن"))
                    repository.insertHabit(Habit(name = "الالتزام بالصلاة في وقتها"))
                    repository.insertHabit(Habit(name = "ممارسة الرياضة 20 دقيقة"))
                    repository.insertHabit(Habit(name = "شرب 2 لتر من الماء"))
                }
            }
            
            // Populate screen usage record if empty for today
            repository.getUsageForDate(getTodayDateString()).first().let { currentRecords ->
                if (currentRecords.isEmpty()) {
                    generateMockUsageStats()
                }
            }

            // Populate mock historical records if habit logs are empty
            repository.allHabitLogs.first().let { currentLogs ->
                if (currentLogs.isEmpty()) {
                    generateMockHistory()
                }
            }
            updateMonthlyHabitAlertState()
            loadPostPrayerQuranStatus()
        }
    }

    // --- Task Add Dialog Trigger on Startup (Widget link) ---
    private val _showTaskAddDialogOnStart = MutableStateFlow(false)
    val showTaskAddDialogOnStart: StateFlow<Boolean> = _showTaskAddDialogOnStart.asStateFlow()

    fun triggerTaskAddDialog(show: Boolean) {
        _showTaskAddDialogOnStart.value = show
    }

    // --- Task Actions ---
    fun addTask(title: String, description: String, dueDate: Long, category: String, isAppointment: Boolean) {
        viewModelScope.launch {
            repository.insertTask(
                Task(
                    title = title,
                    description = description,
                    dueDate = dueDate,
                    category = category,
                    isAppointment = isAppointment
                )
            )
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    // --- Habit Actions ---
    fun addHabit(name: String) {
        viewModelScope.launch {
            repository.insertHabit(Habit(name = name))
            val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            prefs.edit().putString("LastHabitAddedMonthYear", currentMonthStr).apply()
            updateMonthlyHabitAlertState()
        }
    }

    fun deleteHabit(habitId: Int) {
        viewModelScope.launch {
            repository.deleteHabit(habitId)
        }
    }

    fun toggleHabit(habit: Habit) {
        viewModelScope.launch {
            val date = currentDate.value
            val isCompleted = todayHabitLogs.value.none { it.habitId == habit.id }
            repository.toggleHabitCompletion(habit, date, isCompleted)
        }
    }

    // --- City Configuration ---
    fun setCity(city: String) {
        _selectedCity.value = city
        prefs.edit().putString("SelectedCity", city).apply()
        com.example.widget.HayatyWidgetProvider.triggerUpdate(getApplication())
        com.example.widget.HayatyTasksWidgetProvider.triggerUpdate(getApplication())
    }

    // --- AI Strategy Execution ---
    fun analyzeProductivityWithAI() {
        viewModelScope.launch {
            _isAnalyzingProductivity.value = true
            try {
                val tasks = allTasks.value
                val habits = allHabits.value
                val usage = usageRecords.value
                val response = GeminiClient.getProductivityCoaching(tasks, habits, usage)
                _aiProductivityAdvice.value = response
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isAnalyzingProductivity.value = false
            }
        }
    }

    // --- Focus lock logic ---
    fun startFocusMode(durationMinutes: Int) {
        _isFocusActive.value = true
        _focusRemainingSeconds.value = durationMinutes * 60
        focusTimer?.cancel()
        focusTimer = Timer()
        focusTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (_focusRemainingSeconds.value > 0) {
                    _focusRemainingSeconds.value -= 1
                } else {
                    stopFocusMode()
                }
            }
        }, 1000, 1000)
    }

    fun stopFocusMode() {
        focusTimer?.cancel()
        _isFocusActive.value = false
        _focusRemainingSeconds.value = 0
    }

    // --- Native Usage Query & Mock Fallback ---
    fun refreshUsageStats(context: Context) {
        viewModelScope.launch {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageStatsManager != null && hasUsageStatsPermission(context)) {
                val calendar = Calendar.getInstance()
                val endTime = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startTime = calendar.timeInMillis

                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )

                if (stats != null && stats.isNotEmpty()) {
                    repository.deleteUsageForDate(getTodayDateString())
                    val pm = context.packageManager
                    val sortedStats = stats.filter { it.totalTimeInForeground > 30000 }
                        .sortedByDescending { it.totalTimeInForeground }
                        .take(10)

                    for (usage in sortedStats) {
                        val appName = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(usage.packageName, 0)).toString()
                        } catch (e: Exception) {
                            usage.packageName.substringAfterLast('.')
                        }
                        repository.insertUsageRecord(
                            AppUsageRecord(
                                packageName = usage.packageName,
                                appName = appName,
                                durationMs = usage.totalTimeInForeground,
                                date = getTodayDateString()
                            )
                        )
                    }
                } else {
                    generateMockUsageStats()
                }
            } else {
                generateMockUsageStats()
            }
        }
    }

    private suspend fun generateMockUsageStats() {
        repository.deleteUsageForDate(getTodayDateString())
        val mockApps = listOf(
            Pair("يوتيوب", "com.google.android.youtube") to 84 * 60 * 1000L,
            Pair("تيك توك", "com.zhiliaoapp.musically") to 125 * 60 * 1000L,
            Pair("إنستغرام", "com.instagram.android") to 62 * 60 * 1000L,
            Pair("واتساب", "com.whatsapp") to 45 * 60 * 1000L,
            Pair("إكس (تويتر)", "com.twitter.android") to 30 * 60 * 1000L,
            Pair("سناب شات", "com.snapchat.android") to 25 * 60 * 1000L
        )
        for ((app, time) in mockApps) {
            repository.insertUsageRecord(
                AppUsageRecord(
                    appName = app.first,
                    packageName = app.second,
                    durationMs = time,
                    date = getTodayDateString()
                )
            )
        }
    }

    private suspend fun generateMockHistory() {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        val habits = repository.allHabits.first()
        if (habits.isNotEmpty()) {
            for (i in 1..7) {
                cal.time = Date()
                cal.add(Calendar.DAY_OF_YEAR, -i)
                val dateStr = sdf.format(cal.time)
                
                val completedCount = when (i % 3) {
                    0 -> 2
                    1 -> 3
                    else -> 4
                }
                habits.shuffled().take(completedCount).forEach { habit ->
                    database.habitDao().insertHabitLog(HabitLog(habitId = habit.id, date = dateStr))
                }
                
                val taskCategory = when (i % 4) {
                    0 -> "عبادة"
                    1 -> "دراسة"
                    2 -> "عمل"
                    else -> "صحة"
                }
                
                val taskTitlePrefix = when (i % 4) {
                    0 -> "مراجعة وتلاوة ورد"
                    1 -> "حضور درس برمجة"
                    2 -> "إكمال متطلبات المشروع"
                    else -> "المشي السريع"
                }
                
                database.taskDao().insertTask(
                    Task(
                        title = "$taskTitlePrefix - يوم $i",
                        description = "هذه المهمة تم إكمالها تلقائياً كجزء من سجل إنجازاتك في الأسبوع.",
                        dueDate = cal.timeInMillis,
                        isCompleted = true,
                        category = taskCategory
                    )
                )
            }
        }
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(context.packageName, 0)
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOpsManager.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                appInfo.uid,
                context.packageName
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        focusTimer?.cancel()
    }
}
