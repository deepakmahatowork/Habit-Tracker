package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.data.Habit
import com.example.data.HabitCompletion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TodayTasksWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Run update asynchronously
        val application = context.applicationContext as HabitTrackerApplication
        val repository = application.repository

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val activeHabitsList = repository.activeHabits.first()
                val allCompletionsList = repository.allCompletions.first()

                val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val displayDateFormatter = SimpleDateFormat("EEEE, MMM d", Locale.US)
                val todayStr = sdfDate.format(Date())
                val displayDate = displayDateFormatter.format(Date())

                val todayCompletionsSet = allCompletionsList
                    .filter { it.dateString == todayStr }
                    .map { it.habitId }
                    .toSet()

                for (appWidgetId in appWidgetIds) {
                    updateWidget(
                        context,
                        appWidgetManager,
                        appWidgetId,
                        activeHabitsList,
                        todayCompletionsSet,
                        displayDate
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        habits: List<Habit>,
        completions: Set<Int>,
        displayDate: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.today_tasks_widget)

        // Set date
        views.setTextViewText(R.id.widget_date, displayDate)

        // Calculate progress percentage
        val totalActive = habits.size
        val completedCount = habits.count { completions.contains(it.id) }
        val percentage = if (totalActive > 0) {
            (completedCount.toFloat() / totalActive.toFloat() * 100).toInt()
        } else {
            0
        }
        views.setTextViewText(R.id.widget_progress, "$percentage%")

        if (totalActive == 0) {
            views.setViewVisibility(R.id.widget_habits_container, View.GONE)
            views.setViewVisibility(R.id.widget_more_count, View.GONE)
            views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_habits_container, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty_text, View.GONE)

            // Setup the 5 rows
            val rowGroupIds = listOf(
                R.id.widget_habit_1 to (R.id.status_1 to R.id.name_1),
                R.id.widget_habit_2 to (R.id.status_2 to R.id.name_2),
                R.id.widget_habit_3 to (R.id.status_3 to R.id.name_3),
                R.id.widget_habit_4 to (R.id.status_4 to R.id.name_4),
                R.id.widget_habit_5 to (R.id.status_5 to R.id.name_5)
            )

            for (i in 0 until 5) {
                val (rowId, innerIds) = rowGroupIds[i]
                val (statusViewId, nameViewId) = innerIds

                if (i < habits.size) {
                    val habit = habits[i]
                    views.setViewVisibility(rowId, View.VISIBLE)
                    views.setTextViewText(nameViewId, habit.name)

                    val isCompleted = completions.contains(habit.id)
                    if (isCompleted) {
                        views.setInt(statusViewId, "setBackgroundResource", R.drawable.widget_status_checked_bg)
                    } else {
                        views.setInt(statusViewId, "setBackgroundResource", R.drawable.widget_status_unchecked_bg)
                    }
                } else {
                    views.setViewVisibility(rowId, View.GONE)
                }
            }

            // More count
            if (habits.size > 5) {
                views.setViewVisibility(R.id.widget_more_count, View.VISIBLE)
                val extraCount = habits.size - 5
                views.setTextViewText(R.id.widget_more_count, "+ $extraCount more habits pending")
            } else {
                views.setViewVisibility(R.id.widget_more_count, View.GONE)
            }
        }

        // Action when widget itself is clicked: launches active logger in application
        val mainActivityIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        // Instruct manager to perform update
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        fun triggerUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, TodayTasksWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            val intent = Intent(context, TodayTasksWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            context.sendBroadcast(intent)
        }
    }
}
