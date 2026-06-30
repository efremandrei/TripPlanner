package com.tripplanner.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.tripplanner.app.data.local.entity.PoolItemAttributeEntity
import com.tripplanner.app.data.local.entity.PoolItemEntity
import com.tripplanner.app.data.local.entity.PoolItemRelationEntity
import com.tripplanner.app.data.local.entity.PoolMembershipEntity
import com.tripplanner.app.data.local.model.PoolItemWithDetails
import com.tripplanner.app.model.TripObjectType
import kotlinx.coroutines.flow.Flow

@Dao
interface PoolItemDao {
    @Query(
        """
        SELECT pool_items.*
        FROM pool_items
        INNER JOIN pool_memberships ON pool_memberships.item_id = pool_items.id
        INNER JOIN item_pools ON item_pools.id = pool_memberships.pool_id
        WHERE item_pools.type = 'GENERAL'
        ORDER BY pool_memberships.priority_order ASC
        """
    )
    fun observeGeneralPoolItems(): Flow<List<PoolItemEntity>>

    @Query(
        """
        SELECT pool_items.*
        FROM pool_items
        INNER JOIN pool_memberships ON pool_memberships.item_id = pool_items.id
        INNER JOIN item_pools ON item_pools.id = pool_memberships.pool_id
        WHERE item_pools.type = 'TRIP'
            AND item_pools.trip_id = :tripId
        ORDER BY pool_memberships.priority_order ASC
        """
    )
    fun observeTripPoolItems(tripId: Long): Flow<List<PoolItemEntity>>

    @Query(
        """
        SELECT pool_items.*
        FROM pool_items
        INNER JOIN pool_memberships ON pool_memberships.item_id = pool_items.id
        INNER JOIN item_pools ON item_pools.id = pool_memberships.pool_id
        WHERE item_pools.type = 'GENERAL'
            AND pool_items.type = :type
            AND pool_items.name = :name
        LIMIT 1
        """
    )
    suspend fun findGeneralPoolItem(
        name: String,
        type: TripObjectType
    ): PoolItemEntity?

    @Query("SELECT * FROM pool_items WHERE id = :itemId")
    suspend fun getItem(itemId: Long): PoolItemEntity?

    @Transaction
    @Query("SELECT * FROM pool_items WHERE id = :itemId")
    suspend fun getItemWithDetails(itemId: Long): PoolItemWithDetails?

    @Query("SELECT * FROM pool_memberships WHERE pool_id = :poolId ORDER BY priority_order ASC")
    suspend fun getMembershipsForPool(poolId: Long): List<PoolMembershipEntity>

    @Query("SELECT * FROM pool_memberships WHERE pool_id = :poolId AND item_id = :itemId")
    suspend fun getMembership(poolId: Long, itemId: Long): PoolMembershipEntity?

    @Query("SELECT COALESCE(MAX(priority_order), 0) + 1 FROM pool_memberships WHERE pool_id = :poolId")
    suspend fun nextPriorityForPool(poolId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: PoolItemEntity): Long

    @Update
    suspend fun updateItem(item: PoolItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttributes(attributes: List<PoolItemAttributeEntity>)

    @Query("DELETE FROM pool_item_attributes WHERE item_id = :itemId")
    suspend fun deleteAttributesForItem(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelations(relations: List<PoolItemRelationEntity>)

    @Query(
        """
        DELETE FROM pool_item_relations
        WHERE item_id = :itemId OR related_item_id = :itemId
        """
    )
    suspend fun deleteRelationsForItem(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemberships(memberships: List<PoolMembershipEntity>)

    @Query("DELETE FROM pool_memberships WHERE pool_id = :poolId")
    suspend fun deleteMembershipsForPool(poolId: Long)

    @Transaction
    suspend fun replaceItemAttributes(
        itemId: Long,
        attributes: List<PoolItemAttributeEntity>
    ) {
        deleteAttributesForItem(itemId)
        if (attributes.isNotEmpty()) {
            upsertAttributes(attributes)
        }
    }

    @Transaction
    suspend fun replaceItemRelations(
        itemId: Long,
        relatedItemIds: Set<Long>
    ) {
        deleteRelationsForItem(itemId)
        if (relatedItemIds.isEmpty()) return

        val bidirectionalRelations = relatedItemIds.flatMap { relatedItemId ->
            listOf(
                PoolItemRelationEntity(
                    itemId = itemId,
                    relatedItemId = relatedItemId
                ),
                PoolItemRelationEntity(
                    itemId = relatedItemId,
                    relatedItemId = itemId
                )
            )
        }
        upsertRelations(bidirectionalRelations)
    }
}
