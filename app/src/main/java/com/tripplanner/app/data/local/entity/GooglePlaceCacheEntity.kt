package com.tripplanner.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "google_place_cache")
data class GooglePlaceCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "place_id")
    val placeId: String,
    @ColumnInfo(name = "display_name")
    val displayName: String?,
    @ColumnInfo(name = "formatted_address")
    val formattedAddress: String?,
    @ColumnInfo(name = "latitude")
    val latitude: Double?,
    @ColumnInfo(name = "longitude")
    val longitude: Double?,
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String?,
    @ColumnInfo(name = "website_url")
    val websiteUrl: String?,
    @ColumnInfo(name = "google_maps_uri")
    val googleMapsUri: String?,
    @ColumnInfo(name = "opening_hours")
    val openingHours: String?,
    @ColumnInfo(name = "fetched_at_millis")
    val fetchedAtMillis: Long
)
