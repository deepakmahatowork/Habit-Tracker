package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.HabitTrackerDashboard
import com.example.ui.HabitViewModel
import com.example.ui.HabitViewModelFactory
import com.example.ui.theme.MyApplicationTheme

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val app = application as HabitTrackerApplication
      val habitViewModel: HabitViewModel = viewModel(
          factory = HabitViewModelFactory(app, app.repository)
      )
      val darkThemeEnabled by habitViewModel.isDarkTheme.collectAsState()

      MyApplicationTheme(darkTheme = darkThemeEnabled) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          HabitTrackerDashboard(viewModel = habitViewModel)
        }
      }
    }
  }
}
