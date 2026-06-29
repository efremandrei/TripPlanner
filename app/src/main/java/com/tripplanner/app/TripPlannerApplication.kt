package com.tripplanner.app

import android.app.Application
import com.google.android.libraries.places.api.Places
import com.tripplanner.app.data.local.TripPlannerDatabase

class TripPlannerApplication : Application() {
    val database: TripPlannerDatabase by lazy {
        TripPlannerDatabase.create(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.PLACES_API_KEY.isNotBlank() && !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, BuildConfig.PLACES_API_KEY)
        }
    }
}
