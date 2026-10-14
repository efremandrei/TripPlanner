package com.tripplanner.app.model

data class TripEditorSnapshot(
    val destination: String,
    val startDate: String,
    val endDate: String,
    val items: List<TripObjectDraft>
) {
    val canSave: Boolean get() = destination.isNotBlank() && validTripDates(startDate, endDate)
}
