package com.gameyourfitness.app.data.auth.google

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Der Credential Manager braucht fuer seinen Dialog eine Activity. Diese Klasse
 * verfolgt die aktuell sichtbare Activity ueber Lifecycle-Callbacks — so bleibt
 * die Activity aus dem Repository/Client heraus erreichbar, ohne dass die UI
 * sie durch die Schichten reichen muss.
 */
@Singleton
class CurrentActivityProvider @Inject constructor() : Application.ActivityLifecycleCallbacks {
    private var current = WeakReference<Activity>(null)

    val currentActivity: Activity?
        get() = current.get()

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (current.get() === activity) {
            current = WeakReference(null)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
