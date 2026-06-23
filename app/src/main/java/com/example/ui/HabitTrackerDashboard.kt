package com.example.ui

import android.media.RingtoneManager
import android.net.Uri
import kotlinx.coroutines.delay
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import android.content.Intent
import android.provider.Settings
import android.app.backup.BackupManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Habit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitTrackerDashboard(viewModel: HabitViewModel) {
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = viewModel.getBackupJson()
                context.contentResolver.openOutputStream(uri).use { outputStream ->
                    outputStream?.write(json.toByteArray(kotlin.text.Charsets.UTF_8))
                }
                Toast.makeText(context, "Data exported to file successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri).use { inputStream ->
                    val jsonStr = inputStream?.bufferedReader(kotlin.text.Charsets.UTF_8)?.readText()
                    if (!jsonStr.isNullOrBlank()) {
                        viewModel.importBackupJson(jsonStr) { success ->
                            if (success) {
                                Toast.makeText(context, "Data imported from file successfully!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Failed to parse JSON backup. Check file content.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val displayDayFormat = remember { SimpleDateFormat("EEE", Locale.US) }
    val displayNumFormat = remember { SimpleDateFormat("d", Locale.US) }
    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }

    val activeHabits by viewModel.activeHabits.collectAsState()
    val archivedHabits by viewModel.archivedHabits.collectAsState()
    val allCompletions by viewModel.allCompletions.collectAsState()
    val selectedDateStr by viewModel.selectedDateString.collectAsState()
    val todaySelectedComps by viewModel.selectedDateCompletions.collectAsState()
    val analytics by viewModel.habitAnalytics.collectAsState()
    val weeklyReports by viewModel.weeklyReports.collectAsState()
    val monthlyReports by viewModel.monthlyReports.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()

    val hideCompletedToday by viewModel.hideCompletedToday.collectAsState()
    var sortByBestHabit by remember { mutableStateOf(false) }

    val filteredActiveHabits = remember(activeHabits, hideCompletedToday, todaySelectedComps, sortByBestHabit, analytics) {
        var list = activeHabits
        if (hideCompletedToday) {
            list = list.filter { habit -> !todaySelectedComps.contains(habit.id) }
        }
        if (sortByBestHabit) {
            list = list.sortedWith { h1, h2 ->
                val meta1 = analytics.find { it.habit.id == h1.id }
                val meta2 = analytics.find { it.habit.id == h2.id }
                val c1 = meta1?.totalCompletions ?: 0
                val c2 = meta2?.totalCompletions ?: 0
                if (c1 != c2) {
                    c2.compareTo(c1)
                } else {
                    val s1 = meta1?.maxStreak ?: 0
                    val s2 = meta2?.maxStreak ?: 0
                    s2.compareTo(s1)
                }
            }
        }
        list
    }

    var activeTab by remember { mutableStateOf(0) } // 0 = Logger, 1 = Analytics
    var selectedAnalyticHabit by remember { mutableStateOf<Habit?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showTrashDialog by remember { mutableStateOf(false) }
    var showReadmeDialog by remember { mutableStateOf(false) }
    var showTimeProgressDialog by remember { mutableStateOf(false) }
    var isPomodoroFullScreen by remember { mutableStateOf(false) }
    val pomodoroState = remember { PomodoroState() }

    LaunchedEffect(pomodoroState.isRunning) {
        if (pomodoroState.isRunning) {
            pomodoroState.isFinished = false
            while (pomodoroState.timeRemainingSeconds > 0 && pomodoroState.isRunning) {
                delay(1000L)
                pomodoroState.timeRemainingSeconds -= 1
                if (pomodoroState.timeRemainingSeconds <= 0) {
                    pomodoroState.isRunning = false
                    pomodoroState.isFinished = true
                    try {
                        val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        val r = RingtoneManager.getRingtone(context, notification)
                        r.play()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    if (pomodoroState.mode == "Pomodoro") {
                        pomodoroState.pomodorosCompleted += 1
                        if (pomodoroState.pomodorosCompleted % 4 == 0) {
                            pomodoroState.changeMode("Long Break")
                        } else {
                            pomodoroState.changeMode("Short Break")
                        }
                    } else {
                        pomodoroState.changeMode("Pomodoro")
                    }
                }
            }
        }
    }

    var anchorDate by remember { mutableStateOf(Date()) }

    val sharedScrollState = rememberScrollState()

    val scrollEnabledDates = remember {
        val list = mutableListOf<Date>()
        val cal = Calendar.getInstance()
        // Align to Sunday of current week mathematically (locale independent)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        cal.add(Calendar.DAY_OF_YEAR, -(dayOfWeek - Calendar.SUNDAY))
        // Subtract 21 days (3 weeks) to start from Sunday 3 weeks ago
        cal.add(Calendar.DAY_OF_YEAR, -21)
        // Generate exactly 28 days (4 full weeks, Sun to Sat)
        for (i in 0 until 28) {
            list.add(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    LaunchedEffect(sharedScrollState.maxValue) {
        if (sharedScrollState.maxValue > 0) {
            sharedScrollState.scrollTo(sharedScrollState.maxValue)
        }
    }

    val weekDates = remember(anchorDate) {
        val list = mutableListOf<Date>()
        val cal = Calendar.getInstance()
        cal.time = anchorDate
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        cal.add(Calendar.DAY_OF_YEAR, -(dayOfWeek - Calendar.SUNDAY))
        for (i in 0 until 7) {
            list.add(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    // Selected Date Calendar state
    val selectedCalendar = remember(selectedDateStr) {
        val cal = Calendar.getInstance()
        try {
            cal.time = sdf.parse(selectedDateStr) ?: Date()
        } catch (e: Exception) {
            // ignore
        }
        cal
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Habit Loop",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Track progress & weekly dashboard",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.toggleTheme() },
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isDarkTheme) "Switch to Light Mode" else "Switch to Dark Mode",
                                tint = if (isDarkTheme) Color(0xFFFFCC00) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                try {
                                    BackupManager(context).dataChanged()
                                    Toast.makeText(context, "Google Cloud Sync Flagged! Handed backup job to Android background manager successfully.", Toast.LENGTH_LONG).show()
                                } catch (e: java.lang.Exception) {
                                    Toast.makeText(context, "Trigger Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("app_bar_sync_cloud_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Sync habits now to Google Cloud",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { showTimeProgressDialog = true },
                            modifier = Modifier.testTag("app_bar_time_progress_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassEmpty,
                                contentDescription = "Time Progress",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.testTag("backup_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu Options"
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("App Guidelines & README") },
                                    onClick = {
                                        showMenu = false
                                        showReadmeDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("Backup, Import & Export") },
                                    onClick = {
                                        showMenu = false
                                        showImportDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Trash & Recovery") },
                                    onClick = {
                                        showMenu = false
                                        showTrashDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("Export Human Report") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.exportReport(context)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (activeTab == 0) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("add_habit_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create New Habit")
                }
            }
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Daily Habits Logger") },
                    label = { Text("Track") },
                    modifier = Modifier.testTag("nav_track_tab")
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "History Month Calendar") },
                    label = { Text("Calendar") },
                    modifier = Modifier.testTag("nav_calendar_tab")
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Weekly & Monthly Dashboard") },
                    label = { Text("Dashboard") },
                    modifier = Modifier.testTag("nav_dashboard_tab")
                )
                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(Icons.Default.HourglassEmpty, contentDescription = "Time Log") },
                    label = { Text("Time Log") },
                    modifier = Modifier.testTag("nav_time_log_tab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (activeTab == 3) {
                // Time Log Tab logic
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimeProgressDialog(onDismiss = { /* do nothing, it's a page now */ }, isDialog = false)
                    PomodoroTimerView(
                        state = pomodoroState,
                        isFullScreen = false,
                        onFullScreenToggle = { isPomodoroFullScreen = true }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
                
                if (isPomodoroFullScreen) {
                    Dialog(
                        onDismissRequest = { isPomodoroFullScreen = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            PomodoroTimerView(
                                state = pomodoroState,
                                isFullScreen = true,
                                onFullScreenToggle = { isPomodoroFullScreen = false }
                            )
                        }
                    }
                }
            } else if (activeTab == 0) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isSmallScreen = maxWidth < 600.dp
                    val habitsWidth = if (isSmallScreen) 140.dp else 240.dp
                    val scrollableAreaWidth = maxWidth - habitsWidth
                    val daysPerWeek = if (isSmallScreen) 5 else 7
                    val dayCellWidth = remember(scrollableAreaWidth, daysPerWeek) { scrollableAreaWidth / daysPerWeek }

                    val filteredDates = scrollEnabledDates

                    Column(modifier = Modifier.fillMaxSize()) {
                        // LOGGER SCREEN - Slider Month & Year header with Navigation Arrows (Muted / Aesthetic)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Weekly Activity Grid",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Scroll left for previous weeks",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        // Filter and Sorting chips for interactive Logger screen controls
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = hideCompletedToday,
                                onClick = { viewModel.toggleHideCompletedToday() },
                                label = { Text("Hide Completed", style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = if (hideCompletedToday) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                modifier = Modifier.testTag("filter_hide_completed")
                            )
                            FilterChip(
                                selected = sortByBestHabit,
                                onClick = { sortByBestHabit = !sortByBestHabit },
                                label = { Text("Sort by Best Habit", style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = if (sortByBestHabit) {
                                    { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                modifier = Modifier.testTag("filter_sort_best")
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(habitsWidth)
                                    .padding(start = 16.dp)
                            ) {
                                Text(
                                    text = "HABITS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(sharedScrollState),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                filteredDates.forEach { date ->
                                    val isToday = remember(date) {
                                        val cal1 = Calendar.getInstance()
                                        val cal2 = Calendar.getInstance()
                                        cal2.time = date
                                        cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                                        cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(dayCellWidth)
                                    ) {
                                        Text(
                                            text = displayDayFormat.format(date).uppercase(Locale.US),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = displayNumFormat.format(date),
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                        if (activeHabits.isEmpty()) {
                            // Empty list state
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "No habits icon",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No active habits",
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Add a habit using the '+' button below to start tracking your daily loop.",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredActiveHabits) { habit ->
                                    val meta = analytics.find { it.habit.id == habit.id }
                                    
                                    HabitItemRow(
                                        habit = habit,
                                        scrollEnabledDates = filteredDates,
                                        sharedScrollState = sharedScrollState,
                                        dayCellWidth = dayCellWidth,
                                        habitsWidth = habitsWidth,
                                        allCompletions = allCompletions,
                                        currentStreak = meta?.currentStreak ?: 0,
                                        maxStreak = meta?.maxStreak ?: 0,
                                        masteryLevel = meta?.masteryLevel ?: 0,
                                        masteryLabel = meta?.masteryLabel ?: "Rookie",
                                        sdf = sdf,
                                        onToggleForDate = { dateStr ->
                                            viewModel.toggleHabitCompletionForDate(habit.id, dateStr)
                                        },
                                        onDelete = { viewModel.deleteHabit(habit.id) },
                                        onUpdate = { name, desc, target, color, freqAmount, freqPeriod ->
                                            viewModel.updateHabit(habit.id, name, desc, target, color, freqAmount, freqPeriod)
                                        },
                                        onMoveUp = { viewModel.moveHabit(habit.id, true) },
                                        onMoveDown = { viewModel.moveHabit(habit.id, false) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (activeTab == 1) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp)
                ) {
                    MonthCalendarView(
                        activeHabits = activeHabits,
                        allCompletions = allCompletions,
                        onToggleCompletion = { habitId, dateStr ->
                            viewModel.toggleHabitCompletionForDate(habitId, dateStr)
                        },
                        dateFormat = sdf
                    )
                }
            } else {
                // ANALYTICS & WEEKLY/MONTHLY DASHBOARD
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Category/Habit Selector Filter Chips Row at the Top
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedAnalyticHabit == null,
                                onClick = { selectedAnalyticHabit = null },
                                label = { Text("All Habits (Global)") },
                                modifier = Modifier.testTag("filter_chip_all_habits")
                            )
                        }
                        items(activeHabits) { habit ->
                            val baseColor = try { Color(android.graphics.Color.parseColor(habit.colorHex)) } catch (e: Exception) { Color(0xFF6200EE) }
                            FilterChip(
                                selected = selectedAnalyticHabit?.id == habit.id,
                                onClick = { selectedAnalyticHabit = habit },
                                label = { Text(habit.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = baseColor.copy(alpha = 0.15f),
                                    selectedLabelColor = baseColor,
                                    selectedLeadingIconColor = baseColor
                                ),
                                modifier = Modifier.testTag("filter_chip_habit_${habit.id}")
                            )
                        }
                    }

                    if (selectedAnalyticHabit == null) {
                        // RENDER AGGREGATE DASHBOARD
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Quick Stat summary
                            item {
                                WeeklyScoreCard(weeklyReports)
                            }

                            // Canvas-based Weekly Progress Trend Chart
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Weekly Status Score Trend",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Task completion score based on weekly goals",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )

                                        WeeklyTrendChart(weeklyReports)
                                    }
                                }
                            }

                            // Streaks & Leaderboard
                            item {
                                HabitStreaksPanel(analytics, onHabitSelected = { selectedAnalyticHabit = it })
                            }

                            // Month-by-month Summary Reports
                            item {
                                MonthlyDashboardPanel(monthlyReports)
                            }

                            // Export button
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Comprehensive Reports",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "Export detailed habit completions, weekly achievements, and monthly summaries.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Button(
                                            onClick = { viewModel.exportReport(context) },
                                            modifier = Modifier.testTag("export_report_footer")
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Export")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // RENDER HIGH-FIDELITY DETAILED COMPREHENSIVE HABIT ANALYSIS (Matching Screenshots!)
                        val habit = selectedAnalyticHabit!!
                        val baseColor = try { Color(android.graphics.Color.parseColor(habit.colorHex)) } catch (e: Exception) { Color(0xFF6200EE) }
                        
                        // Computations for this specific habit
                        val totalCompCount = remember(allCompletions, habit.id) { allCompletions.count { it.habitId == habit.id } }
                        
                        // 30 day score calculation
                        val completions30Count = remember(allCompletions, habit.id, anchorDate) {
                            val cal = Calendar.getInstance()
                            cal.time = anchorDate
                            val set30 = mutableSetOf<String>()
                            val localSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            for (i in 0 until 30) {
                                set30.add(localSdf.format(cal.time))
                                cal.add(Calendar.DAY_OF_YEAR, -1)
                            }
                            allCompletions.count { it.habitId == habit.id && set30.contains(it.dateString) }
                        }
                        val limit30Days = (habit.targetDaysPerWeek.toFloat() / 7f) * 30f
                        val monthScore = if (limit30Days > 0f) {
                            ((completions30Count.toFloat() / limit30Days) * 100f).coerceIn(0f, 100f).toInt()
                        } else 0

                        // Line graph score trend over past 6 weeks
                        val scoreHistory = remember(allCompletions, habit.id, anchorDate) {
                            val scores = mutableListOf<Pair<Int, Float>>()
                            val cal = Calendar.getInstance()
                            cal.time = anchorDate
                            val offset = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
                            cal.add(Calendar.DAY_OF_YEAR, -offset)
                            cal.add(Calendar.WEEK_OF_YEAR, -5)
                            val localSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val todayCal = Calendar.getInstance()
                            todayCal.time = anchorDate
                            val todayStr = localSdf.format(todayCal.time)
                            for (w in 0 until 6) {
                                var completed = 0
                                var daysPassed = 0
                                val weekNumber = cal.get(Calendar.WEEK_OF_YEAR)
                                for (d in 0 until 7) {
                                    val dStr = localSdf.format(cal.time)
                                    if (dStr <= todayStr) daysPassed++
                                    if (allCompletions.any { it.habitId == habit.id && it.dateString == dStr }) {
                                        completed++
                                    }
                                    cal.add(Calendar.DAY_OF_YEAR, 1)
                                }
                                val weekTarget = habit.targetDaysPerWeek.coerceAtLeast(1)
                                // Adjust target if it's the current incomplete week
                                val actualTarget = if (daysPassed < 7 && daysPassed > 0) {
                                    (weekTarget * (daysPassed / 7f)).coerceAtLeast(1f)
                                } else weekTarget.toFloat()
                                val percentage = (completed.toFloat() / actualTarget) * 100f
                                scores.add(Pair(weekNumber, percentage.coerceIn(0f, 100f)))
                            }
                            scores
                        }

                        // Bar chart completions over past 8 weeks
                        val weeklyCounts = remember(allCompletions, habit.id, anchorDate) {
                            val counts = mutableListOf<Pair<Int, Int>>()
                            val cal = Calendar.getInstance()
                            cal.time = anchorDate
                            val offset = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
                            cal.add(Calendar.DAY_OF_YEAR, -offset)
                            cal.add(Calendar.WEEK_OF_YEAR, -7)
                            val localSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            for (w in 0 until 8) {
                                var completed = 0
                                val weekNumber = cal.get(Calendar.WEEK_OF_YEAR)
                                for (d in 0 until 7) {
                                    val dStr = localSdf.format(cal.time)
                                    if (allCompletions.any { it.habitId == habit.id && it.dateString == dStr }) {
                                        completed++
                                    }
                                    cal.add(Calendar.DAY_OF_YEAR, 1)
                                }
                                counts.add(Pair(weekNumber, completed))
                            }
                            counts
                        }

                        // Grid Calendar calculations (columns = 20 weeks, rows = Sun-Sat)
                        val calendarGrid = remember(allCompletions, habit.id) {
                            val grid = mutableListOf<List<Pair<Date, Boolean>>>()
                            val cal = Calendar.getInstance()
                            cal.add(Calendar.WEEK_OF_YEAR, -19)
                            cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                            val localSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            for (w in 0 until 20) {
                                val week = mutableListOf<Pair<Date, Boolean>>()
                                for (d in 0 until 7) {
                                    val date = cal.time
                                    val dStr = localSdf.format(date)
                                    val completed = allCompletions.any { it.habitId == habit.id && it.dateString == dStr }
                                    week.add(Pair(date, completed))
                                    cal.add(Calendar.DAY_OF_YEAR, 1)
                                }
                                grid.add(week)
                            }
                            grid
                        }

                        // Best streaks calculations
                        val bestStreaks = remember(allCompletions, habit.id) {
                            val habitCompletions = allCompletions
                                .filter { it.habitId == habit.id }
                                .map { it.dateString }
                                .sorted()
                            if (habitCompletions.isEmpty()) return@remember emptyList<Triple<String, String, Int>>()
                            
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val displayDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
                            
                            val streaks = mutableListOf<Triple<String, String, Int>>()
                            var currentStartStr = habitCompletions[0]
                            var currentPrevStr = habitCompletions[0]
                            var count = 1
                            
                            for (i in 1 until habitCompletions.size) {
                                val currentStr = habitCompletions[i]
                                try {
                                    val d1 = dateFormat.parse(currentPrevStr)
                                    val d2 = dateFormat.parse(currentStr)
                                    if (d1 != null && d2 != null) {
                                        val diff = (d2.time - d1.time) / (1000 * 60 * 60 * 24)
                                        if (diff <= 1) {
                                            count++
                                            currentPrevStr = currentStr
                                        } else {
                                            val start = displayDateFormat.format(dateFormat.parse(currentStartStr)!!)
                                            val end = displayDateFormat.format(dateFormat.parse(currentPrevStr)!!)
                                            streaks.add(Triple(start, end, count))
                                            currentStartStr = currentStr
                                            currentPrevStr = currentStr
                                            count = 1
                                        }
                                    }
                                } catch (e: Exception) {}
                            }
                            try {
                                val start = displayDateFormat.format(dateFormat.parse(currentStartStr)!!)
                                val end = displayDateFormat.format(dateFormat.parse(currentPrevStr)!!)
                                streaks.add(Triple(start, end, count))
                            } catch (e: Exception) {}
                            
                            streaks.sortedByDescending { it.third }.take(3)
                        }

                        // Weekday check-ins count frequency distribution
                        val weekdayFrequency = remember(allCompletions, habit.id) {
                            val dayCount = IntArray(7)
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val cal = Calendar.getInstance()
                            allCompletions.filter { it.habitId == habit.id }.forEach { comp ->
                                try {
                                    val date = dateFormat.parse(comp.dateString)
                                    if (date != null) {
                                        cal.time = date
                                        val day = cal.get(Calendar.DAY_OF_WEEK) - 1
                                        if (day in 0..6) {
                                            dayCount[day]++
                                        }
                                    }
                                } catch (e: Exception) {}
                            }
                            dayCount
                        }

                        // Fully dynamic past 15-day and 30-day relative trend computations
                        val monthTrend = remember(allCompletions, habit.id) {
                            val localSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val cal = Calendar.getInstance()
                            val setRecent = mutableSetOf<String>()
                            for (i in 0 until 15) {
                                setRecent.add(localSdf.format(cal.time))
                                cal.add(Calendar.DAY_OF_YEAR, -1)
                            }
                            val setPast = mutableSetOf<String>()
                            for (i in 0 until 15) {
                                setPast.add(localSdf.format(cal.time))
                                cal.add(Calendar.DAY_OF_YEAR, -1)
                            }
                            val recentComps = allCompletions.count { it.habitId == habit.id && setRecent.contains(it.dateString) }
                            val pastComps = allCompletions.count { it.habitId == habit.id && setPast.contains(it.dateString) }
                            if (pastComps == 0) {
                                if (recentComps > 0) 100 else 0
                            } else {
                                (((recentComps - pastComps).toFloat() / pastComps.toFloat()) * 100).toInt()
                            }
                        }

                        val yearTrend = remember(allCompletions, habit.id) {
                            val localSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val cal = Calendar.getInstance()
                            val setRecent = mutableSetOf<String>()
                            for (i in 0 until 30) {
                                setRecent.add(localSdf.format(cal.time))
                                cal.add(Calendar.DAY_OF_YEAR, -1)
                            }
                            val setPast = mutableSetOf<String>()
                            for (i in 0 until 30) {
                                setPast.add(localSdf.format(cal.time))
                                cal.add(Calendar.DAY_OF_YEAR, -1)
                            }
                            val recentComps = allCompletions.count { it.habitId == habit.id && setRecent.contains(it.dateString) }
                            val pastComps = allCompletions.count { it.habitId == habit.id && setPast.contains(it.dateString) }
                            if (pastComps == 0) {
                                if (recentComps > 0) 100 else 0
                            } else {
                                (((recentComps - pastComps).toFloat() / pastComps.toFloat()) * 100).toInt()
                            }
                        }

                        // Gestures to swipe from one habit stats page to another
                        var accumulatedDrag by remember(habit.id) { mutableStateOf(0f) }
                        val detailSwipeModifier = Modifier.pointerInput(habit.id) {
                            detectHorizontalDragGestures(
                                onDragStart = { accumulatedDrag = 0f },
                                onDragEnd = {
                                    if (accumulatedDrag < -120f) {
                                        // Swipe Left -> Next
                                        val currentIndex = activeHabits.indexOfFirst { it.id == habit.id }
                                        if (currentIndex != -1 && activeHabits.isNotEmpty()) {
                                            val nextIndex = (currentIndex + 1) % activeHabits.size
                                            selectedAnalyticHabit = activeHabits[nextIndex]
                                        }
                                    } else if (accumulatedDrag > 120f) {
                                        // Swipe Right -> Prev
                                        val currentIndex = activeHabits.indexOfFirst { it.id == habit.id }
                                        if (currentIndex != -1 && activeHabits.isNotEmpty()) {
                                            val prevIndex = if (currentIndex - 1 < 0) activeHabits.size - 1 else currentIndex - 1
                                            selectedAnalyticHabit = activeHabits[prevIndex]
                                        }
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    accumulatedDrag += dragAmount
                                    change.consume()
                                }
                            )
                        }

                        // Rendering the details screen content
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(detailSwipeModifier)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            // Carousel Header tab navigation buttons row
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            val currentIndex = activeHabits.indexOfFirst { it.id == habit.id }
                                            if (currentIndex != -1 && activeHabits.isNotEmpty()) {
                                                val prevIndex = if (currentIndex - 1 < 0) activeHabits.size - 1 else currentIndex - 1
                                                selectedAnalyticHabit = activeHabits[prevIndex]
                                            }
                                        },
                                        modifier = Modifier.testTag("prev_habit_stats")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowLeft, 
                                            contentDescription = "Previous Habit Stats",
                                            tint = baseColor
                                        )
                                    }
                                    
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = habit.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = baseColor,
                                            textAlign = TextAlign.Center
                                        )

                                        val meta = analytics.find { it.habit.id == habit.id }
                                        val masteryLevel = meta?.masteryLevel ?: 0
                                        val masteryLabel = meta?.masteryLabel ?: "Rookie"

                                        Badge(
                                            containerColor = baseColor.copy(alpha = 0.15f),
                                            contentColor = baseColor,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                        ) {
                                            Text(
                                                text = "Mastery Level $masteryLevel • $masteryLabel",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }

                                        Text(
                                            text = "Swipe horizontally or use arrows to flip",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.61f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = {
                                            val currentIndex = activeHabits.indexOfFirst { it.id == habit.id }
                                            if (currentIndex != -1 && activeHabits.isNotEmpty()) {
                                                val nextIndex = (currentIndex + 1) % activeHabits.size
                                                selectedAnalyticHabit = activeHabits[nextIndex]
                                            }
                                        },
                                        modifier = Modifier.testTag("next_habit_stats")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowRight, 
                                            contentDescription = "Next Habit Stats",
                                            tint = baseColor
                                        )
                                    }
                                }
                            }

                            // 1. OVERVIEW SCREEN
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            text = "Overview",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = baseColor
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Circular Score
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                CompactProgressCircle(
                                                    scorePercentage = monthScore.toFloat(),
                                                    color = baseColor,
                                                    modifier = Modifier.size(44.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = "$monthScore%",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = "Score",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            
                                            // Trends & Totals
                                            val monthTrendColor = if (monthTrend > 0) baseColor else if (monthTrend < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                            val monthTrendText = if (monthTrend > 0) "+$monthTrend%" else "$monthTrend%"
                                            
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.8f)) {
                                                Text(
                                                    text = monthTrendText,
                                                    color = monthTrendColor,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    text = "Month",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            val yearTrendColor = if (yearTrend > 0) baseColor else if (yearTrend < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                            val yearTrendText = if (yearTrend > 0) "+$yearTrend%" else "$yearTrend%"

                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.8f)) {
                                                Text(
                                                    text = yearTrendText,
                                                    color = yearTrendColor,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    text = "Year",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "$totalCompCount",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    text = "Total Loops",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. SCORE SCREEN BLOCK
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Score",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = baseColor
                                            )
                                            Text(
                                                text = "6 Weeks • Trend",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        ScoreTrendLineChart(scores = scoreHistory, color = baseColor)
                                    }
                                }
                            }

                            // 3. HISTORY SCREEN BLOCK
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "History",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = baseColor
                                            )
                                            Text(
                                                text = "8 Weeks • Count",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        CustomHistoryBarChart(counts = weeklyCounts, color = baseColor)
                                    }
                                }
                            }

                            // 4. CALENDAR SCREEN BLOCK
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Calendar",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = baseColor,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                        TabletCalendarGrid(
                                            calendarGrid = calendarGrid,
                                            baseColor = baseColor,
                                            onTileToggle = { date ->
                                                viewModel.toggleHabitCompletionForDate(habit.id, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date))
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = { activeTab = 0 }) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("EDIT COMPLETIONS")
                                            }
                                        }
                                    }
                                }
                            }

                            // 5. BEST STREAKS SCREEN BLOCK
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Best Streaks",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = baseColor,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                        BestStreaksList(bestStreaks = bestStreaks, baseColor = baseColor)
                                    }
                                }
                            }

                            // 6. FREQUENCY SCREEN BLOCK
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Frequency",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = baseColor,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                        WeekdayFrequencyDistribution(weekdayFrequency = weekdayFrequency, baseColor = baseColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddHabitDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, desc, targetDays, color, freqAmount, freqPeriod ->
                viewModel.addNewHabit(name, desc, targetDays, color, freqAmount, freqPeriod)
                showAddDialog = false
                Toast.makeText(context, "Habit created successfully!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showReadmeDialog) {
        GlobalReadmeDialog(onDismiss = { showReadmeDialog = false })
    }

    if (showTimeProgressDialog) {
        TimeProgressDialog(onDismiss = { showTimeProgressDialog = false })
    }

    if (showImportDialog) {
        BackupImportExportDialog(
            onDismiss = { showImportDialog = false },
            currentJsonBackup = viewModel.getBackupJson(),
            onImport = { json ->
                viewModel.importBackupJson(json) { success ->
                    showImportDialog = false
                    if (success) {
                        Toast.makeText(context, "Data backup imported successfully!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to parse JSON backup. Standard structure required.", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onFileExportTrigger = {
                val dateFormat = SimpleDateFormat("yyyy_MM_dd", Locale.US)
                val dateStr = dateFormat.format(Date())
                exportLauncher.launch("habit_backup_$dateStr.json")
            },
            onFileImportTrigger = {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            viewModel = viewModel
        )
    }

    if (showTrashDialog) {
        TrashBinDialog(
            archivedHabits = archivedHabits,
            onRestore = { id ->
                viewModel.restoreHabit(id)
                Toast.makeText(context, "Habit restored successfully!", Toast.LENGTH_SHORT).show()
            },
            onPermanentDelete = { id ->
                viewModel.permanentlyDeleteHabit(id)
                Toast.makeText(context, "Habit permanently deleted.", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showTrashDialog = false }
        )
    }
}

@Composable
fun CompactProgressCircle(
    scorePercentage: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidthPx = 3.dp.toPx()
        val canvasSize = size.minDimension
        val radius = (canvasSize - strokeWidthPx) / 2f
        
        // Outer light track centered perfectly
        drawCircle(
            color = color.copy(alpha = 0.15f),
            radius = radius,
            center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthPx)
        )
        // Solid or Sweep arc centered perfectly
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * (scorePercentage.coerceIn(0f, 100f) / 100f),
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(
                (size.width - radius * 2) / 2f,
                (size.height - radius * 2) / 2f
            ),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidthPx,
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
fun HabitDetailDialog(
    habit: Habit,
    allCompletions: List<com.example.data.HabitCompletion>,
    currentStreak: Int,
    maxStreak: Int,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (name: String, description: String, targetDays: Int, colorHex: String, freqAmount: Int, freqPeriod: String) -> Unit
) {
    val dateSdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val displayDateSdf = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }
    val monthSdf = remember { SimpleDateFormat("yyyy-MM", Locale.US) }
    val monthLabelSdf = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }

    val baseColor = remember(habit.colorHex) {
        try { Color(android.graphics.Color.parseColor(habit.colorHex)) } catch (e: Exception) { Color(0xFF6200EE) }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        EditHabitDialog(
            habit = habit,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, desc, target, color, freqAmount, freqPeriod ->
                onUpdate(name, desc, target, color, freqAmount, freqPeriod)
                showEditDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "Move to Trash?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text("Are you sure you want to move the habit '${habit.name}' to the Trash? All checkmark history is preserved and you can recover it any time from the top-right options menu.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val myCompletions = remember(allCompletions, habit.id) {
        allCompletions.filter { it.habitId == habit.id }
    }
    val completedDates = remember(myCompletions) {
        myCompletions.map { it.dateString }.toSet()
    }

    // Calculations: Overall progress
    val totalCompletions = myCompletions.size
    val creationDate = remember(habit.createdAt) { Date(habit.createdAt) }
    val daysSinceCreated = remember(habit.createdAt) {
        val elapsedMs = System.currentTimeMillis() - habit.createdAt
        maxOf(1, (elapsedMs / (1000 * 60 * 60 * 24)).toInt())
    }

    // Calculations: Current and Last 2 Weeks (3 weeks in total)
    val weeksData = remember(completedDates) {
        val list = mutableListOf<Pair<String, List<Boolean>>>()
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        // Find current Monday
        val curDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val diff = if (curDayOfWeek == Calendar.SUNDAY) 6 else curDayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_YEAR, -diff)
        
        for (w in 0 until 3) {
            val weekMonday = cal.time
            val weekMondayStr = displayDateSdf.format(weekMonday)
            val weekCompletions = mutableListOf<Boolean>()
            
            // Collect Mon-Sun statuses
            val tempCal = Calendar.getInstance()
            tempCal.time = weekMonday
            for (d in 0 until 7) {
                val dayStr = dateSdf.format(tempCal.time)
                weekCompletions.add(completedDates.contains(dayStr))
                tempCal.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            val tempCalForWeekNumber = Calendar.getInstance()
            tempCalForWeekNumber.time = weekMonday
            val weekNum = tempCalForWeekNumber.get(Calendar.WEEK_OF_YEAR)
            val label = if (w == 0) "Current Week" else "Week $weekNum"
            list.add(Pair(label, weekCompletions))
            cal.add(Calendar.DAY_OF_YEAR, -7)
        }
        list
    }

    val threeWeeksCompletionsCount = remember(weeksData) {
        weeksData.sumOf { it.second.count { completed -> completed } }
    }
    val targetOfThreeWeeks = remember(habit.targetDaysPerWeek) {
        (3 * habit.targetDaysPerWeek).coerceAtLeast(1)
    }
    val consistencyRate = remember(threeWeeksCompletionsCount, targetOfThreeWeeks) {
        ((threeWeeksCompletionsCount.toFloat() / targetOfThreeWeeks.toFloat()) * 100f).coerceIn(0f, 100f)
    }

    val levelInfo = remember(consistencyRate) {
        when {
            consistencyRate >= 90f -> Pair("Grandmaster", "Legendary dedication! Flawless flow.")
            consistencyRate >= 75f -> Pair("Gold Elite", "Excellent habit strength. Keep it up!")
            consistencyRate >= 50f -> Pair("Consistent Builder", "Solid momentum. You are building real change.")
            else -> Pair("Rookie Companion", "Fresh start! Brick by brick, a habit is built.")
        }
    }

    // Calculations: Last 3 Months
    val monthsData = remember(completedDates) {
        val list = mutableListOf<Pair<String, Int>>()
        val cal = Calendar.getInstance()
        for (m in 0 until 3) {
            val monthKey = monthSdf.format(cal.time)
            val label = monthLabelSdf.format(cal.time)
            val completionsCount = completedDates.count { it.startsWith(monthKey) }
            list.add(Pair(label, completionsCount))
            cal.add(Calendar.MONTH, -1)
        }
        list
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Segment
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(baseColor)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = habit.name,
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Created: ${displayDateSdf.format(creationDate)} (${daysSinceCreated}d ago)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Description
                if (habit.description.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = habit.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Streaks & Goals Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Habit Target",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${habit.targetDaysPerWeek} days / week",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = baseColor
                                )
                            }
                            Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Current Streak",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$currentStreak days 🔥",
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = baseColor
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Best Streak",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$maxStreak days 🏆",
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Detailed Weekly Stats breakdown
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "WEEKLY REVIEW (CURRENT & LAST 2 WEEKS)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        weeksData.forEach { (weekLabel, statuses) ->
                            val weeklyComplete = statuses.count { it }
                            val targetMet = weeklyComplete >= habit.targetDaysPerWeek
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = weekLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "$weeklyComplete / ${habit.targetDaysPerWeek} days",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (targetMet) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    // Row of 7 tiny dot indicators mimicking a daily check sheet with day labels above them
                                    val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        statuses.forEachIndexed { index, isDone ->
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = dayLetters.getOrElse(index) { "" },
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(15.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isDone) baseColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Detailed Monthly Stats breakdown
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "MONTHLY REVIEW Summary",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        monthsData.forEach { (monthLabel, completionsCount) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = monthLabel,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    modifier = Modifier.width(100.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val progress = (completionsCount.toFloat() / 31f).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = progress,
                                    color = baseColor,
                                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                                Text(
                                    text = "$completionsCount days",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(55.dp)
                                )
                            }
                        }
                    }
                }

                // Overall Mastery Tier Section
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = baseColor.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, baseColor.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🏆 Mastery: ${levelInfo.first}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = baseColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = levelInfo.second,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${consistencyRate.toInt()}%",
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = baseColor
                                )
                                Text(
                                    text = "Last 3 Weeks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Bottom Buttons (Close & Delete & Edit)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showEditDialog = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = baseColor)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit")
                            }
                            TextButton(
                                onClick = { showDeleteConfirm = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete")
                            }
                        }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = baseColor)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackupImportExportDialog(
    onDismiss: () -> Unit,
    currentJsonBackup: String,
    onImport: (String) -> Unit,
    onFileExportTrigger: () -> Unit,
    onFileImportTrigger: () -> Unit,
    viewModel: HabitViewModel
) {
    var selectedSubTab by remember { mutableStateOf(0) } // 0 = Export, 1 = Import, 2 = Google Backup
    var backupText by remember { mutableStateOf("") }

    val isSqlGuideExpanded by remember { mutableStateOf(false) }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    
    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
    }
    val versionName = packageInfo?.versionName ?: "1.1.0"
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text(
                    text = "Backup & Cloud Sync", 
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Application Version: v$versionName (Build 2)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Inline toggle tab headers with Icons (4 Options now)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        Triple("Export", Icons.Default.FileDownload, 0),
                        Triple("Import", Icons.Default.FileUpload, 1),
                        Triple("Google", Icons.Default.Cloud, 2)
                    )
                    tabs.forEach { (title, icon, idx) ->
                        Button(
                            onClick = { selectedSubTab = idx },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedSubTab == idx) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (selectedSubTab == idx) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(title, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                // Scrollable content pane inside dialogue text
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedSubTab == 0) {
                        // EXPORT CENTER
                        Text(
                            text = "Your daily loops, habits, and completions can be backed up as text or saved directly to a file. Copy/Paste is ideal for managing backups directly inside a web browser.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(currentJsonBackup))
                                    Toast.makeText(context, "Copied backup JSON to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy JSON")
                            }
                            
                            Button(
                                onClick = {
                                    onFileExportTrigger()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                modifier = Modifier.weight(1.0f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save File")
                            }
                        }
                        
                        OutlinedTextField(
                            value = currentJsonBackup,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Verify Backup Payload") },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else if (selectedSubTab == 1) {
                        // IMPORT CENTER
                        Text(
                            text = "Paste your previously exported loop backup below or select a JSON backup file to restore all your habits instantly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    val clip = clipboardManager.getText()
                                    if (clip != null) {
                                        backupText = clip.text
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                modifier = Modifier.weight(1.1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste Clip")
                            }
                            
                            Button(
                                onClick = {
                                    onDismiss()
                                    onFileImportTrigger()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                modifier = Modifier.weight(1.1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose File")
                            }
                        }
                        
                        OutlinedTextField(
                            value = backupText,
                            onValueChange = { backupText = it },
                            label = { Text("Paste JSON Backup Here") },
                            placeholder = { Text("{\n  \"habits\": [...],\n  \"completions\": [...]\n}") },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else if (selectedSubTab == 2) {
                        // GOOGLE CLOUD BACKUP
                        Text(
                            text = "Google Auto Backup keeps your habits synced. This app schedules alerts every 3 hours in the background to flag and sync your latest data to Google Cloud, allowing seamless recovery when setting up your tablet with the same Google Account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "3-Hour Automated Sync Worker:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text("• ⚡ Background Loop: Active (Triggers every 3 hours)", style = MaterialTheme.typography.bodySmall)
                                Text("• ⏰ System Cycle: Android uploads files to your Google drive account when device is charging, on Wi-Fi, and idle.", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "How to Select your Google Account:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text("Android backs up applications to the central Google backup account chosen in your system settings. To use a different Google account for your backup, open system settings below and change your active backup account.", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Button(
                            onClick = {
                                try {
                                    BackupManager(context).dataChanged()
                                    Toast.makeText(context, "Data flagged! Android will queue Google Cloud Backup to run on the next cycle.", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Registration failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("sync_cloud_backup_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync/Flag Cloud Backup Now")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_PRIVACY_SETTINGS)
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            context.startActivity(Intent("android.settings.BACKUP_AND_RESTORE_SETTINGS"))
                                        } catch (e2: Exception) {
                                            Toast.makeText(context, "Please open Android Settings > Google > Backup to check state.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("open_backup_settings_button"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Backup Settings", style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Google Sync settings not accessible directly. Manage via system Accounts.", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("open_sync_settings_button"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.ManageAccounts, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Select Account / Sync", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedSubTab == 1) {
                Button(
                    onClick = {
                        if (backupText.trim().isNotEmpty()) {
                            onImport(backupText)
                        }
                    },
                    enabled = backupText.trim().isNotEmpty(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore Backup")
                }
            } else {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (selectedSubTab == 1) "Cancel" else "Close")
            }
        }
    )
}

@Composable
fun TrashBinDialog(
    archivedHabits: List<Habit>,
    onRestore: (Int) -> Unit,
    onPermanentDelete: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var habitToDeletePermanently by remember { mutableStateOf<Habit?>(null) }

    if (habitToDeletePermanently != null) {
        AlertDialog(
            onDismissRequest = { habitToDeletePermanently = null },
            title = {
                Text(
                    text = "Permanently Delete Habit?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently erase '${habitToDeletePermanently?.name}'? This action is absolutely irreversible and will permanently delete all its logging history."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        habitToDeletePermanently?.let { onPermanentDelete(it.id) }
                        habitToDeletePermanently = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Permanently Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { habitToDeletePermanently = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Trash Recovery",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close Trash")
                    }
                }

                Text(
                    text = "Accidentally deleted a habit? You can restore it and retrieve all its history below instantly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Divider(thickness = 0.5.dp)

                if (archivedHabits.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Your Trash is empty",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(archivedHabits) { habit ->
                            val baseColor = try { Color(android.graphics.Color.parseColor(habit.colorHex)) } catch (e: Exception) { Color(0xFF6200EE) }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(baseColor)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = habit.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (habit.description.isNotEmpty()) {
                                                Text(
                                                    text = habit.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { onRestore(habit.id) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Restore habit",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = { habitToDeletePermanently = habit },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Permanently Delete",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun HabitItemRow(
    habit: Habit,
    scrollEnabledDates: List<Date>,
    sharedScrollState: androidx.compose.foundation.ScrollState,
    dayCellWidth: androidx.compose.ui.unit.Dp,
    habitsWidth: androidx.compose.ui.unit.Dp = 240.dp,
    allCompletions: List<com.example.data.HabitCompletion>,
    currentStreak: Int,
    maxStreak: Int,
    masteryLevel: Int,
    masteryLabel: String,
    sdf: SimpleDateFormat,
    onToggleForDate: (String) -> Unit,
    onDelete: () -> Unit,
    onUpdate: (name: String, description: String, targetDays: Int, colorHex: String, freqAmount: Int, freqPeriod: String) -> Unit,
    onMoveUp: () -> Unit,                // New
    onMoveDown: () -> Unit               // New
) {
    var showDetailDialog by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    val baseColor = remember(habit.colorHex) {
        try { Color(android.graphics.Color.parseColor(habit.colorHex)) } catch (e: Exception) { Color(0xFF6200EE) }
    }

    // Set of complete date strings for quick lookup
    val completedDateStrings = remember(allCompletions, habit.id) {
        allCompletions.filter { it.habitId == habit.id }.map { it.dateString }.toSet()
    }

    // Dynamic 30 day history score
    val completionsLast30Count = remember(allCompletions, habit.id) {
        val cal = Calendar.getInstance()
        val recentDates = mutableSetOf<String>()
        val localSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        for (i in 0 until 30) {
            recentDates.add(localSdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        allCompletions.count { it.habitId == habit.id && recentDates.contains(it.dateString) }
    }
    val target30Comps = (habit.targetDaysPerWeek.toFloat() / 7f) * 30f
    val scorePercentage = if (target30Comps > 0f) {
        ((completionsLast30Count.toFloat() / target30Comps) * 100f).coerceIn(10f, 100f)
    } else 0f

    if (showDetailDialog) {
        HabitDetailDialog(
            habit = habit,
            allCompletions = allCompletions,
            currentStreak = currentStreak,
            maxStreak = maxStreak,
            onDismiss = { showDetailDialog = false },
            onDelete = {
                onDelete()
                showDetailDialog = false
            },
            onUpdate = { name, desc, target, color, freqAmount, freqPeriod ->
                onUpdate(name, desc, target, color, freqAmount, freqPeriod)
                showDetailDialog = false
            }
        )
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("habit_card_${habit.id}")
                .clickable { showDetailDialog = true }
                .graphicsLayer {
                    if (isDragging) {
                        scaleX = 1.04f
                        scaleY = 1.04f
                        shadowElevation = 8.dp.toPx()
                        translationY = dragOffset
                    }
                }
                .pointerInput(habit.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            isDragging = true
                            dragOffset = 0f
                        },
                        onDragEnd = {
                            isDragging = false
                            if (dragOffset > 100f) {
                                onMoveDown()
                            } else if (dragOffset < -100f) {
                                onMoveUp()
                            }
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            isDragging = false
                            dragOffset = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.y
                        }
                    )
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left progress ring and Habit Name column (FROZEN - habitsWidth)
            Row(
                modifier = Modifier
                    .width(habitsWidth)
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactProgressCircle(
                    scorePercentage = scorePercentage,
                    color = baseColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentStreak > 0) {
                            Text(
                                text = "🔥 ${currentStreak}d",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = baseColor,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "🎯 ${habit.targetDaysPerWeek}d",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Lv $masteryLevel: $masteryLabel",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(sharedScrollState),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                scrollEnabledDates.forEach { date ->
                    val dateStr = sdf.format(date)
                    val isCompleted = completedDateStrings.contains(dateStr)

                    // Bouncy dynamic scale animation when clicked/updated
                    val scale by animateFloatAsState(
                        targetValue = if (isCompleted) 1.2f else 0.85f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tick_scale"
                    )

                    // Smooth fade-in of the color checkmark/cross
                    val alpha by animateFloatAsState(
                        targetValue = if (isCompleted) 1.0f else 0.7f,
                        animationSpec = tween(durationMillis = 180),
                        label = "tick_alpha"
                    )

                    // Click trigger to burst confetti only when completed via click
                    var clickTrigger by remember { mutableStateOf(0) }
                    
                    // Localized confetti particles state
                    val cellParticles = remember { mutableStateListOf<Particle>() }
                    val progressVal = remember { androidx.compose.animation.core.Animatable(0f) }
                    
                    LaunchedEffect(clickTrigger) {
                        if (clickTrigger > 0) {
                            cellParticles.clear()
                            val random = java.util.Random()
                            for (i in 0 until 12) {
                                val angle = random.nextFloat() * 2 * Math.PI
                                val speed = 1.8f + random.nextFloat() * 3.8f
                                cellParticles.add(
                                    Particle(
                                        x = 0f,
                                        y = 0f,
                                        speedX = (Math.cos(angle) * speed).toFloat(),
                                        speedY = (Math.sin(angle) * speed).toFloat(),
                                        color = baseColor,
                                        rotation = random.nextFloat() * 360f,
                                        rotationSpeed = -15f + random.nextFloat() * 30f,
                                        alpha = 1.0f,
                                        size = 8f + random.nextFloat() * 12f
                                    )
                                )
                            }
                            progressVal.snapTo(0f)
                            progressVal.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 550, easing = androidx.compose.animation.core.LinearEasing)
                            )
                            cellParticles.clear()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(dayCellWidth)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { 
                                if (!isCompleted) {
                                    clickTrigger++
                                }
                                onToggleForDate(dateStr) 
                            }
                            .testTag("day_cell_${habit.id}_$dateStr"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed on $dateStr",
                                tint = baseColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        alpha = alpha
                                    )
                             )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Clear, // Soft Cross '✖'
                                contentDescription = "Not completed on $dateStr",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .size(13.dp)
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        alpha = alpha
                                    )
                            )
                        }

                        // Localized burst Canvas rendering
                        if (cellParticles.isNotEmpty() && progressVal.value < 1f) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val t = progressVal.value
                                cellParticles.forEach { p ->
                                    val currentX = p.speedX * t * 45f
                                    val currentY = p.speedY * t * 45f + 0.15f * (t * 45f) * (t * 45f)
                                    val currentAlpha = (1f - t).coerceIn(0f, 1f)
                                    val currentRotation = p.rotation + p.rotationSpeed * t * 45f
                                    if (currentAlpha > 0f) {
                                        rotate(currentRotation, pivot = androidx.compose.ui.geometry.Offset(center.x + currentX, center.y + currentY)) {
                                            drawRect(
                                                color = p.color.copy(alpha = currentAlpha),
                                                topLeft = androidx.compose.ui.geometry.Offset(center.x + currentX - p.size / 2, center.y + currentY - p.size / 2),
                                                size = androidx.compose.ui.geometry.Size(p.size, p.size * 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
        }
        Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun WeeklyScoreCard(weeklyReports: List<WeeklyReport>) {
    val thisWeek = weeklyReports.firstOrNull() ?: WeeklyReport("", "This Week", 0, 70, 0f)
    val scoreStr = String.format(Locale.US, "%.1f", thisWeek.scorePercentage)

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weekly_score_card")
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Weekly Review Score",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${thisWeek.completedCount} / ${thisWeek.targetCount} tasks",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "You've fulfilled $scoreStr% of your scheduled habit targets for ${thisWeek.label}!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                val lastWeek = weeklyReports.getOrNull(1)
                if (lastWeek != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Last week completion: ${String.format(Locale.US, "%.1f", lastWeek.scorePercentage)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Big circular progress score
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp)
            ) {
                CircularProgressIndicator(
                    progress = thisWeek.scorePercentage / 100f,
                    strokeWidth = 8.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = "$scoreStr%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun WeeklyTrendChart(reports: List<WeeklyReport>) {
    // We reverse reports so that past weeks render left to right, ending in "this week" on the far right.
    val chronologicalReports = remember(reports) { reports.take(6).reversed() }

    if (chronologicalReports.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp), 
            contentAlignment = Alignment.Center
        ) {
            Text("Insufficient tracking history")
        }
        return
    }

    val chartLineColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val width = size.width
            val height = size.height

            val yPaddingBottom = 30.dp.toPx()
            val yPaddingTop = 15.dp.toPx()
            val xPaddingLeft = 40.dp.toPx()
            val xPaddingRight = 15.dp.toPx()

            val usableWidth = width - xPaddingLeft - xPaddingRight
            val usableHeight = height - yPaddingBottom - yPaddingTop

            // Draw horizontal Grid Reference Lines
            val levels = listOf(0f, 0.5f, 1f)
            levels.forEach { lv ->
                val y = yPaddingTop + usableHeight * (1f - lv)
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.4f),
                    start = Offset(xPaddingLeft, y),
                    end = Offset(width - xPaddingRight, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw data points
            val pointCount = chronologicalReports.size
            val points: List<Offset> = chronologicalReports.mapIndexed { idx, rep ->
                val x = xPaddingLeft + (idx.toFloat() / (pointCount - 1).coerceAtLeast(1)) * usableWidth
                val fraction = (rep.scorePercentage / 100f).coerceIn(0f, 1f)
                val y = yPaddingTop + usableHeight * (1f - fraction)
                Offset(x, y)
            }

            // Draw lines connecting points
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = chartLineColor,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Draw gradient fill under line
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(points.first().x, yPaddingTop + usableHeight)
                points.forEach { pt -> lineTo(pt.x, pt.y) }
                lineTo(points.last().x, yPaddingTop + usableHeight)
                close()
            }
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(chartLineColor.copy(alpha = 0.3f), Color.Transparent),
                    startY = yPaddingTop,
                    endY = yPaddingTop + usableHeight
                )
            )

            // Draw individual data dots & value labels above them
            points.forEachIndexed { idx, pt ->
                drawCircle(
                    color = chartLineColor,
                    radius = 5.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    center = pt
                )
            }
        }
        
        // Y-axis Labels
        Text("100%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.offset(x = 4.dp, y = 7.dp))
        Text("50%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.offset(x = 4.dp, y = 74.5.dp))
        Text("0%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.offset(x = 4.dp, y = 142.dp))
    }

    // Horizontal Labels
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val chronologicalReports = reports.take(6).reversed()
        chronologicalReports.forEach { rep ->
            Text(
                text = rep.label,
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun GlobalReadmeDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = "Habit Loop README",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Habit Loop is an offline-first privacy focused habit tracker app that helps you build reliable routines over time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Mastery Levels Guide",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "The mastery level of your habit is purely based on the number of non-archived completions you have accrued over its total lifetime. The more consistently you complete the task, the faster you rank up.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MasteryRow(level = 0, label = "Rookie", desc = "0 to 6 completions")
                        MasteryRow(level = 1, label = "Apprentice", desc = "7 to 20 completions")
                        MasteryRow(level = 2, label = "Practitioner", desc = "21 to 44 completions")
                        MasteryRow(level = 3, label = "Expert", desc = "45 to 89 completions")
                        MasteryRow(level = 4, label = "Master", desc = "90 to 179 completions")
                        MasteryRow(level = 5, label = "Legend", desc = "180+ completions")
                    }

                    Text(
                        text = "Dashboard & Analytics",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "1. Active Tab: Here you can check off your habits for any day within the last 3 weeks. Tap the left/right arrows to slide between weeks.\n\n" +
                               "2. Analytics Tab: Gives you deep statistical insight into your streaks and performance.\n\n" +
                               "3. Detailed Stats: To see deep specific stats like Heatmaps, 6-Week Score Trends, or 8-Week histories, tap specifically on a habit's Streak Card in the Analytics tab.\n\n" +
                               "4. Color/Frequency Customization: Edit any habit to pick custom display colors via color swatches or hex code, and define weekly occurrence goals.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Awesome, got it!")
                    }
                }
            }
        }
    }
}

@Composable
fun MasteryInfoDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Mastery Levels Guide",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "The mastery level of your habit is purely based on the number of non-archived completions you have accrued over its total lifetime. The more consistently you complete the task, the faster you rank up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MasteryRow(level = 0, label = "Rookie", desc = "0 to 6 completions")
                    MasteryRow(level = 1, label = "Apprentice", desc = "7 to 20 completions")
                    MasteryRow(level = 2, label = "Practitioner", desc = "21 to 44 completions")
                    MasteryRow(level = 3, label = "Expert", desc = "45 to 89 completions")
                    MasteryRow(level = 4, label = "Master", desc = "90 to 179 completions")
                    MasteryRow(level = 5, label = "Legend", desc = "180+ completions")
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun MasteryRow(level: Int, label: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Lv$level", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Column {
            Text(text = label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HabitStreaksPanel(
    analytics: List<HabitAnalytics>,
    onHabitSelected: (Habit) -> Unit
) {
    var showMasteryInfo by remember { mutableStateOf(false) }

    if (showMasteryInfo) {
        MasteryInfoDialog(onDismiss = { showMasteryInfo = false })
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Habit Streaks & Goals",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { showMasteryInfo = true }, modifier = Modifier.size(24.dp)) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = "Mastery Level Info", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                text = "Click any habit below to dive deep into custom goals & streaks stats",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            analytics.forEach { item ->
                val baseColor = remember(item.habit.colorHex) {
                    try { Color(android.graphics.Color.parseColor(item.habit.colorHex)) } catch (e: Exception) { Color(0xFF6200EE) }
                }

                val completionsCount = item.completionsThisMonth.size
                val targetPossible = (item.habit.targetDaysPerWeek.toFloat() / 7f) * 28f
                val completionRatio = if (targetPossible > 0f) {
                    (completionsCount.toFloat() / targetPossible).coerceIn(0.1f, 1f)
                } else 0.2f

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onHabitSelected(item.habit) }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(baseColor)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = item.habit.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Badge(
                                containerColor = baseColor.copy(alpha = 0.15f),
                                contentColor = baseColor
                            ) {
                                Text(
                                    text = "${item.currentStreak}d Streak 🔥",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Badge(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ) {
                                Text(
                                    text = "Max ${item.maxStreak}d 🏆",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Colored Progress Strength indicator matching the screenshot style analytics
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = completionRatio,
                            color = baseColor,
                            trackColor = baseColor.copy(alpha = 0.1f),
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Text(
                            text = "${(completionRatio * 100).toInt()}% strength",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun MonthlyDashboardPanel(monthlyReports: List<MonthlyReport>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Monthly Completion Review",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Aggregate habit statistics and active density per month",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            monthlyReports.forEach { report ->
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = report.label,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${report.completedCount} completes (${report.daysInMonthWithAtLeastOneCompletion} track days)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val progress = (report.completedCount.toFloat() / report.totalPossibleCompletions.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = progress,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun EditHabitDialog(
    habit: Habit,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, targetDays: Int, colorHex: String, freqAmount: Int, freqPeriod: String) -> Unit
) {
    var name by remember { mutableStateOf(habit.name) }
    var description by remember { mutableStateOf(habit.description) }
    var targetDays by remember { mutableStateOf(habit.targetDaysPerWeek) }
    var freqAmount by remember { mutableStateOf(habit.frequencyAmount) }
    var freqPeriod by remember { mutableStateOf(habit.frequencyPeriod) }
    var customHexInput by remember { mutableStateOf(habit.colorHex) }

    // Wide range of colors including vibrant reds, greens, blues, oranges, purples, etc.
    val colorSwatches = listOf(
        Pair("Ocean Wave", "#FF0EA5E9"),   // Modern Electric Blue
        Pair("Emerald Forest", "#FF10B981"), // Fresh Vibrant Green
        Pair("Sunset Peach", "#FFF97316"),   // Neon Sunset Orange
        Pair("Rose Crimson", "#FFF43F5E"),   // Premium Rose
        Pair("Royal Purple", "#FF8B5CF6"),   // Vivid Purple
        Pair("Mint Breeze", "#FF14B8A6"),    // Modern Teal
        Pair("Electric Lemon", "#FFEAB308"), // Luminous Gold Amber
        Pair("Sky Blue", "#FF3B82F6"),       // Deep Royal Blue
        Pair("Wild Lavender", "#FFA855F7"),  // Electric Lavender
        Pair("Neon Green", "#FF22C55E"),     // Ultra Green
        Pair("Flamingo Pink", "#FFEC4899"),  // Bright Magenta Pink
        Pair("Warm Terra", "#FFE06666")      // Terracotta
    )
    
    val initialIndex = remember(habit.colorHex) {
        val found = colorSwatches.indexOfFirst { it.second.equals(habit.colorHex, ignoreCase = true) }
        if (found != -1) found else 0
    }
    var selectedColorIndex by remember { mutableStateOf(initialIndex) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("edit_habit_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Edit Habit Loop",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Habit Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_habit_name_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (e.g., Morning routine)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Weekly Target Goal: ${targetDays} days",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (i in 1..7) {
                            val isSelected = targetDays == i
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { targetDays = i },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = i.toString(),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                     OutlinedTextField(
                        value = freqAmount.toString(),
                        onValueChange = { freqAmount = it.toIntOrNull() ?: 1 },
                        label = { Text("Freq Amount") },
                        modifier = Modifier.weight(1f)
                     )
                     OutlinedTextField(
                        value = freqPeriod,
                        onValueChange = { freqPeriod = it },
                        label = { Text("Freq Period (e.g., DAILY)") },
                        modifier = Modifier.weight(1f)
                     )
                }

                Text(
                    text = "Color Aesthetic Palette:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Color swatches organized in two balanced rows of 6
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val row1 = colorSwatches.take(6)
                    val row2 = colorSwatches.drop(6)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row1.forEachIndexed { idx, swatch ->
                            val globalIndex = idx
                            val color = remember(swatch.second) {
                                try { Color(android.graphics.Color.parseColor(swatch.second)) } catch (e: Exception) { Color.Gray }
                            }
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (selectedColorIndex == globalIndex) 2.dp else 0.dp,
                                        color = if (selectedColorIndex == globalIndex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { 
                                        selectedColorIndex = globalIndex
                                        customHexInput = swatch.second 
                                    }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row2.forEachIndexed { idx, swatch ->
                            val globalIndex = idx + 6
                            val color = remember(swatch.second) {
                                try { Color(android.graphics.Color.parseColor(swatch.second)) } catch (e: Exception) { Color.Gray }
                            }
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (selectedColorIndex == globalIndex) 2.dp else 0.dp,
                                        color = if (selectedColorIndex == globalIndex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { 
                                        selectedColorIndex = globalIndex
                                        customHexInput = swatch.second 
                                    }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customHexInput,
                    onValueChange = { input ->
                        customHexInput = input
                        val matchIdx = colorSwatches.indexOfFirst { it.second.equals(input, ignoreCase = true) }
                        selectedColorIndex = matchIdx
                    },
                    label = { Text("Custom Color Hex (e.g. #FF10B981 or 10B981)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val trimmed = customHexInput.trim()
                                val finalColor = if (trimmed.startsWith("#")) {
                                    if (trimmed.length == 7) "#FF" + trimmed.substring(1) else trimmed
                                } else {
                                    if (trimmed.length == 6) "#FF$trimmed" else if (trimmed.length == 8) "#$trimmed" else "#FF6200EE"
                                }
                                onConfirm(name, description, targetDays, finalColor, freqAmount, freqPeriod)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHabitDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, targetDays: Int, colorHex: String, freqAmount: Int, freqPeriod: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var targetDays by remember { mutableStateOf(7) }
    var freqAmount by remember { mutableStateOf(1) }
    var freqPeriod by remember { mutableStateOf("DAILY") }

    // Wide range of colors including vibrant reds, greens, blues, oranges, purples, etc.
    val colorSwatches = listOf(
        Pair("Ocean Wave", "#FF0EA5E9"),   // Modern Electric Blue
        Pair("Emerald Forest", "#FF10B981"), // Fresh Vibrant Green
        Pair("Sunset Peach", "#FFF97316"),   // Neon Sunset Orange
        Pair("Rose Crimson", "#FFF43F5E"),   // Premium Rose
        Pair("Royal Purple", "#FF8B5CF6"),   // Vivid Purple
        Pair("Mint Breeze", "#FF14B8A6"),    // Modern Teal
        Pair("Electric Lemon", "#FFEAB308"), // Luminous Gold Amber
        Pair("Sky Blue", "#FF3B82F6"),       // Deep Royal Blue
        Pair("Wild Lavender", "#FFA855F7"),  // Electric Lavender
        Pair("Neon Green", "#FF22C55E"),     // Ultra Green
        Pair("Flamingo Pink", "#FFEC4899"),  // Bright Magenta Pink
        Pair("Warm Terra", "#FFE06666")      // Terracotta
    )
    var selectedColorIndex by remember { mutableStateOf(0) }
    var customHexInput by remember { mutableStateOf("#FF0EA5E9") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("add_habit_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "New Habit Loop",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("What is the habit?") },
                    placeholder = { Text("e.g. Read 15 pages") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("habit_name_field")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Describe it (optional)") },
                    placeholder = { Text("e.g. self development books") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = freqAmount.toString(),
                        onValueChange = { freqAmount = it.toIntOrNull() ?: 1 },
                        label = { Text("Amount") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = freqPeriod,
                        onValueChange = { freqPeriod = it.uppercase() },
                        label = { Text("Period (DAILY/WEEKLY/MONTHLY)") },
                        modifier = Modifier.weight(2f)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Weekly Target Days",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$targetDays days/week",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (i in 1..7) {
                            val isSelected = targetDays == i
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { targetDays = i },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = i.toString(),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Color swatches selection
                Column {
                    Text(
                        text = "Visual Motif Color",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val row1 = colorSwatches.take(6)
                        val row2 = colorSwatches.drop(6)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row1.forEachIndexed { idx, swatch ->
                                val globalIndex = idx
                                val color = remember(swatch.second) {
                                    try { Color(android.graphics.Color.parseColor(swatch.second)) } catch (e: Exception) { Color.Gray }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selectedColorIndex == globalIndex) 2.dp else 0.dp,
                                            color = if (selectedColorIndex == globalIndex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { 
                                            selectedColorIndex = globalIndex
                                            customHexInput = swatch.second
                                        }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row2.forEachIndexed { idx, swatch ->
                                val globalIndex = idx + 6
                                val color = remember(swatch.second) {
                                    try { Color(android.graphics.Color.parseColor(swatch.second)) } catch (e: Exception) { Color.Gray }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selectedColorIndex == globalIndex) 2.dp else 0.dp,
                                            color = if (selectedColorIndex == globalIndex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { 
                                            selectedColorIndex = globalIndex
                                            customHexInput = swatch.second
                                        }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = customHexInput,
                    onValueChange = { input ->
                        customHexInput = input
                        val matchIdx = colorSwatches.indexOfFirst { it.second.equals(input, ignoreCase = true) }
                        selectedColorIndex = matchIdx
                    },
                    label = { Text("Custom Color Hex (e.g. #FF10B981 or 10B981)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.trim().isNotEmpty()) {
                                val trimmed = customHexInput.trim()
                                val finalColor = if (trimmed.startsWith("#")) {
                                    if (trimmed.length == 7) "#FF" + trimmed.substring(1) else trimmed
                                } else {
                                    if (trimmed.length == 6) "#FF$trimmed" else if (trimmed.length == 8) "#$trimmed" else "#FF6200EE"
                                }
                                onConfirm(
                                    name.trim(),
                                    description.trim(),
                                    targetDays,
                                    finalColor,
                                    freqAmount,
                                    freqPeriod.trim()
                                )
                            }
                        },
                        enabled = name.trim().isNotEmpty(),
                        modifier = Modifier.testTag("confirm_create_habit")
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreTrendLineChart(scores: List<Pair<Int, Float>>, color: Color) {
    val scoresToDraw = remember(scores) {
        if (scores.isEmpty()) List(6) { Pair(0, 0f) } else scores
    }
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    )
    val scoreStyle = MaterialTheme.typography.labelSmall.copy(
        color = color,
        fontWeight = FontWeight.Bold
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp) // increased height slightly to fit text
            .padding(vertical = 16.dp, horizontal = 12.dp)
    ) {
        val width = size.width
        val height = size.height - 30.dp.toPx() // Reserve bottom space for labels
        val xPaddingLeft = 32.dp.toPx()
        
        val pointsCount = scoresToDraw.size
        val stepX = (width - xPaddingLeft) / (pointsCount - 1).coerceAtLeast(1)
        val stepY = height / 100f
        
        // Draw horizontal grid lines and labels
        val gridLines = listOf(0f, 25f, 50f, 75f, 100f)
        gridLines.forEach { value ->
            val y = height - (value * stepY)
            drawLine(
                color = Color.LightGray.copy(alpha = 0.3f),
                start = Offset(xPaddingLeft, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            val labelTxt = "${value.toInt()}%"
            val textLayoutResult = textMeasurer.measure(labelTxt, labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = labelTxt,
                style = labelStyle,
                topLeft = Offset(0f, y - (textLayoutResult.size.height / 2f))
            )
        }
        
        // Draw a line joining the points
        val points = scoresToDraw.mapIndexed { index, score ->
            Offset(xPaddingLeft + (index * stepX), height - (score.second * stepY))
        }
        
        for (i in 0 until points.size - 1) {
            drawLine(
                color = color,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        
        // Draw dots on each node
        points.forEachIndexed { index, point ->
            drawCircle(
                color = color,
                radius = 5.dp.toPx(),
                center = point
            )
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = point
            )
            val weekTxt = "W" + scoresToDraw[index].first.toString()
            val scoreTxt = "${scoresToDraw[index].second.toInt()}%"
            
            // Measure texts
            val weekLayoutResult = textMeasurer.measure(weekTxt, labelStyle)
            val scoreLayoutResult = textMeasurer.measure(scoreTxt, scoreStyle)
            
            drawText(
                textMeasurer = textMeasurer,
                text = weekTxt,
                style = labelStyle,
                topLeft = Offset(point.x - (weekLayoutResult.size.width / 2f), height + 10.dp.toPx())
            )
            drawText(
                textMeasurer = textMeasurer,
                text = scoreTxt,
                style = scoreStyle,
                topLeft = Offset(point.x - (scoreLayoutResult.size.width / 2f), point.y - 24.dp.toPx())
            )
        }
    }
}

@Composable
fun CustomHistoryBarChart(counts: List<Pair<Int, Int>>, color: Color) {
    val maxVal = counts.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Bottom
    ) {
        counts.forEach { (weekNumber, count) ->
            val fraction = count.toFloat() / maxVal.toFloat()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .fillMaxHeight(fraction = fraction.coerceIn(0.05f, 0.9f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(color)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "W$weekNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun TabletCalendarGrid(
    calendarGrid: List<List<Pair<Date, Boolean>>>,
    baseColor: Color,
    onTileToggle: (Date) -> Unit
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // Helpful tip text
        Text(
            text = "Tip: Tap any tile below to toggle completion for that day directly.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Left column: weekday labels "Sun", "Mon", "Tue"...
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                // Spacer for week-headers row
                Box(modifier = Modifier.height(18.dp))
                val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                weekdays.forEach { dayName ->
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .width(32.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Rest columns: each column represents a week of 7 days
            val totalWeeks = calendarGrid.size
            for (weekIndex in 0 until totalWeeks) {
                val week = calendarGrid[weekIndex]
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(34.dp)
                ) {
                    // Week / Month Label
                    val isStartOfMonth = remember(week) {
                        val cal = Calendar.getInstance()
                        week.any {
                            cal.time = it.first
                            cal.get(Calendar.DAY_OF_MONTH) == 1
                        }
                    }
                    val label = remember(week) {
                        val sdf = SimpleDateFormat("MMM", Locale.US)
                        sdf.format(week[0].first)
                    }
                    Box(
                        modifier = Modifier.height(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isStartOfMonth || weekIndex == 0) label else "",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    for (dayIndex in 0 until 7) {
                        val (date, completed) = week[dayIndex]
                        val isToday = remember(date) {
                            val cal1 = Calendar.getInstance()
                            val cal2 = Calendar.getInstance()
                            cal2.time = date
                            cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
                        }

                        val sdfNum = remember { SimpleDateFormat("d", Locale.US) }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (completed) baseColor else {
                                        if (isToday) baseColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    }
                                )
                                .border(
                                    width = if (isToday) 1.5.dp else 0.dp,
                                    color = if (isToday) baseColor else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onTileToggle(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sdfNum.format(date),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = if (completed) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BestStreaksList(
    bestStreaks: List<Triple<String, String, Int>>,
    baseColor: Color
) {
    if (bestStreaks.isEmpty()) {
        Text(
            text = "No recorded streaks yet. Log days consistently to generate streak markers!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(8.dp)
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            bestStreaks.forEach { (start, end, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = start,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(0.25f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Box(
                        modifier = Modifier
                            .weight(0.5f)
                            .padding(horizontal = 6.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(baseColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (count.toFloat() / 30f).coerceIn(0.15f, 1f))
                                .height(22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(baseColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$count d",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                    
                    Text(
                        text = end,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(0.25f),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun WeekdayFrequencyDistribution(
    weekdayFrequency: IntArray,
    baseColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val maxFreq = weekdayFrequency.maxOrNull()?.coerceAtLeast(1) ?: 1
        for (i in 0 until 7) {
            val freq = weekdayFrequency[i]
            val ratio = freq.toFloat() / maxFreq.toFloat()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            baseColor.copy(alpha = ratio.coerceIn(0.08f, 1f))
                        )
                        .border(
                            width = if (freq > 0) 2.dp else 1.dp,
                            color = if (freq > 0) baseColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$freq",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ratio > 0.45f) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = weekdays[i],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class Particle(
    val x: Float,
    val y: Float,
    val speedX: Float,
    val speedY: Float,
    val color: Color,
    val rotation: Float,
    val rotationSpeed: Float,
    val alpha: Float,
    val size: Float
)

@Composable
fun MonthCalendarView(
    activeHabits: List<Habit>,
    allCompletions: List<com.example.data.HabitCompletion>,
    onToggleCompletion: (Int, String) -> Unit,
    dateFormat: SimpleDateFormat
) {
    var calendarMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedCalendarDateStr by remember { mutableStateOf(dateFormat.format(Date())) }
    
    // Group completions by habit ID to make lookups fast
    val completionsByHabit = remember(allCompletions) {
        allCompletions.groupBy { it.habitId }.mapValues { entry ->
            entry.value.map { it.dateString }.toSet()
        }
    }
    
    val year = calendarMonth.get(Calendar.YEAR)
    val month = calendarMonth.get(Calendar.MONTH)
    
    // Header for Month view
    val monthName = remember(month, year) {
        val monthSdf = SimpleDateFormat("MMMM yyyy", Locale.US)
        monthSdf.format(calendarMonth.time)
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Month switcher header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val nextCal = calendarMonth.clone() as Calendar
                    nextCal.set(Calendar.DAY_OF_MONTH, 1)
                    nextCal.add(Calendar.MONTH, -1)
                    calendarMonth = nextCal
                }) {
                    Text("◀", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = monthName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Week ${calendarMonth.get(Calendar.WEEK_OF_YEAR)} of ${calendarMonth.get(Calendar.YEAR)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    val nextCal = calendarMonth.clone() as Calendar
                    nextCal.set(Calendar.DAY_OF_MONTH, 1)
                    nextCal.add(Calendar.MONTH, 1)
                    calendarMonth = nextCal
                }) {
                    Text("▶", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Days of Week labels (S, M, T, W, T, F, S)
            val daysOfWeekHeader = listOf("S", "M", "T", "W", "T", "F", "S")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                daysOfWeekHeader.forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Calculate grid dates
            val gridDays = remember(month, year) {
                val list = mutableListOf<Date?>()
                val tempCal = Calendar.getInstance()
                tempCal.set(Calendar.YEAR, year)
                tempCal.set(Calendar.MONTH, month)
                tempCal.set(Calendar.DAY_OF_MONTH, 1)
                
                val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
                // Add null placeholders for starting empty days
                for (i in Calendar.SUNDAY until firstDayOfWeek) {
                    list.add(null)
                }
                
                val daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                for (i in 1..daysInMonth) {
                    list.add(tempCal.time)
                    tempCal.add(Calendar.DAY_OF_MONTH, 1)
                }
                
                // Pad to multiple of 7
                while (list.size % 7 != 0) {
                    list.add(null)
                }
                list
            }
            
            // Render grid
            val chunked = gridDays.chunked(7)
            chunked.forEach { rowDates ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    rowDates.forEach { date ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (date != null) {
                                val dateStr = dateFormat.format(date)
                                val isSelected = dateStr == selectedCalendarDateStr
                                val cal = Calendar.getInstance().apply { time = date }
                                val dayNum = cal.get(Calendar.DAY_OF_MONTH)
                                
                                val completedHabits = remember(completionsByHabit, dateStr, activeHabits) {
                                    activeHabits.filter { habit ->
                                        completionsByHabit[habit.id]?.contains(dateStr) == true
                                    }
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                            else Color.Transparent
                                        )
                                        .clickable { selectedCalendarDateStr = dateStr },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(
                                            text = "$dayNum",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                                                    else MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                        // Colored dots representing completed habits
                                        if (completedHabits.isNotEmpty()) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                completedHabits.take(4).forEach { habit ->
                                                    val color = try { Color(android.graphics.Color.parseColor(habit.colorHex)) } catch (e: Exception) { Color.Gray }
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(color)
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
        }
    }
    
    // Details of habits completed on selected date
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        val displayDateLabel = remember(selectedCalendarDateStr) {
            try {
                val d = dateFormat.parse(selectedCalendarDateStr)
                SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(d ?: Date())
            } catch (e: Exception) {
                selectedCalendarDateStr
            }
        }
        
        Text(
            text = displayDateLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        if (activeHabits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No active habits created yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            val completedForSelectedDay = activeHabits.filter { habit ->
                completionsByHabit[habit.id]?.contains(selectedCalendarDateStr) == true
            }
            
            // Progress details
            val pctStr = if (activeHabits.isNotEmpty()) {
                "${completedForSelectedDay.size} of ${activeHabits.size} completed"
            } else ""
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pctStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                val ratio = if (activeHabits.isNotEmpty()) completedForSelectedDay.size.toFloat() / activeHabits.size.toFloat() else 0f
                LinearProgressIndicator(
                    progress = ratio,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.width(100.dp).clip(RoundedCornerShape(4.dp))
                )
            }
            
            activeHabits.forEach { habit ->
                val baseColor = try { Color(android.graphics.Color.parseColor(habit.colorHex)) } catch (e: Exception) { Color(0xFF6200EE) }
                val isCompleted = completionsByHabit[habit.id]?.contains(selectedCalendarDateStr) == true
                
                // Click trigger to burst confetti only when completed via click
                var calClickTrigger by remember { mutableStateOf(0) }
                
                // Custom particles list local to each habit row click
                val particles = remember { mutableStateListOf<Particle>() }
                val calProgressVal = remember { androidx.compose.animation.core.Animatable(0f) }
                
                // Sync animation for confetti when completed is toggled via click
                LaunchedEffect(calClickTrigger) {
                    if (calClickTrigger > 0) {
                        particles.clear()
                        val random = java.util.Random()
                        for (i in 0 until 12) {
                            val angle = random.nextFloat() * 2 * Math.PI
                            val speed = 2f + random.nextFloat() * 4f
                            particles.add(
                                Particle(
                                    x = 0f,
                                    y = 0f,
                                    speedX = (Math.cos(angle) * speed).toFloat(),
                                    speedY = (Math.sin(angle) * speed).toFloat(),
                                    color = baseColor,
                                    rotation = random.nextFloat() * 360f,
                                    rotationSpeed = -15f + random.nextFloat() * 30f,
                                    alpha = 1.0f,
                                    size = 10f + random.nextFloat() * 15f
                                )
                            )
                        }
                        calProgressVal.snapTo(0f)
                        calProgressVal.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 550, easing = androidx.compose.animation.core.LinearEasing)
                        )
                        particles.clear()
                    }
                }
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCompleted) baseColor.copy(alpha = 0.08f) 
                                         else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    if (!isCompleted) {
                                        calClickTrigger++
                                    }
                                    onToggleCompletion(habit.id, selectedCalendarDateStr) 
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(baseColor)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = habit.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!habit.description.isNullOrBlank()) {
                                        Text(
                                            text = habit.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                            
                            val scale by animateFloatAsState(
                                targetValue = if (isCompleted) 1.2f else 0.85f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                label = "tick_scale_cal"
                            )
                            val alphaState by animateFloatAsState(
                                targetValue = if (isCompleted) 1.0f else 0.70f,
                                animationSpec = tween(durationMillis = 180),
                                label = "tick_alpha_cal"
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isCompleted) baseColor.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(1.dp, if (isCompleted) baseColor else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCompleted) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Completed",
                                        tint = baseColor,
                                        modifier = Modifier.size(18.dp).graphicsLayer(scaleX = scale, scaleY = scale, alpha = alphaState)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Not Completed",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                        modifier = Modifier.size(14.dp).graphicsLayer(scaleX = scale, scaleY = scale, alpha = alphaState)
                                    )
                                }
                            }
                        }
                        
                        // Local burst overlay inside card
                        if (particles.isNotEmpty() && calProgressVal.value < 1f) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val t = calProgressVal.value
                                particles.forEach { p ->
                                    val currentX = p.speedX * t * 45f
                                    val currentY = p.speedY * t * 45f + 0.15f * (t * 45f) * (t * 45f)
                                    val currentAlpha = (1f - t).coerceIn(0f, 1f)
                                    val currentRotation = p.rotation + p.rotationSpeed * t * 45f
                                    if (currentAlpha > 0f) {
                                        rotate(currentRotation, pivot = androidx.compose.ui.geometry.Offset(center.x + currentX, center.y + currentY)) {
                                            drawRect(
                                                color = p.color.copy(alpha = currentAlpha),
                                                topLeft = androidx.compose.ui.geometry.Offset(center.x + currentX - p.size / 2, center.y + currentY - p.size / 2),
                                                size = androidx.compose.ui.geometry.Size(p.size, p.size * 0.6f)
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
}

@Composable
fun TimeProgressDialog(onDismiss: () -> Unit, isDialog: Boolean = true) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("habit_prefs", android.content.Context.MODE_PRIVATE) }
    var birthYear by remember { mutableIntStateOf(sharedPrefs.getInt("birth_year", 1996)) }
    var showBirthYearDialog by remember { mutableStateOf(false) }

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    // ...
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            currentTime = System.currentTimeMillis()
        }
    }

    val cal = Calendar.getInstance()
    cal.timeInMillis = currentTime
    
    fun getDayProgress(): Float {
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val s = cal.get(Calendar.SECOND)
        return (h * 3600 + m * 60 + s) / 86400f
    }
    
    fun getWeekProgress(): Float {
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val normalizedDay = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - 2 // Mon=0 .. Sun=6
        val dayProgress = getDayProgress()
        return (normalizedDay.coerceIn(0, 6) + dayProgress) / 7f
    }
    
    fun getMonthProgress(): Float {
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dayProgress = getDayProgress()
        return (dayOfMonth + dayProgress) / daysInMonth.toFloat()
    }
    
    fun getQuarterProgress(): Float {
        val month = cal.get(Calendar.MONTH) // 0-11
        val quarterIndex = month / 3
        val startMonth = quarterIndex * 3
        val startCal = Calendar.getInstance().apply { set(Calendar.MONTH, startMonth); set(Calendar.DAY_OF_MONTH, 1) }
        val endCal = Calendar.getInstance().apply { set(Calendar.MONTH, startMonth + 2); set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) }
        val totalDays = endCal.get(Calendar.DAY_OF_YEAR) - startCal.get(Calendar.DAY_OF_YEAR) + 1
        val daysPassed = cal.get(Calendar.DAY_OF_YEAR) - startCal.get(Calendar.DAY_OF_YEAR)
        val dayProgress = getDayProgress()
        return (daysPassed + dayProgress) / totalDays.coerceAtLeast(1).toFloat()
    }
    
    fun getYearProgress(): Float {
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR) - 1
        val daysInYear = cal.getActualMaximum(Calendar.DAY_OF_YEAR)
        val dayProgress = getDayProgress()
        return (dayOfYear + dayProgress) / daysInYear.toFloat()
    }
    
    fun getLifeProgress(): Float {
        val assumedBirthYear = birthYear
        val startCal = Calendar.getInstance().apply { set(Calendar.YEAR, assumedBirthYear); set(Calendar.MONTH, 0); set(Calendar.DAY_OF_MONTH, 1) }
        val millisLived = currentTime - startCal.timeInMillis
        val totalMillis = 80L * 365L * 24L * 3600L * 1000L // Approx 80 years
        return (millisLived.toDouble() / totalMillis.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }

    val dayP = getDayProgress()
    val weekP = getWeekProgress()
    val monthP = getMonthProgress()
    val quarterP = getQuarterProgress()
    val yearP = getYearProgress()
    val lifeP = getLifeProgress()

    if (showBirthYearDialog) {
        var inputBirthYear by remember { mutableStateOf(birthYear.toString()) }
        AlertDialog(
            onDismissRequest = { showBirthYearDialog = false },
            title = { Text("Set Birth Year") },
            text = {
                OutlinedTextField(
                    value = inputBirthYear,
                    onValueChange = { inputBirthYear = it },
                    label = { Text("Birth Year (e.g. 1996)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newYear = inputBirthYear.toIntOrNull()
                    if (newYear != null) {
                        birthYear = newYear
                        sharedPrefs.edit().putInt("birth_year", newYear).apply()
                    }
                    showBirthYearDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBirthYearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val content = @Composable {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Time Progress",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = { showBirthYearDialog = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Set Birth Year")
                }
            }
            
            TimeProgressBarRow("Day", dayP)
            TimeProgressBarRow("Week", weekP)
            TimeProgressBarRow("Month", monthP)
            TimeProgressBarRow("Quarter", quarterP)
            TimeProgressBarRow("Year", yearP)
            TimeProgressBarRow("Life (80y)", lifeP, overrideColor = Color.White)

            if (isDialog) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }

    if (isDialog) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                content()
            }
        }
    } else {
        content()
    }
}

@Composable
fun TimeProgressBarRow(label: String, progress: Float, overrideColor: Color? = null) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%.2f%%", progress * 100f), style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(7.dp))
                .background(Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(overrideColor ?: MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}


class PomodoroState {
    var mode by mutableStateOf("Pomodoro")
    var pomodoroTimeMinutes by mutableIntStateOf(25)
    var shortBreakMinutes by mutableIntStateOf(5)
    var longBreakMinutes by mutableIntStateOf(15)
    var timeRemainingSeconds by mutableIntStateOf(25 * 60)
    var isRunning by mutableStateOf(false)
    var pomodorosCompleted by mutableIntStateOf(0)
    var showEditDialog by mutableStateOf(false)
    var isFinished by mutableStateOf(false)
    
    fun changeMode(newMode: String) {
        mode = newMode
        isRunning = false
        isFinished = false
        timeRemainingSeconds = when (newMode) {
            "Pomodoro" -> pomodoroTimeMinutes * 60
            "Short Break" -> shortBreakMinutes * 60
            "Long Break" -> longBreakMinutes * 60
            else -> 25 * 60
        }
    }
    
    fun updateTimeRemaining() {
        if (!isRunning) {
            isFinished = false
            timeRemainingSeconds = when (mode) {
                "Pomodoro" -> pomodoroTimeMinutes * 60
                "Short Break" -> shortBreakMinutes * 60
                "Long Break" -> longBreakMinutes * 60
                else -> 25 * 60
            }
        }
    }
}

@Composable
fun PomodoroTimerView(state: PomodoroState, isFullScreen: Boolean, onFullScreenToggle: () -> Unit) {
    var inputPomodoro by remember { mutableStateOf(state.pomodoroTimeMinutes.toString()) }
    var inputShortBreak by remember { mutableStateOf(state.shortBreakMinutes.toString()) }
    var inputLongBreak by remember { mutableStateOf(state.longBreakMinutes.toString()) }
    
    val backgroundColor = remember { androidx.compose.animation.Animatable(Color.Transparent) }
    
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) {
            repeat(3) {
                backgroundColor.animateTo(Color.White, animationSpec = androidx.compose.animation.core.tween(300))
                backgroundColor.animateTo(Color.Black, animationSpec = androidx.compose.animation.core.tween(300))
            }
        } else {
            backgroundColor.snapTo(Color.Transparent)
        }
    }

    if (state.showEditDialog) {
        AlertDialog(
            onDismissRequest = { state.showEditDialog = false },
            title = { Text("Edit Timer (Minutes)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputPomodoro,
                        onValueChange = { inputPomodoro = it },
                        label = { Text("Pomodoro") }
                    )
                    OutlinedTextField(
                        value = inputShortBreak,
                        onValueChange = { inputShortBreak = it },
                        label = { Text("Short Break") }
                    )
                    OutlinedTextField(
                        value = inputLongBreak,
                        onValueChange = { inputLongBreak = it },
                        label = { Text("Long Break") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    state.pomodoroTimeMinutes = inputPomodoro.toIntOrNull() ?: 25
                    state.shortBreakMinutes = inputShortBreak.toIntOrNull() ?: 5
                    state.longBreakMinutes = inputLongBreak.toIntOrNull() ?: 15
                    state.updateTimeRemaining()
                    state.showEditDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { state.showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val displayMinutes = state.timeRemainingSeconds / 60
    val displaySeconds = state.timeRemainingSeconds % 60
    val timeString = String.format(Locale.US, "%02d:%02d", displayMinutes, displaySeconds)

    val bgColor = when (state.mode) {
        "Pomodoro" -> Color(0xFF5C6BC0) // Indigo
        "Short Break" -> Color(0xFF4CAF50) // Green
        "Long Break" -> Color(0xFF2196F3) // Blue
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = if (isFullScreen) RectangleShape else RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isFullScreen) 0.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFullScreen) {
                if (state.isFinished) backgroundColor.value else Color.Black
            } else {
                if (state.isFinished) backgroundColor.value else bgColor
            }
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(if (isFullScreen) 32.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isFullScreen) Arrangement.spacedBy(32.dp) else Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!isFullScreen) {
                    IconButton(onClick = { state.showEditDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                }
                IconButton(onClick = onFullScreenToggle) {
                    Icon(if (isFullScreen) Icons.Default.Close else Icons.Default.Fullscreen, contentDescription = "Full Screen", tint = Color.White)
                }
            }
            
            if (!isFullScreen) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    listOf("Pomodoro", "Short Break", "Long Break").forEach { tabMode ->
                        val isSelected = state.mode == tabMode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) Color.White else Color.Transparent)
                                .clickable { state.changeMode(tabMode) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = tabMode,
                                color = if (isSelected) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "%02d".format(displayMinutes),
                    fontSize = if (isFullScreen) 160.sp else 60.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                // Separator line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isFullScreen) 0.5f else 0.3f)
                        .height(if (isFullScreen) 8.dp else 4.dp)
                        .background(Color.White)
                )
                Text(
                    text = "%02d".format(displaySeconds),
                    fontSize = if (isFullScreen) 160.sp else 60.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (!isFullScreen) {
                Text(
                    text = "Pomodoros completed before long break ${state.pomodorosCompleted % 4}/4",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = if (isFullScreen) 32.dp else 0.dp)
            ) {
                Button(
                    onClick = { state.isRunning = !state.isRunning },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = if (state.isRunning) "Pause" else "Start",
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                TextButton(onClick = { 
                    state.isRunning = false
                    state.updateTimeRemaining()
                }) {
                    Text("Reset", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

