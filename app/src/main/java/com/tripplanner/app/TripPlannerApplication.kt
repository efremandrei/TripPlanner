package com.tripplanner.app

import android.app.Application
import android.app.UiModeManager
import android.os.Build
import com.google.android.libraries.places.api.Places
import com.tripplanner.app.data.local.TripPlannerDatabase

class TripPlannerApplication : Application() {
    val database: TripPlannerDatabase by lazy {
        TripPlannerDatabase.create(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val skin = getSharedPreferences("trip_planner_ui", MODE_PRIVATE).getString("skin", "Dark")
            val mode = when (skin) {
                "Light" -> UiModeManager.MODE_NIGHT_NO
                "Dark" -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
            getSystemService(UiModeManager::class.java).setApplicationNightMode(mode)
        }
        if (BuildConfig.PLACES_API_KEY.isNotBlank() && !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, BuildConfig.PLACES_API_KEY)
        }
    }
}
