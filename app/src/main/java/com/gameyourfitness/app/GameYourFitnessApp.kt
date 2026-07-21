package com.gameyourfitness.app

import android.app.Application
import com.gameyourfitness.app.data.auth.google.CurrentActivityProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GameYourFitnessApp : Application() {
    @Inject
    lateinit var currentActivityProvider: CurrentActivityProvider

    override fun onCreate() {
        super.onCreate()
        // Verfolgt die sichtbare Activity, damit der Credential Manager seinen
        // Dialog aus der data-Schicht heraus anzeigen kann.
        currentActivityProvider.register(this)
    }
}
