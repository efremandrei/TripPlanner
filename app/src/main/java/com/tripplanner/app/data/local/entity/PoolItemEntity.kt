package com.tripplanner.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tripplanner.app.model.TripObjectType

@Entity(tableName = "pool_items")
data class PoolItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: TripObjectType,
    val name: String,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis")
    val updatedAtMillis: Long
)
