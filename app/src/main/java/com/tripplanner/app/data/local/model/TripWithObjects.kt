package com.tripplanner.app.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.tripplanner.app.data.local.entity.TripEntity
import com.tripplanner.app.data.local.entity.TripObjectEntity

data class TripWithObjects(
    @Embedded
    val trip: TripEntity,
    @Relation(
        entity = TripObjectEntity::class,
        parentColumn = "id",
        entityColumn = "trip_id"
    )
    val objects: List<TripObjectWithDetails>
)
