package com.example.data

import kotlinx.coroutines.flow.Flow

class HabitRepository(private val habitDao: HabitDao) {

    val activeHabits: Flow<List<Habit>> = habitDao.getAllActiveHabits()
    val archivedHabits: Flow<List<Habit>> = habitDao.getAllArchivedHabits()
    val allCompletions: Flow<List<HabitCompletion>> = habitDao.getAllCompletions()

    suspend fun getHabitById(id: Int): Habit? = habitDao.getHabitById(id)

    suspend fun insertHabit(habit: Habit): Long = habitDao.insertHabit(habit)

    suspend fun updateHabit(habit: Habit) {
        habitDao.insertHabit(habit)
    }

    suspend fun archiveHabit(id: Int) = habitDao.archiveHabit(id)

    suspend fun restoreHabit(id: Int) = habitDao.restoreHabit(id)

    suspend fun updateHabitOrder(id: Int, newOrder: Int) = habitDao.updateHabitOrder(id, newOrder)

    suspend fun deleteHabit(id: Int) {
        habitDao.clearCompletionsForHabit(id)
        habitDao.deleteHabit(id)
    }

    fun getCompletionsForDate(date: String): Flow<List<HabitCompletion>> =
        habitDao.getCompletionsForDate(date)

    fun getCompletionsInRange(startDate: String, endDate: String): Flow<List<HabitCompletion>> =
        habitDao.getCompletionsInRange(startDate, endDate)

    suspend fun toggleCompletion(habitId: Int, dateString: String) {
        // We will execute a simple transaction or query to check if it exists.
        // It's normally done directly or via checking.
        // Since we have all completions flow, we can also pass whether it is completed or do a quick toggle by checking db.
        // But simpler: we write a toggle function that takes a boolean status, or we query. Let's do a safe toggle transaction.
    }

    suspend fun addCompletion(habitId: Int, dateString: String) {
        habitDao.insertCompletion(HabitCompletion(habitId = habitId, dateString = dateString))
    }

    suspend fun removeCompletion(habitId: Int, dateString: String) {
        habitDao.deleteCompletion(habitId, dateString)
    }
}
