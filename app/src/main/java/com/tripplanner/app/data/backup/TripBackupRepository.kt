package com.tripplanner.app.data.backup

import android.content.Context
import android.database.Cursor
import android.os.Environment
import android.util.Base64
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tripplanner.app.data.TripRepository
import com.tripplanner.app.data.local.TripPlannerDatabase
import com.tripplanner.app.model.TripObjectAttribute
import com.tripplanner.app.model.TripObjectDraft
import com.tripplanner.app.model.TripObjectType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class BackupOperationResult(
    val message: String,
    val file: File? = null
)

class TripBackupRepository(
    context: Context,
    private val database: TripPlannerDatabase
) {
    private val tripRepository = TripRepository(database)
    private val exportDirectory: File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,
        EXPORT_DIRECTORY_NAME
    ).apply { mkdirs() }

    suspend fun populateMockDatabase(): BackupOperationResult {
        val firstTripId = seedTokyoTrip()
        val sharedFamilyMember = database.poolItemDao()
            .findGeneralPoolItem(MOCK_FAMILY_MEMBER_NAME, TripObjectType.FAMILY_MEMBER)
        val sharedAirport = database.poolItemDao()
            .findGeneralPoolItem(MOCK_AIRPORT_NAME, TripObjectType.TRANSPORTATION)
        val londonTrip = tripRepository.createTrip(
            destination = "London, United Kingdom",
            startDate = "2026-09-10",
            endDate = "2026-09-15",
            objects = londonMockObjects(
                sharedFamilyMemberId = sharedFamilyMember?.id,
                sharedAirportId = sharedAirport?.id
            )
        )

        return BackupOperationResult(
            message = "Mock DB populated: trips #$firstTripId and #${londonTrip.tripId}"
        )
    }

    suspend fun exportWholeDatabase(): BackupOperationResult {
        val backupJson = database.withTransaction {
            JSONObject()
                .put("formatVersion", FORMAT_VERSION)
                .put("exportType", EXPORT_TYPE_WHOLE_DATABASE)
                .put("exportedAtMillis", System.currentTimeMillis())
                .put("tables", exportTables(database.openHelper.writableDatabase))
        }
        val file = exportFile(
            prefix = "trip-planner-whole-db",
            json = backupJson
        )
        return BackupOperationResult(
            message = "Whole DB exported to ${file.absolutePath}",
            file = file
        )
    }

    suspend fun importLatestWholeDatabase(): BackupOperationResult {
        val file = latestExportFile(prefix = "trip-planner-whole-db")
            ?: return BackupOperationResult("No whole DB export file found in ${exportDirectory.absolutePath}")
        val backupJson = JSONObject(file.readText())
        require(backupJson.optString("exportType") == EXPORT_TYPE_WHOLE_DATABASE) {
            "Selected file is not a whole DB export"
        }
        database.withTransaction {
            importTables(
                db = database.openHelper.writableDatabase,
                tablesJson = backupJson.getJSONObject("tables")
            )
        }
        return BackupOperationResult(
            message = "Whole DB imported from ${file.name}",
            file = file
        )
    }

    suspend fun exportTrip(tripId: Long): BackupOperationResult {
        val editableTrip = tripRepository.getEditableTrip(tripId)
            ?: return BackupOperationResult("Trip #$tripId was not found")
        val tripJson = JSONObject()
            .put("id", editableTrip.trip.id)
            .put("title", editableTrip.trip.title)
            .put("destination", editableTrip.trip.destination)
            .put("startDate", editableTrip.trip.startDate)
            .put("endDate", editableTrip.trip.endDate)
            .put("isArchived", editableTrip.trip.isArchived)
        val backupJson = JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("exportType", EXPORT_TYPE_TRIP)
            .put("exportedAtMillis", System.currentTimeMillis())
            .put("trip", tripJson)
            .put("objects", JSONArray(editableTrip.objects.map(::tripObjectToJson)))
        val file = exportFile(
            prefix = "trip-planner-trip-${tripId}-${editableTrip.trip.destination.safeFilePart()}",
            json = backupJson
        )
        return BackupOperationResult(
            message = "Trip exported to ${file.absolutePath}",
            file = file
        )
    }

    suspend fun importLatestTripExport(): BackupOperationResult {
        val file = latestExportFile(prefix = "trip-planner-trip")
            ?: return BackupOperationResult("No trip export file found in ${exportDirectory.absolutePath}")
        val backupJson = JSONObject(file.readText())
        require(backupJson.optString("exportType") == EXPORT_TYPE_TRIP) {
            "Selected file is not a trip export"
        }
        val tripJson = backupJson.getJSONObject("trip")
        val objects = parseTripObjects(backupJson.getJSONArray("objects"))
        val importedObjects = remapImportedObjects(objects)
        val result = tripRepository.createTrip(
            destination = tripJson.optString("destination", "Imported trip"),
            startDate = tripJson.optString("startDate"),
            endDate = tripJson.optString("endDate"),
            objects = importedObjects
        )
        return BackupOperationResult(
            message = "Trip imported from ${file.name} as #${result.tripId}",
            file = file
        )
    }

    fun exportDirectoryPath(): String = exportDirectory.absolutePath

    private suspend fun seedTokyoTrip(): Long {
        val result = tripRepository.createTrip(
            destination = "Tokyo, Japan",
            startDate = "2026-07-15",
            endDate = "2026-07-28",
            objects = tokyoMockObjects()
        )
        return result.tripId
    }

    private fun tokyoMockObjects(): List<TripObjectDraft> {
        val sarahId = -1L
        val airportId = -2L
        val hotelId = -3L
        val gardenId = -4L
        val ramenId = -5L
        return listOf(
            TripObjectDraft(
                id = sarahId,
                type = TripObjectType.FAMILY_MEMBER,
                name = MOCK_FAMILY_MEMBER_NAME,
                priorityOrder = 1,
                attributes = mapOf(
                    TripObjectAttribute.PASSPORT_NUMBER to "MOCK123456",
                    TripObjectAttribute.RELATION to "Parent",
                    TripObjectAttribute.AGE to "34",
                    TripObjectAttribute.SEX to "Female",
                    TripObjectAttribute.NOTES to "Mock reusable family member"
                ),
                relatedObjectIds = emptySet()
            ),
            TripObjectDraft(
                id = airportId,
                type = TripObjectType.TRANSPORTATION,
                name = MOCK_AIRPORT_NAME,
                priorityOrder = 2,
                attributes = mapOf(
                    TripObjectAttribute.TRANSPORTATION_MODE to "Commercial plane",
                    TripObjectAttribute.DEPARTURE_LOCATION to "Ben Gurion Airport, Israel",
                    TripObjectAttribute.DEPARTURE_LATITUDE to "32.0055",
                    TripObjectAttribute.DEPARTURE_LONGITUDE to "34.8854",
                    TripObjectAttribute.STOPS_LOCATIONS to "Reusable mock hub"
                ),
                relatedObjectIds = setOf(sarahId)
            ),
            TripObjectDraft(
                id = hotelId,
                type = TripObjectType.HOTEL,
                name = "Park Hyatt Tokyo",
                priorityOrder = 3,
                attributes = mapOf(
                    TripObjectAttribute.ADDRESS to "3-7-1-2 Nishi Shinjuku, Tokyo",
                    TripObjectAttribute.LATITUDE to "35.6852",
                    TripObjectAttribute.LONGITUDE to "139.6909",
                    TripObjectAttribute.WEBSITE_URL to "https://www.hyatt.com",
                    TripObjectAttribute.PHONE_NUMBER to "+81 3 5322 1234",
                    TripObjectAttribute.CHECK_IN to "2026-07-16 15:00",
                    TripObjectAttribute.CHECK_OUT to "2026-07-28 11:00",
                    TripObjectAttribute.IS_WITH_BREAKFAST to "Yes",
                    TripObjectAttribute.ROOM_NUMBER to "Mock 1707"
                ),
                relatedObjectIds = setOf(sarahId, airportId)
            ),
            TripObjectDraft(
                id = gardenId,
                type = TripObjectType.FREE_ATTRACTION,
                name = "Shinjuku Gyoen National Garden",
                priorityOrder = 4,
                attributes = mapOf(
                    TripObjectAttribute.DESCRIPTION to "Morning park visit",
                    TripObjectAttribute.ADDRESS to "11 Naitomachi, Shinjuku City, Tokyo",
                    TripObjectAttribute.LATITUDE to "35.6852",
                    TripObjectAttribute.LONGITUDE to "139.7100",
                    TripObjectAttribute.WORKING_HOURS to "09:00-17:30",
                    TripObjectAttribute.PLANNED_VISIT_DATE_TIME to "2026-07-17 09:00"
                ),
                relatedObjectIds = setOf(hotelId)
            ),
            TripObjectDraft(
                id = ramenId,
                type = TripObjectType.FOOD_PLACE,
                name = "Ichiran Shinjuku",
                priorityOrder = 5,
                attributes = mapOf(
                    TripObjectAttribute.FOOD_TYPE to "Ramen",
                    TripObjectAttribute.ADDRESS to "Shinjuku, Tokyo",
                    TripObjectAttribute.LATITUDE to "35.6938",
                    TripObjectAttribute.LONGITUDE to "139.7034",
                    TripObjectAttribute.WORKING_HOURS to "10:00-23:00",
                    TripObjectAttribute.ENGLISH_MENU_WITH_PRICES to "Classic ramen: 980 JPY"
                ),
                relatedObjectIds = setOf(gardenId)
            )
        )
    }

    private suspend fun londonMockObjects(
        sharedFamilyMemberId: Long?,
        sharedAirportId: Long?
    ): List<TripObjectDraft> {
        val sarahId = sharedFamilyMemberId ?: -1L
        val airportId = sharedAirportId ?: -2L
        val hotelId = -3L
        val museumId = -4L
        val shopId = -5L
        val familyMember = sharedFamilyMemberId?.let { tripRepository.getPoolItemDraft(it) }
            ?: tokyoMockObjects().first { it.name == MOCK_FAMILY_MEMBER_NAME }
        val airport = sharedAirportId?.let { tripRepository.getPoolItemDraft(it) }
            ?: tokyoMockObjects().first { it.name == MOCK_AIRPORT_NAME }

        return listOf(
            familyMember.copy(id = sarahId, priorityOrder = 1, relatedObjectIds = emptySet()),
            airport.copy(
                id = airportId,
                priorityOrder = 2,
                relatedObjectIds = setOf(sarahId)
            ),
            TripObjectDraft(
                id = hotelId,
                type = TripObjectType.HOTEL,
                name = "Mock Covent Garden Hotel",
                priorityOrder = 3,
                attributes = mapOf(
                    TripObjectAttribute.ADDRESS to "Covent Garden, London",
                    TripObjectAttribute.LATITUDE to "51.5117",
                    TripObjectAttribute.LONGITUDE to "-0.1240",
                    TripObjectAttribute.CHECK_IN to "2026-09-10 15:00",
                    TripObjectAttribute.CHECK_OUT to "2026-09-15 11:00",
                    TripObjectAttribute.IS_WITH_BREAKFAST to "No"
                ),
                relatedObjectIds = setOf(sarahId, airportId)
            ),
            TripObjectDraft(
                id = museumId,
                type = TripObjectType.FREE_ATTRACTION,
                name = "British Museum",
                priorityOrder = 4,
                attributes = mapOf(
                    TripObjectAttribute.DESCRIPTION to "Mock museum visit",
                    TripObjectAttribute.ADDRESS to "Great Russell St, London",
                    TripObjectAttribute.LATITUDE to "51.5194",
                    TripObjectAttribute.LONGITUDE to "-0.1270",
                    TripObjectAttribute.WORKING_HOURS to "10:00-17:00",
                    TripObjectAttribute.PLANNED_VISIT_DATE_TIME to "2026-09-11 10:30"
                ),
                relatedObjectIds = setOf(hotelId)
            ),
            TripObjectDraft(
                id = shopId,
                type = TripObjectType.SHOP,
                name = "Covent Garden Market",
                priorityOrder = 5,
                attributes = mapOf(
                    TripObjectAttribute.CATEGORY to "Gifts",
                    TripObjectAttribute.ADDRESS to "Covent Garden, London",
                    TripObjectAttribute.LATITUDE to "51.5120",
                    TripObjectAttribute.LONGITUDE to "-0.1223",
                    TripObjectAttribute.WORKING_HOURS to "10:00-20:00",
                    TripObjectAttribute.NOTES to "Mock shopping stop"
                ),
                relatedObjectIds = setOf(hotelId, museumId)
            )
        )
    }

    private fun exportTables(db: SupportSQLiteDatabase): JSONObject {
        val tablesJson = JSONObject()
        WHOLE_DATABASE_TABLES.forEach { table ->
            db.query("SELECT * FROM `$table`").use { cursor ->
                tablesJson.put(table, cursor.toJsonRows())
            }
        }
        return tablesJson
    }

    private fun importTables(
        db: SupportSQLiteDatabase,
        tablesJson: JSONObject
    ) {
        WHOLE_DATABASE_DELETE_ORDER.forEach { table ->
            db.execSQL("DELETE FROM `$table`")
        }
        WHOLE_DATABASE_INSERT_ORDER.forEach { table ->
            val rows = tablesJson.optJSONArray(table) ?: JSONArray()
            for (index in 0 until rows.length()) {
                insertJsonRow(
                    db = db,
                    table = table,
                    row = rows.getJSONObject(index)
                )
            }
        }
    }

    private fun insertJsonRow(
        db: SupportSQLiteDatabase,
        table: String,
        row: JSONObject
    ) {
        val columns = row.keys().asSequence().toList()
        if (columns.isEmpty()) return
        val quotedColumns = columns.joinToString(", ") { column -> "`$column`" }
        val placeholders = columns.joinToString(", ") { "?" }
        val bindArgs = columns.map { column -> row.toBindValue(column) }.toTypedArray()
        db.execSQL(
            "INSERT OR REPLACE INTO `$table` ($quotedColumns) VALUES ($placeholders)",
            bindArgs
        )
    }

    private fun Cursor.toJsonRows(): JSONArray {
        val rows = JSONArray()
        while (moveToNext()) {
            val row = JSONObject()
            for (index in 0 until columnCount) {
                row.put(getColumnName(index), valueAt(index))
            }
            rows.put(row)
        }
        return rows
    }

    private fun Cursor.valueAt(index: Int): Any {
        return when (getType(index)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_INTEGER -> getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
            Cursor.FIELD_TYPE_BLOB -> JSONObject().put(BLOB_KEY, Base64.encodeToString(getBlob(index), Base64.NO_WRAP))
            else -> getString(index)
        }
    }

    private fun JSONObject.toBindValue(column: String): Any? {
        if (isNull(column)) return null
        val value = get(column)
        return if (value is JSONObject && value.has(BLOB_KEY)) {
            Base64.decode(value.getString(BLOB_KEY), Base64.NO_WRAP)
        } else {
            value
        }
    }

    private fun tripObjectToJson(tripObject: TripObjectDraft): JSONObject {
        val attributesJson = JSONObject()
        tripObject.attributes.forEach { (attribute, value) ->
            attributesJson.put(attribute.name, value)
        }
        return JSONObject()
            .put("id", tripObject.id)
            .put("type", tripObject.type.name)
            .put("name", tripObject.name)
            .put("priorityOrder", tripObject.priorityOrder)
            .put("attributes", attributesJson)
            .put("relatedObjectIds", JSONArray(tripObject.relatedObjectIds.toList()))
    }

    private fun parseTripObjects(objectsJson: JSONArray): List<TripObjectDraft> {
        return (0 until objectsJson.length()).map { index ->
            val objectJson = objectsJson.getJSONObject(index)
            val attributesJson = objectJson.getJSONObject("attributes")
            val attributes = attributesJson.keys().asSequence().associate { attributeName ->
                TripObjectAttribute.valueOf(attributeName) to attributesJson.getString(attributeName)
            }
            val relatedObjectIdsJson = objectJson.getJSONArray("relatedObjectIds")
            TripObjectDraft(
                id = objectJson.getLong("id"),
                type = TripObjectType.valueOf(objectJson.getString("type")),
                name = objectJson.getString("name"),
                priorityOrder = objectJson.getInt("priorityOrder"),
                attributes = attributes,
                relatedObjectIds = (0 until relatedObjectIdsJson.length())
                    .map { relatedObjectIdsJson.getLong(it) }
                    .toSet()
            )
        }
    }

    private fun remapImportedObjects(objects: List<TripObjectDraft>): List<TripObjectDraft> {
        var nextId = -1L
        val idMap = objects.associate { tripObject ->
            tripObject.id to nextId--
        }
        return objects.map { tripObject ->
            val nextObjectId = idMap.getValue(tripObject.id)
            tripObject.copy(
                id = nextObjectId,
                relatedObjectIds = tripObject.relatedObjectIds
                    .mapNotNull { idMap[it] }
                    .filterNot { it == nextObjectId }
                    .toSet()
            )
        }
    }

    private fun exportFile(
        prefix: String,
        json: JSONObject
    ): File {
        val file = File(exportDirectory, "${prefix.safeFilePart()}-${timestamp()}.json")
        file.writeText(json.toString(2))
        return file
    }

    private fun latestExportFile(prefix: String): File? {
        return exportDirectory.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith(prefix) &&
                    file.extension.equals("json", ignoreCase = true)
            }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun String.safeFilePart(): String {
        return lowercase(Locale.US)
            .replace(Regex("[^a-z0-9.-]+"), "-")
            .trim('-')
            .ifBlank { "export" }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    }

    private companion object {
        const val FORMAT_VERSION = 1
        const val EXPORT_DIRECTORY_NAME = "trip-planner-exports"
        const val EXPORT_TYPE_WHOLE_DATABASE = "whole-database"
        const val EXPORT_TYPE_TRIP = "trip"
        const val BLOB_KEY = "__base64Blob"
        const val MOCK_FAMILY_MEMBER_NAME = "Sarah M."
        const val MOCK_AIRPORT_NAME = "Ben Gurion Airport"

        val WHOLE_DATABASE_TABLES = listOf(
            "trips",
            "google_place_cache",
            "pool_items",
            "item_pools",
            "pool_memberships",
            "pool_item_attributes",
            "pool_item_relations",
            "trip_objects",
            "trip_object_attributes",
            "trip_object_relations",
            "map_presets",
            "map_preset_items"
        )

        val WHOLE_DATABASE_INSERT_ORDER = WHOLE_DATABASE_TABLES

        val WHOLE_DATABASE_DELETE_ORDER = listOf(
            "map_preset_items",
            "map_presets",
            "trip_object_relations",
            "trip_object_attributes",
            "trip_objects",
            "pool_item_relations",
            "pool_item_attributes",
            "pool_memberships",
            "item_pools",
            "pool_items",
            "google_place_cache",
            "trips"
        )
    }
}
