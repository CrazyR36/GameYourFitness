package com.gameyourfitness.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gameyourfitness.app.ui.home.HomeScreen
import com.gameyourfitness.app.ui.theme.GameYourFitnessTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GameYourFitnessTheme {
                HomeScreen()
            }
        }
    }
}
