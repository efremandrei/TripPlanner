package com.tripplanner.app.data

import androidx.room.withTransaction
import com.tripplanner.app.data.local.TripPlannerDatabase
import com.tripplanner.app.data.local.entity.ItemPoolEntity
import com.tripplanner.app.data.local.entity.ItemPoolType
import com.tripplanner.app.data.local.entity.MapPresetEntity
import com.tripplanner.app.data.local.entity.MapPresetItemEntity
import com.tripplanner.app.data.local.entity.PoolItemAttributeEntity
import com.tripplanner.app.data.local.entity.PoolItemEntity
import com.tripplanner.app.data.local.entity.PoolMembershipEntity
import com.tripplanner.app.data.local.entity.TripEntity
import com.tripplanner.app.data.local.entity.TripObjectAttributeEntity
import com.tripplanner.app.data.local.entity.TripObjectEntity
import com.tripplanner.app.data.local.model.PoolItemWithDetails
import com.tripplanner.app.model.TripObjectAttribute
import com.tripplanner.app.model.TripObjectDraft

data class CreatedTripResult(
    val tripId: Long,
    val privateMapPresetId: Long?
)

data class EditableTripDraft(
    val trip: TripEntity,
    val objects: List<TripObjectDraft>
)

class TripRepository(
    private val database: TripPlannerDatabase
) {
    suspend fun createTrip(
        destination: String,
        startDate: String,
        endDate: String,
        objects: List<TripObjectDraft>
    ): CreatedTripResult {
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val tripTitle = destination.ifBlank { "Untitled trip" }
            val tripId = database.tripDao().insertTrip(
                TripEntity(
                    title = tripTitle,
                    destination = destination.trim(),
                    startDate = startDate.trim(),
                    endDate = endDate.trim(),
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )

            val tripPoolId = ensureTripPool(
                tripId = tripId,
                tripTitle = tripTitle,
                timestampMillis = now
            )
            val localToPoolItemIds = saveTripPoolObjects(
                tripPoolId = tripPoolId,
                objects = objects,
                timestampMillis = now
            )
            val pooledObjects = objects.withPoolItemIds(localToPoolItemIds)
            val privateMapPresetId = rebuildTripObjectMirrorAndMapPreset(
                tripId = tripId,
                tripTitle = tripTitle,
                objects = pooledObjects,
                timestampMillis = now
            )

            CreatedTripResult(
                tripId = tripId,
                privateMapPresetId = privateMapPresetId
            )
        }
    }

    suspend fun updateTrip(
        tripId: Long,
        destination: String,
        startDate: String,
        endDate: String,
        objects: List<TripObjectDraft>
    ): CreatedTripResult {
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val existingTrip = database.tripDao().getTrip(tripId)
                ?: error("Trip #$tripId was not found")
            val tripTitle = destination.ifBlank { "Untitled trip" }

            database.tripDao().updateTrip(
                existingTrip.copy(
                    title = tripTitle,
                    destination = destination.trim(),
                    startDate = startDate.trim(),
                    endDate = endDate.trim(),
                    updatedAtMillis = now
                )
            )

            val tripPoolId = ensureTripPool(
                tripId = tripId,
                tripTitle = tripTitle,
                timestampMillis = now
            )
            val localToPoolItemIds = saveTripPoolObjects(
                tripPoolId = tripPoolId,
                objects = objects,
                timestampMillis = now
            )
            val pooledObjects = objects.withPoolItemIds(localToPoolItemIds)
            val privateMapPresetId = rebuildTripObjectMirrorAndMapPreset(
                tripId = tripId,
                tripTitle = tripTitle,
                objects = pooledObjects,
                timestampMillis = now
            )

            CreatedTripResult(
                tripId = tripId,
                privateMapPresetId = privateMapPresetId
            )
        }
    }

    suspend fun getEditableTrip(tripId: Long): EditableTripDraft? {
        return database.withTransaction {
            val trip = database.tripDao().getTrip(tripId)
                ?: return@withTransaction null
            val now = System.currentTimeMillis()
            val tripPoolId = ensureTripPool(
                tripId = tripId,
                tripTitle = trip.title,
                timestampMillis = now
            )
            val memberships = database.poolItemDao().getMembershipsForPool(tripPoolId)
                .ifEmpty {
                    bootstrapTripPoolFromLegacyObjects(
                        trip = trip,
                        tripPoolId = tripPoolId,
                        timestampMillis = now
                    )
                }

            EditableTripDraft(
                trip = trip,
                objects = memberships.mapNotNull { membership ->
                    database.poolItemDao().getItemWithDetails(membership.itemId)
                        ?.toDraft(priorityOrder = membership.priorityOrder)
                }
            )
        }
    }

    suspend fun getPoolItemDraft(itemId: Long): TripObjectDraft? {
        return database.withTransaction {
            database.poolItemDao().getItemWithDetails(itemId)
                ?.toDraft(priorityOrder = 0)
        }
    }

    private suspend fun ensureGeneralPool(timestampMillis: Long): Long {
        database.itemPoolDao().getPoolWithoutTrip(ItemPoolType.GENERAL)?.let { return it.id }
        return database.itemPoolDao().insertPool(
            ItemPoolEntity(
                name = "General",
                type = ItemPoolType.GENERAL,
                tripId = null,
                isSystem = true,
                createdAtMillis = timestampMillis,
                updatedAtMillis = timestampMillis
            )
        )
    }

    private suspend fun ensureTripPool(
        tripId: Long,
        tripTitle: String,
        timestampMillis: Long
    ): Long {
        database.itemPoolDao().getPoolForTrip(ItemPoolType.TRIP, tripId)?.let { return it.id }
        return database.itemPoolDao().insertPool(
            ItemPoolEntity(
                name = "${tripTitle.ifBlank { "Untitled trip" }} trip pool",
                type = ItemPoolType.TRIP,
                tripId = tripId,
                isSystem = false,
                createdAtMillis = timestampMillis,
                updatedAtMillis = timestampMillis
            )
        )
    }

    private suspend fun saveTripPoolObjects(
        tripPoolId: Long,
        objects: List<TripObjectDraft>,
        timestampMillis: Long
    ): Map<Long, Long> {
        val generalPoolId = ensureGeneralPool(timestampMillis)
        val localToPoolItemIds = LinkedHashMap<Long, Long>()

        objects.sortedBy { it.priorityOrder }.forEach { draftObject ->
            val poolItemId = upsertPoolItem(
                draftObject = draftObject,
                timestampMillis = timestampMillis
            )
            localToPoolItemIds[draftObject.id] = poolItemId
            ensureGeneralMembership(
                generalPoolId = generalPoolId,
                itemId = poolItemId,
                timestampMillis = timestampMillis
            )
        }

        database.poolItemDao().deleteMembershipsForPool(tripPoolId)
        database.poolItemDao().upsertMemberships(
            objects.sortedBy { it.priorityOrder }.mapNotNull { draftObject ->
                val poolItemId = localToPoolItemIds[draftObject.id] ?: return@mapNotNull null
                PoolMembershipEntity(
                    poolId = tripPoolId,
                    itemId = poolItemId,
                    priorityOrder = draftObject.priorityOrder,
                    createdAtMillis = timestampMillis,
                    updatedAtMillis = timestampMillis
                )
            }
        )

        objects.forEach { draftObject ->
            val poolItemId = localToPoolItemIds[draftObject.id] ?: return@forEach
            val relatedPoolItemIds = draftObject.relatedObjectIds
                .mapNotNull { relatedObjectId ->
                    localToPoolItemIds[relatedObjectId] ?: relatedObjectId.takeIf { it > 0 }
                }
                .filterNot { it == poolItemId }
                .toSet()
            database.poolItemDao().replaceItemRelations(
                itemId = poolItemId,
                relatedItemIds = relatedPoolItemIds
            )
        }

        return localToPoolItemIds
    }

    private suspend fun upsertPoolItem(
        draftObject: TripObjectDraft,
        timestampMillis: Long
    ): Long {
        val existingItem = draftObject.id.takeIf { it > 0 }
            ?.let { database.poolItemDao().getItem(it) }

        val itemId = if (existingItem == null) {
            database.poolItemDao().insertItem(
                PoolItemEntity(
                    type = draftObject.type,
                    name = draftObject.name,
                    createdAtMillis = timestampMillis,
                    updatedAtMillis = timestampMillis
                )
            )
        } else {
            database.poolItemDao().updateItem(
                existingItem.copy(
                    type = draftObject.type,
                    name = draftObject.name,
                    updatedAtMillis = timestampMillis
                )
            )
            existingItem.id
        }

        database.poolItemDao().replaceItemAttributes(
            itemId = itemId,
            attributes = draftObject.attributes.map { (attribute, value) ->
                PoolItemAttributeEntity(
                    itemId = itemId,
                    attribute = attribute,
                    value = value
                )
            }
        )
        return itemId
    }

    private suspend fun ensureGeneralMembership(
        generalPoolId: Long,
        itemId: Long,
        timestampMillis: Long
    ) {
        if (database.poolItemDao().getMembership(generalPoolId, itemId) != null) return

        database.poolItemDao().upsertMemberships(
            listOf(
                PoolMembershipEntity(
                    poolId = generalPoolId,
                    itemId = itemId,
                    priorityOrder = database.poolItemDao().nextPriorityForPool(generalPoolId),
                    createdAtMillis = timestampMillis,
                    updatedAtMillis = timestampMillis
                )
            )
        )
    }

    private suspend fun bootstrapTripPoolFromLegacyObjects(
        trip: TripEntity,
        tripPoolId: Long,
        timestampMillis: Long
    ): List<PoolMembershipEntity> {
        val tripWithObjects = database.tripDao().getTripWithObjects(trip.id)
            ?: return emptyList()
        if (tripWithObjects.objects.isEmpty()) return emptyList()

        val generalPoolId = ensureGeneralPool(timestampMillis)
        val legacyToPoolItemIds = LinkedHashMap<Long, Long>()
        val memberships = tripWithObjects.objects.map { legacyObject ->
            val existingPoolItem = database.poolItemDao().getItem(legacyObject.objectEntity.id)
            val poolItemId = existingPoolItem?.id ?: database.poolItemDao().insertItem(
                PoolItemEntity(
                    type = legacyObject.objectEntity.type,
                    name = legacyObject.objectEntity.name,
                    createdAtMillis = legacyObject.objectEntity.createdAtMillis,
                    updatedAtMillis = legacyObject.objectEntity.updatedAtMillis
                )
            )
            legacyToPoolItemIds[legacyObject.objectEntity.id] = poolItemId
            database.poolItemDao().replaceItemAttributes(
                itemId = poolItemId,
                attributes = legacyObject.attributes.map { attribute ->
                    PoolItemAttributeEntity(
                        itemId = poolItemId,
                        attribute = attribute.attribute,
                        value = attribute.value
                    )
                }
            )
            ensureGeneralMembership(
                generalPoolId = generalPoolId,
                itemId = poolItemId,
                timestampMillis = timestampMillis
            )
            PoolMembershipEntity(
                poolId = tripPoolId,
                itemId = poolItemId,
                priorityOrder = legacyObject.objectEntity.priorityOrder,
                createdAtMillis = legacyObject.objectEntity.createdAtMillis,
                updatedAtMillis = legacyObject.objectEntity.updatedAtMillis
            )
        }

        database.poolItemDao().upsertMemberships(memberships)
        tripWithObjects.objects.forEach { legacyObject ->
            val poolItemId = legacyToPoolItemIds[legacyObject.objectEntity.id] ?: return@forEach
            val relatedPoolItemIds = legacyObject.relations
                .mapNotNull { legacyToPoolItemIds[it.relatedObjectId] }
                .filterNot { it == poolItemId }
                .toSet()
            database.poolItemDao().replaceItemRelations(
                itemId = poolItemId,
                relatedItemIds = relatedPoolItemIds
            )
        }
        return database.poolItemDao().getMembershipsForPool(tripPoolId)
    }

    private suspend fun rebuildTripObjectMirrorAndMapPreset(
        tripId: Long,
        tripTitle: String,
        objects: List<TripObjectDraft>,
        timestampMillis: Long
    ): Long? {
        database.mapPresetDao().deletePresetsForTrip(tripId)
        database.tripObjectDao().deleteObjectsForTrip(tripId)
        val poolToTripObjectIds = insertTripObjects(
            tripId = tripId,
            objects = objects,
            timestampMillis = timestampMillis
        )
        return createPrivateMapPreset(
            tripId = tripId,
            tripTitle = tripTitle,
            objects = objects,
            localToStoredObjectIds = poolToTripObjectIds,
            createdAtMillis = timestampMillis
        )
    }

    private suspend fun insertTripObjects(
        tripId: Long,
        objects: List<TripObjectDraft>,
        timestampMillis: Long
    ): Map<Long, Long> {
        val localToStoredObjectIds = LinkedHashMap<Long, Long>()
        objects.sortedBy { it.priorityOrder }.forEach { draftObject ->
            val storedObjectId = database.tripObjectDao().insertObject(
                TripObjectEntity(
                    tripId = tripId,
                    type = draftObject.type,
                    name = draftObject.name,
                    priorityOrder = draftObject.priorityOrder,
                    createdAtMillis = timestampMillis,
                    updatedAtMillis = timestampMillis
                )
            )
            localToStoredObjectIds[draftObject.id] = storedObjectId
            database.tripObjectDao().replaceObjectAttributes(
                objectId = storedObjectId,
                attributes = draftObject.attributes.map { (attribute, value) ->
                    TripObjectAttributeEntity(
                        objectId = storedObjectId,
                        attribute = attribute,
                        value = value
                    )
                }
            )
        }

        objects.forEach { draftObject ->
            val storedObjectId = localToStoredObjectIds[draftObject.id] ?: return@forEach
            val relatedStoredObjectIds = draftObject.relatedObjectIds
                .mapNotNull { localToStoredObjectIds[it] }
                .toSet()
            database.tripObjectDao().replaceObjectRelations(
                objectId = storedObjectId,
                relatedObjectIds = relatedStoredObjectIds
            )
        }

        return localToStoredObjectIds
    }

    private suspend fun createPrivateMapPreset(
        tripId: Long,
        tripTitle: String,
        objects: List<TripObjectDraft>,
        localToStoredObjectIds: Map<Long, Long>,
        createdAtMillis: Long
    ): Long? {
        val presetObjects = objects
            .filter { it.hasPresetMapDetails() }
            .sortedBy { it.priorityOrder }
        if (presetObjects.isEmpty()) return null

        val presetId = database.mapPresetDao().insertPreset(
            MapPresetEntity(
                tripId = tripId,
                name = "$tripTitle private map",
                createdAtMillis = createdAtMillis,
                updatedAtMillis = createdAtMillis
            )
        )
        database.mapPresetDao().upsertPresetItems(
            presetObjects.mapNotNull { draftObject ->
                val storedObjectId = localToStoredObjectIds[draftObject.id] ?: return@mapNotNull null
                MapPresetItemEntity(
                    presetId = presetId,
                    objectId = storedObjectId,
                    priorityOrder = draftObject.priorityOrder
                )
            }
        )
        return presetId
    }

    private fun PoolItemWithDetails.toDraft(priorityOrder: Int): TripObjectDraft {
        return TripObjectDraft(
            id = item.id,
            type = item.type,
            name = item.name,
            priorityOrder = priorityOrder,
            attributes = attributes.associate { attribute ->
                attribute.attribute to attribute.value
            },
            relatedObjectIds = relations.map { relation ->
                relation.relatedItemId
            }.toSet()
        )
    }

    private fun List<TripObjectDraft>.withPoolItemIds(
        localToPoolItemIds: Map<Long, Long>
    ): List<TripObjectDraft> {
        return map { draftObject ->
            val poolItemId = localToPoolItemIds[draftObject.id] ?: draftObject.id
            draftObject.copy(
                id = poolItemId,
                relatedObjectIds = draftObject.relatedObjectIds
                    .mapNotNull { relatedObjectId ->
                        localToPoolItemIds[relatedObjectId] ?: relatedObjectId.takeIf { it > 0 }
                    }
                    .filterNot { it == poolItemId }
                    .toSet()
            )
        }
    }

    private fun TripObjectDraft.hasPresetMapDetails(): Boolean {
        return listOf(
            TripObjectAttribute.ADDRESS,
            TripObjectAttribute.LATITUDE,
            TripObjectAttribute.LONGITUDE,
            TripObjectAttribute.GOOGLE_PLACE_ID,
            TripObjectAttribute.GOOGLE_MAPS_URL,
            TripObjectAttribute.DEPARTURE_LOCATION,
            TripObjectAttribute.DEPARTURE_LATITUDE,
            TripObjectAttribute.DEPARTURE_LONGITUDE,
            TripObjectAttribute.ARRIVAL_LOCATION,
            TripObjectAttribute.ARRIVAL_LATITUDE,
            TripObjectAttribute.ARRIVAL_LONGITUDE
        ).any { attribute -> attributes[attribute].isNullOrBlank().not() }
    }
}
