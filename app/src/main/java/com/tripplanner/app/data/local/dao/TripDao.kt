package com.tripplanner.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.tripplanner.app.data.local.entity.TripEntity
import com.tripplanner.app.data.local.model.TripWithObjects
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE is_archived = 0 ORDER BY updated_at_millis DESC")
    fun observeActiveTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE is_archived = 1 ORDER BY updated_at_millis DESC")
    fun observeArchivedTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTrip(tripId: Long): TripEntity?

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun countTrips(): Int

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTripWithObjects(tripId: Long): TripWithObjects?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrip(trip: TripEntity): Long

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("UPDATE trips SET is_archived = 1, updated_at_millis = :updatedAtMillis WHERE id = :tripId")
    suspend fun archiveTrip(tripId: Long, updatedAtMillis: Long)
}
