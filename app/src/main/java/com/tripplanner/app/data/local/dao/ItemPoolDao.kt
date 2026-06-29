package com.tripplanner.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tripplanner.app.data.local.entity.ItemPoolEntity
import com.tripplanner.app.data.local.entity.ItemPoolType

@Dao
interface ItemPoolDao {
    @Query("SELECT * FROM item_pools WHERE type = :type AND trip_id IS NULL LIMIT 1")
    suspend fun getPoolWithoutTrip(type: ItemPoolType): ItemPoolEntity?

    @Query("SELECT * FROM item_pools WHERE type = :type AND trip_id = :tripId LIMIT 1")
    suspend fun getPoolForTrip(type: ItemPoolType, tripId: Long): ItemPoolEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPool(pool: ItemPoolEntity): Long
}
