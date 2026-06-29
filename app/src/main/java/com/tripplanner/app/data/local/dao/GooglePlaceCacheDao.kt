package com.tripplanner.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tripplanner.app.data.local.entity.GooglePlaceCacheEntity

@Dao
interface GooglePlaceCacheDao {
    @Query("SELECT * FROM google_place_cache WHERE place_id = :placeId")
    suspend fun getPlace(placeId: String): GooglePlaceCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlace(place: GooglePlaceCacheEntity)
}
