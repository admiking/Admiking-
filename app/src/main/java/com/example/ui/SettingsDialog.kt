package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsDialog(viewModel: HayatyViewModel, onDismiss: () -> Unit) {
    val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()
    val prayerOffset by viewModel.prayerOffsetMinutes.collectAsStateWithLifecycle()
    
    val showTimerBanner by viewModel.showTimerBanner.collectAsStateWithLifecycle()
    val showAiSuggestion by viewModel.showAiSuggestion.collectAsStateWithLifecycle()
    val showStatsGrid by viewModel.showStatsGrid.collectAsStateWithLifecycle()
    val showPrayerWidget by viewModel.showPrayerWidget.collectAsStateWithLifecycle()
    val showQuranTracker by viewModel.showQuranTracker.collectAsStateWithLifecycle()
    val showFiqhWidget by viewModel.showFiqhWidget.collectAsStateWithLifecycle()
    val showHabitWidget by viewModel.showHabitWidget.collectAsStateWithLifecycle()
    val showTasksWidget by viewModel.showTasksWidget.collectAsStateWithLifecycle()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("settings_dialog"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        text = "إعدادات التطبيق والمظهر",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Section 1: Themes (تعديل وتغيير الثيمات)
                Text(
                    text = "مظهر ولون السمة الرئيسية",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = TextAlign.Right
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val colors = listOf(
                        "emerald" to Color(0xFF059669),
                        "royal_blue" to Color(0xFF2563EB),
                        "quiet_purple" to Color(0xFF7C3AED),
                        "authentic_gold" to Color(0xFFD97706)
                    )
                    colors.forEach { (name, color) ->
                        val isSelected = themeColor == name
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { viewModel.setThemeColor(name) }
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "محدد",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))
                
                // Section 2: Timing Calibration (تعديل التوقيت)
                Text(
                    text = "تعديل ومعايرة تواقيت الصلاة (دقائق)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    textAlign = TextAlign.Right
                )
                Text(
                    text = "معايرة الحسابات لتعزيز مطابقة الساعات المحلية في مساجد بلدك.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    textAlign = TextAlign.Right
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (prayerOffset < 15) viewModel.setPrayerOffsetMinutes(prayerOffset + 1) },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "زيادة دقيقة",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    Text(
                        text = if (prayerOffset >= 0) "+$prayerOffset دقيقة" else "$prayerOffset دقيقة",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    
                    IconButton(
                        onClick = { if (prayerOffset > -15) viewModel.setPrayerOffsetMinutes(prayerOffset - 1) },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "نقصان دقيقة",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))
                
                // Section 3: UI Customize Delete/Add widgets (تعديل واجهة المستخدم بالحذف أو الإضافة)
                Text(
                    text = "ترتيب وإظهار عناصر الواجهة (تخصيص)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    textAlign = TextAlign.Right
                )
                Text(
                    text = "قم بإلغاء تحديد أي قسم لإخفائه تماماً، أو فعله لإضافته مجدداً.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    textAlign = TextAlign.Right
                )
                
                val widgets = listOf(
                    WidgetToggleConfig("مؤقت الوقت والترحيب الحي", showTimerBanner) { viewModel.setShowTimerBanner(it) },
                    WidgetToggleConfig("توجيهات ومقترحات الذكاء الإصطناعي", showAiSuggestion) { viewModel.setShowAiSuggestion(it) },
                    WidgetToggleConfig("ملخص واحصائيات الشاشة والتركيز", showStatsGrid) { viewModel.setShowStatsGrid(it) },
                    WidgetToggleConfig("بطاقة أوقات الصلاة اليومية", showPrayerWidget) { viewModel.setShowPrayerWidget(it) },
                    WidgetToggleConfig("متابع ورد القرآن بعد الصلوات", showQuranTracker) { viewModel.setShowQuranTracker(it) },
                    WidgetToggleConfig("سؤال وجواب الفقه اليومي", showFiqhWidget) { viewModel.setShowFiqhWidget(it) },
                    WidgetToggleConfig("متابع وباني عادات اليوم السريعة", showHabitWidget) { viewModel.setShowHabitWidget(it) },
                    WidgetToggleConfig("مفكرة تسيير وإنجاز المهام", showTasksWidget) { viewModel.setShowTasksWidget(it) }
                )
                
                widgets.forEach { config ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = config.onToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                        Text(
                            text = config.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("حفظ وتأكيد الإعدادات", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class WidgetToggleConfig(
    val title: String,
    val enabled: Boolean,
    val onToggle: (Boolean) -> Unit
)
