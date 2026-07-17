package com.example.ui

import android.app.Application
import android.app.usage.UsageStatsManager
import android.app.NotificationManager
import android.app.NotificationChannel
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.api.GeminiClient
import com.example.util.PrayerTime
import com.example.util.PrayerTimesHelper
import kotlinx.coroutines.flow.*
import android.media.MediaPlayer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

data class QuranBookmark(
    val suraId: Int,
    val suraName: String,
    val verseIndex: Int,
    val timestamp: Long
)

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

    // --- Screen Time AI Advice State ---
    private val _screenTimeAiAdvice = MutableStateFlow<String?>(null)
    val screenTimeAiAdvice: StateFlow<String?> = _screenTimeAiAdvice.asStateFlow()

    private val _isScreenTimeAiLoading = MutableStateFlow(false)
    val isScreenTimeAiLoading: StateFlow<Boolean> = _isScreenTimeAiLoading.asStateFlow()

    private val prefs = application.getSharedPreferences("AzkarPrefs", Context.MODE_PRIVATE)

    // --- Theme Mode State ---
    private val _themeMode = MutableStateFlow(prefs.getString("ThemeMode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString("ThemeMode", mode).apply()
    }

    // --- Theme Accent color ---
    private val _themeColor = MutableStateFlow(prefs.getString("ThemeColor", "emerald") ?: "emerald")
    val themeColor: StateFlow<String> = _themeColor.asStateFlow()

    fun setThemeColor(color: String) {
        _themeColor.value = color
        prefs.edit().putString("ThemeColor", color).apply()
    }

    // --- Prayer Offsets ---
    private val _prayerOffsetMinutes = MutableStateFlow(prefs.getInt("PrayerOffsetMinutes", 0))
    val prayerOffsetMinutes: StateFlow<Int> = _prayerOffsetMinutes.asStateFlow()

    fun setPrayerOffsetMinutes(minutes: Int) {
        _prayerOffsetMinutes.value = minutes
        prefs.edit().putInt("PrayerOffsetMinutes", minutes).apply()
        // Force widgets and triggers to update too
        com.example.widget.HayatyWidgetProvider.triggerUpdate(getApplication())
    }

    // --- Component Visibility / Widgets Customization ---
    private val _showTimerBanner = MutableStateFlow(prefs.getBoolean("ShowTimerBanner", true))
    val showTimerBanner: StateFlow<Boolean> = _showTimerBanner.asStateFlow()
    fun setShowTimerBanner(show: Boolean) {
        _showTimerBanner.value = show
        prefs.edit().putBoolean("ShowTimerBanner", show).apply()
    }

    private val _showStatsGrid = MutableStateFlow(prefs.getBoolean("ShowStatsGrid", true))
    val showStatsGrid: StateFlow<Boolean> = _showStatsGrid.asStateFlow()
    fun setShowStatsGrid(show: Boolean) {
        _showStatsGrid.value = show
        prefs.edit().putBoolean("ShowStatsGrid", show).apply()
    }

    private val _showPrayerWidget = MutableStateFlow(prefs.getBoolean("ShowPrayerWidget", true))
    val showPrayerWidget: StateFlow<Boolean> = _showPrayerWidget.asStateFlow()
    fun setShowPrayerWidget(show: Boolean) {
        _showPrayerWidget.value = show
        prefs.edit().putBoolean("ShowPrayerWidget", show).apply()
    }

    private val _showQuranTracker = MutableStateFlow(prefs.getBoolean("ShowQuranTracker", true))
    val showQuranTracker: StateFlow<Boolean> = _showQuranTracker.asStateFlow()
    fun setShowQuranTracker(show: Boolean) {
        _showQuranTracker.value = show
        prefs.edit().putBoolean("ShowQuranTracker", show).apply()
    }

    private val _showFiqhWidget = MutableStateFlow(prefs.getBoolean("ShowFiqhWidget", true))
    val showFiqhWidget: StateFlow<Boolean> = _showFiqhWidget.asStateFlow()
    fun setShowFiqhWidget(show: Boolean) {
        _showFiqhWidget.value = show
        prefs.edit().putBoolean("ShowFiqhWidget", show).apply()
    }

    private val _showHabitWidget = MutableStateFlow(prefs.getBoolean("ShowHabitWidget", true))
    val showHabitWidget: StateFlow<Boolean> = _showHabitWidget.asStateFlow()
    fun setShowHabitWidget(show: Boolean) {
        _showHabitWidget.value = show
        prefs.edit().putBoolean("ShowHabitWidget", show).apply()
    }

    private val _showTasksWidget = MutableStateFlow(prefs.getBoolean("ShowTasksWidget", true))
    val showTasksWidget: StateFlow<Boolean> = _showTasksWidget.asStateFlow()
    fun setShowTasksWidget(show: Boolean) {
        _showTasksWidget.value = show
        prefs.edit().putBoolean("ShowTasksWidget", show).apply()
    }

    private val _showAiSuggestion = MutableStateFlow(prefs.getBoolean("ShowAiSuggestion", true))
    val showAiSuggestion: StateFlow<Boolean> = _showAiSuggestion.asStateFlow()
    fun setShowAiSuggestion(show: Boolean) {
        _showAiSuggestion.value = show
        prefs.edit().putBoolean("ShowAiSuggestion", show).apply()
    }

    // --- Language State ---
    private val _appLanguage = MutableStateFlow(prefs.getString("AppLanguage", "ar") ?: "ar")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    fun setAppLanguage(lang: String) {
        _appLanguage.value = lang
        prefs.edit().putString("AppLanguage", lang).apply()
        com.example.util.LangHelper.setLanguage(lang)
    }

    // --- Dynamic Time of Day State ---
    private val _timeOfDayOverride = MutableStateFlow(prefs.getString("TimeOfDayOverride", "auto") ?: "auto")
    val timeOfDayOverride: StateFlow<String> = _timeOfDayOverride.asStateFlow()

    private val _currentTimeOfDay = MutableStateFlow("morning")
    val currentTimeOfDay: StateFlow<String> = _currentTimeOfDay.asStateFlow()

    fun setTimeOfDayOverride(override: String) {
        _timeOfDayOverride.value = override
        prefs.edit().putString("TimeOfDayOverride", override).apply()
        updateCurrentTimeOfDay()
    }

    fun updateCurrentTimeOfDay() {
        val override = _timeOfDayOverride.value
        if (override != "auto") {
            _currentTimeOfDay.value = override
        } else {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            _currentTimeOfDay.value = when (hour) {
                in 5..16 -> "morning"
                in 17..19 -> "evening"
                else -> "night"
            }
        }
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

    private val _selectedCountry = MutableStateFlow(prefs.getString("SelectedCountry", "المملكة العربية السعودية") ?: "المملكة العربية السعودية")
    val selectedCountry: StateFlow<String> = _selectedCountry.asStateFlow()

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

    private val _apiPrayerTimes = MutableStateFlow<List<PrayerTime>?>(null)
    val apiPrayerTimes: StateFlow<List<PrayerTime>?> = _apiPrayerTimes.asStateFlow()

    val prayerTimes: StateFlow<List<PrayerTime>> = combine(
        combine(_selectedCity, _currentDate, _isUsingGps) { city, date, usingGps ->
            Triple(city, date, usingGps)
        },
        combine(_gpsLatitude, _gpsLongitude, _apiPrayerTimes) { lat, lon, apiTimes ->
            Triple(lat, lon, apiTimes)
        },
        _prayerOffsetMinutes
    ) { group1, group2, offset ->
        val (city, _, usingGps) = group1
        val (lat, lon, apiTimes) = group2
        
        val baseList = if (apiTimes != null && apiTimes.isNotEmpty()) {
            apiTimes
        } else {
            if (usingGps && lat != null && lon != null) {
                PrayerTimesHelper.getPrayerTimesForCoordinates(lat as Double, lon as Double)
            } else {
                PrayerTimesHelper.getPrayerTimesForCity(city as String)
            }
        }
        
        if (offset == 0) {
            baseList
        } else {
            baseList.map { prayer ->
                try {
                    val parts = prayer.time.split(":")
                    if (parts.size == 2) {
                        val hour = parts[0].toInt()
                        val minute = parts[1].toInt()
                        val cal = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, hour)
                            set(java.util.Calendar.MINUTE, minute)
                            set(java.util.Calendar.SECOND, 0)
                        }
                        cal.add(java.util.Calendar.MINUTE, offset)
                        val formatted = String.format(java.util.Locale.US, "%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
                        prayer.copy(time = formatted)
                    } else {
                        prayer
                    }
                } catch (e: Exception) {
                    prayer
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedCountryAndCity(country: String, city: String) {
        _selectedCountry.value = country
        _selectedCity.value = city
        prefs.edit()
            .putString("SelectedCountry", country)
            .putString("SelectedCity", city)
            .apply()
        triggerApiPrayerTimesFetch()
        com.example.widget.HayatyWidgetProvider.triggerUpdate(getApplication())
        com.example.widget.HayatyTasksWidgetProvider.triggerUpdate(getApplication())
    }

    fun triggerApiPrayerTimesFetch() {
        viewModelScope.launch(Dispatchers.IO) {
            val usingGps = _isUsingGps.value
            val lat = _gpsLatitude.value
            val lon = _gpsLongitude.value
            val city = _selectedCity.value
            val country = _selectedCountry.value

            val times = PrayerTimesHelper.fetchPrayerTimesFromApi(
                latitude = if (usingGps) lat else null,
                longitude = if (usingGps) lon else null,
                cityName = if (!usingGps) city else null,
                countryName = if (!usingGps) country else null
            )
            
            if (times != null && times.isNotEmpty()) {
                _apiPrayerTimes.value = times
            } else {
                _apiPrayerTimes.value = null
            }
        }
    }

    fun setUsingGps(enabled: Boolean, context: Context) {
        _isUsingGps.value = enabled
        prefs.edit().putBoolean("IsUsingGps", enabled).apply()
        if (enabled) {
            fetchGpsLocation(context)
        } else {
            triggerApiPrayerTimesFetch()
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
        triggerApiPrayerTimesFetch()
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

    // --- Warsh Quran Audio State ---
    val reciters = mapOf(
        "الشيخ محمود خليل الحصري (ورش)" to "https://server14.mp3quran.net/husr/warsh/",
        "الشيخ ياسين الجزائري (ورش)" to "https://download.quranicaudio.com/quran/yassin_al_jazaery_warsh/",
        "الشيخ عبد الباسط عبد الصمد (ورش)" to "https://server11.mp3quran.net/basit_warsh/"
    )

    private val _selectedReciterName = MutableStateFlow(prefs.getString("SelectedWarshReciter", "الشيخ محمود خليل الحصري (ورش)") ?: "الشيخ محمود خليل الحصري (ورش)")
    val selectedReciterName: StateFlow<String> = _selectedReciterName.asStateFlow()

    fun setSelectedReciter(name: String) {
        _selectedReciterName.value = name
        prefs.edit().putString("SelectedWarshReciter", name).apply()
        refreshDownloadedSuras()
    }

    private val _downloadedSuras = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val downloadedSuras: StateFlow<Map<Int, Boolean>> = _downloadedSuras.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<Int, Int>> = _downloadProgress.asStateFlow()

    private val _downloadingJobs = mutableMapOf<Int, kotlinx.coroutines.Job>()

    private var quranMediaPlayer: android.media.MediaPlayer? = null
    private var progressJob: kotlinx.coroutines.Job? = null

    private val _playingSuraId = MutableStateFlow<Int?>(null)
    val playingSuraId: StateFlow<Int?> = _playingSuraId.asStateFlow()

    private val _isAudioPlaying = MutableStateFlow(false)
    val isAudioPlaying: StateFlow<Boolean> = _isAudioPlaying.asStateFlow()

    private val _audioProgress = MutableStateFlow(0f)
    val audioProgress: StateFlow<Float> = _audioProgress.asStateFlow()

    private val _currentPlayTime = MutableStateFlow("00:00")
    val currentPlayTime: StateFlow<String> = _currentPlayTime.asStateFlow()

    private val _audioDuration = MutableStateFlow("00:00")
    val audioDuration: StateFlow<String> = _audioDuration.asStateFlow()

    fun getSuraSaveFile(suraId: Int, reciterName: String): java.io.File {
        val reciterFolderSafe = reciterName.replace(" ", "_").replace("(", "").replace(")", "")
        val dir = java.io.File(getApplication<Application>().filesDir, "warsh_quran/$reciterFolderSafe")
        if (!dir.exists()) dir.mkdirs()
        return java.io.File(dir, String.format(Locale.US, "%03d.mp3", suraId))
    }

    fun isSuraDownloaded(suraId: Int, reciterName: String): Boolean {
        val file = getSuraSaveFile(suraId, reciterName)
        return file.exists() && file.length() > 1024
    }

    fun refreshDownloadedSuras() {
        val currentReciter = _selectedReciterName.value
        val map = (1..114).associateWith { id ->
            isSuraDownloaded(id, currentReciter)
        }
        _downloadedSuras.value = map
    }

    fun downloadSuraAudio(suraId: Int) {
        if (_downloadProgress.value.containsKey(suraId)) return
        
        val reciterName = _selectedReciterName.value
        val reciterUrlBase = reciters[reciterName] ?: "https://server14.mp3quran.net/husr/warsh/"
        val fileUrlStr = "$reciterUrlBase${String.format(Locale.US, "%03d.mp3", suraId)}"
        val destinationFile = getSuraSaveFile(suraId, reciterName)
        val tempFile = java.io.File(destinationFile.absolutePath + ".tmp")

        val job = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _downloadProgress.value = _downloadProgress.value + (suraId to 0)
                
                val url = java.net.URL(fileUrlStr)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 12000
                connection.connect()

                if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    throw java.io.IOException("HTTP code: ${connection.responseCode}")
                }

                val fileLength = connection.contentLength
                val input = connection.inputStream
                val output = java.io.FileOutputStream(tempFile)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        val percentage = ((total * 100) / fileLength).toInt()
                        _downloadProgress.value = _downloadProgress.value + (suraId to percentage)
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()
                
                if (tempFile.exists()) {
                    if (destinationFile.exists()) {
                        destinationFile.delete()
                    }
                    tempFile.renameTo(destinationFile)
                }

                _downloadProgress.value = _downloadProgress.value - suraId
                refreshDownloadedSuras()
            } catch (e: Exception) {
                e.printStackTrace()
                _downloadProgress.value = _downloadProgress.value - suraId
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        "فشل تحميل سورة ${com.example.util.QuranData.suras.find { it.id == suraId }?.name ?: suraId}. يرجى التحقق من اتصال الإنترنت.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                refreshDownloadedSuras()
            } finally {
                _downloadingJobs.remove(suraId)
            }
        }
        _downloadingJobs[suraId] = job
    }

    // --- Batch/Full Quran Download State ---
    private val _isFullDownloading = MutableStateFlow(false)
    val isFullDownloading: StateFlow<Boolean> = _isFullDownloading.asStateFlow()

    private val _fullDownloadProgress = MutableStateFlow(0f)
    val fullDownloadProgress: StateFlow<Float> = _fullDownloadProgress.asStateFlow()

    private val _fullDownloadStatus = MutableStateFlow("")
    val fullDownloadStatus: StateFlow<String> = _fullDownloadStatus.asStateFlow()

    private var fullDownloadJob: kotlinx.coroutines.Job? = null

    fun startFullDownload() {
        if (_isFullDownloading.value) return
        _isFullDownloading.value = true
        _fullDownloadProgress.value = 0f
        _fullDownloadStatus.value = "جاري بدء تحميل المصحف كاملاً..."

        fullDownloadJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val reciterName = _selectedReciterName.value
            val reciterUrlBase = reciters[reciterName] ?: "https://server14.mp3quran.net/husr/warsh/"
            
            try {
                var completedCount = 0
                for (suraId in 1..114) {
                    if (!isActive) break
                    
                    val suraName = com.example.util.QuranData.suras.find { it.id == suraId }?.name ?: "السورة $suraId"
                    _fullDownloadStatus.value = "تحميل $suraName (النص والتلاوة)... ($suraId/114)"
                    
                    // 1. Download & cache verses if not loaded
                    val cachedText = prefs.getString("sura_verses_$suraId", null)
                    if (cachedText == null) {
                        try {
                            val url = java.net.URL("https://api.alquran.cloud/v1/surah/$suraId/ar.warsh")
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.connectTimeout = 8000
                            connection.readTimeout = 10000
                            connection.connect()

                            if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                                val text = connection.inputStream.bufferedReader().use { it.readText() }
                                val jsonObject = org.json.JSONObject(text)
                                val data = jsonObject.getJSONObject("data")
                                val ayahs = data.getJSONArray("ayahs")
                                val list = mutableListOf<String>()
                                for (i in 0 until ayahs.length()) {
                                    val ayah = ayahs.getJSONObject(i)
                                    list.add(ayah.getString("text"))
                                }

                                if (list.isNotEmpty()) {
                                    val joined = list.joinToString("|||")
                                    prefs.edit().putString("sura_verses_$suraId", joined).apply()
                                    _loadedSuraVerses.value = _loadedSuraVerses.value + (suraId to list)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else if (!_loadedSuraVerses.value.containsKey(suraId)) {
                        // Put in memory if not already there
                        val versesList = cachedText.split("|||")
                        _loadedSuraVerses.value = _loadedSuraVerses.value + (suraId to versesList)
                    }

                    // 2. Download audio if not existing
                    val destinationFile = getSuraSaveFile(suraId, reciterName)
                    if (!isSuraDownloaded(suraId, reciterName)) {
                        val tempFile = java.io.File(destinationFile.absolutePath + ".tmp")
                        val fileUrlStr = "$reciterUrlBase${String.format(Locale.US, "%03d.mp3", suraId)}"
                        try {
                            val url = java.net.URL(fileUrlStr)
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.connectTimeout = 8000
                            connection.readTimeout = 12000
                            connection.connect()

                            if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                                val input = connection.inputStream
                                val output = java.io.FileOutputStream(tempFile)
                                val data = ByteArray(4096)
                                var count: Int
                                while (input.read(data).also { count = it } != -1) {
                                    if (!isActive) {
                                        output.close()
                                        input.close()
                                        if (tempFile.exists()) tempFile.delete()
                                        throw kotlinx.coroutines.CancellationException()
                                    }
                                    output.write(data, 0, count)
                                }
                                output.flush()
                                output.close()
                                input.close()

                                if (tempFile.exists()) {
                                    if (destinationFile.exists()) {
                                        destinationFile.delete()
                                    }
                                    tempFile.renameTo(destinationFile)
                                }
                            }
                        } catch (ce: kotlinx.coroutines.CancellationException) {
                            if (tempFile.exists()) tempFile.delete()
                            throw ce
                        } catch (e: Exception) {
                            e.printStackTrace()
                            if (tempFile.exists()) {
                                tempFile.delete()
                            }
                        }
                    }

                    completedCount++
                    _fullDownloadProgress.value = completedCount / 114f
                    refreshDownloadedSuras()
                }

                if (isActive) {
                    _fullDownloadStatus.value = "تم تحميل المصحف الشريف بكامل تلاواته وصفحاته بنجاح! 🎉"
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                _fullDownloadStatus.value = "تم إلغاء عملية التحميل."
            } catch (e: Exception) {
                e.printStackTrace()
                _fullDownloadStatus.value = "خطأ أثناء التحميل: ${e.localizedMessage}"
            } finally {
                _isFullDownloading.value = false
                refreshDownloadedSuras()
            }
        }
    }

    fun cancelFullDownload() {
        fullDownloadJob?.cancel()
        _isFullDownloading.value = false
        _fullDownloadStatus.value = "تم إلغاء التحميل."
    }

    fun deleteSuraAudio(suraId: Int) {
        val reciterName = _selectedReciterName.value
        val file = getSuraSaveFile(suraId, reciterName)
        if (file.exists()) {
            file.delete()
        }
        refreshDownloadedSuras()
    }

    fun playSuraAudio(suraId: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                if (_playingSuraId.value == suraId) {
                    toggleAudioPlayPause()
                    return@launch
                }

                stopAudioPlay()

                val reciterName = _selectedReciterName.value
                val file = getSuraSaveFile(suraId, reciterName)
                val mediaPath = if (isSuraDownloaded(suraId, reciterName)) {
                    file.absolutePath
                } else {
                    val reciterUrlBase = reciters[reciterName] ?: "https://server14.mp3quran.net/husr/warsh/"
                    "$reciterUrlBase${String.format(Locale.US, "%03d.mp3", suraId)}"
                }

                val mp = android.media.MediaPlayer().apply {
                    setDataSource(mediaPath)
                    prepareAsync()
                    setOnPreparedListener {
                        start()
                        _isAudioPlaying.value = true
                        _playingSuraId.value = suraId
                        startProgressUpdates()
                    }
                    setOnCompletionListener {
                        stopAudioPlay()
                    }
                    setOnErrorListener { _, what, extra ->
                        stopAudioPlay()
                        android.widget.Toast.makeText(
                            getApplication(),
                            "عذراً، فشل تشغيل تلاوة السورة. تحقق من اتصالك الإنترنت.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        true
                    }
                }
                quranMediaPlayer = mp
            } catch (e: Exception) {
                e.printStackTrace()
                stopAudioPlay()
                android.widget.Toast.makeText(
                    getApplication(),
                    "عذراً، حدث خطأ أثناء تشغيل الملف الصوتي.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun toggleAudioPlayPause() {
        quranMediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                _isAudioPlaying.value = false
            } else {
                mp.start()
                _isAudioPlaying.value = true
                startProgressUpdates()
            }
        }
    }

    fun seekAudioTo(fraction: Float) {
        quranMediaPlayer?.let { mp ->
            val duration = mp.duration
            if (duration > 0) {
                val dest = (duration * fraction).toInt()
                mp.seekTo(dest)
                _audioProgress.value = fraction
            }
        }
    }

    fun stopAudioPlay() {
        progressJob?.cancel()
        progressJob = null
        try {
            quranMediaPlayer?.stop()
            quranMediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        quranMediaPlayer = null
        _isAudioPlaying.value = false
        _playingSuraId.value = null
        _audioProgress.value = 0f
        _currentPlayTime.value = "00:00"
        _audioDuration.value = "00:00"
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            while (true) {
                quranMediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        val current = mp.currentPosition
                        val duration = mp.duration
                        if (duration > 0) {
                            _audioProgress.value = current.toFloat() / duration.toFloat()
                            _currentPlayTime.value = formatMs(current)
                            _audioDuration.value = formatMs(duration)
                        }
                    }
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun formatMs(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    // --- Warsh Quran Verses Loader & Cache Engine ---
    private val _loadedSuraVerses = MutableStateFlow<Map<Int, List<String>>>(emptyMap())
    val loadedSuraVerses: StateFlow<Map<Int, List<String>>> = _loadedSuraVerses.asStateFlow()

    private val _isLoadingVerses = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val isLoadingVerses: StateFlow<Map<Int, Boolean>> = _isLoadingVerses.asStateFlow()

    fun loadSuraVerses(suraId: Int) {
        if (_loadedSuraVerses.value.containsKey(suraId)) return
        if (_isLoadingVerses.value[suraId] == true) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLoadingVerses.value = _isLoadingVerses.value + (suraId to true)
            try {
                val cached = prefs.getString("sura_verses_$suraId", null)
                if (cached != null) {
                    val versesList = cached.split("|||")
                    _loadedSuraVerses.value = _loadedSuraVerses.value + (suraId to versesList)
                    _isLoadingVerses.value = _isLoadingVerses.value - suraId
                    return@launch
                }

                val url = java.net.URL("https://api.alquran.cloud/v1/surah/$suraId/ar.warsh")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 10000
                connection.connect()

                if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = org.json.JSONObject(text)
                    val data = jsonObject.getJSONObject("data")
                    val ayahs = data.getJSONArray("ayahs")
                    val list = mutableListOf<String>()
                    for (i in 0 until ayahs.length()) {
                        val ayah = ayahs.getJSONObject(i)
                        list.add(ayah.getString("text"))
                    }

                    if (list.isNotEmpty()) {
                        _loadedSuraVerses.value = _loadedSuraVerses.value + (suraId to list)
                        val joined = list.joinToString("|||")
                        prefs.edit().putString("sura_verses_$suraId", joined).apply()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingVerses.value = _isLoadingVerses.value - suraId
            }
        }
    }

    // --- AI Advising State ---
    private val _aiProductivityAdvice = MutableStateFlow<String?>(null)
    val aiProductivityAdvice: StateFlow<String?> = _aiProductivityAdvice.asStateFlow()

    private val _isAnalyzingProductivity = MutableStateFlow(false)
    val isAnalyzingProductivity: StateFlow<Boolean> = _isAnalyzingProductivity.asStateFlow()

    private val _aiFuturePlanAdvice = MutableStateFlow<String?>(null)
    val aiFuturePlanAdvice: StateFlow<String?> = _aiFuturePlanAdvice.asStateFlow()

    private val _isAnalyzingFuturePlan = MutableStateFlow(false)
    val isAnalyzingFuturePlan: StateFlow<Boolean> = _isAnalyzingFuturePlan.asStateFlow()

    private val _futureQuranBaseline = MutableStateFlow(prefs.getInt("FutureQuranBaseline", 10))
    val futureQuranBaseline: StateFlow<Int> = _futureQuranBaseline.asStateFlow()

    fun updateFutureQuranBaseline(baseline: Int) {
        _futureQuranBaseline.value = baseline
        prefs.edit().putInt("FutureQuranBaseline", baseline).apply()
    }

    // --- Quran Bookmarks State ---
    private val _quranBookmarks = MutableStateFlow<List<QuranBookmark>>(emptyList())
    val quranBookmarks: StateFlow<List<QuranBookmark>> = _quranBookmarks.asStateFlow()

    fun loadQuranBookmarks() {
        val bookmarksStr = prefs.getString("quran_bookmarks_list_v3", "") ?: ""
        if (bookmarksStr.isEmpty()) {
            _quranBookmarks.value = emptyList()
            return
        }
        val list = mutableListOf<QuranBookmark>()
        bookmarksStr.split(";;;").forEach { item ->
            val parts = item.split("|")
            if (parts.size >= 4) {
                try {
                    list.add(
                        QuranBookmark(
                            suraId = parts[0].toInt(),
                            verseIndex = parts[1].toInt(),
                            timestamp = parts[2].toLong(),
                            suraName = parts[3]
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        _quranBookmarks.value = list
    }

    fun addQuranBookmark(suraId: Int, suraName: String, verseIndex: Int) {
        val currentList = _quranBookmarks.value.toMutableList()
        // Remove existing bookmark for same sura & verse to put the newly updated one at the top
        currentList.removeAll { it.suraId == suraId && it.verseIndex == verseIndex }
        currentList.add(0, QuranBookmark(suraId, suraName, verseIndex, System.currentTimeMillis()))
        saveQuranBookmarks(currentList)
    }

    fun removeQuranBookmark(suraId: Int, verseIndex: Int) {
        val currentList = _quranBookmarks.value.filterNot { it.suraId == suraId && it.verseIndex == verseIndex }
        saveQuranBookmarks(currentList)
    }

    private fun saveQuranBookmarks(list: List<QuranBookmark>) {
        _quranBookmarks.value = list
        val joined = list.joinToString(";;;") { "${it.suraId}|${it.verseIndex}|${it.timestamp}|${it.suraName}" }
        prefs.edit().putString("quran_bookmarks_list_v3", joined).apply()
    }

    // --- Daily Quran Recitation Progress Tracker State ---
    private val _quranPagesReadToday = MutableStateFlow(0)
    val quranPagesReadToday: StateFlow<Int> = _quranPagesReadToday.asStateFlow()

    private val _quranDailyGoalPages = MutableStateFlow(prefs.getInt("quran_daily_goal_pages", 4))
    val quranDailyGoalPages: StateFlow<Int> = _quranDailyGoalPages.asStateFlow()

    private val _quranStreakDays = MutableStateFlow(0)
    val quranStreakDays: StateFlow<Int> = _quranStreakDays.asStateFlow()

    fun loadQuranProgress() {
        val today = getTodayDateString()
        _quranPagesReadToday.value = prefs.getInt("quran_pages_read_$today", 0)
        _quranDailyGoalPages.value = prefs.getInt("quran_daily_goal_pages", 4)
        _quranStreakDays.value = calculateQuranStreak()
    }

    fun addPagesRead(pages: Int) {
        val today = getTodayDateString()
        val currentRead = prefs.getInt("quran_pages_read_$today", 0)
        val newRead = maxOf(0, currentRead + pages)
        prefs.edit().putInt("quran_pages_read_$today", newRead).apply()
        _quranPagesReadToday.value = newRead
        _quranStreakDays.value = calculateQuranStreak()

        // Also check if we reached the goal. If so, automatically mark the "الورد اليومي" habit as completed if it exists!
        if (newRead >= _quranDailyGoalPages.value) {
            viewModelScope.launch {
                repository.allHabits.first().find { it.name.contains("الورد اليومي") || it.name.contains("القرآن") }?.let { habit ->
                    val todayLogs = repository.getLogsForDate(today).first()
                    if (todayLogs.none { it.habitId == habit.id }) {
                        // Mark habit completed
                        repository.insertHabitLog(HabitLog(habitId = habit.id, date = today))
                    }
                }
            }
        }
    }

    fun setQuranDailyGoal(pages: Int) {
        val validPages = maxOf(1, pages)
        prefs.edit().putInt("quran_daily_goal_pages", validPages).apply()
        _quranDailyGoalPages.value = validPages
        _quranStreakDays.value = calculateQuranStreak()
    }

    private fun calculateQuranStreak(): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        var streak = 0
        
        // Start checking from today
        val todayStr = sdf.format(cal.time)
        val todayRead = prefs.getInt("quran_pages_read_$todayStr", 0)
        val goal = prefs.getInt("quran_daily_goal_pages", 4)

        if (todayRead >= goal) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
            while (true) {
                val dateStr = sdf.format(cal.time)
                val read = prefs.getInt("quran_pages_read_$dateStr", 0)
                if (read >= goal) {
                    streak++
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
                if (streak > 365) break // safety guard
            }
        } else {
            // If today is not completed, check if yesterday was completed. If so, streak is preserved (excluding today).
            cal.add(Calendar.DAY_OF_YEAR, -1)
            while (true) {
                val dateStr = sdf.format(cal.time)
                val read = prefs.getInt("quran_pages_read_$dateStr", 0)
                if (read >= goal) {
                    streak++
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
                if (streak > 365) break // safety guard
            }
        }
        return streak
    }

    // --- Focus Mode State ---
    private val _isFocusActive = MutableStateFlow(false)
    val isFocusActive: StateFlow<Boolean> = _isFocusActive.asStateFlow()

    private val _focusRemainingSeconds = MutableStateFlow(0)
    val focusRemainingSeconds: StateFlow<Int> = _focusRemainingSeconds.asStateFlow()

    private var focusTimer: Timer? = null

    init {
        com.example.util.LangHelper.setLanguage(_appLanguage.value)
        // Hydrate database with initial apps & habits if empty
        viewModelScope.launch {
            repository.allHabits.first().let { currentHabits ->
                if (currentHabits.isEmpty()) {
                    repository.insertHabit(Habit(name = "قراءة الورد اليومي من القرآن", icon = "📖", category = "عبادة", targetDurationMinutes = 20, aiExpectedDays = 30, aiExplanation = "المواظبة على الورد القرآني تملأ اليوم بركة ونوراً."))
                    repository.insertHabit(Habit(name = "الالتزام بالصلاة في وقتها", icon = "🕌", category = "عبادة", targetDurationMinutes = 15, aiExpectedDays = 40, aiExplanation = "الصلاة ركيزة اليوم ومفتاح كل توفيق ونجاح."))
                    repository.insertHabit(Habit(name = "ممارسة الرياضة 20 دقيقة", icon = "💪", category = "رياضة", targetDurationMinutes = 20, aiExpectedDays = 45, aiExplanation = "الحركة تنشط البدن، تجدد الطاقة، وترفع مستوى التركيز."))
                    repository.insertHabit(Habit(name = "شرب 2 لتر من الماء", icon = "💧", category = "صحة", targetDurationMinutes = 5, aiExpectedDays = 21, aiExplanation = "الترطيب المستمر يحافظ على حيوية خلايا الجسم وإنتاجيتها."))
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
            refreshDownloadedSuras()
            loadQuranBookmarks()
            loadQuranProgress()
            triggerApiPrayerTimesFetch()

            updateCurrentTimeOfDay()
            launch {
                while (true) {
                    updateCurrentTimeOfDay()
                    kotlinx.coroutines.delay(60 * 1000L) // update every minute
                }
            }
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
            val task = Task(
                title = title,
                description = description,
                dueDate = dueDate,
                category = category,
                isAppointment = isAppointment
            )
            val insertedId = repository.insertTask(task)
            // Mirror to Firestore in background
            try {
                com.example.util.FirestoreHelper.saveTask(getApplication(), task.copy(id = insertedId.toInt()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Trigger home screen widget updates
            com.example.widget.HayatyWidgetProvider.triggerUpdate(getApplication())
            com.example.widget.HayatyTasksWidgetProvider.triggerUpdate(getApplication())
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val updatedTask = task.copy(isCompleted = !task.isCompleted)
            repository.updateTask(updatedTask)
            // Mirror to Firestore in background
            try {
                com.example.util.FirestoreHelper.saveTask(getApplication(), updatedTask)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Trigger home screen widget updates
            com.example.widget.HayatyWidgetProvider.triggerUpdate(getApplication())
            com.example.widget.HayatyTasksWidgetProvider.triggerUpdate(getApplication())
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            // Mirror to Firestore in background
            try {
                com.example.util.FirestoreHelper.deleteTask(getApplication(), task.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Trigger home screen widget updates
            com.example.widget.HayatyWidgetProvider.triggerUpdate(getApplication())
            com.example.widget.HayatyTasksWidgetProvider.triggerUpdate(getApplication())
        }
    }

    // --- Habit Actions ---
    private val _isPredictingSolidification = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val isPredictingSolidification: StateFlow<Map<Int, Boolean>> = _isPredictingSolidification.asStateFlow()

    fun addHabit(
        name: String,
        targetTime: String? = null,
        icon: String = "✨",
        category: String = "عام",
        targetDurationMinutes: Int = 15
    ) {
        viewModelScope.launch {
            val prediction = com.example.api.GeminiClient.predictHabitSolidification(name, category, targetDurationMinutes)
            val habit = Habit(
                name = name,
                icon = icon,
                category = category,
                targetDurationMinutes = targetDurationMinutes,
                reminderTime = targetTime,
                aiExpectedDays = prediction.first,
                aiExplanation = prediction.second
            )
            val habitId = repository.insertHabit(habit)
            // Sync to Firestore in background
            try {
                com.example.util.FirestoreHelper.saveHabit(getApplication(), habit.copy(id = habitId.toInt()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (!targetTime.isNullOrBlank()) {
                prefs.edit().putString("HabitTargetTime_$habitId", targetTime).apply()
            }
            val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            prefs.edit().putString("LastHabitAddedMonthYear", currentMonthStr).apply()
            updateMonthlyHabitAlertState()
        }
    }

    fun updateHabit(habit: Habit) {
        viewModelScope.launch {
            repository.updateHabit(habit)
            // Sync to Firestore in background
            try {
                com.example.util.FirestoreHelper.saveHabit(getApplication(), habit)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun triggerAiSolidificationPrediction(habit: Habit) {
        viewModelScope.launch {
            _isPredictingSolidification.value = _isPredictingSolidification.value + (habit.id to true)
            try {
                val prediction = com.example.api.GeminiClient.predictHabitSolidification(
                    habitName = habit.name,
                    category = habit.category,
                    targetDurationMinutes = habit.targetDurationMinutes
                )
                val updatedHabit = habit.copy(
                    aiExpectedDays = prediction.first,
                    aiExplanation = prediction.second
                )
                repository.updateHabit(updatedHabit)
                // Sync to Firestore
                try {
                    com.example.util.FirestoreHelper.saveHabit(getApplication(), updatedHabit)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isPredictingSolidification.value = _isPredictingSolidification.value - habit.id
            }
        }
    }

    fun deleteHabit(habitId: Int) {
        viewModelScope.launch {
            repository.deleteHabit(habitId)
            // Sync to Firestore
            try {
                com.example.util.FirestoreHelper.deleteHabit(getApplication(), habitId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            prefs.edit().remove("HabitTargetTime_$habitId").apply()
        }
    }

    // App Launch notification for habit completion
    private val _launchAppEvent = MutableSharedFlow<String>()
    val launchAppEvent: SharedFlow<String> = _launchAppEvent.asSharedFlow()

    // Installed activities for launcher linkage
    data class AppInfo(val label: String, val packageName: String)
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    fun loadInstalledApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                val appsList = resolveInfos.mapNotNull { resolveInfo ->
                    val packageName = resolveInfo.activityInfo.packageName
                    val label = resolveInfo.loadLabel(pm).toString()
                    if (packageName != context.packageName) {
                        AppInfo(label, packageName)
                    } else null
                }.distinctBy { it.packageName }.sortedBy { it.label }
                _installedApps.value = appsList
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleHabit(habit: Habit) {
        viewModelScope.launch {
            val date = currentDate.value
            val isCompleted = todayHabitLogs.value.none { it.habitId == habit.id }
            repository.toggleHabitCompletion(habit, date, isCompleted)
            // Sync updated Habit to Firestore in background
            try {
                val updatedHabit = if (isCompleted) {
                    val newStreak = if (habit.lastCompletedDate == null) 1 else habit.streak + 1
                    habit.copy(streak = newStreak, lastCompletedDate = date)
                } else {
                    val newStreak = maxOf(0, habit.streak - 1)
                    habit.copy(streak = newStreak, lastCompletedDate = null)
                }
                com.example.util.FirestoreHelper.saveHabit(getApplication(), updatedHabit)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (isCompleted) {
                val completionTime = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                prefs.edit().putString("HabitCompletionTime_${habit.id}_$date", completionTime).apply()
                // Emit targetAppPackage to launch it
                habit.targetAppPackage?.let { packageName ->
                    if (packageName.isNotBlank()) {
                        _launchAppEvent.emit(packageName)
                    }
                }
            } else {
                prefs.edit().remove("HabitCompletionTime_${habit.id}_$date").apply()
            }
        }
    }

    fun getHabitTargetTime(habitId: Int): String? {
        return prefs.getString("HabitTargetTime_$habitId", null)
    }

    fun getHabitCompletionTime(habitId: Int, date: String): String? {
        return prefs.getString("HabitCompletionTime_${habitId}_$date", null)
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

    fun analyzeFuturePlanWithAI() {
        viewModelScope.launch {
            _isAnalyzingFuturePlan.value = true
            try {
                val tasks = allTasks.value
                val habits = allHabits.value
                val baseline = _futureQuranBaseline.value
                val response = com.example.api.GeminiClient.getFuturePlanEvaluation(baseline, tasks, habits)
                _aiFuturePlanAdvice.value = response
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isAnalyzingFuturePlan.value = false
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
                    checkAndBlockDistractingApplication()
                } else {
                    stopFocusMode()
                }
            }
        }, 1000, 1000)
    }

    private fun checkAndBlockDistractingApplication() {
        val context = getApplication<Application>()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        if (!hasUsageStatsPermission(context)) return
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 15000,
            time
        )
        if (stats != null && stats.isNotEmpty()) {
            val recentApp = stats.maxByOrNull { it.lastTimeUsed } ?: return
            val currentPkg = recentApp.packageName
            val myPkg = context.packageName
            
            // Distracting app packages to block during active focus mode
            val distractingPackages = listOf(
                "com.google.android.youtube",
                "com.zhiliaoapp.musically",
                "com.facebook.katana",
                "com.instagram.android",
                "com.twitter.android",
                "com.whatsapp",
                "com.snapchat.android",
                "com.pinterest",
                "com.reddit.frontpage",
                "com.linkedin.android"
            )
            
            if (currentPkg != myPkg && distractingPackages.contains(currentPkg)) {
                // Relaunch Hayaty to cover it!
                val intent = context.packageManager.getLaunchIntentForPackage(myPkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    context.startActivity(intent)
                }
            }
        }
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
            com.example.widget.HayatyUsageWidgetProvider.triggerUpdate(getApplication())
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

    fun getScreenTimeAiAdvice() {
        viewModelScope.launch {
            _isScreenTimeAiLoading.value = true
            try {
                val currentUsage = usageRecords.value
                val advice = GeminiClient.getScreenTimeAdvice(currentUsage)
                _screenTimeAiAdvice.value = advice
            } catch (e: Exception) {
                e.printStackTrace()
                _screenTimeAiAdvice.value = "حدث خطأ أثناء الاتصال بمستشار الذكاء الاصطناعي: ${e.message}"
            } finally {
                _isScreenTimeAiLoading.value = false
            }
        }
    }

    fun sendScreenTimeNotification(title: String, message: String) {
        val context = getApplication<Application>()
        val channelId = "screen_time_wellbeing_channel"
        val notificationId = 10099

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "الصحة الرقمية والتركيز",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات وتذكيرات لتقليل استخدام الهاتف وصيانة الوقت"
            }
            notificationManager.createNotificationChannel(channel)
        }
        notificationManager.notify(notificationId, builder.build())
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

    // --- Google Drive Backup State ---
    private val _googleAccountEmail = MutableStateFlow<String?>(prefs.getString("GoogleAccountEmail", null))
    val googleAccountEmail: StateFlow<String?> = _googleAccountEmail.asStateFlow()

    private val _googleBackupTime = MutableStateFlow<String>(prefs.getString("GoogleBackupTime", "لم يتم الرفع بعد") ?: "لم يتم الرفع بعد")
    val googleBackupTime: StateFlow<String> = _googleBackupTime.asStateFlow()

    fun connectGoogleAccount(email: String) {
        prefs.edit().putString("GoogleAccountEmail", email).apply()
        _googleAccountEmail.value = email
    }

    fun disconnectGoogleAccount() {
        prefs.edit().remove("GoogleAccountEmail").apply()
        _googleAccountEmail.value = null
    }

    fun performGoogleDriveBackup(context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Collect settings representation
                val azkarPrefs = context.getSharedPreferences("AzkarPrefs", Context.MODE_PRIVATE)
                val hayatyPrefs = context.getSharedPreferences("HayatyPrefs", Context.MODE_PRIVATE)
                
                // Get all preferences as a Map
                val azkarEntries = azkarPrefs.all
                val hayatyEntries = hayatyPrefs.all

                // Simple JSON builder using JSONObject
                val backupJson = org.json.JSONObject()
                val azkarJson = org.json.JSONObject()
                val hayatyJson = org.json.JSONObject()

                for ((key, value) in azkarEntries) {
                    if (value != null) {
                        azkarJson.put(key, value)
                    }
                }
                for ((key, value) in hayatyEntries) {
                    if (value != null) {
                        hayatyJson.put(key, value)
                    }
                }

                backupJson.put("azkar_prefs", azkarJson)
                backupJson.put("hayaty_prefs", hayatyJson)
                backupJson.put("backup_timestamp", System.currentTimeMillis())

                // Fetch tasks and habits
                val tasks = repository.allTasks.first()
                val habits = repository.allHabits.first()
                val habitLogs = repository.allHabitLogs.first()

                // Convert to JsonArray
                val tasksArray = org.json.JSONArray()
                for (t in tasks) {
                    val obj = org.json.JSONObject()
                    obj.put("id", t.id)
                    obj.put("title", t.title)
                    obj.put("description", t.description)
                    obj.put("isCompleted", t.isCompleted)
                    obj.put("dueDate", t.dueDate)
                    obj.put("category", t.category)
                    tasksArray.put(obj)
                }

                val habitsArray = org.json.JSONArray()
                for (h in habits) {
                    val obj = org.json.JSONObject()
                    obj.put("id", h.id)
                    obj.put("name", h.name)
                    obj.put("streak", h.streak)
                    obj.put("lastCompletedDate", h.lastCompletedDate ?: "")
                    obj.put("icon", h.icon)
                    obj.put("category", h.category)
                    obj.put("targetDurationMinutes", h.targetDurationMinutes)
                    obj.put("reminderTime", h.reminderTime ?: "")
                    obj.put("aiExpectedDays", h.aiExpectedDays)
                    obj.put("aiExplanation", h.aiExplanation ?: "")
                    habitsArray.put(obj)
                }

                val logsArray = org.json.JSONArray()
                for (l in habitLogs) {
                    val obj = org.json.JSONObject()
                    obj.put("id", l.id)
                    obj.put("habitId", l.habitId)
                    obj.put("date", l.date)
                    obj.put("isCompleted", l.isCompleted)
                    logsArray.put(obj)
                }

                backupJson.put("tasks", tasksArray)
                backupJson.put("habits", habitsArray)
                backupJson.put("habit_logs", logsArray)

                val backupString = backupJson.toString()

                // Save locally first to simulate Drive backup file
                val file = File(context.filesDir, "google_drive_hayaty_backup.json")
                FileOutputStream(file).use {
                    it.write(backupString.toByteArray())
                }

                // Update timestamp
                val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                val nowStr = sdf.format(Date())
                prefs.edit().putString("GoogleBackupTime", nowStr).apply()
                _googleBackupTime.value = nowStr

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "حدث خطأ غير معروف")
                }
            }
        }
    }

    fun restoreGoogleDriveBackup(context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "google_drive_hayaty_backup.json")
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        onError("لا توجد ملفات نسخة احتياطية محفوظة على خدمة الحساب السحابي Google Drive بعد.")
                    }
                    return@launch
                }

                val jsonStr = file.readText()
                val backupJson = org.json.JSONObject(jsonStr)

                val azkarJson = backupJson.optJSONObject("azkar_prefs")
                val hayatyJson = backupJson.optJSONObject("hayaty_prefs")

                val azkarPrefs = context.getSharedPreferences("AzkarPrefs", Context.MODE_PRIVATE).edit()
                val hayatyPrefs = context.getSharedPreferences("HayatyPrefs", Context.MODE_PRIVATE).edit()

                if (azkarJson != null) {
                    val keys = azkarJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = azkarJson.get(key)
                        when (value) {
                            is Boolean -> azkarPrefs.putBoolean(key, value)
                            is Int -> azkarPrefs.putInt(key, value)
                            is Long -> azkarPrefs.putLong(key, value)
                            is Double -> azkarPrefs.putFloat(key, value.toFloat())
                            is Float -> azkarPrefs.putFloat(key, value)
                            is String -> azkarPrefs.putString(key, value)
                        }
                    }
                    azkarPrefs.apply()
                }

                if (hayatyJson != null) {
                    val keys = hayatyJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = hayatyJson.get(key)
                        when (value) {
                            is Boolean -> hayatyPrefs.putBoolean(key, value)
                            is Int -> hayatyPrefs.putInt(key, value)
                            is Long -> hayatyPrefs.putLong(key, value)
                            is Double -> hayatyPrefs.putFloat(key, value.toFloat())
                            is Float -> hayatyPrefs.putFloat(key, value)
                            is String -> hayatyPrefs.putString(key, value)
                        }
                    }
                    hayatyPrefs.apply()
                }

                // Sync and restore database entries
                val tasksArray = backupJson.optJSONArray("tasks")
                if (tasksArray != null) {
                    for (i in 0 until tasksArray.length()) {
                        val obj = tasksArray.getJSONObject(i)
                        repository.insertTask(
                            Task(
                                id = obj.optInt("id", 0),
                                title = obj.optString("title", ""),
                                description = obj.optString("description", ""),
                                isCompleted = obj.optBoolean("isCompleted", false),
                                dueDate = obj.optLong("dueDate", System.currentTimeMillis()),
                                category = obj.optString("category", "عام")
                            )
                        )
                    }
                }

                val habitsArray = backupJson.optJSONArray("habits")
                if (habitsArray != null) {
                    for (i in 0 until habitsArray.length()) {
                        val obj = habitsArray.getJSONObject(i)
                        repository.insertHabit(
                            Habit(
                                id = obj.optInt("id", 0),
                                name = obj.optString("name", ""),
                                streak = obj.optInt("streak", 0),
                                lastCompletedDate = obj.optString("lastCompletedDate").let { if (it.isEmpty()) null else it },
                                icon = obj.optString("icon", "✨"),
                                category = obj.optString("category", "عام"),
                                targetDurationMinutes = obj.optInt("targetDurationMinutes", 15),
                                reminderTime = obj.optString("reminderTime").let { if (it.isEmpty()) null else it },
                                aiExpectedDays = obj.optInt("aiExpectedDays", 21),
                                aiExplanation = obj.optString("aiExplanation").let { if (it.isEmpty()) null else it }
                            )
                        )
                    }
                }

                val logsArray = backupJson.optJSONArray("habit_logs")
                if (logsArray != null) {
                    for (i in 0 until logsArray.length()) {
                        val obj = logsArray.getJSONObject(i)
                        repository.insertHabitLog(
                            HabitLog(
                                id = obj.optInt("id", 0),
                                habitId = obj.optInt("habitId", 0),
                                date = obj.optString("date", ""),
                                isCompleted = obj.optBoolean("isCompleted", false)
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "فشل فك تشفير واستعادة النسخة الاحتياطية")
                }
            }
        }
    }

    // --- Firestore Sync & CRUD State ---
    private val _firestoreTasks = MutableStateFlow<List<Task>>(emptyList())
    val firestoreTasks: StateFlow<List<Task>> = _firestoreTasks.asStateFlow()

    private val _firestoreHabits = MutableStateFlow<List<Habit>>(emptyList())
    val firestoreHabits: StateFlow<List<Habit>> = _firestoreHabits.asStateFlow()

    private val _firestoreDailySchedules = MutableStateFlow<List<com.example.util.FirestoreHelper.DailySchedule>>(emptyList())
    val firestoreDailySchedules: StateFlow<List<com.example.util.FirestoreHelper.DailySchedule>> = _firestoreDailySchedules.asStateFlow()

    private val _isFirestoreLoading = MutableStateFlow(false)
    val isFirestoreLoading: StateFlow<Boolean> = _isFirestoreLoading.asStateFlow()

    private val _firestoreStatusMessage = MutableStateFlow<String?>(null)
    val firestoreStatusMessage: StateFlow<String?> = _firestoreStatusMessage.asStateFlow()

    fun clearFirestoreStatus() {
        _firestoreStatusMessage.value = null
    }

    fun fetchFirestoreData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isFirestoreLoading.value = true
            try {
                val tasks = com.example.util.FirestoreHelper.getTasks(getApplication())
                val habits = com.example.util.FirestoreHelper.getHabits(getApplication())
                val schedules = com.example.util.FirestoreHelper.getDailySchedules(getApplication())
                _firestoreTasks.value = tasks
                _firestoreHabits.value = habits
                _firestoreDailySchedules.value = schedules
                _firestoreStatusMessage.value = "تم جلب البيانات السحابية بنجاح! ☁️"
            } catch (e: Exception) {
                _firestoreStatusMessage.value = "خطأ أثناء جلب البيانات: ${e.localizedMessage}"
            } finally {
                _isFirestoreLoading.value = false
            }
        }
    }

    fun addFirestoreTask(title: String, description: String, category: String, isAppointment: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _isFirestoreLoading.value = true
            val dummyId = (1000..9999).random()
            val task = Task(
                id = dummyId,
                title = title,
                description = description,
                dueDate = System.currentTimeMillis(),
                category = category,
                isCompleted = false,
                isAppointment = isAppointment
            )
            com.example.util.FirestoreHelper.saveTask(getApplication(), task)
            _firestoreStatusMessage.value = "تم إضافة المهمة السحابية بنجاح! ☁️"
            fetchFirestoreData()
        }
    }

    fun updateFirestoreTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            _isFirestoreLoading.value = true
            com.example.util.FirestoreHelper.saveTask(getApplication(), task)
            _firestoreStatusMessage.value = "تم تحديث المهمة السحابية بنجاح! ☁️"
            fetchFirestoreData()
        }
    }

    fun deleteFirestoreTask(taskId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _isFirestoreLoading.value = true
            com.example.util.FirestoreHelper.deleteTask(getApplication(), taskId)
            _firestoreStatusMessage.value = "تم حذف المهمة السحابية بنجاح! 🗑️"
            fetchFirestoreData()
        }
    }

    fun addFirestoreHabit(name: String, icon: String, category: String, durationMinutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _isFirestoreLoading.value = true
            val dummyId = (1000..9999).random()
            val habit = Habit(
                id = dummyId,
                name = name,
                icon = icon,
                category = category,
                targetDurationMinutes = durationMinutes
            )
            com.example.util.FirestoreHelper.saveHabit(getApplication(), habit)
            _firestoreStatusMessage.value = "تم إضافة العادة السحابية بنجاح! ☁️"
            fetchFirestoreData()
        }
    }

    fun updateFirestoreHabit(habit: Habit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isFirestoreLoading.value = true
            com.example.util.FirestoreHelper.saveHabit(getApplication(), habit)
            _firestoreStatusMessage.value = "تم تحديث العادة السحابية بنجاح! ☁️"
            fetchFirestoreData()
        }
    }

    fun deleteFirestoreHabit(habitId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _isFirestoreLoading.value = true
            com.example.util.FirestoreHelper.deleteHabit(getApplication(), habitId)
            _firestoreStatusMessage.value = "تم حذف العادة السحابية بنجاح! 🗑️"
            fetchFirestoreData()
        }
    }

    fun addFirestoreDailySchedule(title: String, timeSlot: String, date: String, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isFirestoreLoading.value = true
            val schedule = com.example.util.FirestoreHelper.DailySchedule(
                id = "",
                title = title,
                timeSlot = timeSlot,
                date = date,
                note = note
            )
            com.example.util.FirestoreHelper.saveDailySchedule(getApplication(), schedule)
            _firestoreStatusMessage.value = "تم إضافة الجدول اليومي السحابي بنجاح! 🗓️"
            fetchFirestoreData()
        }
    }

    fun updateFirestoreDailySchedule(schedule: com.example.util.FirestoreHelper.DailySchedule) {
        viewModelScope.launch(Dispatchers.IO) {
            _isFirestoreLoading.value = true
            com.example.util.FirestoreHelper.saveDailySchedule(getApplication(), schedule)
            _firestoreStatusMessage.value = "تم تحديث الجدول اليومي السحابي بنجاح! 🗓️"
            fetchFirestoreData()
        }
    }

    fun deleteFirestoreDailySchedule(scheduleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isFirestoreLoading.value = true
            com.example.util.FirestoreHelper.deleteDailySchedule(getApplication(), scheduleId)
            _firestoreStatusMessage.value = "تم حذف الجدول اليومي السحابي بنجاح! 🗑️"
            fetchFirestoreData()
        }
    }

    fun syncLocalDatabaseToFirestore() {
        viewModelScope.launch(Dispatchers.IO) {
            _isFirestoreLoading.value = true
            _firestoreStatusMessage.value = "جاري رفع ومزامنة البيانات المحلية إلى السحابة... ⏳"
            try {
                val tasks = repository.allTasks.first()
                val habits = repository.allHabits.first()
                
                // Save all tasks to Firestore
                for (task in tasks) {
                    com.example.util.FirestoreHelper.saveTask(getApplication(), task)
                }

                // Save all habits to Firestore
                for (habit in habits) {
                    com.example.util.FirestoreHelper.saveHabit(getApplication(), habit)
                }

                _firestoreStatusMessage.value = "تم مزامنة ورفع كافة البيانات وبنجاح! ☁️🚀"
                fetchFirestoreData()
            } catch (e: Exception) {
                _firestoreStatusMessage.value = "فشل المزامنة المتقدمة: ${e.localizedMessage}"
            } finally {
                _isFirestoreLoading.value = false
            }
        }
    }

    fun syncFirestoreToLocalDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            _isFirestoreLoading.value = true
            _firestoreStatusMessage.value = "جاري جلب واستعادة البيانات من السحابة... ⏳"
            try {
                val tasks = com.example.util.FirestoreHelper.getTasks(getApplication())
                val habits = com.example.util.FirestoreHelper.getHabits(getApplication())
                
                for (task in tasks) {
                    repository.insertTask(task)
                }

                for (habit in habits) {
                    repository.insertHabit(habit)
                }

                _firestoreStatusMessage.value = "تم استعادة كافة البيانات السحابية بنجاح محلياً! 🎉"
            } catch (e: Exception) {
                _firestoreStatusMessage.value = "فشل تحميل البيانات محلياً: ${e.localizedMessage}"
            } finally {
                _isFirestoreLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        focusTimer?.cancel()
    }
}
