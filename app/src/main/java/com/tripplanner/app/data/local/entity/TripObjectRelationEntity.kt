package com.tripplanner.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "trip_object_relations",
    primaryKeys = ["object_id", "related_object_id"],
    foreignKeys = [
        ForeignKey(
            entity = TripObjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["object_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TripObjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["related_object_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("object_id"),
        Index("related_object_id")
    ]
)
data class TripObjectRelationEntity(
    @ColumnInfo(name = "object_id")
    val objectId: Long,
    @ColumnInfo(name = "related_object_id")
    val relatedObjectId: Long
)
