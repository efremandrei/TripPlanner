package com.tripplanner.app.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.tripplanner.app.data.local.entity.TripObjectAttributeEntity
import com.tripplanner.app.data.local.entity.TripObjectEntity
import com.tripplanner.app.data.local.entity.TripObjectRelationEntity

data class TripObjectWithDetails(
    @Embedded
    val objectEntity: TripObjectEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "object_id"
    )
    val attributes: List<TripObjectAttributeEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "object_id"
    )
    val relations: List<TripObjectRelationEntity>
)
