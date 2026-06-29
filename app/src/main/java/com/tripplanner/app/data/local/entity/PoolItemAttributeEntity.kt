package com.tripplanner.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.tripplanner.app.model.TripObjectAttribute

@Entity(
    tableName = "pool_item_attributes",
    primaryKeys = ["item_id", "attribute"],
    foreignKeys = [
        ForeignKey(
            entity = PoolItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("item_id")]
)
data class PoolItemAttributeEntity(
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    val attribute: TripObjectAttribute,
    val value: String
)
