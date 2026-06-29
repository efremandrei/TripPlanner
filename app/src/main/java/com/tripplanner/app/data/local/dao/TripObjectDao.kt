package com.tripplanner.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.tripplanner.app.data.local.entity.TripObjectAttributeEntity
import com.tripplanner.app.data.local.entity.TripObjectEntity
import com.tripplanner.app.data.local.entity.TripObjectRelationEntity
import com.tripplanner.app.data.local.model.TripObjectWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface TripObjectDao {
    @Query("SELECT * FROM trip_objects WHERE trip_id = :tripId ORDER BY priority_order ASC")
    fun observeObjectsForTrip(tripId: Long): Flow<List<TripObjectEntity>>

    @Transaction
    @Query("SELECT * FROM trip_objects WHERE id = :objectId")
    suspend fun getObjectWithDetails(objectId: Long): TripObjectWithDetails?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertObject(tripObject: TripObjectEntity): Long

    @Update
    suspend fun updateObject(tripObject: TripObjectEntity)

    @Query("DELETE FROM trip_objects WHERE id = :objectId")
    suspend fun deleteObject(objectId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttributes(attributes: List<TripObjectAttributeEntity>)

    @Query("DELETE FROM trip_object_attributes WHERE object_id = :objectId")
    suspend fun deleteAttributesForObject(objectId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelations(relations: List<TripObjectRelationEntity>)

    @Query(
        """
        DELETE FROM trip_object_relations
        WHERE object_id = :objectId OR related_object_id = :objectId
        """
    )
    suspend fun deleteRelationsForObject(objectId: Long)

    @Transaction
    suspend fun replaceObjectAttributes(
        objectId: Long,
        attributes: List<TripObjectAttributeEntity>
    ) {
        deleteAttributesForObject(objectId)
        if (attributes.isNotEmpty()) {
            upsertAttributes(attributes)
        }
    }

    @Transaction
    suspend fun replaceObjectRelations(
        objectId: Long,
        relatedObjectIds: Set<Long>
    ) {
        deleteRelationsForObject(objectId)
        if (relatedObjectIds.isEmpty()) return

        val bidirectionalRelations = relatedObjectIds.flatMap { relatedObjectId ->
            listOf(
                TripObjectRelationEntity(
                    objectId = objectId,
                    relatedObjectId = relatedObjectId
                ),
                TripObjectRelationEntity(
                    objectId = relatedObjectId,
                    relatedObjectId = objectId
                )
            )
        }
        upsertRelations(bidirectionalRelations)
    }
}
