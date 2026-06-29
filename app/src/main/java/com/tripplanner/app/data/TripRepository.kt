package com.tripplanner.app.data

import androidx.room.withTransaction
import com.tripplanner.app.data.local.TripPlannerDatabase
import com.tripplanner.app.data.local.entity.MapPresetEntity
import com.tripplanner.app.data.local.entity.MapPresetItemEntity
import com.tripplanner.app.data.local.entity.TripEntity
import com.tripplanner.app.data.local.entity.TripObjectAttributeEntity
import com.tripplanner.app.data.local.entity.TripObjectEntity
import com.tripplanner.app.model.TripObjectAttribute
import com.tripplanner.app.model.TripObjectDraft

data class CreatedTripResult(
    val tripId: Long,
    val privateMapPresetId: Long?
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

            val localToStoredObjectIds = LinkedHashMap<Long, Long>()
            objects.sortedBy { it.priorityOrder }.forEach { draftObject ->
                val storedObjectId = database.tripObjectDao().insertObject(
                    TripObjectEntity(
                        tripId = tripId,
                        type = draftObject.type,
                        name = draftObject.name,
                        priorityOrder = draftObject.priorityOrder,
                        createdAtMillis = now,
                        updatedAtMillis = now
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

            val privateMapPresetId = createPrivateMapPreset(
                tripId = tripId,
                tripTitle = tripTitle,
                objects = objects,
                localToStoredObjectIds = localToStoredObjectIds,
                createdAtMillis = now
            )

            CreatedTripResult(
                tripId = tripId,
                privateMapPresetId = privateMapPresetId
            )
        }
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
