package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import com.example.data.AppUsageRecord
import com.example.data.Habit
import com.example.data.HabitLog
import com.example.data.Task
import com.example.data.AzkarData
import com.example.data.AzkarOverlayService
import android.net.Uri
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.ui.HayatyViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.util.PrayerTime
import com.example.util.PrayerTimesHelper
import com.example.util.QuranData
import com.example.util.Sura
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: HayatyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleWidgetIntent(intent)

        // Auto-start continuous daytime Azkar reminder if allowed and enabled
        val prefs = getSharedPreferences(com.example.data.AzkarOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(com.example.data.AzkarOverlayService.KEY_ENABLED, true)
        if (isEnabled && hasOverlayPermission(this)) {
            com.example.data.AzkarOverlayService.startService(this)
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemDark
            }
            MyApplicationTheme(darkTheme = darkTheme, themeColor = themeColor) {
                HayatyApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.action == "com.example.action.ADD_TASK") {
            viewModel.triggerTaskAddDialog(true)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh usage stats on resume to get real screen time changes
        viewModel.refreshUsageStats(this)
    }
}

// --- Navigation Item Enum ---
enum class NavItem(val route: String, val title: String, val iconSelected: androidx.compose.ui.graphics.vector.ImageVector, val iconUnselected: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("home", "الرئيسية", Icons.Filled.Home, Icons.Outlined.Home),
    HABITS("habits", "العادات", Icons.Filled.DateRange, Icons.Outlined.DateRange),
    FOCUS("focus", "مراقب التركيز", Icons.Filled.Lock, Icons.Outlined.Lock),
    QURAN("quran", "القرآن والصلاة", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    AI_COACH("ai_coach", "مستشار الذكاء", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
}

@Composable
fun HayatyApp(viewModel: HayatyViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(NavItem.HOME) }
    
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val layoutDirection = if (appLanguage == "ar") {
        androidx.compose.ui.unit.LayoutDirection.Rtl
    } else {
        androidx.compose.ui.unit.LayoutDirection.Ltr
    }
    
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection
    ) {
        LaunchedEffect(Unit) {
            viewModel.loadInstalledApps(context)
            viewModel.launchAppEvent.collect { packageName ->
                try {
                    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        context.startActivity(intent)
                    } else {
                        android.widget.Toast.makeText(context, "تنبيه: التطبيق المرتبط غير مثبت على هذا الجهاز.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "خطأ أثناء محاولة تشغيل التطبيق.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        val isFocusActive by viewModel.isFocusActive.collectAsStateWithLifecycle()
        val focusSeconds by viewModel.focusRemainingSeconds.collectAsStateWithLifecycle()

        Box(modifier = Modifier.fillMaxSize()) {
            if (isFocusActive) {
                val focusTasks by viewModel.allTasks.collectAsStateWithLifecycle()
                FocusActiveOverlay(
                    remainingSeconds = focusSeconds,
                    tasks = focusTasks,
                    onToggleTask = { task -> viewModel.toggleTaskCompletion(task) },
                    onCancel = { viewModel.stopFocusMode() }
                )
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize().testTag("app_scaffold"),
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            modifier = Modifier.navigationBarsPadding().testTag("bottom_navigation")
                        ) {
                            NavItem.values().forEach { item ->
                                val isSelected = currentTab == item
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentTab = item },
                                    label = { 
                                        Text(
                                            text = com.example.util.LangHelper.tr(item.title), 
                                            fontSize = 11.sp, 
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.SansSerif
                                        ) 
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) item.iconSelected else item.iconUnselected,
                                            contentDescription = com.example.util.LangHelper.tr(item.title),
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    modifier = Modifier.testTag("nav_item_${item.route}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        },
                        modifier = Modifier.padding(innerPadding).fillMaxSize(),
                        label = "TabTransition"
                    ) { targetTab ->
                        when (targetTab) {
                            NavItem.HOME -> HomeScreen(viewModel, onNavigateToFocus = { currentTab = NavItem.FOCUS })
                            NavItem.HABITS -> HabitsScreen(viewModel)
                            NavItem.FOCUS -> FocusScreen(viewModel)
                            NavItem.QURAN -> QuranScreen(viewModel)
                            NavItem.AI_COACH -> AICoachScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. HOME SCREEN (الرئيسية)
// ==========================================
@Composable
fun HomeScreen(viewModel: HayatyViewModel, onNavigateToFocus: () -> Unit) {
    val context = LocalContext.current
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val prayerTimes by viewModel.prayerTimes.collectAsStateWithLifecycle()
    val selectedCity by viewModel.selectedCity.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.selectedCountry.collectAsStateWithLifecycle()
    val habits by viewModel.allHabits.collectAsStateWithLifecycle()
    val todayLogs by viewModel.todayHabitLogs.collectAsStateWithLifecycle()
    val allHabitLogs by viewModel.allHabitLogs.collectAsStateWithLifecycle()
    val usageRecords by viewModel.usageRecords.collectAsStateWithLifecycle()
    
    val showTimerBanner by viewModel.showTimerBanner.collectAsStateWithLifecycle()
    val showStatsGrid by viewModel.showStatsGrid.collectAsStateWithLifecycle()
    val showPrayerWidget by viewModel.showPrayerWidget.collectAsStateWithLifecycle()
    val showQuranTracker by viewModel.showQuranTracker.collectAsStateWithLifecycle()
    val showFiqhWidget by viewModel.showFiqhWidget.collectAsStateWithLifecycle()
    val showHabitWidget by viewModel.showHabitWidget.collectAsStateWithLifecycle()
    val showTasksWidget by viewModel.showTasksWidget.collectAsStateWithLifecycle()
    val showAiSuggestion by viewModel.showAiSuggestion.collectAsStateWithLifecycle()

    val currentTimeOfDay by viewModel.currentTimeOfDay.collectAsStateWithLifecycle()
    val timeOfDayOverride by viewModel.timeOfDayOverride.collectAsStateWithLifecycle()

    val backgroundBrush = remember(currentTimeOfDay, androidx.compose.foundation.isSystemInDarkTheme()) {
        val colors = when (currentTimeOfDay) {
            "morning" -> {
                if (true) { // We can use the dynamic colors or dark theme check
                    listOf(Color(0xFF0B121F), Color(0xFF111E36))
                } else {
                    listOf(Color(0xFFE0F2FE), Color(0xFFF0F9FF))
                }
            }
            "evening" -> {
                if (true) {
                    listOf(Color(0xFF1E0C26), Color(0xFF2E173D))
                } else {
                    listOf(Color(0xFFFFECE0), Color(0xFFFFF6F0))
                }
            }
            "night" -> {
                if (true) {
                    listOf(Color(0xFF070A13), Color(0xFF0F1528))
                } else {
                    listOf(Color(0xFFEBEFF5), Color(0xFFF1F5F9))
                }
            }
            else -> {
                if (true) {
                    listOf(Color(0xFF121212), Color(0xFF1E1E1E))
                } else {
                    listOf(Color(0xFFF9FAFB), Color(0xFFFFFFFF))
                }
            }
        }
        androidx.compose.ui.graphics.Brush.verticalGradient(colors)
    }

    val isUsingGps by viewModel.isUsingGps.collectAsStateWithLifecycle()
    val gpsLatitude by viewModel.gpsLatitude.collectAsStateWithLifecycle()
    val gpsLongitude by viewModel.gpsLongitude.collectAsStateWithLifecycle()
    val apiPrayerTimes by viewModel.apiPrayerTimes.collectAsStateWithLifecycle()

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.setUsingGps(true, context)
        } else {
            android.widget.Toast.makeText(context, "يرجى منح صلاحية الموقع لتحديد أوقات الصلاة بدقة عبر الأقمار الاصطناعية (GPS)", android.widget.Toast.LENGTH_LONG).show()
            viewModel.setUsingGps(false, context)
        }
    }

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showCitySelectorDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedPrayerDetailKey by remember { mutableStateOf<String?>(null) }

    val showTaskAddDialogOnStart by viewModel.showTaskAddDialogOnStart.collectAsStateWithLifecycle()
    LaunchedEffect(showTaskAddDialogOnStart) {
        if (showTaskAddDialogOnStart) {
            showAddTaskDialog = true
            viewModel.triggerTaskAddDialog(false)
        }
    }

    var activeTaskFilter by remember { mutableStateOf("remaining") } // "all", "remaining", "completed"
    var quickTaskTitle by remember { mutableStateOf("") }
    var quickTaskCategory by remember { mutableStateOf("عام") }

    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val formattedDate = remember(appLanguage) {
        val locale = if (appLanguage == "ar") Locale("ar") else Locale.ENGLISH
        val pattern = if (appLanguage == "ar") "EEEE، d MMMM yyyy" else "EEEE, d MMMM yyyy"
        val sdf = SimpleDateFormat(pattern, locale)
        sdf.format(Date())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- HIGH DENSITY DESIGN HEADER ---
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "الملف الشخصي",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "حياتي",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Right
                        )
                        Text(
                            text = formattedDate,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            textAlign = TextAlign.Right,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
                    IconButton(
                        onClick = {
                            val nextLang = if (appLanguage == "ar") "en" else "ar"
                            viewModel.setAppLanguage(nextLang)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Text(
                            text = if (appLanguage == "ar") "EN" else "ع",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val currentThemeMode by viewModel.themeMode.collectAsStateWithLifecycle()
                    IconButton(
                        onClick = {
                            val nextMode = when (currentThemeMode) {
                                "system" -> "light"
                                "light" -> "dark"
                                else -> "system"
                            }
                            viewModel.setThemeMode(nextMode)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    ) {
                        val iconEmoji = when (currentThemeMode) {
                            "light" -> "☀️"
                            "dark" -> "🌙"
                            else -> "🌓"
                        }
                        Text(text = iconEmoji, fontSize = 16.sp)
                    }

                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { /* Informational action */ },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "التنبيهات",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { showAddTaskDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .testTag("add_task_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "إضافة مهمة",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // --- TIME OF DAY DYNAMIC BANNER WITH LIVE CLOCK ---
        if (showTimerBanner) {
            item {
            var timeString by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                while (true) {
                    val sdf = SimpleDateFormat("HH:mm:ss a", Locale("ar"))
                    timeString = sdf.format(Date())
                    kotlinx.coroutines.delay(1000)
                }
            }

            val cardGradientColors = when (currentTimeOfDay) {
                "morning" -> listOf(Color(0xFF2563EB), Color(0xFF38BDF8), Color(0xFFFBBF24)) // Bright morning blue-amber
                "evening" -> listOf(Color(0xFFEA580C), Color(0xFFBE185D), Color(0xFF311042)) // Sunset warm orange-magenta
                "night" -> listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF111827)) // Deep celestial midnight navy
                else -> listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
            }

            val greetingText = when (currentTimeOfDay) {
                "morning" -> "صباح الخير والبركة والنشاط! ☀️"
                "evening" -> "مساء الخير والهدوء والبر والتقوى! 🌅"
                "night" -> "مساء الطمأنينة والذكر والقيام! 🌙"
                else -> "طاب يومك بكل خير وسعادة! ✨"
            }

            val subtitleText = when (currentTimeOfDay) {
                "morning" -> "«يا حي يا قيوم برحمتك أستغيث..» ابدأ يومك بهمة ونشاط وعزيمة."
                "evening" -> "صلاة المساء وأذكار الغروب تبث الطمأنينة في قلبك وعائلتك."
                "night" -> "سكينة الليل فرصة للذكر، قراءة الورد، وصلاة ركعتين في جوف الليل."
                else -> "اذكر الله في كل وقت وحين، وتوكل عليه في كل أمورك."
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("time_of_day_banner_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .background(Brush.linearGradient(cardGradientColors))
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = com.example.util.LangHelper.tr(greetingText),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            // Manual override control bar
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                val modes = listOf("auto" to "🔄", "morning" to "☀️", "evening" to "🌅", "night" to "🌙")
                                modes.forEach { (m, emoji) ->
                                    val isSel = timeOfDayOverride == m
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(if (isSel) Color.White else Color.Transparent)
                                            .clickable { viewModel.setTimeOfDayOverride(m) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 11.sp, color = if (isSel) Color.Black else Color.White)
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        // Live digital clock with beautiful M3 layout
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                .padding(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "الساعة والوقت",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = timeString,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = com.example.util.LangHelper.tr(subtitleText),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
        }

        // --- AI INTUITIVE GRADIENT CARD (AI Suggestion Card) ---
        if (showAiSuggestion) {
            item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_suggestion_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = com.example.util.LangHelper.tr("ذكاء اصطناعي • نشط ✨"),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "نشط",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = com.example.util.LangHelper.tr("وقت ذروة الإنتاجية المكتشف: الآن"),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = com.example.util.LangHelper.tr("بناءً على نشاطك، هذا هو أفضل وقت لإتمام مهام البرمجة والعبادة وعاداتك اليومية. تم قفل التطبيقات المشتتة."),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.startFocusMode(25) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text(
                                text = com.example.util.LangHelper.tr("بدء جلسة تركيز (٢٥ د)"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
        }

        // --- HIGH DENSITY STATS GRID (2 COLUMNS) ---
        if (showStatsGrid) {
            item {
            val totalMinutes = usageRecords.sumOf { it.durationMs } / 60000
            val usageText = "${totalMinutes / 60} س ${totalMinutes % 60} د"
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stat 1: Spiritual & Prayer
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = "الصلاة",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = com.example.util.LangHelper.tr("الصلاة القادمة"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val currentHourStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                        val nextPrayer = prayerTimes.firstOrNull { it.time >= currentHourStr } ?: prayerTimes.firstOrNull()
                        Text(
                            text = com.example.util.LangHelper.tr(nextPrayer?.arabicName ?: "العصر"),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = com.example.util.LangHelper.tr(if (nextPrayer != null) "خلال دافئة" else "بعد قليل"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = com.example.util.LangHelper.tr("الورد اليومي: ٧٥٪"),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
 
                // Stat 2: Screen Time
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = "استخدام الهاتف",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = com.example.util.LangHelper.tr("استخدام الهاتف"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = usageText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = com.example.util.LangHelper.tr("+١٢٪ عن الأمس"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Small high density progress line
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(modifier = Modifier.height(3.dp).weight(1f).clip(RoundedCornerShape(1.5.dp)).background(MaterialTheme.colorScheme.primary))
                            Box(modifier = Modifier.height(3.dp).weight(1f).clip(RoundedCornerShape(1.5.dp)).background(MaterialTheme.colorScheme.primary))
                            Box(modifier = Modifier.height(3.dp).weight(1f).clip(RoundedCornerShape(1.5.dp)).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
                        }
                    }
                }
            }
        }
        }

        // --- PRAYER TIMES WIDGET ---
        if (showPrayerWidget) {
            item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("prayer_widget"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🕌 مواقيت الصلاة والأذان",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { showCitySelectorDialog = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isUsingGps) {
                                    if (gpsLatitude != null && gpsLongitude != null) {
                                        "📍 موقعي (${String.format(Locale.US, "%.2f", gpsLatitude)}، ${String.format(Locale.US, "%.2f", gpsLongitude)})"
                                    } else {
                                        "📍 موقعي (جاري التحديد...)"
                                    }
                                } else {
                                    if (selectedCountry.isNotBlank() && selectedCountry != "المفترضة" && selectedCountry != "المملكة العربية السعودية") {
                                        "$selectedCity، $selectedCountry"
                                    } else {
                                        selectedCity
                                    }
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "تغيير المدينة",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // API Status Badge & Refresh Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (apiPrayerTimes != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (apiPrayerTimes != null) Color(0xFF4CAF50)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                            )
                            Text(
                                text = if (apiPrayerTimes != null) "جُلب حياً عبر واجهة Aladhan المفتوحة 📡" else "حساب فلكي محلي عالي الدقة 🌙",
                                fontSize = 10.sp,
                                color = if (apiPrayerTimes != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.triggerApiPrayerTimesFetch()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تحديث البيانات من الإنترنت",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentHourStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                        prayerTimes.forEach { prayer ->
                            val isPassed = prayer.time < currentHourStr
                            val isSelected = selectedPrayerDetailKey == prayer.name
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedPrayerDetailKey = if (isSelected) null else prayer.name
                                    }
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else Color.Transparent
                                    )
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = prayer.arabicName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else if (!isPassed) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = prayer.time,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else if (!isPassed) MaterialTheme.colorScheme.secondary 
                                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (!isPassed) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                                    )
                                }
                            }
                        }
                    }

                    if (selectedPrayerDetailKey != null) {
                        val key = selectedPrayerDetailKey
                        val recommendationText = when (key) {
                            "Fajr" -> "سورة الإخلاص والمعوذات ثلاث مرات للتحصين الصباحي والبركة والنشاط ☀️"
                            "Dhuhr" -> "قراءة آية الكرسي عقب الفريضة وسورة الفاتحة وآيات البقرة للهدى وفتح الرزق 💼"
                            "Asr" -> "الاستغفار 100 مرة وسورة الإخلاص والمعوذتين للنقاء النفسي والتركيز 🧘"
                            "Maghrib" -> "أذكار المساء، آية الكرسي، وخواتيم سورة الحشر للحفظ التام والسكينة 🌙"
                            "Isha" -> "سورة الملك الكريمة المنجية من عذاب القبر والهدوء وسكينة النوم 🌌"
                            else -> "تلاوة ما تيسر من الذكر وجوامع الكلم من الأدعية المأثورة عقب الصلاة 🌿"
                        }
                        val prayerArabic = when (key) {
                            "Fajr" -> "الفجر"
                            "Dhuhr" -> "الظهر"
                            "Asr" -> "العصر"
                            "Maghrib" -> "المغرب"
                            "Isha" -> "العشاء"
                            else -> "الصلاة"
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "💡 توصية تلاوة صلاة $prayerArabic",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = { selectedPrayerDetailKey = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "إغلاق",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = recommendationText,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        // --- POST-PRAYER QURAN TRACKER WIDGET ---
        if (showQuranTracker) {
            item {
            val quranStatus by viewModel.quranPostPrayerStatus.collectAsStateWithLifecycle()
            val totalCompleted = quranStatus.values.count { it }
            val totalPrayers = 5

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.12f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📖 تلاوة القرآن بعد كل صلاة",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$totalCompleted من $totalPrayers صلوات",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "عقّب صلاتك بآيات من الذكر الحكيم لتنل الأجر العظيم والسكينة الدائمة.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Row of 5 prayers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val prayerKeys = listOf(
                            Triple("Fajr", "الفجر", "سورة الإخلاص والمعوذات"),
                            Triple("Dhuhr", "الظهر", "سورة الفاتحة وآيات البقرة"),
                            Triple("Asr", "العصر", "سورة الإخلاص والمعوذتين"),
                            Triple("Maghrib", "المغرب", "آية الكرسي وأواخر الحشر"),
                            Triple("Isha", "العشاء", "سورة الملك الكريمة")
                        )

                        prayerKeys.forEach { (key, arabic, recommendedText) ->
                            val isCompleted = quranStatus[key] == true
                            var showReadDialog by remember { mutableStateOf(false) }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isCompleted) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        showReadDialog = true
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isCompleted) MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCompleted) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "مكتمل",
                                            tint = MaterialTheme.colorScheme.onTertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.MenuBook,
                                            contentDescription = "ابدأ القراءة",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = arabic,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCompleted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (showReadDialog) {
                                Dialog(onDismissRequest = { showReadDialog = false }) {
                                    Card(
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "📖 تلاوة القرآن بعد صلاة $arabic",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "الورد الموصى به: $recommendedText",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            
                                            // Predefined verses for high quality simulation
                                            val demoVerses = when (key) {
                                                "Fajr" -> listOf(
                                                    "قُلْ هُوَ اللَّهُ أَحَدٌ ۞ اللَّهُ الصَّمَدُ ۞ لَمْ يَلِدْ وَلَمْ يُولَدْ ۞ وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ",
                                                    "قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ ۞ مِنْ شَرِّ مَا خَلَقَ ۞ وَمِنْ شَرِّ غَاسِقٍ إِذَا وَقَبَ ۞ وَمِنْ شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ ۞ وَمِنْ شَرِّ حَاسِدٍ إِذَا حَسَدَ"
                                                )
                                                "Dhuhr" -> listOf(
                                                    "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ۞ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ ۞ الرَّحْمَٰنِ الرَّحِيمِ ۞ مَالِكِ يَوْمِ الدِّينِ ۞ إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ ۞ اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ"
                                                )
                                                "Maghrib" -> listOf(
                                                    "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۚ مَنْ ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلَّا بِإِذْنِهِ..."
                                                )
                                                "Isha" -> listOf(
                                                    "تَبَارَكَ الَّذِي بِيَدِهِ الْمُلْكُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ ۞ الَّذِي خَلَقَ الْمَوْتَ وَالْحَيَاةَ لِيَبْلُوَكُمْ أَيُّكُمْ أَحْسَنُ عَمَلًا..."
                                                )
                                                else -> listOf(
                                                    "إِذَا جَاءَ نَصْرُ اللَّهِ وَالْفَتْحُ ۞ وَرَأَيْتَ النَّاسَ يَدْخُلُونَ فِي دِينِ اللَّهِ أَفْوَاجًا ۞ فَسَبِّحْ بِحَمْدِ رَبِّكَ وَاسْتَغْفِرْهُ ۚ إِنَّهُ كَانَ تَوَّابًا"
                                                )
                                            }

                                            Card(
                                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(14.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    demoVerses.forEach { verse ->
                                                        Text(
                                                            text = verse,
                                                            fontSize = 13.sp,
                                                            textAlign = TextAlign.Center,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(20.dp))
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        viewModel.togglePostPrayerQuranReading(key)
                                                        showReadDialog = false
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                                    modifier = Modifier.weight(1.5f),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text(
                                                        text = if (isCompleted) "تراجع عن الإكمال" else "تمت القراءة تلقائياً ✓",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onTertiary
                                                    )
                                                }
                                                
                                                OutlinedButton(
                                                    onClick = { showReadDialog = false },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text("إغلاق", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // --- DAILY FIQH QUESTION OF THE DAY WIDGET ---
        if (showFiqhWidget) {
            item {
            val todayDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefs = remember(context) { context.getSharedPreferences("HayatyPrefs", android.content.Context.MODE_PRIVATE) }
            var isFiqhReadToday by remember(todayDateStr) { mutableStateOf(prefs.getBoolean("FiqhReadMark_$todayDateStr", false)) }
            var showAnswerState by remember { mutableStateOf(false) }

            val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            val fiqhQuestions = com.example.data.FiqhData.questions
            val currentQuestion = if (fiqhQuestions.isNotEmpty()) {
                fiqhQuestions[dayOfYear % fiqhQuestions.size]
            } else {
                null
            }

            if (currentQuestion != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFiqhReadToday) Color(0xFFE8F5E9).copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isFiqhReadToday) Color(0xFF81C784) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "💡 مسألة اليوم الفقهية",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFiqhReadToday) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            if (isFiqhReadToday) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF2E7D32))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "مُكتملة اليوم ✓",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "غير مقروءة",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Category tag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isFiqhReadToday) Color(0x202E7D32) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "الباب: ${currentQuestion.category}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFiqhReadToday) Color(0xFF2E7D32) else MaterialTheme.colorScheme.secondary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "السؤال المطروح:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.61f),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = currentQuestion.question,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Animated Visibility / Expandable answer box
                        if (showAnswerState) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isFiqhReadToday) Color(0xFFF1F8E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "الجواب الشرعي المعتمد والمستنبط:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFiqhReadToday) Color(0xFF33691E) else MaterialTheme.colorScheme.secondary,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = currentQuestion.answer,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth(),
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { showAnswerState = !showAnswerState },
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFiqhReadToday) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = if (showAnswerState) "إخفاء الجواب 🙈" else "عرض الجواب الفقهي 📜",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (!isFiqhReadToday) {
                                OutlinedButton(
                                    onClick = {
                                        isFiqhReadToday = true
                                        prefs.edit().putBoolean("FiqhReadMark_$todayDateStr", true).apply()
                                    },
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Text(
                                        text = "قرأت مـسألة اليوم ✓",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        isFiqhReadToday = false
                                        prefs.edit().putBoolean("FiqhReadMark_$todayDateStr", false).apply()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                                    border = BorderStroke(1.dp, Color(0xFFEF9A9A))
                                ) {
                                    Text(
                                        text = "إلغاء الإتمام",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // --- HABIT TRACKER INTERACTIVE WIDGET ---
        if (showHabitWidget) {
            item {
            var quickHabitName by remember { mutableStateOf("") }
            val todayHabitLogs by viewModel.todayHabitLogs.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("home_habits_widget"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌱 تعقب العادات والالتزام",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            val completedCount = habits.count { h -> todayHabitLogs.any { it.habitId == h.id } }
                            Text(
                                text = "مكتمل: $completedCount من ${habits.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "اضغط على زر النجمة لتسجيل التزامك اليومي وتنمية سلسلة أيامك 🌱",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    var habitsChartMode by remember { mutableStateOf("heatmap") }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (habitsChartMode == "heatmap") MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { habitsChartMode = "heatmap" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "التقويم الشهري",
                                    tint = if (habitsChartMode == "heatmap") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "التقويم الشهري (Heatmap) 📅",
                                    fontSize = 11.sp,
                                    fontWeight = if (habitsChartMode == "heatmap") FontWeight.Bold else FontWeight.Normal,
                                    color = if (habitsChartMode == "heatmap") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (habitsChartMode == "weekly") MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { habitsChartMode = "weekly" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = "الرسم الأسبوعي",
                                    tint = if (habitsChartMode == "weekly") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "الرسم الأسبوعي 📊",
                                    fontSize = 11.sp,
                                    fontWeight = if (habitsChartMode == "weekly") FontWeight.Bold else FontWeight.Normal,
                                    color = if (habitsChartMode == "weekly") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (habitsChartMode == "heatmap") {
                        HabitsMonthlyHeatmap(habits = habits, logs = allHabitLogs, viewModel = viewModel)
                    } else {
                        HabitsRechartsChart(habits = habits, logs = allHabitLogs, viewModel = viewModel)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (habits.isEmpty()) {
                        Text(
                            text = "لا توجد عادات حالياً. استخدم الحقل أدناه لإضافة عادتك الأولى سريعا!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            habits.forEach { habit ->
                                val isCompleted = todayHabitLogs.any { it.habitId == habit.id }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                        .clickable { viewModel.toggleHabit(habit) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isCompleted) MaterialTheme.colorScheme.primary 
                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isCompleted) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "التزام",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = habit.icon,
                                                    fontSize = 16.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column {
                                            Text(
                                                text = habit.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Whatshot,
                                                        contentDescription = "سلسلة الالتزام",
                                                        tint = if (habit.streak > 0) Color(0xFFFF6D00) else Color.Gray,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "سلسلة الالتزام: ${habit.streak} أيام متواصلة",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                    )
                                                }
                                                
                                                val targetTime = viewModel.getHabitTargetTime(habit.id)
                                                if (!targetTime.isNullOrBlank()) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.Schedule,
                                                            contentDescription = "المستهدف",
                                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = "المستهدف: $targetTime",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                        )
                                                    }
                                                }
                                                
                                                if (isCompleted) {
                                                    val compTime = viewModel.getHabitCompletionTime(habit.id, viewModel.currentDate.value)
                                                    if (compTime != null) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                imageVector = Icons.Default.CheckCircle,
                                                                contentDescription = "وقت الإنجاز",
                                                                tint = Color(0xFF4CAF50),
                                                                modifier = Modifier.size(11.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text(
                                                                text = "أُنحز $compTime",
                                                                fontSize = 10.sp,
                                                                color = Color(0xFF4CAF50)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    IconButton(
                                        onClick = { viewModel.deleteHabit(habit.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف العادة",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Add Habit Text Bar
                    OutlinedTextField(
                        value = quickHabitName,
                        onValueChange = { quickHabitName = it },
                        placeholder = { Text("أضف عادة جديدة سريعة... (مثال: شرب الماء 💧)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("quick_habit_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (quickHabitName.isNotBlank()) {
                                        viewModel.addHabit(quickHabitName.trim())
                                        quickHabitName = ""
                                    }
                                },
                                modifier = Modifier.testTag("quick_habit_add_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "إضافة سريعة",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            }
        }
        }

        // --- TASKS LIST WIDGET (To-Do List Manager) ---
        if (showTasksWidget) {
            item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("home_tasks_widget"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header & Active Task Count
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🎯 قائمة المهام اليومية",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "${tasks.count { !it.isCompleted }} متبقية",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    // Integrated Quick Add Input Bar
                    OutlinedTextField(
                        value = quickTaskTitle,
                        onValueChange = { quickTaskTitle = it },
                        placeholder = { Text("أضف مهمة جديدة سريعة...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("quick_task_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (quickTaskTitle.isNotBlank()) {
                                        viewModel.addTask(
                                            title = quickTaskTitle.trim(),
                                            description = "تمت إضافتها من قائمة المهام السريعة",
                                            dueDate = System.currentTimeMillis(),
                                            category = quickTaskCategory,
                                            isAppointment = false
                                        )
                                        quickTaskTitle = ""
                                    }
                                },
                                modifier = Modifier.testTag("quick_task_add_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "إضافة سريعة",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )

                    // Quick Category Badges Selection Flow
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val categories = listOf("عام", "عمل", "دراسة", "عبادة", "صحة")
                        categories.forEach { cat ->
                            val isSel = quickTaskCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { quickTaskCategory = cat }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Interactive Status Navigation Tabs (All, Active, Completed)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val filters = listOf(
                            Triple("remaining", "المتبقية ⏳", tasks.count { !it.isCompleted }),
                            Triple("completed", "المكتملة ✓", tasks.count { it.isCompleted }),
                            Triple("all", "الكل 📁", tasks.size)
                        )
                        filters.forEach { (filterType, label, count) ->
                            val isSel = activeTaskFilter == filterType
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { activeTaskFilter = filterType }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$label ($count)",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // --- FILTERED TASKS RENDERING LIST INSIDE CARD ---
                    val filteredTasks = when (activeTaskFilter) {
                        "remaining" -> tasks.filter { !it.isCompleted }
                        "completed" -> tasks.filter { it.isCompleted }
                        else -> tasks
                    }

                    if (filteredTasks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "لا يوجد مهام",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val emptyLabel = when (activeTaskFilter) {
                                    "remaining" -> "لا متبقي اليوم! يومك مميز ونظيف 🌱"
                                    "completed" -> "لم تنجز مهاماً اليوم بعد. ابدأ الآن! 💪"
                                    else -> "قائمتك فارغة الآن. أضف مهامك الأولى!"
                                }
                                Text(
                                    text = emptyLabel,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            filteredTasks.forEach { task ->
                                TaskRowItem(
                                    task = task,
                                    onToggle = { viewModel.toggleTaskCompletion(task) },
                                    onDelete = { viewModel.deleteTask(task) }
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        // --- QUICK FOCUS & PERSISTENCE INSIGHT ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToFocus() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = "التركيز والإنتاجية",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "جلسة حجب مخصصة للتركيز الآن ⚡",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "احبس نفسك عن التطبيقات المشتتة لزيادة إنجازاتك اليومية.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // --- Settings Dialog ---
    if (showSettingsDialog) {
        com.example.ui.SettingsDialog(viewModel = viewModel, onDismiss = { showSettingsDialog = false })
    }

    // --- City Picker Dialog ---
    if (showCitySelectorDialog) {
        Dialog(onDismissRequest = { showCitySelectorDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                var customCountryInput by remember { mutableStateOf("") }
                var customCityInput by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "طريقة تحديد مواقيت الصلاة", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Navigation GPS Tracker Selector Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isUsingGps) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                                showCitySelectorDialog = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "GPS Auto Positioning",
                            tint = if (isUsingGps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "📍 تحديد تلقائي (نظام الملاحة GPS)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isUsingGps) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "🌍 تحديد دولة ومدينة مخصصة (توقيت عالمي):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customCountryInput,
                        onValueChange = { customCountryInput = it },
                        label = { Text("اسم الدولة (مثال: فرنسا, كندا, مصر)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customCityInput,
                        onValueChange = { customCityInput = it },
                        label = { Text("اسم المدينة (مثال: باريس, مونتريال, الإسكندرية)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (customCountryInput.isNotBlank() && customCityInput.isNotBlank()) {
                                viewModel.setUsingGps(false, context)
                                viewModel.setSelectedCountryAndCity(customCountryInput.trim(), customCityInput.trim())
                                showCitySelectorDialog = false
                            }
                        },
                        enabled = customCountryInput.isNotBlank() && customCityInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تطبيق الموقع مخصّصاً 🌐", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "أو اختر يدوياً من المدن الكبرى الرسمية:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    for (city in PrayerTimesHelper.cities) {
                        TextButton(
                            onClick = {
                                viewModel.setUsingGps(false, context)
                                viewModel.setSelectedCountryAndCity("", city)
                                showCitySelectorDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = city, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    // --- Settings Dialog ---
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    // --- Add Task Dialog ---
    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { title, desc, category, isAppointment ->
                viewModel.addTask(title, desc, System.currentTimeMillis(), category, isAppointment)
                showAddTaskDialog = false
            }
        )
    }
}

@Composable
fun TaskRowItem(task: Task, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_${task.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f) 
                             else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("task_checkbox_${task.id}")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.description.isNotEmpty()) {
                    Text(
                        text = task.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (task.category) {
                                    "عمل" -> Color(0xFFE3F2FD)
                                    "دراسة" -> Color(0xFFFFF3E0)
                                    "عبادة" -> Color(0xFFE8F5E9)
                                    "صحة" -> Color(0xFFFCE4EC)
                                    else -> Color(0xFFECEFF1)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = task.category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (task.category) {
                                "عمل" -> Color(0xFF1565C0)
                                "دراسة" -> Color(0xFFE65100)
                                "عبادة" -> Color(0xFF2E7D32)
                                "صحة" -> Color(0xFFC2185B)
                                else -> Color(0xFF37474F)
                            }
                        )
                    }
                    if (task.isAppointment) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "مزامنة التقويم",
                                tint = Color(0xFF1A73E8),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("تقويم جوجل", fontSize = 10.sp, color = Color(0xFF1A73E8), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp).testTag("delete_task_btn")) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "حذف المهمة",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==========================================
// 2. HABITS SCREEN (العادات والالتزام)
// ==========================================
@Composable
fun HabitsScreen(viewModel: HayatyViewModel) {
    val context = LocalContext.current
    val habits by viewModel.allHabits.collectAsStateWithLifecycle()
    val logs by viewModel.todayHabitLogs.collectAsStateWithLifecycle()
    var showAddHabitDialog by remember { mutableStateOf(false) }
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (editingHabit != null) {
        val habitToEdit = habits.firstOrNull { it.id == editingHabit?.id } ?: editingHabit!!
        HabitDetailScreen(
            habit = habitToEdit,
            viewModel = viewModel,
            onBack = { editingHabit = null }
        )
    } else {
        val showMonthlyHabitAlert by viewModel.showMonthlyHabitAlert.collectAsStateWithLifecycle()
        val quranWirdPages by viewModel.quranWirdPages.collectAsStateWithLifecycle()
        val quranWirdIncreasePeriod by viewModel.quranWirdIncreasePeriod.collectAsStateWithLifecycle()
        val quranWirdIncreasePages by viewModel.quranWirdIncreasePages.collectAsStateWithLifecycle()
        val quranWirdStartDate by viewModel.quranWirdStartDate.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🌱 بناء العادات وإحصائياتك",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "تتبع التزامك اليومي لتحقيق الانضباط.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                IconButton(
                    onClick = { showAddHabitDialog = true },
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .testTag("add_habit_fab")
                ) {
                    Icon(imageVector = Icons.Default.AddCircle, contentDescription = "إنشاء عادة", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        // --- MONTHLY HABIT ALERT BANNER ---
        if (showMonthlyHabitAlert) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monthly_habit_alert_banner"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "🌟", fontSize = 24.sp)
                            Text(
                                text = "تذكير بداية الشهر الجديد",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لقد بدأ شهر جديد بحمد الله! لبناء ذاتك وزيادة التزامك اليومي، يُنصح بتبني عادة إيجابية جديدة تواكب تطلعاتك. أضف عاداتك بكل سهولة.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { viewModel.dismissMonthlyHabitAlert() }
                            ) {
                                Text(
                                    text = "تجاهل مؤقتاً",
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { showAddHabitDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Text(
                                    text = "+ إضافة عادة جديدة 🌱",
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- CUSTOM DAILY QURAN WIRD ITEM ---
        item {
            // Calculate current required pages based on elapsed time
            val startCal = Calendar.getInstance().apply { timeInMillis = quranWirdStartDate }
            val nowCal = Calendar.getInstance()
            val yearDiff = nowCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR)
            val monthDiff = nowCal.get(Calendar.MONTH) - startCal.get(Calendar.MONTH) + (yearDiff * 12)
            
            val periodIncrement = when (quranWirdIncreasePeriod) {
                "1_month" -> monthDiff * quranWirdIncreasePages
                "6_months" -> (monthDiff / 6) * quranWirdIncreasePages
                else -> 0
            }
            val currentRequiredPages = maxOf(1, quranWirdPages + periodIncrement)

            // Check if Quran Wird habit is completed today.
            val quranHabit = habits.find { it.name.contains("الورد اليومي") || it.name.contains("القرآن") }
            val isQuranWirdCompleted = quranHabit?.let { h -> logs.any { it.habitId == h.id } } ?: false

            var isSettingsExpanded by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quran_wird_customizable_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📖", fontSize = 24.sp)
                            Column {
                                Text(
                                    text = "ورد القرآن الكريم اليومي",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "متابعة وزيادة دورية مستدامة",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        IconButton(
                            onClick = { isSettingsExpanded = !isSettingsExpanded },
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Text(text = "⚙️", fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Status display card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isQuranWirdCompleted) Color(0xFFE8F5E9)
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isQuranWirdCompleted) "تم إنجاز تلاوة اليوم بنجاح! 🎉" else "الورد اليومي للقرآن",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isQuranWirdCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isQuranWirdCompleted) Color(0xFF2E7D32)
                                            else MaterialTheme.colorScheme.primary
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$currentRequiredPages صفحات",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "تستند هذه القيمة لورد البداية ($quranWirdPages صفحات) مع زيادة مقدارها +$quranWirdIncreasePages صفحات كل ${if (quranWirdIncreasePeriod == "1_month") "شهر" else if (quranWirdIncreasePeriod == "6_months") "6 أشهر" else "فترة يدوية"}.",
                                fontSize = 11.sp,
                                color = if (isQuranWirdCompleted) Color(0xFF2E7D32).copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (periodIncrement > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "⚠️ زاد الورد تلقائياً بمقدار +$periodIncrement صفحات لمرور $monthDiff شهور منذ بدء البرنامج.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Completion Toggle button
                    Button(
                        onClick = {
                            if (quranHabit != null) {
                                viewModel.toggleHabit(quranHabit)
                            } else {
                                viewModel.addHabit("قراءة الورد اليومي من القرآن")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isQuranWirdCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isQuranWirdCompleted) "✓ تم إنهاء الورد اليومي" else "وضع علامة كمنهٍ لقراءة الورد ✅",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Expandable settings drawer
                    AnimatedVisibility(visible = isSettingsExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "إعدادات تلاوة الورد والزيادة الدورية:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // 1. Starting Pages Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "عدد صفحات البداية:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (quranWirdPages > 1) {
                                                viewModel.updateQuranWirdConfig(
                                                    pages = quranWirdPages - 1,
                                                    period = quranWirdIncreasePeriod,
                                                    increasePages = quranWirdIncreasePages,
                                                    startDate = quranWirdStartDate
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    ) {
                                        Text("-", fontWeight = FontWeight.Bold)
                                    }
                                    Text("$quranWirdPages", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    IconButton(
                                        onClick = {
                                            viewModel.updateQuranWirdConfig(
                                                pages = quranWirdPages + 1,
                                                period = quranWirdIncreasePeriod,
                                                increasePages = quranWirdIncreasePages,
                                                startDate = quranWirdStartDate
                                            )
                                        },
                                        modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    ) {
                                        Text("+", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 2. Increase Pages Increment Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "مقدار الزيادة الدورية:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (quranWirdIncreasePages > 1) {
                                                viewModel.updateQuranWirdConfig(
                                                    pages = quranWirdPages,
                                                    period = quranWirdIncreasePeriod,
                                                    increasePages = quranWirdIncreasePages - 1,
                                                    startDate = quranWirdStartDate
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    ) {
                                        Text("-", fontWeight = FontWeight.Bold)
                                    }
                                    Text("+$quranWirdIncreasePages", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    IconButton(
                                        onClick = {
                                            viewModel.updateQuranWirdConfig(
                                                pages = quranWirdPages,
                                                period = quranWirdIncreasePeriod,
                                                increasePages = quranWirdIncreasePages + 1,
                                                startDate = quranWirdStartDate
                                            )
                                        },
                                        modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    ) {
                                        Text("+", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 3. Periodicity Selectors
                            Text(
                                text = "دورية زيادة كمية الصفحات:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "1_month" to "كل شهر 📆",
                                    "6_months" to "6 شهور 📅",
                                    "none" to "يدوية فقط 🔒"
                                ).forEach { (optVal, label) ->
                                    val isSelected = quranWirdIncreasePeriod == optVal
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable {
                                                viewModel.updateQuranWirdConfig(
                                                    pages = quranWirdPages,
                                                    period = optVal,
                                                    increasePages = quranWirdIncreasePages,
                                                    startDate = quranWirdStartDate
                                                )
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 4. Manual Add Actions (Simulating Month/6 Month passes in development, or just resetting startDate)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.updateQuranWirdConfig(
                                            pages = quranWirdPages,
                                            period = quranWirdIncreasePeriod,
                                            increasePages = quranWirdIncreasePages,
                                            startDate = System.currentTimeMillis()
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                                    elevation = null
                                ) {
                                    Text("البدء من البداية اليوم ↩️", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, maxLines = 1)
                                }

                                Button(
                                    onClick = {
                                        val c = Calendar.getInstance().apply { timeInMillis = quranWirdStartDate }
                                        c.add(Calendar.DAY_OF_YEAR, -30)
                                        viewModel.updateQuranWirdConfig(
                                            pages = quranWirdPages,
                                            period = quranWirdIncreasePeriod,
                                            increasePages = quranWirdIncreasePages,
                                            startDate = c.timeInMillis
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    elevation = null
                                ) {
                                    Text("تسريع +30 يوم (تجربة) ⏩", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, maxLines = 1)
                                }
                            }

                            // --- GOOGLE DRIVE CLOUD SYNC SECTION ---
                            val googleEmail by viewModel.googleAccountEmail.collectAsStateWithLifecycle()
                            val backupTime by viewModel.googleBackupTime.collectAsStateWithLifecycle()

                            Spacer(modifier = Modifier.height(18.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "☁️ النسخ الاحتياطي ومزامنة الحساب السحابي (Google Drive):",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            if (googleEmail == null) {
                                Button(
                                    onClick = {
                                        viewModel.connectGoogleAccount("user@gmail.com")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Cloud, contentDescription = "سحابة", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ربط حساب Google ومزامنة الإعدادات 🔗", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("الحساب المتصل: $googleEmail ✅", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Text("آخر مزامنة للتطبيق: $backupTime", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        IconButton(
                                            onClick = { viewModel.disconnectGoogleAccount() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Logout, contentDescription = "خروج", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.performGoogleDriveBackup(
                                                    context,
                                                    onSuccess = {},
                                                    onError = {}
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.Default.CloudUpload, contentDescription = "رفع", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("إجراء المزامنة 📤", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                viewModel.restoreGoogleDriveBackup(
                                                    context,
                                                    onSuccess = {},
                                                    onError = {}
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.Default.CloudDownload, contentDescription = "استعادة", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("استعادة المزامنة 📥", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- FIREBASE FIRESTORE CLOUD DATABASE HUB ---
        item {
            val firestoreTasks by viewModel.firestoreTasks.collectAsStateWithLifecycle()
            val firestoreHabits by viewModel.firestoreHabits.collectAsStateWithLifecycle()
            val firestoreDailySchedules by viewModel.firestoreDailySchedules.collectAsStateWithLifecycle()
            val isFirestoreLoading by viewModel.isFirestoreLoading.collectAsStateWithLifecycle()
            val firestoreStatus by viewModel.firestoreStatusMessage.collectAsStateWithLifecycle()

            var activeDialogType by remember { mutableStateOf<String?>(null) } // "task", "habit", "schedule"
            var showFirestoreStatusToast by remember { mutableStateOf(false) }

            LaunchedEffect(firestoreStatus) {
                if (firestoreStatus != null) {
                    showFirestoreStatusToast = true
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("firestore_cloud_hub_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔥", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "قاعدة بيانات Firebase Firestore ☁️",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                             )
                        }

                        if (isFirestoreLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "إدارة البيانات السحابية مباشرة. يمكنك إضافة وعرض وتعديل وحذف المهام، العادات والجدول اليومي على Firestore بكثافة متناهية.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    if (firestoreStatus != null && showFirestoreStatusToast) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = firestoreStatus ?: "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { 
                                        viewModel.clearFirestoreStatus()
                                        showFirestoreStatusToast = false
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "اغلاق", modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }

                    // --- SYNC CONTROLS ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.syncLocalDatabaseToFirestore() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "رفع", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مزامنة للكل سحابياً 📤", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.syncFirestoreToLocalDatabase() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "تنزيل", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("استيراد من السحاب 📥", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { viewModel.fetchFirestoreData() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                        elevation = null
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تحديث وجلب بيانات Firestore 🔄", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // --- TABS FOR CLOUD VIEW ---
                    var cloudTab by remember { mutableStateOf(0) } // 0: Tasks, 1: Habits, 2: Schedules
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("📝 المهام", "✨ العادات", "🗓️ الجدول").forEachIndexed { index, title ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (cloudTab == index) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { cloudTab = index }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cloudTab == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // --- DISPLAY AND CRUD LISTINGS ---
                    when (cloudTab) {
                        0 -> {
                            // CLOUD TASKS LISTING
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("المهام السحابية في Firestore (${firestoreTasks.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { activeDialogType = "task" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = "اضافة", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            if (firestoreTasks.isEmpty()) {
                                Text("لا يوجد مهام في السحاب حالياً. اضغط على الزر أعلاه لإضافة أول مهمة سحابية.", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    firestoreTasks.forEach { task ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = task.title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(text = task.description.ifEmpty { "لا يوجد وصف" }, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                            }
                                            Row {
                                                IconButton(
                                                    onClick = { viewModel.updateFirestoreTask(task.copy(isCompleted = !task.isCompleted)) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (task.isCompleted) Icons.Default.Check else Icons.Default.Circle,
                                                        contentDescription = "تحويل",
                                                        tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.deleteFirestoreTask(task.id) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // CLOUD HABITS LISTING
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                             ) {
                                 Text("العادات السحابية في Firestore (${firestoreHabits.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                 IconButton(onClick = { activeDialogType = "habit" }, modifier = Modifier.size(24.dp)) {
                                     Icon(Icons.Default.Add, contentDescription = "اضافة", tint = MaterialTheme.colorScheme.primary)
                                 }
                             }

                             if (firestoreHabits.isEmpty()) {
                                 Text("لا يوجد عادات في السحاب حالياً. اضغط على زر (+) في الأعلى للبدء.", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                             } else {
                                 Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                     firestoreHabits.forEach { habit ->
                                         Row(
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                 .padding(8.dp),
                                             verticalAlignment = Alignment.CenterVertically,
                                             horizontalArrangement = Arrangement.SpaceBetween
                                         ) {
                                             Row(verticalAlignment = Alignment.CenterVertically) {
                                                 Text(text = habit.icon, fontSize = 18.sp)
                                                 Spacer(modifier = Modifier.width(6.dp))
                                                 Column {
                                                     Text(text = habit.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                     Text(text = "سلسلة الالتزام: ${habit.streak} يوم 🔥", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                                 }
                                             }
                                             Row {
                                                 IconButton(
                                                     onClick = { viewModel.updateFirestoreHabit(habit.copy(streak = habit.streak + 1)) },
                                                     modifier = Modifier.size(24.dp)
                                                 ) {
                                                     Icon(Icons.Default.Star, contentDescription = "زيادة السلسلة", tint = MaterialTheme.colorScheme.primary)
                                                 }
                                                 IconButton(
                                                     onClick = { viewModel.deleteFirestoreHabit(habit.id) },
                                                     modifier = Modifier.size(24.dp)
                                                 ) {
                                                     Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                                 }
                                             }
                                         }
                                     }
                                 }
                             }
                         }
                         2 -> {
                             // CLOUD DAILY SCHEDULES LISTING
                             Row(
                                 horizontalArrangement = Arrangement.SpaceBetween,
                                 verticalAlignment = Alignment.CenterVertically,
                                 modifier = Modifier.fillMaxWidth()
                             ) {
                                 Text("الجدول اليومي السحابي في Firestore (${firestoreDailySchedules.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                 IconButton(onClick = { activeDialogType = "schedule" }, modifier = Modifier.size(24.dp)) {
                                     Icon(Icons.Default.Add, contentDescription = "اضافة", tint = MaterialTheme.colorScheme.primary)
                                 }
                             }

                             if (firestoreDailySchedules.isEmpty()) {
                                 Text("لا يوجد جدول زمني في السحاب حالياً. اضغط على الزر أعلاه لإضافة حجز أو موعد.", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                             } else {
                                 Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                     firestoreDailySchedules.forEach { schedule ->
                                         Row(
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                 .padding(8.dp),
                                             verticalAlignment = Alignment.CenterVertically,
                                             horizontalArrangement = Arrangement.SpaceBetween
                                         ) {
                                             Column(modifier = Modifier.weight(1f)) {
                                                 Text(text = schedule.title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                                     Icon(Icons.Default.Schedule, contentDescription = "وقت", modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.outline)
                                                     Spacer(modifier = Modifier.width(4.dp))
                                                     Text(text = "${schedule.timeSlot} | ${schedule.note}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                                 }
                                             }
                                             IconButton(
                                                 onClick = { viewModel.deleteFirestoreDailySchedule(schedule.id) },
                                                 modifier = Modifier.size(24.dp)
                                             ) {
                                                 Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                             }
                                         }
                                     }
                                 }
                             }
                         }
                     }
                 }
             }

             // --- POPUP DIALOGS FOR CLOUD CRUD CREATION ---
             if (activeDialogType != null) {
                 AlertDialog(
                     onDismissRequest = { activeDialogType = null },
                     title = {
                         Text(
                             text = when (activeDialogType) {
                                 "task" -> "إضافة مهمة سحابية 📝"
                                 "habit" -> "إضافة عادة سحابية ✨"
                                 else -> "إضافة جدول سحابي 🗓️"
                             },
                             fontSize = 16.sp,
                             fontWeight = FontWeight.Bold
                         )
                     },
                     text = {
                         Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                             var titleInput by remember { mutableStateOf("") }
                             var descInput by remember { mutableStateOf("") }
                             var timeInput by remember { mutableStateOf("") }
                             var extraInput by remember { mutableStateOf("") }

                             when (activeDialogType) {
                                 "task" -> {
                                     OutlinedTextField(
                                         value = titleInput,
                                         onValueChange = { titleInput = it },
                                         label = { Text("أسم المهمة") },
                                         singleLine = true,
                                         modifier = Modifier.fillMaxWidth()
                                     )
                                     OutlinedTextField(
                                         value = descInput,
                                         onValueChange = { descInput = it },
                                         label = { Text("تفاصيل/وصف") },
                                         singleLine = true,
                                         modifier = Modifier.fillMaxWidth()
                                     )
                                 }
                                 "habit" -> {
                                     OutlinedTextField(
                                         value = titleInput,
                                         onValueChange = { titleInput = it },
                                         label = { Text("أسم العادة") },
                                         singleLine = true,
                                         modifier = Modifier.fillMaxWidth()
                                     )
                                     OutlinedTextField(
                                         value = descInput,
                                         onValueChange = { descInput = it },
                                         label = { Text("الأيقونة (مثال: 🏃‍♂️)") },
                                         singleLine = true,
                                         modifier = Modifier.fillMaxWidth()
                                     )
                                 }
                                 "schedule" -> {
                                     OutlinedTextField(
                                         value = titleInput,
                                         onValueChange = { titleInput = it },
                                         label = { Text("عنوان الموعد/الجدول") },
                                         singleLine = true,
                                         modifier = Modifier.fillMaxWidth()
                                     )
                                     OutlinedTextField(
                                         value = timeInput,
                                         onValueChange = { timeInput = it },
                                         label = { Text("التوقيت السحابي (مثال: 10:00 - 11:30)") },
                                         singleLine = true,
                                         modifier = Modifier.fillMaxWidth()
                                     )
                                     OutlinedTextField(
                                         value = extraInput,
                                         onValueChange = { extraInput = it },
                                         label = { Text("ملاحظة إضافية") },
                                         singleLine = true,
                                         modifier = Modifier.fillMaxWidth()
                                     )
                                 }
                             }

                             Spacer(modifier = Modifier.height(8.dp))

                             Row(
                                 modifier = Modifier.fillMaxWidth(),
                                 horizontalArrangement = Arrangement.spacedBy(8.dp)
                             ) {
                                 Button(
                                     onClick = {
                                         if (titleInput.isNotBlank()) {
                                             when (activeDialogType) {
                                                 "task" -> viewModel.addFirestoreTask(titleInput, descInput, "عام", false)
                                                 "habit" -> viewModel.addFirestoreHabit(titleInput, descInput.ifBlank { "✨" }, "عام", 15)
                                                 "schedule" -> viewModel.addFirestoreDailySchedule(titleInput, timeInput, "", extraInput)
                                             }
                                             activeDialogType = null
                                         }
                                     },
                                     modifier = Modifier.weight(1f)
                                 ) {
                                     Text("تأكيد وحفظ ☁️")
                                 }
                                 
                                 OutlinedButton(
                                     onClick = { activeDialogType = null },
                                     modifier = Modifier.weight(1f)
                                 ) {
                                     Text("إلغاء")
                                 }
                             }
                         }
                     },
                     confirmButton = {}
                 )
             }
         }

        // --- RADIAL COMMITMENT CHART ---
        item {
            val progress = if (habits.isNotEmpty()) logs.size.toFloat() / habits.size.toFloat() else 0f
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("habit_stats_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "معدل الالتزام اليومي",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "أكملت ${logs.size} من أصل ${habits.size} عادات اليوم.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (progress >= 1f) "إنجاز ممتاز! 🌟" else "استمر في التقدم! 💪",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Hand-crafted beautiful Canvas Circular Chart
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(100.dp)
                    ) {
                        val strokeColor = MaterialTheme.colorScheme.primary
                        val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        val animatedProgress = remember { Animatable(0f) }
                        
                        LaunchedEffect(progress) {
                            animatedProgress.animateTo(progress, animationSpec = tween(1000, easing = LinearOutSlowInEasing))
                        }

                        Canvas(modifier = Modifier.size(90.dp)) {
                            drawArc(
                                color = trackColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = strokeColor,
                                startAngle = -90f,
                                sweepAngle = animatedProgress.value * 360f,
                                useCenter = false,
                                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // --- WEEKLY D3.JS PRODUCTIVITY GRAPH ---
        item {
            val allHabitLogs by viewModel.allHabitLogs.collectAsStateWithLifecycle()
            val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
            
            WeeklyProductivityD3Chart(tasks = allTasks, habitLogs = allHabitLogs)
        }

        // --- HABITS ITERATION ---
        items(habits) { habit ->
            val isCompleted = logs.any { it.habitId == habit.id }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("habit_card_${habit.id}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.toggleHabit(habit) },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                CircleShape
                            )
                            .testTag("habit_complete_btn_${habit.id}")
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.Star,
                            contentDescription = "التزام",
                            tint = if (isCompleted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { editingHabit = habit }
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = habit.icon, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = habit.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "تخصيص",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Whatshot,
                                    contentDescription = "سلسلة الالتزام",
                                    tint = if (habit.streak > 0) Color(0xFFFF6D00) else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "سلسلة الالتزام: ${habit.streak} أيام متواصلة",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            val targetTime = viewModel.getHabitTargetTime(habit.id)
                            if (!targetTime.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "المستهدف",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "المستهدف: $targetTime",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                    )
                                }
                            }
                            
                            if (isCompleted) {
                                val compTime = viewModel.getHabitCompletionTime(habit.id, viewModel.currentDate.value)
                                if (compTime != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "وقت الإنجاز",
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "أُنحز $compTime",
                                            fontSize = 12.sp,
                                            color = Color(0xFF4CAF50)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    IconButton(onClick = { viewModel.deleteHabit(habit.id) }, modifier = Modifier.testTag("delete_habit_${habit.id}")) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "حذف العادة",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    if (showAddHabitDialog) {
        AddHabitDialog(
            onDismiss = { showAddHabitDialog = false },
            onConfirm = { name, targetTime ->
                viewModel.addHabit(name, targetTime)
                showAddHabitDialog = false
            }
        )
    }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HabitsRechartsChart(habits: List<com.example.data.Habit>, logs: List<com.example.data.HabitLog>, viewModel: HayatyViewModel) {
    val dataJson = remember(habits, logs) {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFormat = SimpleDateFormat("E", Locale("ar"))
        
        val list = mutableListOf<Map<String, Any>>()
        
        for (i in 6 downTo 0) {
            val loopCal = Calendar.getInstance()
            loopCal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = sdf.format(loopCal.time)
            val dayLabel = dayFormat.format(loopCal.time)
            
            val totalHabits = habits.size
            val completedLogsCount = logs.count { log -> log.date == dateStr && log.isCompleted }
            
            val completionTimes = habits.mapNotNull { h ->
                viewModel.getHabitCompletionTime(h.id, dateStr)
            }
            val latestTime = if (completionTimes.isNotEmpty()) completionTimes.maxOrNull() ?: "--:--" else "غير محدد"
            
            list.add(
                mapOf(
                    "date" to dateStr,
                    "dayLabel" to dayLabel,
                    "completed" to completedLogsCount,
                    "total" to totalHabits,
                    "latestTime" to latestTime
                )
            )
        }
        
        val jsonBuilder = StringBuilder("[")
        list.forEachIndexed { idx, map ->
            jsonBuilder.append("{")
            jsonBuilder.append("\"date\":\"${map["date"]}\",")
            jsonBuilder.append("\"dayLabel\":\"${map["dayLabel"]}\",")
            jsonBuilder.append("\"completed\":${map["completed"]},")
            jsonBuilder.append("\"total\":${map["total"]},")
            jsonBuilder.append("\"latestTime\":\"${map["latestTime"]}\"")
            jsonBuilder.append("}")
            if (idx < list.size - 1) jsonBuilder.append(",")
        }
        jsonBuilder.append("]")
        jsonBuilder.toString()
    }

    val primaryColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb())
    val backgroundColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f).toArgb())
    val onBackgroundHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.onSurface.toArgb())
    val surfaceColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.surface.toArgb())
    val outlineColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.outline.toArgb())

    val htmlTemplate = remember(dataJson, primaryColorHex, backgroundColorHex, onBackgroundHex, surfaceColorHex, outlineColorHex) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            <style>
                body {
                    background-color: $surfaceColorHex;
                    color: $onBackgroundHex;
                    font-family: system-ui, -apple-system, sans-serif;
                    margin: 0;
                    padding: 8px 4px 4px 4px;
                    direction: rtl;
                    overflow: hidden;
                    user-select: none;
                }
                .chart-container {
                    width: 100%;
                    height: 125px;
                    position: relative;
                }
                svg {
                    width: 100%;
                    height: 100%;
                }
                .recharts-grid-line {
                    stroke: $outlineColorHex;
                    stroke-opacity: 0.15;
                    stroke-dasharray: 3 3;
                }
                .recharts-bar {
                    fill: url(#barGradient);
                    rx: 4px;
                    ry: 4px;
                    transition: fill-opacity 0.2s;
                }
                .recharts-bar-empty {
                    fill: $outlineColorHex;
                    fill-opacity: 0.08;
                    rx: 4px;
                    ry: 4px;
                }
                .axis-text {
                    font-size: 9px;
                    fill: $onBackgroundHex;
                    opacity: 0.65;
                }
                .tooltip {
                    position: absolute;
                    background: $surfaceColorHex;
                    color: $onBackgroundHex;
                    border: 1px solid $outlineColorHex;
                    border-radius: 8px;
                    padding: 8px;
                    font-size: 11px;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
                    pointer-events: none;
                    opacity: 0;
                    transition: opacity 0.2s;
                    z-index: 100;
                    direction: rtl;
                    text-align: right;
                }
                .tooltip-title {
                    font-weight: bold;
                    font-size: 11px;
                    margin-bottom: 4px;
                    color: $primaryColorHex;
                }
                .tooltip-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    gap: 16px;
                    margin-top: 2px;
                }
                .tooltip-dot {
                    width: 8px;
                    height: 8px;
                    border-radius: 50%;
                    background: $primaryColorHex;
                    display: inline-block;
                    margin-left: 6px;
                }
                .legend {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    font-size: 10px;
                    opacity: 0.8;
                    padding: 0 4px 6px 4px;
                    border-bottom: 1px dashed $outlineColorHex;
                    margin-bottom: 4px;
                }
                .legend-item {
                    display: flex;
                    align-items: center;
                    gap: 4px;
                }
                .legend-dot {
                    width: 8px;
                    height: 8px;
                    border-radius: 50%;
                }
            </style>
        </head>
        <body>
            <div class="legend">
                <div class="legend-item">
                    <span class="legend-dot" style="background: $primaryColorHex;"></span>
                    <span>📈 مؤشر الالتزام الأسبوعي (RechartsStyle)</span>
                </div>
                <div style="font-size: 9px; opacity: 0.7;">⏰ اضغط لمعاينة التفاصيل والتوقيت</div>
            </div>
            
            <div class="chart-container">
                <svg id="recharts-svg" viewBox="0 0 350 120" preserveAspectRatio="none">
                    <defs>
                        <linearGradient id="barGradient" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stop-color="$primaryColorHex" />
                            <stop offset="100%" stop-color="$primaryColorHex" stop-opacity="0.4" />
                        </linearGradient>
                    </defs>
                    
                    <!-- Content Grid lines -->
                    <line x1="25" y1="20" x2="340" y2="20" class="recharts-grid-line" />
                    <line x1="25" y1="50" x2="340" y2="50" class="recharts-grid-line" />
                    <line x1="25" y1="80" x2="340" y2="80" class="recharts-grid-line" />
                    <line x1="25" y1="100" x2="340" y2="100" class="recharts-grid-line" />
                    
                    <line x1="25" y1="100" x2="340" y2="100" stroke="$outlineColorHex" stroke-opacity="0.3" stroke-width="1" />
                    
                    <!-- Labels -->
                    <text x="18" y="23" text-anchor="end" class="axis-text">100%</text>
                    <text x="18" y="53" text-anchor="end" class="axis-text">50%</text>
                    <text x="18" y="83" text-anchor="end" class="axis-text">20%</text>
                    <text x="18" y="103" text-anchor="end" class="axis-text">0%</text>
                    
                    <g id="bars-group"></g>
                    <g id="labels-group"></g>
                </svg>
                <div id="recharts-tooltip" class="tooltip"></div>
            </div>

            <script>
                const data = $dataJson;
                const svg = document.getElementById('recharts-svg');
                const barsGroup = document.getElementById('bars-group');
                const labelsGroup = document.getElementById('labels-group');
                const tooltip = document.getElementById('recharts-tooltip');
                
                const paddingLeft = 28;
                const paddingRight = 10;
                const chartWidth = 350 - paddingLeft - paddingRight;
                const chartHeight = 80;
                const barWidth = 22;
                const colWidth = chartWidth / data.length;
                
                data.forEach((d, i) => {
                    const x = paddingLeft + (i * colWidth) + (colWidth - barWidth) / 2;
                    const completionRate = d.total > 0 ? (d.completed / d.total) : 0;
                    
                    // Column background (empty slot)
                    const bgRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
                    bgRect.setAttribute('x', x);
                    bgRect.setAttribute('y', 20);
                    bgRect.setAttribute('width', barWidth);
                    bgRect.setAttribute('height', chartHeight);
                    bgRect.setAttribute('class', 'recharts-bar-empty');
                    barsGroup.appendChild(bgRect);
                    
                    // Actual achievement bar
                    const barHeight = chartHeight * completionRate;
                    const y = 100 - barHeight;
                    
                    if (completionRate > 0) {
                        const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
                        rect.setAttribute('x', x);
                        rect.setAttribute('y', y);
                        rect.setAttribute('width', barWidth);
                        rect.setAttribute('height', barHeight);
                        rect.setAttribute('class', 'recharts-bar');
                        rect.setAttribute('rx', 4);
                        rect.setAttribute('ry', 4);
                        
                        const showTooltip = (event) => {
                            event.stopPropagation();
                            const ratePct = Math.round(completionRate * 100);
                            tooltip.innerHTML = 
                                '<div class="tooltip-title">' + d.dayLabel + ' (' + d.date + ')</div>' +
                                '<div class="tooltip-row">' +
                                    '<span><span class="tooltip-dot"></span>معدل الإنجاز</span>' +
                                    '<strong>' + d.completed + ' من ' + d.total + ' (' + ratePct + '%)</strong>' +
                                '</div>' +
                                '<div class="tooltip-row">' +
                                    '<span>⏰ أحدث التزام</span>' +
                                    '<strong>' + d.latestTime + '</strong>' +
                                '</div>';
                            tooltip.style.opacity = '1';
                            const rectContainer = svg.getBoundingClientRect();
                            const tooltipRect = tooltip.getBoundingClientRect();
                            let left = event.clientX - rectContainer.left - (tooltipRect.width / 2);
                            let top = event.clientY - rectContainer.top - tooltipRect.height - 10;
                            if (left < 0) left = 4;
                            if (left + tooltipRect.width > rectContainer.width) left = rectContainer.width - tooltipRect.width - 4;
                            if (top < 0) top = event.clientY - rectContainer.top + 15;
                            tooltip.style.left = left + 'px';
                            tooltip.style.top = top + 'px';
                        };
                        rect.addEventListener('touchstart', showTooltip);
                        rect.addEventListener('click', showTooltip);
                        barsGroup.appendChild(rect);
                    }
                    
                    const showBgTooltip = (event) => {
                        event.stopPropagation();
                        const ratePct = Math.round(completionRate * 100);
                        tooltip.innerHTML = 
                            '<div class="tooltip-title">' + d.dayLabel + ' (' + d.date + ')</div>' +
                            '<div class="tooltip-row">' +
                                '<span>🌿 مستوى التزامك</span>' +
                                '<strong>' + d.completed + ' من ' + d.total + ' (' + ratePct + '%)</strong>' +
                            '</div>' +
                            (d.completed > 0 ? (
                            '<div class="tooltip-row">' +
                                '<span>⏰ أحدث التزام</span>' +
                                '<strong>' + d.latestTime + '</strong>' +
                            '</div>') : '');
                        tooltip.style.opacity = '1';
                        const rectContainer = svg.getBoundingClientRect();
                        const tooltipRect = tooltip.getBoundingClientRect();
                        let left = event.clientX - rectContainer.left - (tooltipRect.width / 2);
                        let top = event.clientY - rectContainer.top - tooltipRect.height - 10;
                        if (left < 0) left = 4;
                        if (left + tooltipRect.width > rectContainer.width) left = rectContainer.width - tooltipRect.width - 4;
                        if (top < 0) top = event.clientY - rectContainer.top + 15;
                        tooltip.style.left = left + 'px';
                        tooltip.style.top = top + 'px';
                    };
                    bgRect.addEventListener('touchstart', showBgTooltip);
                    bgRect.addEventListener('click', showBgTooltip);
                    
                    // Day text
                    const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
                    text.setAttribute('x', x + (barWidth / 2));
                    text.setAttribute('y', 113);
                    text.setAttribute('text-anchor', 'middle');
                    text.setAttribute('class', 'axis-text');
                    text.textContent = d.dayLabel;
                    labelsGroup.appendChild(text);
                });
                
                document.body.addEventListener('click', (e) => {
                    if (!e.target.classList.contains('recharts-bar') && !e.target.classList.contains('recharts-bar-empty')) {
                        tooltip.style.opacity = '0';
                    }
                }, true);
                document.body.addEventListener('touchstart', (e) => {
                    if (!e.target.classList.contains('recharts-bar') && !e.target.classList.contains('recharts-bar-empty')) {
                        tooltip.style.opacity = '0';
                    }
                }, true);
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webViewClient = WebViewClient()
                loadDataWithBaseURL("https://recharts.org/", htmlTemplate, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://recharts.org/", htmlTemplate, "text/html", "UTF-8", null)
        }
    )
}

@Composable
fun HabitsMonthlyHeatmap(
    habits: List<com.example.data.Habit>,
    logs: List<com.example.data.HabitLog>,
    viewModel: com.example.ui.HayatyViewModel
) {
    val cal = Calendar.getInstance()
    val currentMonth = cal.get(Calendar.MONTH)
    val currentYear = cal.get(Calendar.YEAR)
    val todayDay = cal.get(Calendar.DAY_OF_MONTH)
    
    val monthNameAr = when(currentMonth) {
        0 -> "يناير"
        1 -> "فبراير"
        2 -> "مارس"
        3 -> "أبريل"
        4 -> "مايو"
        5 -> "يونيو"
        6 -> "يوليو"
        7 -> "أغسطس"
        8 -> "سبتمبر"
        9 -> "أكتوبر"
        10 -> "نوفمبر"
        11 -> "ديسمبر"
        else -> ""
    }
    
    // Set first day of month to find starting week column
    val firstDayCal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val startDayOfWeek = firstDayCal.get(Calendar.DAY_OF_WEEK) // 1=Sunday, 2=Monday, ..., 7=Saturday
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    
    // Convert to standard index starting on Sunday = 0
    val emptySlots = startDayOfWeek - 1
    
    var selectedDayDetail by remember { mutableStateOf<String?>(null) }
    var selectedDayCompletedList by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedDayTotal by remember { mutableStateOf(0) }
    var selectedDayNum by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تقويم الالتزام لشهر $monthNameAr $currentYear 📅",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "اليوم: $todayDay $monthNameAr",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        val weekdays = listOf("أح", "ن", "ث", "ر", "خ", "ج", "س") // Sun to Sat
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            weekdays.forEach { day ->
                Box(
                    modifier = Modifier.width(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        val totalCells = emptySlots + daysInMonth
        val rowsCount = (totalCells + 6) / 7
        
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            for (row in 0 until rowsCount) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        if (cellIndex < emptySlots || cellIndex >= totalCells) {
                            Box(modifier = Modifier.size(32.dp))
                        } else {
                            val dayNum = cellIndex - emptySlots + 1
                            
                            val loopCal = Calendar.getInstance().apply {
                                set(Calendar.YEAR, currentYear)
                                set(Calendar.MONTH, currentMonth)
                                set(Calendar.DAY_OF_MONTH, dayNum)
                            }
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val dateStr = sdf.format(loopCal.time)
                            
                            val totalHabits = habits.size
                            val completedLogs = logs.filter { log -> log.date == dateStr && log.isCompleted }
                            val completedCount = completedLogs.size
                            
                            val percent = if (totalHabits > 0) completedCount.toFloat() / totalHabits else 0f
                            
                            val boxColor = when {
                                totalHabits == 0 || completedCount == 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                percent <= 0.25f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                percent <= 0.5f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                percent <= 0.75f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                else -> MaterialTheme.colorScheme.primary
                            }
                            
                            val isToday = (dayNum == todayDay)
                            val isSelected = (selectedDayNum == dayNum)
                            
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(boxColor)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(6.dp))
                                        } else if (isToday) {
                                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable {
                                        selectedDayNum = dayNum
                                        selectedDayTotal = totalHabits
                                        val compNames = completedLogs.mapNotNull { log ->
                                            habits.find { it.id == log.habitId }?.run { "$icon $name" }
                                        }
                                        selectedDayCompletedList = compNames
                                        selectedDayDetail = "التزام يوم $dayNum: $completedCount من أصل $totalHabits"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayNum.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = when {
                                        totalHabits == 0 || completedCount == 0 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        percent > 0.5f -> MaterialTheme.colorScheme.onPrimary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "كثافة الالتزام:  ", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            listOf(
                0.0f to "٠%",
                0.25f to "٢٥%",
                0.5f to "٥٠%",
                0.75f to "٧٥%",
                1.0f to "١٠٠%"
            ).forEach { (pct, label) ->
                val bg = when {
                    pct == 0.0f -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    pct <= 0.25f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    pct <= 0.5f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    pct <= 0.75f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                    else -> MaterialTheme.colorScheme.primary
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(bg)
                )
                Text(text = " $label ", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }
        }

        if (selectedDayDetail != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            selectedDayDetail = null
                            selectedDayNum = null
                            selectedDayCompletedList = emptyList()
                        },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = selectedDayDetail!!,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                if (selectedDayCompletedList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "العادات المنجزة: " + selectedDayCompletedList.joinToString(" ، "),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (selectedDayTotal > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "لم يتم إنجاز أي عادات في هذا اليوم 🧊",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WeeklyProductivityD3Chart(tasks: List<Task>, habitLogs: List<HabitLog>) {
    val dataJson = getWeeklyPerformanceJson(tasks, habitLogs)
    
    // Theme Colors
    val primaryColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb())
    val secondaryColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.secondary.toArgb())
    val backgroundColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.background.toArgb())
    val onBackgroundHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.onBackground.toArgb())
    val surfaceColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.surface.toArgb())
    val outlineColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.outline.toArgb())

    val htmlTemplate = getHtmlTemplate(
        dataJson = dataJson,
        primaryColorHex = primaryColorHex,
        secondaryColorHex = secondaryColorHex,
        backgroundColorHex = backgroundColorHex,
        onBackgroundHex = onBackgroundHex,
        surfaceColorHex = surfaceColorHex,
        outlineColorHex = outlineColorHex
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .testTag("weekly_productivity_d3_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "📊 معدل الإنجاز والإنتاجية الأسبوعية (D3.js)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp)),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.domStorageEnabled = true
                        loadDataWithBaseURL("https://d3js.org/", htmlTemplate, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL("https://d3js.org/", htmlTemplate, "text/html", "UTF-8", null)
                }
            )
        }
    }
}

fun getHtmlTemplate(
    dataJson: String,
    primaryColorHex: String,
    secondaryColorHex: String,
    backgroundColorHex: String,
    onBackgroundHex: String,
    surfaceColorHex: String,
    outlineColorHex: String
): String {
    val template = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://d3js.org/d3.v7.min.js"></script>
            <style>
                body {
                    background-color: %SURFACE_COLOR%;
                    color: %ON_BACKGROUND_COLOR%;
                    font-family: system-ui, -apple-system, sans-serif;
                    margin: 0;
                    padding: 8px;
                    direction: rtl;
                    overflow: hidden;
                    user-select: none;
                }
                .chart-container {
                    width: 100%;
                    height: 190px;
                    position: relative;
                }
                svg {
                    width: 100%;
                    height: 100%;
                }
                .tooltip {
                    position: absolute;
                    text-align: center;
                    padding: 4px 8px;
                    font-size: 10px;
                    background: %BACKGROUND_COLOR%;
                    color: %ON_BACKGROUND_COLOR%;
                    border: 1px solid %OUTLINE_COLOR%;
                    border-radius: 6px;
                    pointer-events: none;
                    opacity: 0;
                    transition: opacity 0.2s;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.15);
                    z-index: 10;
                }
                .legend {
                    display: flex;
                    justify-content: center;
                    gap: 12px;
                    margin-bottom: 8px;
                    font-size: 11px;
                    font-weight: bold;
                }
                .legend-item {
                    display: flex;
                    align-items: center;
                    gap: 4px;
                }
                .legend-color {
                    width: 10px;
                    height: 10px;
                    border-radius: 3px;
                }
                .axis-label {
                    font-size: 10px;
                    fill: %ON_BACKGROUND_COLOR%;
                    opacity: 0.65;
                }
            </style>
        </head>
        <body>
            <div class="legend">
                <div class="legend-item">
                    <span class="legend-color" style="background: %PRIMARY_COLOR%;"></span>
                    <span>🎯 مهام مكتملة</span>
                </div>
                <div class="legend-item">
                    <span class="legend-color" style="background: %SECONDARY_COLOR%;"></span>
                    <span>🌱 عادات منفذة</span>
                </div>
            </div>
            <div id="chart" class="chart-container"></div>
            <div id="tooltip" class="tooltip"></div>

            <script>
                const data = %DATA_JSON%;
                
                function renderChart() {
                    const container = d3.select("#chart");
                    container.selectAll("*").remove();
                    
                    const rect = container.node().getBoundingClientRect();
                    const width = rect.width || 340;
                    const height = rect.height || 190;
                    
                    const margin = { top: 10, right: 10, bottom: 25, left: 25 };
                    const chartWidth = width - margin.left - margin.right;
                    const chartHeight = height - margin.top - margin.bottom;
                    
                    const svg = container.append("svg")
                        .attr("viewBox", "0 0 " + width + " " + height)
                        .attr("width", "100%")
                        .attr("height", "100%");
                        
                    const g = svg.append("g")
                        .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
                        
                    const x0 = d3.scaleBand()
                        .domain(data.map(d => d.dayLabel))
                        .rangeRound([chartWidth, 0])
                        .paddingInner(0.3);
                        
                    const x1 = d3.scaleBand()
                        .domain(["tasks", "habits"])
                        .rangeRound([0, x0.bandwidth()])
                        .padding(0.15);
                        
                    const maxVal = d3.max(data, d => Math.max(d.tasks, d.habits)) || 1;
                    const y = d3.scaleLinear()
                        .domain([0, d3.max([4, maxVal + 1])])
                        .rangeRound([chartHeight, 0]);
                        
                    g.append("g")
                        .style("stroke-dasharray", "3,3")
                        .style("opacity", 0.1)
                        .call(d3.axisLeft(y)
                            .ticks(4)
                            .tickSize(-chartWidth)
                            .tickFormat("")
                        );

                    g.append("g")
                        .attr("transform", "translate(0," + chartHeight + ")")
                        .call(d3.axisBottom(x0).tickSize(0))
                        .selectAll("text")
                        .attr("class", "axis-label")
                        .attr("dy", "10px")
                        .style("font-weight", "600");
                        
                    g.append("g")
                        .call(d3.axisLeft(y).ticks(4).tickFormat(d3.format("d")).tickSize(0))
                        .selectAll("text")
                        .attr("class", "axis-label")
                        .attr("dx", "-4px")
                        .style("font-weight", "600");

                    g.selectAll(".domain").style("stroke", "%OUTLINE_COLOR%").style("stroke-width", "0.5px");

                    const tooltip = d3.select("#tooltip");

                    const dayGroups = g.selectAll(".day-group")
                        .data(data)
                        .enter().append("g")
                        .attr("class", "day-group")
                        .attr("transform", d => "translate(" + x0(d.dayLabel) + ",0)");
                        
                    dayGroups.append("rect")
                        .attr("x", x1("tasks"))
                        .attr("y", chartHeight)
                        .attr("width", x1.bandwidth())
                        .attr("height", 0)
                        .attr("fill", "%PRIMARY_COLOR%")
                        .attr("rx", 3)
                        .on("mouseover", function(event, d) {
                            d3.select(this).style("filter", "brightness(1.15)");
                            tooltip.style("opacity", 1)
                                .html("<strong>" + d.dayLabel + " (" + d.date + ")</strong><br/>🎯 مهام: " + d.tasks);
                        })
                        .on("mousemove", function(event) {
                            const rect = container.node().getBoundingClientRect();
                            tooltip.style("left", (event.clientX - rect.left + 8) + "px")
                                   .style("top", (event.clientY - rect.top - 35) + "px");
                        })
                        .on("mouseleave", function() {
                            d3.select(this).style("filter", "none");
                            tooltip.style("opacity", 0);
                        })
                        .transition()
                        .duration(850)
                        .delay((d, i) => i * 40)
                        .attr("y", d => y(d.tasks))
                        .attr("height", d => Math.max(0, chartHeight - y(d.tasks)));

                    dayGroups.append("rect")
                        .attr("x", x1("habits"))
                        .attr("y", chartHeight)
                        .attr("width", x1.bandwidth())
                        .attr("height", 0)
                        .attr("fill", "%SECONDARY_COLOR%")
                        .attr("rx", 3)
                        .on("mouseover", function(event, d) {
                            d3.select(this).style("filter", "brightness(1.15)");
                            tooltip.style("opacity", 1)
                                .html("<strong>" + d.dayLabel + " (" + d.date + ")</strong><br/>🌱 عادات: " + d.habits);
                        })
                        .on("mousemove", function(event) {
                            const rect = container.node().getBoundingClientRect();
                            tooltip.style("left", (event.clientX - rect.left + 8) + "px")
                                   .style("top", (event.clientY - rect.top - 35) + "px");
                        })
                        .on("mouseleave", function() {
                            d3.select(this).style("filter", "none");
                            tooltip.style("opacity", 0);
                        })
                        .transition()
                        .duration(850)
                        .delay((d, i) => i * 40 + 80)
                        .attr("y", d => y(d.habits))
                        .attr("height", d => Math.max(0, chartHeight - y(d.habits)));
                }

                renderChart();
                window.addEventListener("resize", renderChart);
            </script>
        </body>
        </html>
    """.trimIndent()

    return template
        .replace("%DATA_JSON%", dataJson)
        .replace("%PRIMARY_COLOR%", primaryColorHex)
        .replace("%SECONDARY_COLOR%", secondaryColorHex)
        .replace("%BACKGROUND_COLOR%", backgroundColorHex)
        .replace("%ON_BACKGROUND_COLOR%", onBackgroundHex)
        .replace("%SURFACE_COLOR%", surfaceColorHex)
        .replace("%OUTLINE_COLOR%", outlineColorHex)
}

fun getWeeklyPerformanceJson(tasks: List<Task>, habitLogs: List<HabitLog>): String {
    val cal = Calendar.getInstance()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dayFormat = SimpleDateFormat("E", Locale("ar"))
    
    val list = mutableListOf<Map<String, Any>>()
    
    for (i in 6 downTo 0) {
        val loopCal = Calendar.getInstance()
        loopCal.add(Calendar.DAY_OF_YEAR, -i)
        val dateStr = sdf.format(loopCal.time)
        val dayLabel = dayFormat.format(loopCal.time)
        
        val completedTasksCount = tasks.count { task ->
            task.isCompleted && sdf.format(Date(task.dueDate)) == dateStr
        }
        
        val completedHabitsCount = habitLogs.count { log ->
            log.date == dateStr
        }
        
        list.add(
            mapOf(
                "date" to dateStr,
                "dayLabel" to dayLabel,
                "tasks" to completedTasksCount,
                "habits" to completedHabitsCount
            )
        )
    }
    
    val jsonBuilder = StringBuilder("[")
    list.forEachIndexed { idx, map ->
        jsonBuilder.append("{")
        jsonBuilder.append("\"date\":\"${map["date"]}\",")
        jsonBuilder.append("\"dayLabel\":\"${map["dayLabel"]}\",")
        jsonBuilder.append("\"tasks\":${map["tasks"]},")
        jsonBuilder.append("\"habits\":${map["habits"]}")
        jsonBuilder.append("}")
        if (idx < list.size - 1) jsonBuilder.append(",")
    }
    jsonBuilder.append("]")
    return jsonBuilder.toString()
}

// ==========================================
// 3. SCREEN GUARD & FOCUS (مراقب المشتتات والتركيز)
// ==========================================
@Composable
fun FocusScreen(viewModel: HayatyViewModel) {
    val usageRecords by viewModel.usageRecords.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var blockDurationMinutes by remember { mutableStateOf(25f) }
    
    // State of the selected app in the interactive analysis panel
    var selectedAppRecord by remember { mutableStateOf<com.example.data.AppUsageRecord?>(null) }
    var userReflectionAnswer by remember { mutableStateOf<String?>(null) }

    var hasUsageStats by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    // Poll the permission status
    LaunchedEffect(Unit) {
        while (true) {
            val currentStatus = hasUsageStatsPermission(context)
            if (currentStatus != hasUsageStats) {
                hasUsageStats = currentStatus
            }
            kotlinx.coroutines.delay(1500)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "📵 مراقبة الهاتف وقفل التشتت",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "تحكم في استخدامك للهاتف وركز على يومك بكل وعي واحترف مكافحة التشتت.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        if (!hasUsageStats) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "⚠️ ميزة غلق التطبيقات المشتتة غير نشطة",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "الرجاء تفعيل إذن 'الوصول إلى بيانات الاستخدام' لتمكين التطبيق من التعرف على التطبيقات الأخرى المفتوحة (يوتيوب، فيسبوك، إلخ) وإجبارها على الإغلاق والتبديل لوضع التركيز.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Text("منح إذن غلق التطبيقات وتتبع الاستخدام 🔓", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- SCREEN TIME GRAPH & INTERACTIVE ANALYSIS ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("screen_usage_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    val totalMinutes = usageRecords.sumOf { it.durationMs } / 60000
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "مجموع استخدام الهاتف اليوم", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${totalMinutes / 60} ساعة و ${totalMinutes % 60} دقيقة",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Button(
                            onClick = { 
                                try {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    viewModel.refreshUsageStats(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("أذونات النظام", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // --- DISTRACTION LEVEL SHIELD DIAL ---
                    val distractionLevel = when {
                        totalMinutes > 150 -> "مشتت بشدة 🚨"
                        totalMinutes > 60 -> "تشتت معتدل ⚠️"
                        else -> "تركيز ممتاز وصحي ❇️"
                    }
                    val dialColor = when {
                        totalMinutes > 150 -> Color(0xFFE53935)
                        totalMinutes > 60 -> Color(0xFFF57C00)
                        else -> Color(0xFF2E7D32)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(dialColor.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .border(0.5.dp, dialColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "مؤشر جودة التركيز الإجمالي:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = distractionLevel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = dialColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "📱 انقر على أي تطبيق لتحليله وتلقي نصائح ذكية:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bar Chart - Interactive App Selection Row
                    if (usageRecords.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "يرجى منح أذونات النظام لاسترداد سجلات الاستخدام أو توليد عينة.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        usageRecords.take(6).forEach { record ->
                            val mins = record.durationMs / 60000
                            val isSelected = selectedAppRecord?.packageName == record.packageName
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        selectedAppRecord = record
                                        userReflectionAnswer = null // reset reflection
                                    }
                                    .padding(vertical = 6.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    text = record.appName,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.width(90.dp),
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1
                                )
                                
                                Spacer(modifier = Modifier.width(6.dp))
                                
                                // Bar container
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                ) {
                                    val fractionalWidth = minOf(1f, mins.toFloat() / 120f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fractionalWidth)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (mins > 45) Color(0xFFE53935) else MaterialTheme.colorScheme.primary
                                            )
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(10.dp))
                                
                                Text(
                                    text = "$mins د",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- INTERACTIVE SMART ADVICE PANEL FOR THE SELECTED APP ---
        if (selectedAppRecord != null) {
            val record = selectedAppRecord!!
            val minsUsed = record.durationMs / 60000
            
            // Generate tailored dynamic advices
            val isSocialOrMedia = record.appName.contains("انستقرام", true) || 
                                  record.appName.contains("فيسبوك", true) || 
                                  record.appName.contains("تيك", true) || 
                                  record.appName.contains("سناب", true) || 
                                  record.appName.contains("تويتر", true) || 
                                  record.appName.contains("يوتيوب", true) || 
                                  record.packageName.contains("instagram", true) || 
                                  record.packageName.contains("facebook", true) || 
                                  record.packageName.contains("tiktok", true) || 
                                  record.packageName.contains("twitter", true) || 
                                  record.packageName.contains("youtube", true) || 
                                  record.packageName.contains("snapchat", true)

            val appCategory = if (isSocialOrMedia) "وسائل التواصل والتسلية 🎭" else "أدوات وتطبيقات عامة ⚙️"
            
            val appRisk = when {
                minsUsed > 60 -> "خطر تشتت حرج 🚨"
                minsUsed > 25 -> "تأهب وقلق تشتت متوسط ⚠️"
                else -> "تصفح معتدل وآمن ❇️"
            }
            
            val riskColor = when {
                minsUsed > 60 -> Color(0xFFE53935)
                minsUsed > 25 -> Color(0xFFF57C00)
                else -> Color(0xFF2E7D32)
            }

            val adviceText = if (isSocialOrMedia) {
                when {
                    minsUsed > 60 -> "أنت تهدر وقتاً ثميناً هنا! هذا التطبيق يستهلك انتباهك ونفسيتك بلذة مؤقتة وهمية. ننصحك بنقل التطبيق لمجلد مخفي، وحظر الهاتف بنمط التركيز لـ 25 دقيقة فوراً لاسترداد توازنك العقلي."
                    minsUsed > 25 -> "الاستخدام يقترب من حاجز الخطر. خذ استراحة كسر تشتت، وقم بإنجاز ورد تلاوة من المصحف أو أذكار الصباح/المساء لإرساء السكينة مجدداً."
                    else -> "استخدام رائع وواعٍ لهاتفك. واصل التحكم ولا تستسلم لدوامة الم scrolling اللانهائي المتعب."
                }
            } else {
                when {
                    minsUsed > 60 -> "بالرغم من كونه تطبيقاً عاماً، إلا أن المكوث الطويل يمنحك وهماً بالإنتاجية. قم بتقسيم عملك وبدء فترات تركيز حازمة."
                    else -> "استخدام طبيعي ومتزن ولا غبار عليه. أنت تقود هاتفك ولا يقتادك هو."
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("app_analysis_detail"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📊 مجهر التحليل: ${record.appName}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = { selectedAppRecord = null }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إغلاق التفاصيل",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("نوع التطبيق: $appCategory", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = appRisk,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = riskColor
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "💡 نصيحة التحرر من التشتت:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = adviceText,
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // --- INTERACTIVE REFLECTION POLL ---
                        Text(
                            text = "❓ تفاعل وقيّم استخدامك النفسي للـ $minsUsed دقيقة الأخيرة:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { userReflectionAnswer = "productive" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (userReflectionAnswer == "productive") Color(0xFF2E7D32) else MaterialTheme.colorScheme.surface,
                                    contentColor = if (userReflectionAnswer == "productive") Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Text("كان مثمراً ونبيلاً ❇️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { userReflectionAnswer = "unproductive" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (userReflectionAnswer == "unproductive") Color(0xFFE53935) else MaterialTheme.colorScheme.surface,
                                    contentColor = if (userReflectionAnswer == "unproductive") Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Text("كان تشتتاً وهدراً 🛡️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (userReflectionAnswer != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val responseText = if (userReflectionAnswer == "productive") {
                                "رائع جداً! بارك الله في همّتك وجعل علمك نافعاً. استمر في البناء الصالح وحافظ على الاتزان لئلا تزل القدم."
                            } else {
                                "الاعتراف بالخطأ أول خطوات الإصلاح! ننصحك فوراً بلجم هذا الهدر وسحب طاقتك لتبدأ تفعيل مؤقت التركيز في الأسفل."
                            }
                            Text(
                                text = responseText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (userReflectionAnswer == "productive") Color(0xFF2E7D32) else Color(0xFFE53935),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // --- ACTIVE LOCK SLIDER ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🛡️ بدء نمط التركيز وحبس التطبيقات المشتتة", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "عند تشغيل وضع التركيز سيتطهر استخدامك تلقائياً وسيعمل التطبيق كدرع منع يقظ لمقاومة التشتت حتى انتهاء المؤقت.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "مدة الجلسة: ${blockDurationMinutes.toInt()} دقيقة",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Slider(
                        value = blockDurationMinutes,
                        onValueChange = { blockDurationMinutes = it },
                        valueRange = 5f..120f,
                        steps = 22,
                        modifier = Modifier.fillMaxWidth().testTag("duration_slider")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.startFocusMode(blockDurationMinutes.toInt()) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("start_focus_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "قفل")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تفعيل نمط التركيز العميق", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

// ==========================================
// 4. QURAN & PRAYER SCREEN (المصحف والذكر)
// ==========================================
fun hasOverlayPermission(context: Context): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

fun hasUsageStatsPermission(context: Context): Boolean {
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

@Composable
fun QuranScreen(viewModel: HayatyViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    var selectedSura by remember { mutableStateOf<Sura?>(null) }
    var targetFirstVerseIndex by remember { mutableStateOf<Int?>(null) }
    val prayerTimes by viewModel.prayerTimes.collectAsStateWithLifecycle()
    val selectedCity by viewModel.selectedCity.collectAsStateWithLifecycle()

    val selectedReciterName by viewModel.selectedReciterName.collectAsStateWithLifecycle()
    val downloadedSuras by viewModel.downloadedSuras.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    
    val isFullDownloading by viewModel.isFullDownloading.collectAsStateWithLifecycle()
    val fullDownloadProgress by viewModel.fullDownloadProgress.collectAsStateWithLifecycle()
    val fullDownloadStatus by viewModel.fullDownloadStatus.collectAsStateWithLifecycle()
    
    val playingSuraId by viewModel.playingSuraId.collectAsStateWithLifecycle()
    val isAudioPlaying by viewModel.isAudioPlaying.collectAsStateWithLifecycle()
    val audioProgress by viewModel.audioProgress.collectAsStateWithLifecycle()
    val currentPlayTime by viewModel.currentPlayTime.collectAsStateWithLifecycle()
    val audioDuration by viewModel.audioDuration.collectAsStateWithLifecycle()

    // 5 Sub-tabs: 0 = Quran, 1 = Azkar counters, 2 = Electronic Tasbih, 3 = Fiqh Questions, 4 = Overlay settings
    var activeTab by remember { mutableStateOf(0) }

    // SharedPreferences for continuous daytime overlay
    val prefs = remember { context.getSharedPreferences(AzkarOverlayService.PREFS_NAME, Context.MODE_PRIVATE) }
    var azkarOverlayEnabled by remember { mutableStateOf(prefs.getBoolean(AzkarOverlayService.KEY_ENABLED, true)) }
    var azkarOverlayInterval by remember { mutableStateOf(prefs.getInt(AzkarOverlayService.KEY_INTERVAL, 5)) }
    var prefMorning by remember { mutableStateOf(prefs.getBoolean(AzkarOverlayService.KEY_PREF_MORNING, true)) }
    var prefEvening by remember { mutableStateOf(prefs.getBoolean(AzkarOverlayService.KEY_PREF_EVENING, true)) }
    var prefGeneral by remember { mutableStateOf(prefs.getBoolean(AzkarOverlayService.KEY_PREF_GENERAL, true)) }
    var permissionGranted by remember { mutableStateOf(hasOverlayPermission(context)) }

    // Automatic permission polling when overlay settings is active (now tab 4)
    if (activeTab == 4) {
        LaunchedEffect(Unit) {
            while (true) {
                val currentStatus = hasOverlayPermission(context)
                if (currentStatus != permissionGranted) {
                    permissionGranted = currentStatus
                }
                kotlinx.coroutines.delay(1500)
            }
        }
    }

    // High quality local map state for tracking remaining Azkar count
    val zikrCounts = remember { mutableStateMapOf<Int, Int>() }
    
    // Helper to reset counters
    val resetAzkarCounts = {
        AzkarData.morningAzkar.forEach { zikrCounts[it.id] = it.count }
        AzkarData.eveningAzkar.forEach { zikrCounts[it.id] = it.count }
    }

    LaunchedEffect(Unit) {
        if (zikrCounts.isEmpty()) {
            resetAzkarCounts()
        }
    }

    if (selectedSura != null) {
        QuranReaderScreen(
            sura = selectedSura!!,
            viewModel = viewModel,
            initialTargetVerseIndex = targetFirstVerseIndex,
            onBack = { 
                selectedSura = null
                targetFirstVerseIndex = null
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Screen Title & Description
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "📖 العبادات والذكر والورد اليومي",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "حافظ على مصحفك الشريف، أذكارك اليومية، واستثمر نهارك بذكر الله.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            // Beautiful Material 3 Scrollable Tab Row to accommodate five tabs perfectly
            ScrollableTabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 8.dp,
                modifier = Modifier.fillMaxWidth().testTag("quran_tabs")
            ) {
                val tabs = listOf("المصحف", "الأذكار", "المسبحة", "الأسئلة الفقهية ❓", "تنبيهات النهار")
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { 
                            activeTab = index 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        text = { 
                            Text(
                                text = title,
                                fontSize = 12.sp, 
                                fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            ) 
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Content Router
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                when (activeTab) {
                    0 -> { // ---------------------- TAB 0: QURAN LIST ----------------------
                        var searchQuery by remember { mutableStateOf("") }
                        var searchMode by remember { mutableStateOf(0) } // 0 = Sura names, 1 = Verse Content
                        val loadedSuraVerses by viewModel.loadedSuraVerses.collectAsStateWithLifecycle()

                        val allLoadedVerses = remember(loadedSuraVerses) {
                            val list = mutableListOf<Triple<Sura, Int, String>>() // Sura, VerseIndex, VerseText
                            QuranData.suras.forEach { sura ->
                                val verses = if (sura.verses.isNotEmpty()) sura.verses else (loadedSuraVerses[sura.id] ?: emptyList())
                                verses.forEachIndexed { vIdx, verse ->
                                    list.add(Triple(sura, vIdx, verse))
                                }
                            }
                            list
                        }

                        val matchedVerses = remember(searchQuery, allLoadedVerses) {
                            if (searchQuery.trim().length < 2) {
                                emptyList<Triple<Sura, Int, String>>()
                            } else {
                                val queryNorm = normalizeArabic(searchQuery)
                                allLoadedVerses.filter { (_, _, verse) ->
                                    normalizeArabic(verse).contains(queryNorm)
                                }
                            }
                        }
                        
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Sura Search Bar
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("sura_search_field"),
                                placeholder = { Text("ابحث عن سورة بالاسم أو الرقم...", fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, "بحث", tint = MaterialTheme.colorScheme.outline) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, "مسح", tint = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))

                            // Dual Search Mode Selector Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("البحث في السور 📖" to 0, "البحث في الآيات المحملة 🔍" to 1).forEach { (label, mode) ->
                                    val isSelected = searchMode == mode
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable {
                                                searchMode = mode
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))

                            if (searchMode == 0) {
                                // Reciter Select Title
                                Text(
                                    text = "اختر القارئ برواية ورش عن نافع للتحميل والاستماع:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )

                                val reciterOptions = listOf(
                                    "الشيخ محمود خليل الحصري (ورش)",
                                    "الشيخ ياسين الجزائري (ورش)",
                                    "الشيخ عبد الباسط عبد الصمد (ورش)"
                                )
                                
                                // Reciters selection chip bar (Horizontal scroll)
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(reciterOptions) { reciter ->
                                        val isSelected = reciter == selectedReciterName
                                        val shortName = reciter.replace(" (ورش)", "")
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .clickable {
                                                    viewModel.setSelectedReciter(reciter)
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = shortName,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))

                                // Comprehensive offline downloader Card
                                val allDownloaded = remember(downloadedSuras) {
                                    (1..114).all { downloadedSuras[it] == true }
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isFullDownloading) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                         else if (allDownloaded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                                         else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    border = BorderStroke(1.dp, if (isFullDownloading) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (allDownloaded) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                                    contentDescription = null,
                                                    tint = if (allDownloaded || isFullDownloading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = "تحميل المصحف الشامل",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = if (isFullDownloading) "جاري تنزيل الصوت والصفحات..." 
                                                               else if (allDownloaded) "المصحف الشريف متوفر بالكامل بدون إنترنت 🎉" 
                                                               else "تنزيل تلاوات جميع السور والصفحات دفعة واحدة",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            
                                            if (isFullDownloading) {
                                                IconButton(
                                                    onClick = { viewModel.cancelFullDownload() },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "إلغاء",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (isFullDownloading) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            LinearProgressIndicator(
                                                progress = fullDownloadProgress,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(4.dp)),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = fullDownloadStatus,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "${(fullDownloadProgress * 100).toInt()}%",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        } else if (!allDownloaded) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = { viewModel.startFullDownload() },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CloudDownload,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "تحميل المصحف ليعمل بدون إنترنت",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "القارئ الحالي محمل بالكامل: $selectedReciterName",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                
                                // Filter suras list by search query
                                val filteredSuras = remember(searchQuery) {
                                    QuranData.suras.filter { sura ->
                                        searchQuery.isEmpty() ||
                                        sura.name.contains(searchQuery) ||
                                        sura.englishName.contains(searchQuery, ignoreCase = true) ||
                                        sura.id.toString() == searchQuery.trim()
                                    }
                                }
                                
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    items(filteredSuras) { sura ->
                                        val isDownloaded = downloadedSuras[sura.id] == true
                                        val progress = downloadProgress[sura.id]
                                        val isPlaying = playingSuraId == sura.id
                                        
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedSura = sura },
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                                 else MaterialTheme.colorScheme.surface
                                            ),
                                            border = if (isPlaying) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    // Sura ID indicator Badge
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .background(
                                                                if (isPlaying) MaterialTheme.colorScheme.primary
                                                                else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                                                CircleShape
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "${sura.id}",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary
                                                        )
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    
                                                    Column {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = "سورة ${sura.name}",
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            // Warsh indicator tag
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text("ورش", fontSize = 8.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = "${sura.versesCount} آية • ${sura.type}",
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                                
                                                // Action controls side
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    // Download button
                                                    if (progress != null) {
                                                        // Currently downloading
                                                        Box(
                                                            modifier = Modifier.size(32.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            CircularProgressIndicator(
                                                                progress = progress / 100f,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                strokeWidth = 2.dp,
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                            Text("$progress%", fontSize = 7.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                        }
                                                    } else if (isDownloaded) {
                                                        // Downloaded, click to delete
                                                        IconButton(
                                                            onClick = {
                                                                viewModel.deleteSuraAudio(sura.id)
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = "حذف المقطع الصوتي",
                                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    } else {
                                                        // Not downloaded, click to download
                                                        IconButton(
                                                            onClick = {
                                                                viewModel.downloadSuraAudio(sura.id)
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.ArrowDownward,
                                                                contentDescription = "تحميل الصوت",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                    
                                                    // Quick Play / Pause Button
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.playSuraAudio(sura.id)
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isPlaying && isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                            contentDescription = "تشغيل التلاوة",
                                                            tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (filteredSuras.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(24.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "لا توجد نتائج بحث مطابقة لـ \"$searchQuery\"",
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Verse Content Search Mode
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "معلومات",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "يبحث هذا الوضع في جميع الآيات المحملة محلياً (مثل الفاتحة والكهف وأي سورة قمت بفتحها مسبقاً).",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            lineHeight = 14.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                if (searchQuery.trim().length < 2) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "يرجى إدخال حرفين على الأقل للبحث في الآيات...",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else if (matchedVerses.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "لم يتم العثور على أي آية مطابقة لـ \"$searchQuery\"",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "نتائج البحث الأقرب لمطابقتك:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = " تم العثور على ${matchedVerses.size} آية",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    ) {
                                        items(matchedVerses) { (sura, verseIdx, verseText) ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        // Navigate directly to this Sura and highlight the verse!
                                                        selectedSura = sura
                                                        targetFirstVerseIndex = verseIdx
                                                    },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "سورة ${sura.name}",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = "الآية ${verseIdx + 1}",
                                                                fontSize = 9.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        
                                                        Icon(
                                                            imageVector = Icons.Default.ArrowBack,
                                                            contentDescription = "قراءة الآية",
                                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                                        HighlightedQuranText(
                                                            text = verseText.trim(),
                                                            query = searchQuery,
                                                            style = androidx.compose.ui.text.TextStyle(
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = FontFamily.Serif,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                lineHeight = 22.sp
                                                            ),
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> { // ---------------------- TAB 1: DAILY AZKAR COUNTERS ----------------------
                        var azkarSectionTab by remember { mutableStateOf(0) } // 0 = Morning, 1 = Evening
                        val currentAzkarList = if (azkarSectionTab == 0) AzkarData.morningAzkar else AzkarData.eveningAzkar

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Sub Tab Selector Header & Reset Button
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                ) {
                                    // Morning tab
                                    Button(
                                        onClick = { azkarSectionTab = 0 },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (azkarSectionTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (azkarSectionTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("الصباح 🌅", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    // Evening tab
                                    Button(
                                        onClick = { azkarSectionTab = 1 },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (azkarSectionTab == 1) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (azkarSectionTab == 1) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("المساء 🌌", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Reset All Counters
                                TextButton(
                                    onClick = {
                                        resetAzkarCounts()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "تصفير", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("تصفير الورد", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Azkar Scrollable Content List
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(currentAzkarList) { zikr ->
                                    val currentCount = zikrCounts[zikr.id] ?: zikr.count
                                    val isDone = currentCount == 0

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (currentCount > 0) {
                                                    zikrCounts[zikr.id] = currentCount - 1
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            }
                                            .testTag("zikr_card_${zikr.id}"),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isDone) Color(0xFF81C784).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDone) Color(0xFFE8F5E9).copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp)
                                        ) {
                                            // Title / Benefit Source Indicator
                                            if (zikr.description.isNotEmpty()) {
                                                Text(
                                                    text = zikr.description,
                                                    fontSize = 11.sp,
                                                    color = if (isDone) Color(0xFF2E7D32).copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    modifier = Modifier.padding(bottom = 6.dp)
                                                )
                                            }

                                            // Actual Zikr Text (Arabic script, elegant layout)
                                            Text(
                                                text = zikr.text,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Right,
                                                lineHeight = 22.sp,
                                                color = if (isDone) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                            )

                                            // Count Counter and Action indicator
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = if (isDone) "اضغط مطولاً لإعادة البدء" else "انقر في أي مكان على الكرت للتسبيح",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                                    modifier = Modifier.clickable {
                                                        // Reset only this specific zikr count on click
                                                        zikrCounts[zikr.id] = zikr.count
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )

                                                // Beautiful counter visual pill
                                                Card(
                                                    shape = RoundedCornerShape(30.dp),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isDone) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primaryContainer
                                                    ),
                                                    modifier = Modifier
                                                        .height(32.dp)
                                                        .width(90.dp)
                                                        .clickable {
                                                            if (currentCount > 0) {
                                                                zikrCounts[zikr.id] = currentCount - 1
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            } else {
                                                                zikrCounts[zikr.id] = zikr.count
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            }
                                                        }
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = if (isDone) "مكتمل ✓" else "التكرار: $currentCount",
                                                            color = if (isDone) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }
                        }
                    }

                    2 -> { // ---------------------- TAB 2: ELECTRONIC TASBIH ----------------------
                        val commonPhrases = listOf(
                            "سُبْحَانَ اللَّهِ",
                            "الْحَمْدُ لِلَّهِ",
                            "لَا إِلَهَ إِلَّا اللَّهُ",
                            "اللَّهُ أَكْبَرُ",
                            "لا حَوْلَ وَلا قُوَّةَ إِلا بِاللَّهِ",
                            "أَسْتَغْفِرُ اللَّهَ الْعَظِيمَ وَأَتُوبُ إِلَيْهِ",
                            "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ"
                        )
                        var chosenPhraseIdx by remember { mutableStateOf(0) }
                        var customPhrase by remember { mutableStateOf("") }
                        val activePhrase = if (customPhrase.isNotEmpty()) customPhrase else commonPhrases[chosenPhraseIdx]

                        var tasbihCount by remember { mutableStateOf(0) }
                        var targetGoal by remember { mutableStateOf(33) } // 33, 100, 0 (limitless)

                        val pct = if (targetGoal > 0) Math.min(1f, tasbihCount.toFloat() / targetGoal) else 0f
                        val finishedGoal = targetGoal > 0 && tasbihCount >= targetGoal

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Goal selector chip buttons
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "🎯 حدد الهدف والمقدار المطلوب للتسبيح:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(33, 100, 0).forEach { goal ->
                                            val goalLabel = if (goal == 0) "غير محدود" else "$goal تسبيحة"
                                            Button(
                                                onClick = { targetGoal = goal },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (targetGoal == goal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = if (targetGoal == goal) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(goalLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Phrase Preset Scrollable Selector
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "📿 اختر الورد أو التسبيحة المطلوبة:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    
                                    // Row of fast selectors
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("سبحان الله", "الحمد لله", "أستغفر الله", "صلِّ على النبي").forEachIndexed { index, name ->
                                            val matchIdx = when (index) {
                                                0 -> 0
                                                1 -> 1
                                                2 -> 5
                                                else -> 6
                                            }
                                            Button(
                                                onClick = { 
                                                    chosenPhraseIdx = matchIdx
                                                    customPhrase = "" 
                                                    tasbihCount = 0
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (chosenPhraseIdx == matchIdx && customPhrase.isEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = if (chosenPhraseIdx == matchIdx && customPhrase.isEmpty()) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(name, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Custom input field text
                                    OutlinedTextField(
                                        value = customPhrase,
                                        onValueChange = { 
                                            customPhrase = it 
                                            tasbihCount = 0
                                        },
                                        label = { Text("أو اكتب ورد مخصص هنا...", fontSize = 10.sp) },
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                }
                            }

                            // Current Active Phrase Display Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = activePhrase,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                                )
                            }

                            // Giant Intersecting Circle Tasbih Clicker Layout
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .aspectRatio(1f)
                                    .shadow(elevation = 8.dp, shape = CircleShape)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable {
                                        tasbihCount += 1
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    .testTag("tasbih_circle_clicker"),
                                contentAlignment = Alignment.Center
                            ) {
                                // Double circular visual borders reflecting Progress relative to Target Goal
                                Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                    // background grey circle
                                    drawCircle(
                                        color = Color.LightGray.copy(alpha = 0.2f),
                                        style = Stroke(width = 8.dp.toPx())
                                    )
                                    
                                    // Progress circle
                                    if (pct > 0f) {
                                        drawArc(
                                            color = if (finishedGoal) Color(0xFF4CAF50) else Color(0xFFFFB300),
                                            startAngle = -90f,
                                            sweepAngle = 360f * pct,
                                            useCenter = false,
                                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "$tasbihCount",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (finishedGoal) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    val targetLabel = if (targetGoal > 0) "/ $targetGoal" else "مفتوح"
                                    Text(
                                        text = targetLabel,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )

                                    if (finishedGoal) {
                                        Text(
                                            text = "مكتمل ✨",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Reset Controls Task Button
                            Button(
                                onClick = {
                                    tasbihCount = 0
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)),
                                modifier = Modifier.height(34.dp).width(110.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "تصفير", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تصفير العداد", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    3 -> { // ---------------------- TAB 3: ISLAMIC FIQH Q&A ----------------------
                        FiqhQuestionsScreen()
                    }

                    4 -> { // ---------------------- TAB 4: OVERLAY SETTINGS ----------------------
                        var hasNotificationPermission by remember {
                            mutableStateOf(
                                if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                } else {
                                    true
                                }
                            )
                        }

                        val notificationPermissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission()
                        ) { isGranted ->
                            hasNotificationPermission = isGranted
                        }

                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // --- CARD 0: LANGUAGE SWITCHER ---
                            val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("language_settings_card"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = if (appLanguage == "ar") "لغة واجهات التطبيق (App Language) 🌐" else "Application Language 🌐",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (appLanguage == "ar") "اللغة النشطة حالياً: العربية" else "Active Language: English",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.setAppLanguage("ar")
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (appLanguage == "ar") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (appLanguage == "ar") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("العربية", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        
                                        Button(
                                            onClick = {
                                                viewModel.setAppLanguage("en")
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (appLanguage == "en") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (appLanguage == "en") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("English", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "🌾 التنبيه بذكر الله التلقائي طوال النهار:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Text(
                                text = "حافظ على حضور قلبك باستمرار. يعرض لك هذا الملف كرت منبثق لطيف وقابل للسحب يحتوي على ذكر عشوائي على فترات منظمة، حتى أثناء قراءة تطبيقات أخرى أو إغلاق الشاشة الشريفة.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                lineHeight = 16.sp
                            )

                            // --- CARD 1: OVERLAY PERMISSION STATUS ---
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (permissionGranted) Color(0xFF81C784).copy(alpha = 0.5f) else Color(0xFFFFD54F).copy(alpha = 0.5f)
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (permissionGranted) Color(0xFFE8F5E9).copy(alpha = 0.35f) else Color(0xFFFFFDE7).copy(alpha = 0.35f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "إذن الظهور فوق التطبيقات الاخرى",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (permissionGranted) Color(0xFF1B5E20) else Color(0xFFE65100)
                                        )
                                        
                                        Text(
                                            text = if (permissionGranted) "تلقائي العمل ✅" else "غير مسموح ⚠️",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (permissionGranted) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = if (permissionGranted) "الملف مسموح به وجاهز لتنشيط الكرت المنبثق التلقائي." else "الرجاء منح تطبيق حياتي إذن العرض فوق التطبيقات لتتمكن الخدمة من عرض البطاقات المنبثقة.",
                                        fontSize = 11.sp,
                                        color = if (permissionGranted) Color(0xFF2E7D32).copy(alpha = 0.8f) else Color(0xFFD84315),
                                        lineHeight = 15.sp
                                    )

                                    if (!permissionGranted) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val intent = Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                                context.startActivity(intent)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                                            modifier = Modifier.fillMaxWidth().height(38.dp)
                                        ) {
                                            Text("منح إذن الظهور فوق التطبيقات 🔓", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // --- CARD 1.5: NOTIFICATION PERMISSION STATUS ---
                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (hasNotificationPermission) Color(0xFF81C784).copy(alpha = 0.5f) else Color(0xFFFFD54F).copy(alpha = 0.5f)
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (hasNotificationPermission) Color(0xFFE8F5E9).copy(alpha = 0.35f) else Color(0xFFFFFDE7).copy(alpha = 0.35f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "إذن إشعارات التطبيق والخدمة",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (hasNotificationPermission) Color(0xFF1B5E20) else Color(0xFFE65100)
                                            )
                                            
                                            Text(
                                                text = if (hasNotificationPermission) "مسموح ومفعل ✅" else "غير مسموح ⚠️",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (hasNotificationPermission) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = if (hasNotificationPermission) "إعلانات وخدمة التذكير بالأذكار تعمل بسلاسة تامة في شريط الإشعارات." else "يرجى منح إذن إشعارات النظام للتطبيق لضمان عمل الخدمة وعرض التنبيهات في الخلفية.",
                                            fontSize = 11.sp,
                                            color = if (hasNotificationPermission) Color(0xFF2E7D32).copy(alpha = 0.8f) else Color(0xFFD84315),
                                            lineHeight = 15.sp
                                        )

                                        if (!hasNotificationPermission) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Button(
                                                onClick = {
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                                                modifier = Modifier.fillMaxWidth().height(38.dp)
                                            ) {
                                                Text("منح إذن الإشعارات الفوري 🔔", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // --- CARD 2: SWITCH ON/OFF AND CHOICE OF INTERVALS ---
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    // Switch toggle row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "تشغيل التذكير والبطاقات التلقائية",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (azkarOverlayEnabled) "نشطة الآن في الخلفية" else "موقفة مؤقتاً",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        
                                        Switch(
                                            checked = azkarOverlayEnabled,
                                            onCheckedChange = { isEnabled ->
                                                azkarOverlayEnabled = isEnabled
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                
                                                prefs.edit().putBoolean(AzkarOverlayService.KEY_ENABLED, isEnabled).apply()
                                                if (isEnabled) {
                                                    AzkarOverlayService.startService(context)
                                                } else {
                                                    AzkarOverlayService.stopService(context)
                                                }
                                            },
                                            modifier = Modifier.testTag("azkar_service_toggle")
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Interval speed choice buttons row
                                    Text(
                                        text = "⏱️ اختر معدل وفترة تكرار الأذكار التلقائية:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    
                                    val intervals = listOf(
                                        1 to "كل دقيقة",
                                        5 to "5 د",
                                        15 to "15 د",
                                        30 to "30 د",
                                        60 to "ساعة"
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        intervals.forEach { pair ->
                                            val minutes = pair.first
                                            val label = pair.second
                                            val isSelected = azkarOverlayInterval == minutes

                                            Button(
                                                onClick = {
                                                    azkarOverlayInterval = minutes
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    
                                                    prefs.edit().putInt(AzkarOverlayService.KEY_INTERVAL, minutes).apply()
                                                    // restart service if enabled to pick up new interval
                                                    if (azkarOverlayEnabled) {
                                                        AzkarOverlayService.startService(context)
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // --- CARD 2.5: PREFERRED AZKAR TYPES ---
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "🌸 اختر أنواع الأذكار المفضلة للظهور تلقائياً:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Morning category checkbox row
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val newValue = !prefMorning
                                                    prefMorning = newValue
                                                    prefs.edit().putBoolean(com.example.data.AzkarOverlayService.KEY_PREF_MORNING, newValue).apply()
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = prefMorning,
                                                onCheckedChange = { checked ->
                                                    prefMorning = checked
                                                    prefs.edit().putBoolean(com.example.data.AzkarOverlayService.KEY_PREF_MORNING, checked).apply()
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("🌅 أذكار الصباح", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Text("تشمل الآيات الشريفة والأدعية الواردة في المأثور الصباحي", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                            }
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                                        // Evening category checkbox row
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val newValue = !prefEvening
                                                    prefEvening = newValue
                                                    prefs.edit().putBoolean(com.example.data.AzkarOverlayService.KEY_PREF_EVENING, newValue).apply()
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = prefEvening,
                                                onCheckedChange = { checked ->
                                                    prefEvening = checked
                                                    prefs.edit().putBoolean(com.example.data.AzkarOverlayService.KEY_PREF_EVENING, checked).apply()
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("🌌 أذكار المساء", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Text("تشمل آية الكرسي والمعوذات والورد المسائي اليومي لحفظ المسلم", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                            }
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                                        // General category checkbox row
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val newValue = !prefGeneral
                                                    prefGeneral = newValue
                                                    prefs.edit().putBoolean(com.example.data.AzkarOverlayService.KEY_PREF_GENERAL, newValue).apply()
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = prefGeneral,
                                                onCheckedChange = { checked ->
                                                    prefGeneral = checked
                                                    prefs.edit().putBoolean(com.example.data.AzkarOverlayService.KEY_PREF_GENERAL, checked).apply()
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("🌾 أذكار واستغفار عام", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Text("حوقلة، استغفار، الصلاة على النبي والتهليل لدوام الاستحضار", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                            }
                                        }
                                    }
                                }
                            }

                            // --- CARD 3: TEST PREVIEW ACTION BUTTON ---
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "🎯 اختبر مظهر المسبحة والكرت المنبثق فوراً:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Text(
                                        text = "سينشأ هذا الخيار كرت تجريبي على شاشتك يعرض آيات من الذكر بأسلوب قابل للسحب والكشط للاختبار السريع.",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        lineHeight = 14.sp
                                    )
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (permissionGranted) {
                                                // Start service manually which forces a trigger
                                                AzkarOverlayService.startService(context)
                                                android.widget.Toast.makeText(context, "🌾 جاري تحضير الكرت وتنشيطه الآن...", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "الرجاء منح إذن الظهور فوق التطبيقات أولاً", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth().height(38.dp)
                                    ) {
                                        Text("إطلاق الكرت التجريبي المنبثق 🌟", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Global Floating Audio Player Bar for Warsh Recitation
            if (playingSuraId != null) {
                val playingSura = QuranData.suras.find { it.id == playingSuraId }
                if (playingSura != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .testTag("global_audio_player"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MenuBook,
                                            contentDescription = "تلاوة",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "تلاوة سورة ${playingSura.name}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = selectedReciterName,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.toggleAudioPlayPause() }
                                    ) {
                                        Icon(
                                            imageVector = if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isAudioPlaying) "إيقاف مؤقت" else "تشغيل",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.stopAudioPlay() }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "إغلاق المشغل",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            // Audio Slider & Timeline
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentPlayTime,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                
                                Slider(
                                    value = audioProgress,
                                    onValueChange = { fraction -> viewModel.seekAudioTo(fraction) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                        .height(18.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                                    )
                                )
                                
                                Text(
                                    text = audioDuration,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuranReaderScreen(
    sura: Sura,
    viewModel: HayatyViewModel,
    initialTargetVerseIndex: Int? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val loadedSuraVerses by viewModel.loadedSuraVerses.collectAsStateWithLifecycle()
    val isLoadingVerses by viewModel.isLoadingVerses.collectAsStateWithLifecycle()
    
    val isCurrentlyLoading = isLoadingVerses[sura.id] == true
    val versesList = if (sura.verses.isNotEmpty()) sura.verses else (loadedSuraVerses[sura.id] ?: emptyList())

    var readerMode by remember { mutableStateOf(0) } // 0 = standard text, 1 = yousefheiba website
    var inSuraSearchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    // Fetch verses if list is empty and we aren't already loading
    LaunchedEffect(sura.id) {
        if (sura.verses.isEmpty() && !loadedSuraVerses.containsKey(sura.id)) {
            viewModel.loadSuraVerses(sura.id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF6EE)) // Arabic paper parchment background
    ) {
        // Reader Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back_to_suras")) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "عودة",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "سورة ${sura.name}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = "رواية ورش عن نافع بالرسم العثماني",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }
            
            // Play audio and Search control directly in the reader header! Extremely practical!
            val playingSuraId by viewModel.playingSuraId.collectAsStateWithLifecycle()
            val isAudioPlaying by viewModel.isAudioPlaying.collectAsStateWithLifecycle()
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { 
                        isSearchExpanded = !isSearchExpanded
                        if (!isSearchExpanded) {
                            inSuraSearchQuery = ""
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "بحث في السورة",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                IconButton(
                    onClick = { viewModel.playSuraAudio(sura.id) }
                ) {
                    Icon(
                        imageVector = if (playingSuraId == sura.id && isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "تشغيل الصوت",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // Segmented Reader Mode selector
        TabRow(
            selectedTabIndex = readerMode,
            containerColor = Color(0xFFF1ECE4),
            contentColor = Color(0xFF1B5E20),
            modifier = Modifier.fillMaxWidth().testTag("quran_reader_mode_tabs")
        ) {
            Tab(
                selected = readerMode == 0,
                onClick = { readerMode = 0 },
                text = { Text("قراءة بالرسم العثماني 📝", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = readerMode == 1,
                onClick = { readerMode = 1 },
                text = { Text("مصحف يوسف هيبة الإلكتروني 🌐", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
        }

        if (isSearchExpanded && readerMode == 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1ECE4))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = inSuraSearchQuery,
                    onValueChange = { inSuraSearchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("insura_search_field"),
                    placeholder = { Text("بحث عن كلمة أو عبارة في سورة ${sura.name}...", fontSize = 12.sp, color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, "بحث", tint = Color.Gray, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (inSuraSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { inSuraSearchQuery = "" }) {
                                Icon(Icons.Default.Close, "مسح", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.LightGray
                    )
                )
            }
        }

        if (readerMode == 1) {
            // Premium interactive WebView loader for quran.yousefheiba.com
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        }
                        webViewClient = android.webkit.WebViewClient()
                        loadUrl("https://quran.yousefheiba.com/")
                    }
                },
                modifier = Modifier.fillMaxSize().testTag("quran_yousefheiba_webview")
            )
        } else {
            // Surah Verses
            if (isCurrentlyLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "جاري تحميل نص السورة برواية ورش...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (versesList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "تنبيه اتصال",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "يتطلب قراءة سورة ${sura.name} الاتصال بالإنترنت لأول مرة لتحميلها برواية ورش.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "بإمكانك تشغيل التلاوة الصوتية مباشرة مع تحميلها للاستماع دون اتصال.",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadSuraVerses(sura.id) }
                        ) {
                            Text("تحميل النص الآن")
                        }
                    }
                }
            } else {
                val versesPerPage = 8
                val totalPages = (versesList.size + versesPerPage - 1) / versesPerPage
                
                val initialPage = remember(sura.id, initialTargetVerseIndex) {
                    if (initialTargetVerseIndex != null && initialTargetVerseIndex in 0 until versesList.size) {
                        initialTargetVerseIndex / versesPerPage
                    } else {
                        0
                    }
                }
                var currentPage by remember(sura.id, initialPage) { mutableStateOf(initialPage) }
                var highlightedVerseIndex by remember(sura.id, initialTargetVerseIndex) { mutableStateOf(initialTargetVerseIndex) }

                val pageStart = currentPage * versesPerPage
                val pageEnd = minOf(pageStart + versesPerPage, versesList.size)
                val pageVerses = versesList.subList(pageStart, pageEnd)

                // Match in-sura verses
                val matchedInSuraVerses = remember(inSuraSearchQuery, versesList) {
                    if (inSuraSearchQuery.trim().length < 2) {
                        emptyList<Pair<Int, String>>()
                    } else {
                        val queryNorm = normalizeArabic(inSuraSearchQuery)
                        versesList.mapIndexed { idx, verse -> idx to verse }
                            .filter { (_, verse) -> normalizeArabic(verse).contains(queryNorm) }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Sura details headers / Basmalah
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (inSuraSearchQuery.isNotEmpty()) {
                            Text(
                                text = "نتائج البحث في سورة ${sura.name}: تم العثور على ${matchedInSuraVerses.size} آية",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9E782F),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "سورة ${sura.name} - الصفحة ${currentPage + 1} من $totalPages",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9E782F),
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Centered Basmalah on the very first page of appropriate surahs (only if not searching)
                        if (currentPage == 0 && sura.id != 1 && sura.id != 9 && inSuraSearchQuery.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Main Quran Page content or Search results inside Surah
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBF7)), // Soft parchment style
                        border = BorderStroke(1.5.dp, Color(0xFFD4AF37).copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            if (inSuraSearchQuery.isNotEmpty()) {
                                if (inSuraSearchQuery.trim().length < 2) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "يرجى كتابة حرفين على الأقل للبحث...",
                                            fontSize = 13.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else if (matchedInSuraVerses.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "لم يتم العثور على نتائج مطابقة لـ \"$inSuraSearchQuery\"",
                                            fontSize = 13.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(matchedInSuraVerses) { (index, verse) ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFFF1ECE4).copy(alpha = 0.4f))
                                                    .clickable {
                                                        // Click to navigate directly to the page containing this verse!
                                                        currentPage = index / versesPerPage
                                                        highlightedVerseIndex = index
                                                        inSuraSearchQuery = ""
                                                        isSearchExpanded = false
                                                    }
                                                    .padding(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(Color(0xFFD4AF37).copy(alpha = 0.2f), shape = CircleShape)
                                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "آية ${index + 1}",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF9E782F)
                                                        )
                                                    }
                                                    
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowForward,
                                                        contentDescription = "انتقال للآية",
                                                        tint = Color(0xFF1B5E20).copy(alpha = 0.6f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                                    HighlightedQuranText(
                                                        text = verse.trim(),
                                                        query = inSuraSearchQuery,
                                                        style = androidx.compose.ui.text.TextStyle(
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Serif,
                                                            color = Color(0xFF1B5E20),
                                                            lineHeight = 24.sp
                                                        ),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Rich concatenated flowing text
                                    val annotatedText = buildAnnotatedString {
                                        pageVerses.forEachIndexed { relativeIndex, verse ->
                                            val absoluteIndex = pageStart + relativeIndex + 1
                                            val isHighlighted = (absoluteIndex - 1) == highlightedVerseIndex
                                            withStyle(style = SpanStyle(
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Serif,
                                                color = if (isHighlighted) Color(0xFFD84315) else Color(0xFF1B5E20),
                                                background = if (isHighlighted) Color(0xFFFFCCBC).copy(alpha = 0.5f) else Color.Transparent
                                            )) {
                                                append(verse.trim())
                                            }
                                            append(" ")
                                            // Custom golden inline verse circle index: ﴿١﴾
                                            withStyle(style = SpanStyle(
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isHighlighted) Color(0xFFD84315) else Color(0xFFD4AF37)
                                            )) {
                                                append(" ﴿$absoluteIndex﴾ ")
                                            }
                                            append(" ")
                                        }
                                    }
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                        Text(
                                            text = annotatedText,
                                            textAlign = TextAlign.Justify,
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                // Tapping on page clears the active highlight safely:
                                                highlightedVerseIndex = null
                                            },
                                            lineHeight = 44.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Paging control bar (only show if not searching)
                    if (inSuraSearchQuery.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (currentPage > 0) {
                                        currentPage--
                                        highlightedVerseIndex = null // Clear highlight when changing page so it's not confusing
                                    }
                                },
                                enabled = currentPage > 0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1B5E20),
                                    disabledContainerColor = Color.LightGray
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("الصفحة السابقة ◀", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))

                            Button(
                                onClick = {
                                    if (currentPage < totalPages - 1) {
                                        currentPage++
                                        highlightedVerseIndex = null // Clear highlight when changing page so it's not confusing
                                    }
                                },
                                enabled = currentPage < totalPages - 1,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1B5E20),
                                    disabledContainerColor = Color.LightGray
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("الصفحة التالية ▶", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. AI PRODUCTIVITY COACH (مستشار الذكاء الاصطناعي)
// ==========================================
@Composable
fun AICoachScreen(viewModel: HayatyViewModel) {
    val currentAdvice by viewModel.aiProductivityAdvice.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzingProductivity.collectAsStateWithLifecycle()

    val aiFuturePlanAdvice by viewModel.aiFuturePlanAdvice.collectAsStateWithLifecycle()
    val isAnalyzingFuturePlan by viewModel.isAnalyzingFuturePlan.collectAsStateWithLifecycle()
    val futureQuranBaseline by viewModel.futureQuranBaseline.collectAsStateWithLifecycle()

    var coachSubTab by remember { mutableStateOf(0) } // 0 = Daily Routine, 1 = Future Plan + 10%

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("AzkarPrefs", android.content.Context.MODE_PRIVATE) }
    var completedMonths by remember { mutableStateOf(prefs.getStringSet("CompletedMonths", emptySet()) ?: emptySet()) }
    var selectedMonthIndex by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Horizontal premium design tabs for AI Coach Sub-modes
        TabRow(
            selectedTabIndex = coachSubTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Tab(
                selected = coachSubTab == 0,
                onClick = { coachSubTab = 0 },
                text = { Text("المستشار اليومي ⚡", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = coachSubTab == 1,
                onClick = { coachSubTab = 1 },
                text = { Text("خطة التقويم والقرآن 📈", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (coachSubTab == 0) {
                // --- DAILY ROUTINE SUB-TAB ---
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "✨ مستشار الإنتاجية بالذكاء الاصطناعي",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "يقوم محرك الذكاء الاصطناعي بتحليل عاداتك اليومية واستخدام تطبيقات الهاتف، ويقترح عليك جدول يومي ذكي وتوصيات مذهلة للتخلص من الإفراط والكسل.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                // --- TRIGGER BUTTON ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("ai_trigger_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "جدول زمني ذكي وتوجيه مخصص لنشاطك ⚡",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "اضغط على الزر أدناه وسيتم التواصل الآمن مع مستشارك الشخصي وإصدار روتينك المتكيف.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp).testTag("ai_loading_spinner")
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("يجري تحليل بياناتك وصياغة الحل الأمثل...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            } else {
                                Button(
                                    onClick = { viewModel.analyzeProductivityWithAI() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("trigger_ai_btn")
                                ) {
                                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "تحليل")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("توليد جدول ذكي بالذكاء الاصطناعي", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // --- ANALYSIS RESPONSE CONTAINER ---
                if (currentAdvice != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("ai_response_card"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = "توصية ذكية",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "مخطط الأداء وتوصيات مستشارك المخصص:",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = currentAdvice!!,
                                    fontSize = 14.sp,
                                    lineHeight = 24.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Right
                                )
                            }
                        }
                    }
                }
            } else {
                // --- FUTURE PLAN SUB-TAB ---
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "📈 الخطة المستقبلية وخطة تقويم تلاوة القرآن",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "خطة تقويمية مستقبلية للارتقاء بورد القرآن الكريم اليومي بزيادة تدريجية تقدر بـ 10% شهرياً طوال 6 أشهر لترويض النفس والارتقاء المتزن.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "⚙️ ضبط صفحات الأساس الحالية (الورد اليومي الحالي):",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { if (futureQuranBaseline > 1) viewModel.updateFutureQuranBaseline(futureQuranBaseline - 1) },
                                    modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                ) {
                                    Icon(imageVector = Icons.Default.Remove, contentDescription = "نقص صفحات الأساس", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                
                                Text(
                                    text = "$futureQuranBaseline صفحة",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                                
                                IconButton(
                                    onClick = { viewModel.updateFutureQuranBaseline(futureQuranBaseline + 1) },
                                    modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "زيادة صفحات الأساس", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }
                }

                item {
                    // --- INTERACTIVE PROGRESS BAR DIAGRAM / CHART ---
                    val m1 = futureQuranBaseline
                    val m2 = Math.round(futureQuranBaseline * 1.1).toInt().coerceAtLeast(m1 + 1)
                    val m3 = Math.round(m2 * 1.1).toInt().coerceAtLeast(m2 + 1)
                    val m4 = Math.round(m3 * 1.1).toInt().coerceAtLeast(m3 + 1)
                    val m5 = Math.round(m4 * 1.1).toInt().coerceAtLeast(m4 + 1)
                    val m6 = Math.round(m5 * 1.1).toInt().coerceAtLeast(m5 + 1)

                    val monthsValues = listOf(m1, m2, m3, m4, m5, m6)
                    val monthsFullLabels = listOf(
                        "الشهر الأول (الأساس)",
                        "الشهر الثاني (+10% نمو)",
                        "الشهر الثالث (+10% نمو)",
                        "الشهر الرابع (+10% نمو)",
                        "الشهر الخامس (+10% نمو)",
                        "الشهر السادس (+10% متميز)"
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📊", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "مخطط صعود الورد وتكامل العادات 📈",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                Text(
                                    text = "تفاعلي (انقر على الأعمدة) 👆",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(18.dp))

                            // Interactive Bar Chart
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                monthsValues.forEachIndexed { index, value ->
                                    val isSelected = selectedMonthIndex == index
                                    val isCompleted = completedMonths.contains(index.toString())
                                    
                                    // Scale height proportionally
                                    val maxHeight = 90.dp
                                    val barHeight = ((value.toFloat() / m6.toFloat()) * maxHeight.value).dp.coerceAtLeast(30.dp)

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedMonthIndex = index }
                                            .padding(horizontal = 4.dp)
                                    ) {
                                        // Bar view
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(barHeight)
                                                .background(
                                                    color = when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        isCompleted -> Color(0xFF2E7D32)
                                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                                    },
                                                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                                )
                                                .border(
                                                    width = if (isSelected) 2.dp else 0.dp,
                                                    color = if (isSelected) Color(0xFFD4AF37) else Color.Transparent,
                                                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                                ),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)
                                            ) {
                                                // Value indicator inside or above
                                                Text(
                                                    text = "$value",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                                
                                                if (isCompleted) {
                                                    Text("✔️", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                                } else {
                                                    Spacer(modifier = Modifier.height(1.dp))
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        // Label below bar
                                        Text(
                                            text = "ش${index + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(14.dp))

                            // Details of the clicked/selected month
                            val label = monthsFullLabels[selectedMonthIndex]
                            val pages = monthsValues[selectedMonthIndex]
                            val isCompleted = completedMonths.contains(selectedMonthIndex.toString())
                            
                            val tip = when(selectedMonthIndex) {
                                0 -> "ابدأ بتهيئة ذهنك وربط الورد بصلاتين رئيسيتين كصلاتي الفجر والعشاء لتثبيت العادة بنسبة ديمومة عالية."
                                1 -> "الزيادة يسيرة جداً، ركّبها على الصلوات الباقية بإضافة صفحة واحدة دبر صلاة العصر لكي لا تشعر بأي ثقل."
                                2 -> "استخدم طريقة قراءة البكور (ما قبل شروق الشمس). الهدوء والسكينة تضاعف سرعة حفظك وتدبرك."
                                3 -> "قم بتجزئة الورد إلى نصفين: نصف بعد الفجر ونصف قبل النوم لتسهيل قراءة هذا المقدار المتنامي."
                                4 -> "لقد اقتربت من خط نهاية النصف الأول من الخطة بنجاح. ركّز الآن على ترطيب لسانك بالتدبر أثناء القراءة."
                                else -> "وصلت لقمة التميز والتلاوة الذهبية! أنت الآن تقرأ بزيادة تبلغ 60% مقارنة بالأساس دون أدنى تعب بفضل التدرج الرائع!"
                            }

                            val challenge = when(selectedMonthIndex) {
                                0 -> "التلاوة لمدة 7 أيام متتالية دون انقطاع لتأسيس الروتين."
                                1 -> "قراءة الورد اليومي بصوت مسموع خاشع ومحبر."
                                2 -> "تدوين آية واحدة استوقفتك يومياً لفهم معانيها العميقة."
                                3 -> "الاستماع لتلاوة شيخ متقن لنفس صفحات الورد لتصحيح النطق."
                                4 -> "شرح أسباب نزول آية واحدة لأحد أفراد عائلتك لنشر علم القرآن."
                                else -> "التلاوة المتصلة لكامل الورد اليومي دون تفريق لتعزيز المهارة وصقل الحفظ."
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "$label 🎯",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        
                                        Text(
                                            text = "$pages صفحات / يوم",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        text = "💡 نصيحة التطوير والارتقاء:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = tip,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        text = "🏆 تحدّي هذا الشهر:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = challenge,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Interactive toggle complete button
                                    Button(
                                        onClick = {
                                            val currentSet = prefs.getStringSet("CompletedMonths", emptySet()) ?: emptySet()
                                            val updated = if (currentSet.contains(selectedMonthIndex.toString())) {
                                                currentSet - selectedMonthIndex.toString()
                                            } else {
                                                currentSet + selectedMonthIndex.toString()
                                            }
                                            prefs.edit().putStringSet("CompletedMonths", updated).apply()
                                            completedMonths = updated
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 12.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Star,
                                            contentDescription = "شارة الإنجاز",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isCompleted) "لقد أنجزت هذا الشهر بنجاح! 🎉" else "ضع علامة منجز لهذا الشهر 🏆",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "تحليل وتطوير المستقبل بالذكاء الاصطناعي 🧠",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "دع محرك الذكاء الاصطناعي يصوغ ويقترح لك خطة تقويم ومطالعة وموازنة عادات مخصصة لتمكين زيادة الـ 10% بنجاح وسهولة.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            if (isAnalyzingFuturePlan) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("يجري تحليل الذكاء وصياغة خطة التقويم...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            } else {
                                Button(
                                    onClick = { viewModel.analyzeFuturePlanWithAI() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "برمجة التقويم")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("توليد خطة تقويم مستقبلي متكاملة", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (aiFuturePlanAdvice != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = "الخطة الإسلامية المتكاملة",
                                        tint = Color(0xFFD4AF37),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "الخطة المستقبلية الإسلامية وتوجيهات التقويم:",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = aiFuturePlanAdvice!!,
                                    fontSize = 14.sp,
                                    lineHeight = 24.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Right
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

// ==========================================
// 5.5 ISLAMIC FIQH QUESTIONS (قسم الأسئلة الفقهية)
// ==========================================
@Composable
fun FiqhQuestionsScreen() {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("الكل") }
    
    // Choose the Daily Feature Question based on current Day of Year
    val calendar = remember { java.util.Calendar.getInstance() }
    val dayOfYear = remember { calendar.get(java.util.Calendar.DAY_OF_YEAR) }
    val dailyQuestion = remember {
        val questions = com.example.data.FiqhData.questions
        questions[dayOfYear % questions.size]
    }

    // Keep track of which question cards are expanded
    val expandedQuestions = remember { mutableStateMapOf<Int, Boolean>() }

    // Always keep daily question expanded by default when first opening
    LaunchedEffect(Unit) {
        expandedQuestions[dailyQuestion.id] = true
    }

    val categories = listOf("الكل", "الصلاة والعبادات", "فقه الصيام", "الطهور والوضوء", "آداب وقراءة القرآن")

    val filteredQuestions = remember(searchQuery, selectedCategory) {
        com.example.data.FiqhData.questions.filter { question ->
            val matchesSearch = question.question.contains(searchQuery, ignoreCase = true) || 
                                question.answer.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "الكل" || question.category == selectedCategory
            matchesSearch && matchesCategory
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // --- DAILY FEATURE Q&A CARD ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✨", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "سؤال وجواب اليوم الفقهي المميز",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = dailyQuestion.category,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = dailyQuestion.question,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = dailyQuestion.answer,
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = "المصدر: ${dailyQuestion.reference}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        textAlign = TextAlign.Left,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // --- FILTER & SEARCH HEADER ---
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "🔎 موسوعة الفقه الميسر والأسئلة الشائعة",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث عن مسألة فقهية...", fontSize = 12.sp) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "بحث") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // --- CATEGORIES HORIZONTAL CHIPS ---
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = { Text(category, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // --- QUESTIONS LIST ---
        if (filteredQuestions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "عذراً، لم يتم العثور على نتائج تطابق معايير البحث.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(filteredQuestions) { question ->
                val isExpanded = expandedQuestions[question.id] ?: false
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    onClick = { expandedQuestions[question.id] = !isExpanded }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💡 ${question.category}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "تقليص" else "توسيع",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = question.question,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Text(
                                text = question.answer,
                                fontSize = 12.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Text(
                                text = "المصدر: ${question.reference}",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                textAlign = TextAlign.Left,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ==========================================
// 6. FOCUS ACTIVE OVERLAY (ستار واجهة التركيز غير مشتت)
// ==========================================
@Composable
fun FocusActiveOverlay(
    remainingSeconds: Int,
    tasks: List<com.example.data.Task>,
    onToggleTask: (com.example.data.Task) -> Unit,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C110D)) // Extremely dark organic forest green
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Pulse flower element
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulse)
                    .border(2.dp, Color(0xFF4CAF50), CircleShape)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AllInclusive,
                    contentDescription = "تركيز كامل",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "جلسة التركيز العميق نشطة 🧘",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "حياتك أثمن من أن تضيع في التصفح المشتت. تم قفل التنبيهات المزعجة لحمايتك.",
                fontSize = 12.sp,
                color = Color.LightGray.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Timer display
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            Text(
                text = String.format(Locale.US, "%02d:%02d", mins, secs),
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFFD54F) // Glorious Gold timer text
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- WIDGET CHECKLIST ON THE CHRONOMETER SCREEN ---
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E291F).copy(alpha = 0.6f)
                ),
                border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📋 قائمة التركيز والتنفيذ",
                        color = Color(0xFF34D399),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    val activeTasks = tasks.filter { !it.isCompleted }
                    if (activeTasks.isEmpty()) {
                        Text(
                            text = "لا توجد مهام نشطة حالياً! أحسنت صنعاً في إنجاز أعمالك 🌿",
                            color = Color.LightGray.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        activeTasks.take(4).forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleTask(task) }
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "⬜",
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = task.title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Right,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "المجال: " + task.category,
                                        color = Color.LightGray.copy(alpha = 0.6f),
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Right
                                    )
                                }
                            }
                        }
                        
                        if (activeTasks.size > 4) {
                            Text(
                                text = "+ ${activeTasks.size - 4} مهام متبقية",
                                color = Color.LightGray.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("end_focus_early")
            ) {
                Text(
                    text = "إنهاء مبكر (غير منصوح به)",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==========================================
// UTILITY DIALOGS (حوارات الحوار لإضافة المهام والعادات)
// ==========================================
@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onConfirm: (String, String, String, Boolean) -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("عام") }
    var isAppointment by remember { mutableStateOf(false) }

    val categories = listOf("عام", "عمل", "دراسة", "عبادة", "صحة")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().testTag("add_task_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "إضافة مهمة جديدة", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان المهمة") },
                    modifier = Modifier.fillMaxWidth().testTag("task_title_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("الوصف أو التفاصيل") },
                    modifier = Modifier.fillMaxWidth().testTag("task_desc_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(text = "تصنيف المهمة:", fontSize = 14.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSel = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Appoint checkbox toggling calendar integration
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isAppointment,
                        onCheckedChange = { isAppointment = it },
                        modifier = Modifier.testTag("appointment_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "تزامن تلقائي مع تقويم جوجل", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("إلغاء")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (title.isNotEmpty()) onConfirm(title, desc, selectedCategory, isAppointment) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("confirm_add_task")
                    ) {
                        Text("إنشاء")
                    }
                }
            }
        }
    }
}

@Composable
fun AddHabitDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var targetTime by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().testTag("add_habit_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "إنشاء عادة جديدة 🌱", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم العادة (مثال: الاستغفار 100 مرة)") },
                    modifier = Modifier.fillMaxWidth().testTag("habit_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = targetTime,
                    onValueChange = { targetTime = it },
                    label = { Text("توقيت العادة (مثال: 07:30 ص أو بعد العصر)") },
                    placeholder = { Text("اختياري - لمساعدتك في التنبيه والالتزام") },
                    modifier = Modifier.fillMaxWidth().testTag("habit_time_input"),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Schedule, contentDescription = "توقيت")
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("إلغاء")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (name.isNotEmpty()) onConfirm(name, targetTime) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("confirm_add_habit")
                    ) {
                        Text("إنشاء")
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HabitDetailScreen(
    habit: Habit,
    viewModel: HayatyViewModel,
    onBack: () -> Unit
) {
    var name by remember(habit) { mutableStateOf(habit.name) }
    var selectedIcon by remember(habit) { mutableStateOf(habit.icon) }
    var selectedCategory by remember(habit) { mutableStateOf(habit.category) }
    var durationMinutes by remember(habit) { mutableStateOf(habit.targetDurationMinutes) }
    var reminderTime by remember(habit) { mutableStateOf(habit.reminderTime ?: "") }
    var targetAppPackage by remember(habit) { mutableStateOf(habit.targetAppPackage ?: "") }

    val isPredictingState by viewModel.isPredictingSolidification.collectAsStateWithLifecycle()
    val isCurrentlyPredicting = isPredictingState[habit.id] ?: false

    val allLogs by viewModel.allHabitLogs.collectAsStateWithLifecycle()
    val habitLogs = remember(allLogs, habit) { allLogs.filter { it.habitId == habit.id } }

    val categories = listOf("عام", "عبادة", "رياضة", "تعلم", "صحة", "عمل", "تطوير")
    val emojis = listOf("📖", "🕌", "💪", "💧", "🥗", "🏃", "😴", "🧠", "☕", "✨", "📝", "💸", "🌳", "🚶", "🧹")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top app bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "عودة"
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "تخصيص وتعديل العادة ⚙️",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon Selection Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "اختر أيقونة العادة 🎨",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Large Preview
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = selectedIcon, fontSize = 36.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Grid of emojis
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            emojis.forEach { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selectedIcon == emoji) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .clickable { selectedIcon = emoji }
                                        .testTag("emoji_$emoji"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Customize Attributes Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "معلومات العادة الأساسية 📝",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("اسم العادة") },
                            modifier = Modifier.fillMaxWidth().testTag("edit_habit_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Category Chips
                        Column {
                            Text(
                                text = "تصنيف العادة المتكررة:",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                categories.forEach { cat ->
                                    val isSel = selectedCategory == cat
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectedCategory = cat },
                                        label = { Text(cat) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }

                        // Duration/Timing Targets
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "توقيت المدة (مستهدف الممارسة اليومي):",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$durationMinutes دقيقة",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = durationMinutes.toFloat(),
                                onValueChange = { durationMinutes = it.toInt() },
                                valueRange = 1f..120f,
                                steps = 24
                            )
                        }

                        // Reminder Time Text input
                        OutlinedTextField(
                            value = reminderTime,
                            onValueChange = { reminderTime = it },
                            label = { Text("وقت التذكير اليومي (مثال: 08:30 ص)") },
                            modifier = Modifier.fillMaxWidth().testTag("edit_habit_reminder_input"),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Schedule, contentDescription = "توقيت")
                            }
                        )
                    }
                }
            }

            // Continuity / Streak section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Whatshot,
                                contentDescription = "استمرارية",
                                tint = Color(0xFFFF6D00),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "مستوى استمراريتك بالعلاقة ⚡",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${habit.streak}",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF6D00)
                                )
                                Text(
                                    text = "اليومي المتتالي",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${habitLogs.size}",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "إجمالي تكرار الإنجاز",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "سجل آخر 7 أيام من العادة:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Draw last 7 days checklist
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val displaySdf = SimpleDateFormat("E", Locale("ar"))
                            (0..6).reversed().forEach { daysAgo ->
                                val cal = Calendar.getInstance()
                                cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
                                val dateStr = sdf.format(cal.time)
                                val dayName = displaySdf.format(cal.time)
                                val wasCompleted = habitLogs.any { it.date == dateStr }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = dayName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (wasCompleted) Color(0xFF4CAF50)
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (wasCompleted) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "مكتمل",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        } else {
                                            Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // AI Solidification Predict Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "مستشار الذكاء الاصطناعي",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "مستشار الذكاء الاصطناعي لتثبيت العادات 🧠✨",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }

                        Text(
                            text = "المدة المتوقعة للتثبيت (التحول لعادة راسخة تلقائياً):",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )

                        Text(
                            text = "${habit.aiExpectedDays} يوماً من المتابعة 📈",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.tertiary
                        )

                        Text(
                            text = habit.aiExplanation ?: "اضغط على زر تحديث التحليل بالأسفل ليقوم مستشار الذكاء الاصطناعي بتحليل الأهداف وتقديم نصيحة للتغلب على التكاسل وموائمة الخطة الزمنية.",
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )

                        Button(
                            onClick = { viewModel.triggerAiSolidificationPrediction(habit) },
                            enabled = !isCurrentlyPredicting,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.fillMaxWidth().testTag("ai_solidify_predict_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isCurrentlyPredicting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري فحص العادة مع الذكاء الاصطناعي...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "تحليل",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تحليلات الذكاء الاصطناعي للتثبيت 🧠")
                            }
                        }
                    }
                }
            }

            // App Linkage Section
            item {
                val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
                var appSearchQuery by remember { mutableStateOf("") }
                var isExpanded by remember { mutableStateOf(false) }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Launch,
                                contentDescription = "فتح تطبيق آخر",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ربط العادة بفتح تطبيق آخر عند الإنجاز 📲",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "عند وضع علامة صح (إكمال العادة)، سيقوم التطبيق تلقائياً بفتح التطبيق المحدد لمساعدتك على المتابعة والالتزام مباشرة (مثل تطبيق قرآن، أو مفكرة، أو تواصل).",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        // Current Picked App Label
                        val selectedAppLabel = remember(targetAppPackage, installedApps) {
                            installedApps.find { it.packageName == targetAppPackage }?.label ?: targetAppPackage
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clickable { isExpanded = !isExpanded }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (targetAppPackage.isBlank()) "لا يوجد تطبيق مرتبط حالياً" else "التطبيق المرتبط: $selectedAppLabel",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (targetAppPackage.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                                )
                                if (targetAppPackage.isNotBlank()) {
                                    Text(
                                        text = targetAppPackage,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (targetAppPackage.isNotBlank()) {
                                    IconButton(
                                        onClick = { 
                                            targetAppPackage = "" 
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "إلغاء الربط",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "عرض الخيارات",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isExpanded) {
                            OutlinedTextField(
                                value = appSearchQuery,
                                onValueChange = { appSearchQuery = it },
                                label = { Text("بحث في التطبيقات المثبتة 🔍") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )

                            val filteredApps = remember(appSearchQuery, installedApps) {
                                installedApps.filter { 
                                    it.label.contains(appSearchQuery, ignoreCase = true) || 
                                    it.packageName.contains(appSearchQuery, ignoreCase = true)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .align(Alignment.CenterHorizontally)
                            ) {
                                if (filteredApps.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "لم يتم العثور على أي تطبيق يتطابق مع البحث",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(filteredApps) { appInfo ->
                                            val isSelected = appInfo.packageName == targetAppPackage
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                        else MaterialTheme.colorScheme.surface
                                                    )
                                                    .clickable {
                                                        targetAppPackage = appInfo.packageName
                                                        isExpanded = false
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(
                                                            if (isSelected) MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                            CircleShape
                                                        )
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = appInfo.label,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = appInfo.packageName,
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Save Buttons
            item {
                Button(
                    onClick = {
                        val updated = habit.copy(
                            name = name,
                            icon = selectedIcon,
                            category = selectedCategory,
                            targetDurationMinutes = durationMinutes,
                            reminderTime = reminderTime.ifBlank { null },
                            targetAppPackage = targetAppPackage.ifBlank { null }
                        )
                        viewModel.updateHabit(updated)
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_habit_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("حفظ التخصيص والعودة ✅", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

// ==========================================
// QURAN SEARCH UTILITIES & COMPONENTS
// ==========================================

fun normalizeArabic(text: String): String {
    val diacritics = Regex("[\u064B-\u065F\u0670]")
    var normalized = text.replace(diacritics, "")
    normalized = normalized.replace(Regex("[أإآٱ]"), "ا")
    normalized = normalized.replace("ة", "ه")
    normalized = normalized.replace("ى", "ي")
    return normalized.trim()
}

fun highlightMultipleWords(
    originalText: String,
    query: String,
    highlightColor: Color = Color(0xFFD4AF37)
): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        if (query.isEmpty()) {
            append(originalText)
            return@buildAnnotatedString
        }

        val normQuery = normalizeArabic(query)
        if (normQuery.isEmpty()) {
            append(originalText)
            return@buildAnnotatedString
        }

        val normText = normalizeArabic(originalText)
        val diacritics = Regex("[\u064B-\u065F\u0670]")
        val originalIndices = mutableListOf<Int>()
        for (i in originalText.indices) {
            val charStr = originalText[i].toString()
            if (!charStr.matches(diacritics)) {
                originalIndices.add(i)
            }
        }

        val indicesMap = originalIndices.toIntArray()
        var lastOriginalIdx = 0
        var normSearchIdx = 0

        while (true) {
            val startNormIdx = normText.indexOf(normQuery, normSearchIdx)
            if (startNormIdx == -1) {
                append(originalText.substring(lastOriginalIdx))
                break
            }

            val endNormIdx = startNormIdx + normQuery.length

            if (startNormIdx < indicesMap.size) {
                val startOriginalIdx = indicesMap[startNormIdx]
                val endOriginalIdx = if (endNormIdx - 1 < indicesMap.size) {
                    indicesMap[endNormIdx - 1] + 1
                } else {
                    originalText.length
                }

                if (startOriginalIdx > lastOriginalIdx) {
                    append(originalText.substring(lastOriginalIdx, startOriginalIdx))
                }

                withStyle(SpanStyle(background = highlightColor.copy(alpha = 0.35f), fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))) {
                    append(originalText.substring(startOriginalIdx, endOriginalIdx))
                }

                lastOriginalIdx = endOriginalIdx
                normSearchIdx = endNormIdx
            } else {
                append(originalText.substring(lastOriginalIdx))
                break
            }
        }
    }
}

@Composable
fun HighlightedQuranText(
    text: String,
    query: String,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default,
    highlightColor: Color = Color(0xFFD4AF37),
    modifier: Modifier = Modifier
) {
    val annotatedString = remember(text, query) {
        highlightMultipleWords(text, query, highlightColor)
    }
    Text(text = annotatedString, style = style, modifier = modifier)
}

@Composable
fun SettingsDialog(
    viewModel: HayatyViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()
    val prayerOffsetMinutes by viewModel.prayerOffsetMinutes.collectAsStateWithLifecycle()
    
    val showTimerBanner by viewModel.showTimerBanner.collectAsStateWithLifecycle()
    val showStatsGrid by viewModel.showStatsGrid.collectAsStateWithLifecycle()
    val showPrayerWidget by viewModel.showPrayerWidget.collectAsStateWithLifecycle()
    val showQuranTracker by viewModel.showQuranTracker.collectAsStateWithLifecycle()
    val showFiqhWidget by viewModel.showFiqhWidget.collectAsStateWithLifecycle()
    val showHabitWidget by viewModel.showHabitWidget.collectAsStateWithLifecycle()
    val showTasksWidget by viewModel.showTasksWidget.collectAsStateWithLifecycle()
    val showAiSuggestion by viewModel.showAiSuggestion.collectAsStateWithLifecycle()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 560.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الاعدادات وتخصيص الواجهة",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: Themes & Colors
                Text(
                    text = "🎨 المظهر والثيم البصري",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Theme mode selection row (System, Light, Dark)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        Triple("system", "تلقائي", "🌓"),
                        Triple("light", "مضيء", "☀️"),
                        Triple("dark", "مظلم", "🌙")
                    )
                    modes.forEach { (mode, label, emoji) ->
                        val isSel = themeMode == mode
                        Button(
                            onClick = { viewModel.setThemeMode(mode) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(emoji, fontSize = 16.sp)
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Accent color selector circles
                Text(
                    text = "اختر لون سمة التطبيق الأساسي:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val colors = listOf(
                        Triple("emerald", Color(0xFF059669), "زمردي"),
                        Triple("royal_blue", Color(0xFF2563EB), "ملكي"),
                        Triple("quiet_purple", Color(0xFF7C3AED), "بنفسجي"),
                        Triple("authentic_gold", Color(0xFFD97706), "ذهبي")
                    )

                    colors.forEach { (colorKey, colorVal, name) ->
                        val isSel = themeColor == colorKey
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { viewModel.setThemeColor(colorKey) }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(colorVal, CircleShape)
                                    .border(
                                        width = if (isSel) 3.dp else 0.dp,
                                        color = if (isSel) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSel) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = name,
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Prayer Timing adjustments (offsets)
                Text(
                    text = "⏰ ضبط فارق توقيت الصلاة",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Text(
                    text = "يمكنك تعديل توقيت الصلاة بإضافة أو خصم دقائق يدوياً.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.setPrayerOffsetMinutes(prayerOffsetMinutes - 5) },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .size(36.dp)
                    ) {
                        Text("-5", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.setPrayerOffsetMinutes(prayerOffsetMinutes - 1) },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "خصم دقيقة", modifier = Modifier.size(16.dp))
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (prayerOffsetMinutes > 0) "+$prayerOffsetMinutes د"
                                   else if (prayerOffsetMinutes < 0) "$prayerOffsetMinutes د"
                                   else "الافتراضي (0 د)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(
                        onClick = { viewModel.setPrayerOffsetMinutes(prayerOffsetMinutes + 1) },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "إضافة دقيقة", modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.setPrayerOffsetMinutes(prayerOffsetMinutes + 5) },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .size(36.dp)
                    ) {
                        Text("+5", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                if (prayerOffsetMinutes != 0) {
                    TextButton(onClick = { viewModel.setPrayerOffsetMinutes(0) }) {
                        Text("إعادة التعيين التلقائي", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                // Section 3: UI Widget Customizability (Show/Hide)
                Text(
                    text = "🖥️ تخصيص الواجهة والقطع",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Text(
                    text = "قم بإلغاء التحديد لحذف أو إخفاء أي قسم تريده من الشاشة الرئيسية.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(10.dp))

                val toggles = listOf(
                    Quadruple("بنر الوقت التنازلي والترحيب", showTimerBanner, { b: Boolean -> viewModel.setShowTimerBanner(b) }, "clock"),
                    Quadruple("بطاقة توجيهات ومستشار الذكاء", showAiSuggestion, { b: Boolean -> viewModel.setShowAiSuggestion(b) }, "intelligence"),
                    Quadruple("لوحة الإحصائيات والأرقام", showStatsGrid, { b: Boolean -> viewModel.setShowStatsGrid(b) }, "assessment"),
                    Quadruple("جدول مواقيت الصلاة والقبلة", showPrayerWidget, { b: Boolean -> viewModel.setShowPrayerWidget(b) }, "schedule"),
                    Quadruple("متابع ختمة القرآن الكريم", showQuranTracker, { b: Boolean -> viewModel.setShowQuranTracker(b) }, "book"),
                    Quadruple("فقرة سؤال الفقه المتجدد", showFiqhWidget, { b: Boolean -> viewModel.setShowFiqhWidget(b) }, "menu_book"),
                    Quadruple("منظم المهام والمسؤوليات اليومية", showTasksWidget, { b: Boolean -> viewModel.setShowTasksWidget(b) }, "checklist")
                )

                toggles.forEach { (label, value, onToggle, tag) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .clickable { onToggle(!value) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = value,
                            onCheckedChange = { onToggle(it) },
                            modifier = Modifier.testTag("switch_widget_$tag")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (value) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("حفظ وتعديل الواجهة 🎉", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)



