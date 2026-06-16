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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemDark
            }
            MyApplicationTheme(darkTheme = darkTheme) {
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
    
    val isFocusActive by viewModel.isFocusActive.collectAsStateWithLifecycle()
    val focusSeconds by viewModel.focusRemainingSeconds.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isFocusActive) {
            FocusActiveOverlay(
                remainingSeconds = focusSeconds,
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
                                        text = item.title, 
                                        fontSize = 11.sp, 
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = FontFamily.SansSerif
                                    ) 
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) item.iconSelected else item.iconUnselected,
                                        contentDescription = item.title,
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

// ==========================================
// 1. HOME SCREEN (الرئيسية)
// ==========================================
@Composable
fun HomeScreen(viewModel: HayatyViewModel, onNavigateToFocus: () -> Unit) {
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val prayerTimes by viewModel.prayerTimes.collectAsStateWithLifecycle()
    val selectedCity by viewModel.selectedCity.collectAsStateWithLifecycle()
    val habits by viewModel.allHabits.collectAsStateWithLifecycle()
    val todayLogs by viewModel.todayHabitLogs.collectAsStateWithLifecycle()
    val usageRecords by viewModel.usageRecords.collectAsStateWithLifecycle()

    val isUsingGps by viewModel.isUsingGps.collectAsStateWithLifecycle()
    val gpsLatitude by viewModel.gpsLatitude.collectAsStateWithLifecycle()
    val gpsLongitude by viewModel.gpsLongitude.collectAsStateWithLifecycle()

    val context = LocalContext.current
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

    val formattedDate = remember {
        val sdf = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar"))
        sdf.format(Date())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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

        // --- AI INTUITIVE GRADIENT CARD (AI Suggestion Card) ---
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
                                    text = "ذكاء اصطناعي • نشط ✨",
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
                            text = "وقت ذروة الإنتاجية المكتشف: الآن",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "بناءً على نشاطك، هذا هو أفضل وقت لإتمام مهام البرمجة والعبادة وعاداتك اليومية. تم قفل التطبيقات المشتتة.",
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
                                text = "بدء جلسة تركيز (٢٥ د)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // --- HIGH DENSITY STATS GRID (2 COLUMNS) ---
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
                                text = "الصلاة القادمة",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val currentHourStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                        val nextPrayer = prayerTimes.firstOrNull { it.time >= currentHourStr } ?: prayerTimes.firstOrNull()
                        Text(
                            text = nextPrayer?.arabicName ?: "العصر",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (nextPrayer != null) "خلال دافئة" else "بعد قليل",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "الورد اليومي: ٧٥٪",
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
                                text = "استخدام الهاتف",
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
                            text = "+١٢٪ عن الأمس",
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

        // --- PRAYER TIMES WIDGET ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("prayer_widget"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
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
                                    selectedCity
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentHourStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                        prayerTimes.forEach { prayer ->
                            val isPassed = prayer.time < currentHourStr
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = prayer.arabicName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (!isPassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = prayer.time,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isPassed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
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
                }
            }
        }

        // --- POST-PRAYER QURAN TRACKER WIDGET ---
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

        // --- TASKS LIST WIDGET (To-Do List Manager) ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
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
                                color = MaterialTheme.colorScheme.onSurface
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
                }
            }
        }

        // --- FILTERED TASKS RENDERING LIST ---
        val filteredTasks = when (activeTaskFilter) {
            "remaining" -> tasks.filter { !it.isCompleted }
            "completed" -> tasks.filter { it.isCompleted }
            else -> tasks
        }

        if (filteredTasks.isEmpty()) {
            item {
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
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            items(filteredTasks) { task ->
                TaskRowItem(
                    task = task,
                    onToggle = { viewModel.toggleTaskCompletion(task) },
                    onDelete = { viewModel.deleteTask(task) }
                )
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

    // --- City Picker Dialog ---
    if (showCitySelectorDialog) {
        Dialog(onDismissRequest = { showCitySelectorDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "أو اختر يدوياً من المدن المتاحة:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    for (city in PrayerTimesHelper.cities) {
                        TextButton(
                            onClick = {
                                viewModel.setUsingGps(false, context)
                                viewModel.setCity(city)
                                showCitySelectorDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = city, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
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
    val habits by viewModel.allHabits.collectAsStateWithLifecycle()
    val logs by viewModel.todayHabitLogs.collectAsStateWithLifecycle()
    var showAddHabitDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
                        }
                    }
                }
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

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = habit.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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
            onConfirm = { name ->
                viewModel.addHabit(name)
                showAddHabitDialog = false
            }
        )
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
                text = "تحكم في استخدامك للهاتف وركز على يومك بكل وعي.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        // --- SCREEN TIME GRAPH ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("screen_usage_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val totalMinutes = usageRecords.sumOf { it.durationMs } / 60000
                        Column {
                            Text(text = "مجموع استخدام الهاتف اليوم", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "${totalMinutes / 60} ساعة و ${totalMinutes % 60} دقيقة", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            onClick = { 
                                // Open system usage access settings
                                try {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    viewModel.refreshUsageStats(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Text("أذونات النظام", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "توزيع التشتت لكل تطبيق ومستوى الإفراط:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bar Chart via standard Compose Canvas Custom Draw
                    usageRecords.take(5).forEach { record ->
                        val mins = record.durationMs / 60000
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = record.appName,
                                fontSize = 12.sp,
                                modifier = Modifier.width(80.dp),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Bar container
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            ) {
                                val fractionalWidth = minOf(1f, mins.toFloat() / 150f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fractionalWidth)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (mins > 60) Color(0xFFE53935) else MaterialTheme.colorScheme.primary
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "$mins د", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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

@Composable
fun QuranScreen(viewModel: HayatyViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    var selectedSura by remember { mutableStateOf<Sura?>(null) }
    val prayerTimes by viewModel.prayerTimes.collectAsStateWithLifecycle()
    val selectedCity by viewModel.selectedCity.collectAsStateWithLifecycle()

    val selectedReciterName by viewModel.selectedReciterName.collectAsStateWithLifecycle()
    val downloadedSuras by viewModel.downloadedSuras.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    
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
        QuranReaderScreen(sura = selectedSura!!, viewModel = viewModel, onBack = { selectedSura = null })
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
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
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
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
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
fun QuranReaderScreen(sura: Sura, viewModel: HayatyViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val loadedSuraVerses by viewModel.loadedSuraVerses.collectAsStateWithLifecycle()
    val isLoadingVerses by viewModel.isLoadingVerses.collectAsStateWithLifecycle()
    
    val isCurrentlyLoading = isLoadingVerses[sura.id] == true
    val versesList = if (sura.verses.isNotEmpty()) sura.verses else (loadedSuraVerses[sura.id] ?: emptyList())

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
            
            // Play audio directly in the reader header! Extremely practical!
            val playingSuraId by viewModel.playingSuraId.collectAsStateWithLifecycle()
            val isAudioPlaying by viewModel.isAudioPlaying.collectAsStateWithLifecycle()
            
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (sura.id != 1 && sura.id != 9) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "الرَّحْمَٰنِ الرَّحِيمِ",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                items(versesList.indices.toList()) { index ->
                    val verse = versesList[index]
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = verse,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = Color(0xFF1B5E20),
                            textAlign = TextAlign.Center,
                            lineHeight = 36.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .border(1.5.dp, Color(0xFFD4AF37), CircleShape)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD4AF37)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 0.5.dp)
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
                    // Month by month projection grid
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📅", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "جدول التقويم والورد الرقمي المتوقع (+10% شهرياً):",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            val m1 = futureQuranBaseline
                            val m2 = Math.round(futureQuranBaseline * 1.1).toInt().coerceAtLeast(m1 + 1)
                            val m3 = Math.round(m2 * 1.1).toInt().coerceAtLeast(m2 + 1)
                            val m4 = Math.round(m3 * 1.1).toInt().coerceAtLeast(m3 + 1)
                            val m5 = Math.round(m4 * 1.1).toInt().coerceAtLeast(m4 + 1)
                            val m6 = Math.round(m5 * 1.1).toInt().coerceAtLeast(m5 + 1)

                            val months = listOf(
                                "الشهر الأول (الأساس)" to m1,
                                "الشهر الثاني (+10%)" to m2,
                                "الشهر الثالث (+10%)" to m3,
                                "الشهر الرابع (+10%)" to m4,
                                "الشهر الخامس (+10%)" to m5,
                                "الشهر السادس (+10%)" to m6
                            )

                            months.forEachIndexed { index, pair ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(
                                                    if (index == 0) MaterialTheme.colorScheme.secondaryContainer
                                                    else MaterialTheme.colorScheme.primaryContainer,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (index == 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(text = pair.first, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${pair.second} صفحات يومياً",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (index == 5) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (index == 5) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("👑", fontSize = 14.sp)
                                        }
                                    }
                                }
                                if (index < months.size - 1) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
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
fun FocusActiveOverlay(remainingSeconds: Int, onCancel: () -> Unit) {
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
            modifier = Modifier.fillMaxWidth()
        ) {
            // Pulse flower element
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(150.dp)
                    .scale(pulse)
                    .border(2.dp, Color(0xFF4CAF50), CircleShape)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AllInclusive,
                    contentDescription = "تركيز كامل",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "جلسة التركيز العميق نشطة 🧘",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "حياتك أثمن من أن تضيع في التصفح اللاواعي للمشتتات. تم قفل التنبيهات والتطبيقات المزعجة لحمايتك.",
                fontSize = 14.sp,
                color = Color.LightGray.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Timer display
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            Text(
                text = String.format(Locale.US, "%02d:%02d", mins, secs),
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFFD54F) // Glorious Gold timer text
            )

            Spacer(modifier = Modifier.height(48.dp))

            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("end_focus_early")
            ) {
                Text(
                    text = "إنهاء مبكر (غير منصوح به)",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
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
fun AddHabitDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().testTag("add_habit_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "إنشاء عادة جديدة", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم العادة (مثال: الاستغفار 100 مرة)") },
                    modifier = Modifier.fillMaxWidth().testTag("habit_name_input"),
                    shape = RoundedCornerShape(12.dp)
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
                        onClick = { if (name.isNotEmpty()) onConfirm(name) },
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


