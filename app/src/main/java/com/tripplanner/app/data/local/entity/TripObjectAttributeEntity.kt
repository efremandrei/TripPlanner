package com.tripplanner.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.tripplanner.app.model.TripObjectAttribute

@Entity(
    tableName = "trip_object_attributes",
    primaryKeys = ["object_id", "attribute"],
    foreignKeys = [
        ForeignKey(
            entity = TripObjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["object_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("object_id")]
)
data class TripObjectAttributeEntity(
    @ColumnInfo(name = "object_id")
    val objectId: Long,
    val attribute: TripObjectAttribute,
    val value: String
)
