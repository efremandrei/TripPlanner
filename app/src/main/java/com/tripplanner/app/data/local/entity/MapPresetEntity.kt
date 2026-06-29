package com.tripplanner.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "map_presets",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trip_id")]
)
data class MapPresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "trip_id")
    val tripId: Long?,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "provider")
    val provider: String = PROVIDER_LOCAL,
    @ColumnInfo(name = "visibility")
    val visibility: String = VISIBILITY_PRIVATE,
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = SYNC_LOCAL_ONLY,
    @ColumnInfo(name = "external_map_id")
    val externalMapId: String? = null,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis")
    val updatedAtMillis: Long
) {
    companion object {
        const val PROVIDER_LOCAL = "LOCAL"
        const val VISIBILITY_PRIVATE = "PRIVATE"
        const val SYNC_LOCAL_ONLY = "LOCAL_ONLY"
    }
}
