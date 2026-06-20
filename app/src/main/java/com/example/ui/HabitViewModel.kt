package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.HabitTrackerApplication
import com.example.data.Habit
import com.example.data.HabitCompletion
import com.example.data.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class WeeklyReport(
    val mondayOfWeekString: String, // YYYY-MM-DD representing the Monday
    val label: String, // e.g. "Week of Jun 15"
    val completedCount: Int,
    val targetCount: Int,
    val scorePercentage: Float // e.g., 75.5f
)

data class MonthlyReport(
    val monthString: String, // YYYY-MM
    val label: String, // e.g. "June 2026"
    val completedCount: Int,
    val daysInMonthWithAtLeastOneCompletion: Int,
    val totalPossibleCompletions: Int
)

data class HabitStreak(
    val habitId: Int,
    val currentStreak: Int,
    val maxStreak: Int
)

data class HabitAnalytics(
    val habit: Habit,
    val totalCompletions: Int,
    val currentStreak: Int,
    val maxStreak: Int,
    val completionsThisMonth: Set<String>, // "YYYY-MM-DD"
    val completionsThisWeek: Set<String>,
    val completionsLastWeek: Set<String>,
    val masteryLevel: Int, // 0-5
    val masteryLabel: String
)

class HabitViewModel(
    application: Application,
    private val repository: HabitRepository
) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("habit_tracker_prefs", Context.MODE_PRIVATE)
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)

    // Current selected date for logging
    val selectedDateString = MutableStateFlow(sdf.format(Date()))

    // Dark Theme state
    val isDarkTheme = MutableStateFlow(sharedPrefs.getBoolean("dark_theme", true))

    fun toggleTheme() {
        val newValue = !isDarkTheme.value
        isDarkTheme.value = newValue
        sharedPrefs.edit().putBoolean("dark_theme", newValue).apply()
    }

    // Raw habits and completions from DB
    val activeHabits: StateFlow<List<Habit>> = repository.activeHabits
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val archivedHabits: StateFlow<List<Habit>> = repository.archivedHabits
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allCompletions: StateFlow<List<HabitCompletion>> = repository.allCompletions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current selected date completions
    val selectedDateCompletions: StateFlow<Set<Int>> = combine(
        selectedDateString,
        allCompletions
    ) { dateStr, completions ->
        completions.filter { it.dateString == dateStr }.map { it.habitId }.toSet()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    // Analytics Map per habit
    val habitAnalytics: StateFlow<List<HabitAnalytics>> = combine(
        activeHabits,
        allCompletions
    ) { habits, completions ->
        val completionsByHabit = completions.groupBy { it.habitId }

        habits.map { habit ->
            val habitCompDates = completionsByHabit[habit.id]?.map { it.dateString } ?: emptyList()
            val (current, max) = calculateStreaks(habitCompDates)
            
            // Current month completions
            val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            val currentMonthComps = habitCompDates.filter { it.startsWith(currentMonthStr) }.toSet()

            // Current week completions
            val mondayString = getMondayOfWeek(sdf.format(Date()))
            val mCal = Calendar.getInstance()
            mCal.time = sdf.parse(mondayString) ?: Date()
            val weekDates = (0..6).map {
                val dayCal = mCal.clone() as Calendar
                dayCal.add(Calendar.DAY_OF_YEAR, it)
                sdf.format(dayCal.time)
            }.toSet()
            val currentWeekComps = habitCompDates.filter { weekDates.contains(it) }.toSet()

            // Previous week completions
            val lastMondayCal = Calendar.getInstance()
            lastMondayCal.time = sdf.parse(mondayString) ?: Date()
            lastMondayCal.add(Calendar.DAY_OF_YEAR, -7)
            val lastWeekDates = (0..6).map {
                val dayCal = lastMondayCal.clone() as Calendar
                dayCal.add(Calendar.DAY_OF_YEAR, it)
                sdf.format(dayCal.time)
            }.toSet()
            val lastWeekComps = habitCompDates.filter { lastWeekDates.contains(it) }.toSet()

            // Mastery level benchmark
            val totalCompletions = habitCompDates.distinct().size
            val mastery = when {
                totalCompletions < 7 -> 0
                totalCompletions < 21 -> 1
                totalCompletions < 45 -> 2
                totalCompletions < 90 -> 3
                totalCompletions < 180 -> 4
                else -> 5
            }
            val masteryLabel = when (mastery) {
                0 -> "Rookie"
                1 -> "Apprentice"
                2 -> "Practitioner"
                3 -> "Expert"
                4 -> "Master"
                else -> "Legend"
            }
            
            HabitAnalytics(
                habit = habit,
                totalCompletions = habitCompDates.distinct().size,
                currentStreak = current,
                maxStreak = max,
                completionsThisMonth = currentMonthComps,
                completionsThisWeek = currentWeekComps,
                completionsLastWeek = lastWeekComps,
                masteryLevel = mastery,
                masteryLabel = masteryLabel
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Weekly summary report over time (last 6 weeks)
    val weeklyReports: StateFlow<List<WeeklyReport>> = combine(
        activeHabits,
        allCompletions
    ) { habits, completions ->
        if (habits.isEmpty()) return@combine emptyList()

        val reportsMap = mutableMapOf<String, Int>() // MondayStr -> completionCount
        completions.forEach { comp ->
            val monStr = getMondayOfWeek(comp.dateString)
            reportsMap[monStr] = (reportsMap[monStr] ?: 0) + 1
        }

        // Generate reports for the last 6 weeks
        val result = mutableListOf<WeeklyReport>()
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY

        for (i in 0 until 6) {
            val mondayCal = Calendar.getInstance()
            mondayCal.firstDayOfWeek = Calendar.MONDAY
            mondayCal.add(Calendar.WEEK_OF_YEAR, -i)
            // find monday
            val dayOfWeek = mondayCal.get(Calendar.DAY_OF_WEEK)
            val diff = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
            mondayCal.add(Calendar.DAY_OF_YEAR, -diff)
            val monStr = sdf.format(mondayCal.time)

            // Label
            val weekNumber = mondayCal.get(Calendar.WEEK_OF_YEAR)
            val label = "Week $weekNumber"

            // Calculating possible target
            // Max tasks this week = sum(targetDaysPerWeek) of habits active before/during this week
            // For simplicity, sum of targetDaysPerWeek for active habits.
            val completedInThisWeek = reportsMap[monStr] ?: 0
            val targetInThisWeek = habits.sumOf { it.targetDaysPerWeek }

            val scorePercentage = if (targetInThisWeek > 0) {
                (completedInThisWeek.toFloat() / targetInThisWeek.toFloat()) * 100f
            } else 0f

            result.add(
                WeeklyReport(
                    mondayOfWeekString = monStr,
                    label = label,
                    completedCount = completedInThisWeek,
                    targetCount = targetInThisWeek,
                    scorePercentage = scorePercentage
                )
            )
        }
        result // index 0 is this week, last is 5 weeks ago. We'll show chronologically by reversing in UI
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Monthly summary report over time (last 4 months)
    val monthlyReports: StateFlow<List<MonthlyReport>> = combine(
        activeHabits,
        allCompletions
    ) { habits, completions ->
        if (habits.isEmpty()) return@combine emptyList()

        // completions grouped by yyyy-MM
        val monthCompletions = completions.groupBy { it.dateString.substring(0, 7) }

        val result = mutableListOf<MonthlyReport>()
        for (i in 0 until 4) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -i)
            val monthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
            
            val label = monthFormat.format(cal.time)
            
            // Completion data
            val comps = monthCompletions[monthStr] ?: emptyList()
            val completedCount = comps.size
            val distinctDays = comps.map { it.dateString }.distinct().size

            // Total target possible: sum(targetDaysPerWeek) * (number of weeks in month, roughly 4.3)
            // Or simple count of habits * days in month
            val maxDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            // simple proportional target: (targetDaysPerWeek / 7.0f) * maxDaysInMonth
            val totalPossible = habits.sumOf { 
                ((it.targetDaysPerWeek.toFloat() / 7f) * maxDaysInMonth).toInt() 
            }

            result.add(
                MonthlyReport(
                    monthString = monthStr,
                    label = label,
                    completedCount = completedCount,
                    daysInMonthWithAtLeastOneCompletion = distinctDays,
                    totalPossibleCompletions = if (totalPossible > 0) totalPossible else 30
                )
            )
        }
        result
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Seeding database if empty on launch
        viewModelScope.launch {
            val currentHabits = repository.activeHabits.first()
            if (currentHabits.isEmpty()) {
                seedDatabase()
            }
        }
    }

    fun moveHabit(habitId: Int, moveUp: Boolean) {
        viewModelScope.launch {
            val habits = activeHabits.value
            val currentIndex = habits.indexOfFirst { it.id == habitId }
            if (currentIndex == -1) return@launch
            
            val newIndex = if (moveUp) currentIndex - 1 else currentIndex + 1
            if (newIndex >= 0 && newIndex < habits.size) {
                val currentHabit = habits[currentIndex]
                val targetHabit = habits[newIndex]
                
                val currentOrder = currentHabit.orderIndex
                val targetOrder = targetHabit.orderIndex
                
                if (currentOrder == targetOrder) {
                    // Populate explicit sequential orders
                    for (i in habits.indices) {
                        repository.updateHabitOrder(habits[i].id, i)
                    }
                    val updatedHabits = repository.activeHabits.first()
                    val upCurrentIndex = updatedHabits.indexOfFirst { it.id == habitId }
                    val upNewIndex = if (moveUp) upCurrentIndex - 1 else upCurrentIndex + 1
                    if (upNewIndex >= 0 && upNewIndex < updatedHabits.size) {
                        repository.updateHabitOrder(updatedHabits[upCurrentIndex].id, upNewIndex)
                        repository.updateHabitOrder(updatedHabits[upNewIndex].id, upCurrentIndex)
                    }
                } else {
                    repository.updateHabitOrder(currentHabit.id, targetOrder)
                    repository.updateHabitOrder(targetHabit.id, currentOrder)
                }
            }
        }
    }
    
    // Toggle completion status for a habit on selectedDateString
    fun toggleHabitCompletion(habitId: Int) {
        viewModelScope.launch {
            val date = selectedDateString.value
            val isCurrentlyCompleted = selectedDateCompletions.value.contains(habitId)
            if (isCurrentlyCompleted) {
                repository.removeCompletion(habitId, date)
            } else {
                repository.addCompletion(habitId, date)
            }
        }
    }

    // Toggle completion status for a habit on any specified date string
    fun toggleHabitCompletionForDate(habitId: Int, dateStr: String) {
        viewModelScope.launch {
            val completions = repository.allCompletions.first()
            val isCurrentlyCompleted = completions.any { it.habitId == habitId && it.dateString == dateStr }
            if (isCurrentlyCompleted) {
                repository.removeCompletion(habitId, dateStr)
            } else {
                repository.addCompletion(habitId, dateStr)
            }
        }
    }

    // Add a brand-new habit
    fun addNewHabit(name: String, description: String, targetDays: Int, colorHex: String, freqAmount: Int, freqPeriod: String) {
        viewModelScope.launch {
            val habit = Habit(
                name = name,
                description = description,
                targetDaysPerWeek = targetDays,
                colorHex = colorHex,
                frequencyAmount = freqAmount,
                frequencyPeriod = freqPeriod
            )
            repository.insertHabit(habit)
        }
    }

    // Update an existing habit
    fun updateHabit(id: Int, name: String, description: String, targetDays: Int, colorHex: String, freqAmount: Int, freqPeriod: String) {
        viewModelScope.launch {
            val existing = repository.getHabitById(id) ?: return@launch
            val habit = existing.copy(
                name = name,
                description = description,
                targetDaysPerWeek = targetDays,
                colorHex = colorHex,
                frequencyAmount = freqAmount,
                frequencyPeriod = freqPeriod
            )
            repository.updateHabit(habit)
        }
    }

    // Move a habit to trash (Archive)
    fun deleteHabit(habitId: Int) {
        viewModelScope.launch {
            repository.archiveHabit(habitId)
        }
    }

    // Restore a habit from trash
    fun restoreHabit(habitId: Int) {
        viewModelScope.launch {
            repository.restoreHabit(habitId)
        }
    }

    // Permanently remove a habit and its checkmarks
    fun permanentlyDeleteHabit(habitId: Int) {
        viewModelScope.launch {
            repository.deleteHabit(habitId)
        }
    }

    // Change logging date relative to today
    fun changeSelectedDate(daysOffset: Int) {
        val cal = Calendar.getInstance()
        val currentStr = selectedDateString.value
        try {
            cal.time = sdf.parse(currentStr) ?: Date()
        } catch (e: Exception) {
            // fallback
        }
        cal.add(Calendar.DAY_OF_YEAR, daysOffset)
        selectedDateString.value = sdf.format(cal.time)
    }

    fun setSelectedDate(dateStr: String) {
        selectedDateString.value = dateStr
    }

    // Generates a complete JSON backup of habits and progress completions
    fun getBackupJson(): String {
        val habits = activeHabits.value
        val completions = allCompletions.value
        val root = org.json.JSONObject()
        
        val habitsArr = org.json.JSONArray()
        habits.forEach { h ->
            val obj = org.json.JSONObject()
            obj.put("id", h.id)
            obj.put("name", h.name)
            obj.put("description", h.description)
            obj.put("colorHex", h.colorHex)
            obj.put("createdAt", h.createdAt)
            obj.put("targetDaysPerWeek", h.targetDaysPerWeek)
            habitsArr.put(obj)
        }
        root.put("habits", habitsArr)
        
        val compsArr = org.json.JSONArray()
        completions.forEach { c ->
            val obj = org.json.JSONObject()
            obj.put("habitId", c.habitId)
            obj.put("dateString", c.dateString)
            obj.put("timestamp", c.timestamp)
            compsArr.put(obj)
        }
        root.put("completions", compsArr)
        
        return root.toString(2)
    }

    // Share JSON backup payload
    fun shareBackup(context: Context) {
        try {
            val json = getBackupJson()
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, json)
                putExtra(Intent.EXTRA_SUBJECT, "My Habit Tracker JSON Backup")
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Export Backup JSON")
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Restore habits and completions from a valid JSON backup
    fun importBackupJson(jsonStr: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val root = org.json.JSONObject(jsonStr)
                val habitsArr = root.optJSONArray("habits") ?: org.json.JSONArray()
                val compsArr = root.optJSONArray("completions") ?: org.json.JSONArray()
                
                // Keep mapping of old habit IDs to newly inserted habit IDs to avoid collision constraints
                val idMap = mutableMapOf<Int, Int>()
                
                for (i in 0 until habitsArr.length()) {
                    val obj = habitsArr.getJSONObject(i)
                    val oldId = obj.optInt("id", 0)
                    val h = Habit(
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        colorHex = obj.optString("colorHex", "#FF6200EE"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        targetDaysPerWeek = obj.optInt("targetDaysPerWeek", 7)
                    )
                    val newId = repository.insertHabit(h).toInt()
                    idMap[oldId] = newId
                }
                
                for (i in 0 until compsArr.length()) {
                    val obj = compsArr.getJSONObject(i)
                    val oldHabitId = obj.getInt("habitId")
                    val newHabitId = idMap[oldHabitId] ?: oldHabitId
                    val dateString = obj.getString("dateString")
                    repository.addCompletion(newHabitId, dateString)
                }
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }

    // CSV/Text report builder for progress export
    fun exportReport(context: Context) {
        val habitsList = activeHabits.value
        val completionsList = allCompletions.value
        val weekList = weeklyReports.value
        val monthList = monthlyReports.value
        val analytics = habitAnalytics.value

        val stringBuilder = java.lang.StringBuilder()
        stringBuilder.append("# HABIT TRACKER PROGRESS REPORT\n")
        stringBuilder.append("Generated on: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}\n\n")

        stringBuilder.append("## SUMMARY STATISTICS\n")
        stringBuilder.append("Total Habits tracked: ${habitsList.size}\n")
        stringBuilder.append("Total task completions logged: ${completionsList.size}\n\n")

        stringBuilder.append("## HABIT STREAKS & COMPLETION PERFORMANCE\n")
        analytics.forEach { item ->
            stringBuilder.append("- **${item.habit.name}**\n")
            stringBuilder.append("  * Description: ${item.habit.description}\n")
            stringBuilder.append("  * Goal: ${item.habit.targetDaysPerWeek} days/week\n")
            stringBuilder.append("  * Total completions: ${item.totalCompletions}\n")
            stringBuilder.append("  * Current daily streak: ${item.currentStreak} days\n")
            stringBuilder.append("  * Greatest daily streak: ${item.maxStreak} days\n\n")
        }

        stringBuilder.append("## WEEKLY STATUS SCORE\n")
        stringBuilder.append("Monday Week Commencing, Score %%, Tasks Completed, Weekly Target\n")
        weekList.forEach { w ->
            stringBuilder.append("${w.mondayOfWeekString}, ${String.format(Locale.US, "%.1f", w.scorePercentage)}%%, ${w.completedCount}, ${w.targetCount}\n")
        }
        stringBuilder.append("\n")

        stringBuilder.append("## MONTHLY COMPLETION REVIEW\n")
        stringBuilder.append("Month, Completes, Target, Density Days\n")
        monthList.forEach { m ->
            stringBuilder.append("${m.monthString}, ${m.completedCount}, ${m.totalPossibleCompletions}, ${m.daysInMonthWithAtLeastOneCompletion} active days\n")
        }

        val reportText = stringBuilder.toString()

        // Launch share sheet with progress report
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, reportText)
            putExtra(Intent.EXTRA_SUBJECT, "My Habit Tracker Report")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export Progress Report")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    // Helper: calculate Monday commencing week corresponding to dateString
    private fun getMondayOfWeek(dateStr: String): String {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        try {
            cal.time = sdf.parse(dateStr) ?: Date()
        } catch (e: Exception) {
            // fallback
        }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val diff = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_YEAR, -diff)
        return sdf.format(cal.time)
    }

    // Calculate daily consecutive completion streak
    private fun calculateStreaks(completions: List<String>): Pair<Int, Int> {
        if (completions.isEmpty()) return Pair(0, 0)
        
        // Map, parse, remove duplicates and sort chronologically
        val sortedDates = completions
            .mapNotNull { 
                try { sdf.parse(it) } catch(e: Exception) { null } 
            }
            .distinct()
            .sorted()

        if (sortedDates.isEmpty()) return Pair(0, 0)

        // 1. Calculate Maximum Streak
        var maxStreak = 1
        var tempStreak = 1
        for (i in 1 until sortedDates.size) {
            val prev = sortedDates[i-1]
            val curr = sortedDates[i]
            
            val diffMs = curr.time - prev.time
            val diffDays = (diffMs / (1000 * 60 * 60 * 24)).toInt()

            if (diffDays == 1) {
                tempStreak++
                if (tempStreak > maxStreak) {
                    maxStreak = tempStreak
                }
            } else if (diffDays > 1) {
                tempStreak = 1
            }
        }

        // 2. Calculate Current Streak relative to today/yesterday
        val todayCal = Calendar.getInstance()
        clearTime(todayCal)
        val todayTime = todayCal.timeTime()

        val yesterdayCal = Calendar.getInstance()
        yesterdayCal.add(Calendar.DAY_OF_YEAR, -1)
        clearTime(yesterdayCal)
        val yesterdayTime = yesterdayCal.timeTime()

        val compTimes = sortedDates.map { 
            val c = Calendar.getInstance()
            c.time = it
            clearTime(c)
            c.timeTime()
        }.toSet()

        var currentStreak = 0
        if (compTimes.contains(todayTime)) {
            currentStreak = 1
            val checkCal = Calendar.getInstance()
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
            clearTime(checkCal)
            while (compTimes.contains(checkCal.timeTime())) {
                currentStreak++
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            }
        } else if (compTimes.contains(yesterdayTime)) {
            currentStreak = 1
            val checkCal = Calendar.getInstance()
            checkCal.add(Calendar.DAY_OF_YEAR, -2)
            clearTime(checkCal)
            while (compTimes.contains(checkCal.timeTime())) {
                currentStreak++
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            }
        }

        return Pair(currentStreak, maxStreak)
    }

    private fun clearTime(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }

    private fun Calendar.timeTime(): Long = this.time.time

    // Populate database with sample habits and rich last-4-weeks completions
    private suspend fun seedDatabase() {
        // Create 5 standard Loop-like habits
        val h1 = Habit(name = "Drink 3L Water", description = "Stay hydrated throughout the day", colorHex = "#FF2196F3", targetDaysPerWeek = 7)
        val h2 = Habit(name = "8h Deep Sleep", description = "Keep standard sleep hygiene routine", colorHex = "#FF3F51B5", targetDaysPerWeek = 7)
        val h3 = Habit(name = "Read 15 Pages", description = "Non-fiction or self-improvement daily reading", colorHex = "#FF4CAF50", targetDaysPerWeek = 5)
        val h4 = Habit(name = "Gym / Core Cardio", description = "Active training sessions & strength exercise", colorHex = "#FFE91E63", targetDaysPerWeek = 4)
        val h5 = Habit(name = "Mindful Breathing", description = "10 minutes morning zen focus", colorHex = "#FF980000", targetDaysPerWeek = 6)

        val id1 = repository.insertHabit(h1).toInt()
        val id2 = repository.insertHabit(h2).toInt()
        val id3 = repository.insertHabit(h3).toInt()
        val id4 = repository.insertHabit(h4).toInt()
        val id5 = repository.insertHabit(h5).toInt()

        // Create random realistic completions for the last 30 days
        val ids = listOf(id1, id2, id3, id4, id5)
        // Probabilities for completion
        val probabilities = mapOf(
            id1 to 0.85, // 85% completions for water
            id2 to 0.75, // 75% sleep
            id3 to 0.60, // 60% reading
            id4 to 0.50, // 50% gym (roughly 4 days a week)
            id5 to 0.70  // 70% breathing
        )

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -28) // 4 weeks of history

        for (d in 0..28) {
            val dateStr = sdf.format(cal.time)
            ids.forEach { habitId ->
                val prob = probabilities[habitId] ?: 0.5
                if (Math.random() < prob) {
                    repository.addCompletion(habitId, dateStr)
                }
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    // --- HABIT FREQUENCY & ANALYTICS ---
    // (Future implementation)
}

class HabitViewModelFactory(
    private val application: Application,
    private val repository: HabitRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HabitViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HabitViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
