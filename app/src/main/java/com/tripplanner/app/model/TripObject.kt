package com.tripplanner.app.model

data class TripObjectDraft(
    val id: Long,
    val type: TripObjectType,
    val name: String,
    val priorityOrder: Int,
    val attributes: Map<TripObjectAttribute, String>,
    val relatedObjectIds: Set<Long>
)
