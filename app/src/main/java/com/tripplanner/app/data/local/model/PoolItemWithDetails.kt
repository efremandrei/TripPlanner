package com.tripplanner.app.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.tripplanner.app.data.local.entity.PoolItemAttributeEntity
import com.tripplanner.app.data.local.entity.PoolItemEntity
import com.tripplanner.app.data.local.entity.PoolItemRelationEntity

data class PoolItemWithDetails(
    @Embedded
    val item: PoolItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "item_id"
    )
    val attributes: List<PoolItemAttributeEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "item_id"
    )
    val relations: List<PoolItemRelationEntity>
)
