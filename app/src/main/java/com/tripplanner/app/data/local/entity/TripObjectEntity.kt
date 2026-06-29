package com.tripplanner.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tripplanner.app.model.TripObjectType

@Entity(
    tableName = "trip_objects",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("trip_id"),
        Index(value = ["trip_id", "priority_order"], unique = true)
    ]
)
data class TripObjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "trip_id")
    val tripId: Long,
    val type: TripObjectType,
    val name: String,
    @ColumnInfo(name = "priority_order")
    val priorityOrder: Int,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis")
    val updatedAtMillis: Long
)
