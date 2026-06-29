package com.tripplanner.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tripplanner.app.data.local.entity.MapPresetEntity
import com.tripplanner.app.data.local.entity.MapPresetItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MapPresetDao {
    @Query("SELECT * FROM map_presets WHERE trip_id = :tripId ORDER BY updated_at_millis DESC")
    fun observePresetsForTrip(tripId: Long): Flow<List<MapPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPreset(preset: MapPresetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPresetItems(items: List<MapPresetItemEntity>)
}
