package com.tripplanner.app.data.google

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FetchPlaceResponse
import com.tripplanner.app.BuildConfig
import com.tripplanner.app.data.local.dao.GooglePlaceCacheDao
import com.tripplanner.app.data.local.entity.GooglePlaceCacheEntity
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class GooglePlacesRepository(
    private val context: Context,
    private val cacheDao: GooglePlaceCacheDao
) {
    val isConfigured: Boolean
        get() = BuildConfig.PLACES_API_KEY.isNotBlank() && Places.isInitialized()

    suspend fun fetchAndCachePlace(placeId: String): GooglePlaceDetails {
        val normalizedPlaceId = placeId.trim()
        require(normalizedPlaceId.isNotBlank()) { "Place ID is required" }

        if (!isConfigured) {
            return cacheDao.getPlace(normalizedPlaceId)?.toDetails()
                ?: error("Places API key is not configured")
        }

        val request = FetchPlaceRequest.newInstance(normalizedPlaceId, PLACE_FIELDS)
        val response = Places.createClient(context).fetchPlace(request).await()
        val details = response.place.toDetails(normalizedPlaceId)
        cacheDao.upsertPlace(details.toCacheEntity(System.currentTimeMillis()))
        return details
    }

    suspend fun getCachedPlace(placeId: String): GooglePlaceDetails? {
        return cacheDao.getPlace(placeId.trim())?.toDetails()
    }

    private fun Place.toDetails(requestedPlaceId: String): GooglePlaceDetails {
        val location = getLocation()
        val openingHoursText = getCurrentOpeningHours()?.getWeekdayText()
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n")
            ?: getOpeningHours()?.getWeekdayText()
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "\n")

        return GooglePlaceDetails(
            placeId = getId() ?: requestedPlaceId,
            displayName = getDisplayName(),
            formattedAddress = getFormattedAddress(),
            latitude = location?.latitude,
            longitude = location?.longitude,
            phoneNumber = getInternationalPhoneNumber() ?: getNationalPhoneNumber(),
            websiteUrl = getWebsiteUri()?.toString(),
            googleMapsUri = getGoogleMapsUri()?.toString(),
            openingHours = openingHoursText
        )
    }

    private fun GooglePlaceDetails.toCacheEntity(fetchedAtMillis: Long): GooglePlaceCacheEntity {
        return GooglePlaceCacheEntity(
            placeId = placeId,
            displayName = displayName,
            formattedAddress = formattedAddress,
            latitude = latitude,
            longitude = longitude,
            phoneNumber = phoneNumber,
            websiteUrl = websiteUrl,
            googleMapsUri = googleMapsUri,
            openingHours = openingHours,
            fetchedAtMillis = fetchedAtMillis
        )
    }

    private fun GooglePlaceCacheEntity.toDetails(): GooglePlaceDetails {
        return GooglePlaceDetails(
            placeId = placeId,
            displayName = displayName,
            formattedAddress = formattedAddress,
            latitude = latitude,
            longitude = longitude,
            phoneNumber = phoneNumber,
            websiteUrl = websiteUrl,
            googleMapsUri = googleMapsUri,
            openingHours = openingHours
        )
    }

    private suspend fun <T> Task<T>.await(): T {
        return suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                continuation.resume(result)
            }
            addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
            addOnCanceledListener {
                continuation.cancel()
            }
        }
    }

    companion object {
        private val PLACE_FIELDS = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.LOCATION,
            Place.Field.GOOGLE_MAPS_URI,
            Place.Field.INTERNATIONAL_PHONE_NUMBER,
            Place.Field.NATIONAL_PHONE_NUMBER,
            Place.Field.WEBSITE_URI,
            Place.Field.CURRENT_OPENING_HOURS,
            Place.Field.OPENING_HOURS
        )
    }
}
