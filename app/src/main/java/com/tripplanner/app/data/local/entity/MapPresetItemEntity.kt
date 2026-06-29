package com.tripplanner.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "map_preset_items",
    primaryKeys = ["preset_id", "object_id"],
    foreignKeys = [
        ForeignKey(
            entity = MapPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["preset_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TripObjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["object_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("object_id")]
)
data class MapPresetItemEntity(
    @ColumnInfo(name = "preset_id")
    val presetId: Long,
    @ColumnInfo(name = "object_id")
    val objectId: Long,
    @ColumnInfo(name = "priority_order")
    val priorityOrder: Int
)
