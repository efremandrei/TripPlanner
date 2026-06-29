package com.tripplanner.app.util

import android.content.Intent
import android.net.Uri
import com.tripplanner.app.model.TripObjectAttribute
import com.tripplanner.app.model.TripObjectDraft
import com.tripplanner.app.model.TripObjectType
import java.util.Locale

object GoogleMapsIntents {
    fun openMapIntent(tripObjects: List<TripObjectDraft>): Intent? {
        val mapPoint = tripObjects
            .sortedBy { it.priorityOrder }
            .firstNotNullOfOrNull { it.toMapPoint() }
            ?: return null

        val encodedLabel = Uri.encode(mapPoint.label)
        val uri = Uri.parse(
            "geo:0,0?q=${mapPoint.latitude.formatCoordinate()}," +
                "${mapPoint.longitude.formatCoordinate()}($encodedLabel)"
        )
        return Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
    }

    private fun TripObjectDraft.toMapPoint(): MapPoint? {
        if (type == TripObjectType.TRANSPORTATION) {
            return mapPointFrom(
                latitudeAttribute = TripObjectAttribute.DEPARTURE_LATITUDE,
                longitudeAttribute = TripObjectAttribute.DEPARTURE_LONGITUDE,
                label = "$priorityOrder. $name departure"
            ) ?: mapPointFrom(
                latitudeAttribute = TripObjectAttribute.ARRIVAL_LATITUDE,
                longitudeAttribute = TripObjectAttribute.ARRIVAL_LONGITUDE,
                label = "$priorityOrder. $name arrival"
            )
        }

        return mapPointFrom(
            latitudeAttribute = TripObjectAttribute.LATITUDE,
            longitudeAttribute = TripObjectAttribute.LONGITUDE,
            label = "$priorityOrder. $name"
        )
    }

    private fun TripObjectDraft.mapPointFrom(
        latitudeAttribute: TripObjectAttribute,
        longitudeAttribute: TripObjectAttribute,
        label: String
    ): MapPoint? {
        val latitude = attributes[latitudeAttribute]?.toDoubleOrNull() ?: return null
        val longitude = attributes[longitudeAttribute]?.toDoubleOrNull() ?: return null
        return if (latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            MapPoint(latitude = latitude, longitude = longitude, label = label)
        } else {
            null
        }
    }

    private fun Double.formatCoordinate(): String {
        return String.format(Locale.US, "%.7f", this)
    }

    private data class MapPoint(
        val latitude: Double,
        val longitude: Double,
        val label: String
    )
}
