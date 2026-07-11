package com.tripplanner.app.data.google

data class GooglePlaceDetails(
    val placeId: String,
    val displayName: String?,
    val formattedAddress: String?,
    val latitude: Double?,
    val longitude: Double?,
    val phoneNumber: String?,
    val websiteUrl: String?,
    val googleMapsUri: String?,
    val openingHours: String?
)

data class GooglePlaceSearchResult(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String?
)
