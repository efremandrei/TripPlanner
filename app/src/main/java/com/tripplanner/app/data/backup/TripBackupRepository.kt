package com.tripplanner.app.data.backup

import android.content.Context
import android.database.Cursor
import android.net.Uri
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
    private val appContext = context.applicationContext
    private val mockSeedPreferences = appContext.getSharedPreferences(
        MOCK_SEED_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val tripRepository = TripRepository(database)
    private val exportDirectory: File = File(
        appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: appContext.filesDir,
        EXPORT_DIRECTORY_NAME
    ).apply { mkdirs() }

    suspend fun populateMockDatabaseIfEmpty(): BackupOperationResult {
        if (mockSeedPreferences.getBoolean(MOCK_SEED_CLEARED_KEY, false)) {
            return BackupOperationResult("Mock DB seed skipped: mocked data was cleared")
        }
        val tripCount = database.tripDao().countTrips()
        if (tripCount > 0) {
            return BackupOperationResult("Mock DB seed skipped: $tripCount trips already exist")
        }
        return populateMockDatabase()
    }

    suspend fun populateMockDatabase(): BackupOperationResult {
        mockSeedPreferences.edit()
            .putBoolean(MOCK_SEED_CLEARED_KEY, false)
            .apply()
        val englandTrip = tripRepository.createTrip(
            destination = "England family circuit: London, Oxford, Bath, York, Manchester",
            startDate = "2026-08-01",
            endDate = "2026-08-10",
            objects = englandFamilyTripObjects()
        )

        return BackupOperationResult(
            message = "Mock DB populated: 10-day England family trip #${englandTrip.tripId}"
        )
    }

    suspend fun clearMockedData(): BackupOperationResult {
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            clearAppDataTables(db)
            recreateGeneralPool(db)
        }
        mockSeedPreferences.edit()
            .putBoolean(MOCK_SEED_CLEARED_KEY, true)
            .apply()
        return BackupOperationResult(
            message = "Mocked data cleared. The General pool is ready for new items."
        )
    }

    suspend fun exportWholeDatabase(): BackupOperationResult {
        val backupJson = buildWholeDatabaseBackupJson()
        val file = exportFile(
            prefix = "trip-planner-whole-db",
            json = backupJson
        )
        return BackupOperationResult(
            message = "Whole DB exported to ${file.absolutePath}",
            file = file
        )
    }

    suspend fun exportWholeDatabaseToUri(uri: Uri): BackupOperationResult {
        val backupJson = buildWholeDatabaseBackupJson()
        writeJsonToUri(uri = uri, json = backupJson)
        return BackupOperationResult(
            message = "Whole DB exported to selected file"
        )
    }

    suspend fun importLatestWholeDatabase(): BackupOperationResult {
        val file = latestExportFile(prefix = "trip-planner-whole-db")
            ?: return BackupOperationResult("No whole DB export file found in ${exportDirectory.absolutePath}")
        importWholeDatabaseJson(JSONObject(file.readText()))
        return BackupOperationResult(
            message = "Whole DB imported from ${file.name}",
            file = file
        )
    }

    suspend fun importWholeDatabaseFromUri(uri: Uri): BackupOperationResult {
        importWholeDatabaseJson(JSONObject(readTextFromUri(uri)))
        return BackupOperationResult(
            message = "Whole DB imported from selected file"
        )
    }

    suspend fun exportTrip(tripId: Long): BackupOperationResult {
        val exportData = buildTripBackupJson(tripId)
            ?: return BackupOperationResult("Trip #$tripId was not found")
        val file = exportFile(
            prefix = "trip-planner-trip-${tripId}-${exportData.destination.safeFilePart()}",
            json = exportData.json
        )
        return BackupOperationResult(
            message = "Trip exported to ${file.absolutePath}",
            file = file
        )
    }

    suspend fun exportTripToUri(tripId: Long, uri: Uri): BackupOperationResult {
        val exportData = buildTripBackupJson(tripId)
            ?: return BackupOperationResult("Trip #$tripId was not found")
        writeJsonToUri(uri = uri, json = exportData.json)
        return BackupOperationResult(
            message = "Trip exported to selected file"
        )
    }

    suspend fun importLatestTripExport(): BackupOperationResult {
        val file = latestExportFile(prefix = "trip-planner-trip")
            ?: return BackupOperationResult("No trip export file found in ${exportDirectory.absolutePath}")
        val result = importTripJson(JSONObject(file.readText()))
        return BackupOperationResult(
            message = "Trip imported from ${file.name} as #${result.tripId}",
            file = file
        )
    }

    suspend fun importTripExportFromUri(uri: Uri): BackupOperationResult {
        val result = importTripJson(JSONObject(readTextFromUri(uri)))
        return BackupOperationResult(
            message = "Trip imported from selected file as #${result.tripId}"
        )
    }

    fun exportDirectoryPath(): String = exportDirectory.absolutePath

    fun wholeDatabaseExportFileName(): String {
        return "trip-planner-whole-db-${timestamp()}.json"
    }

    fun tripExportFileName(
        tripId: Long,
        destination: String
    ): String {
        return "trip-planner-trip-$tripId-${destination.safeFilePart()}-${timestamp()}.json"
    }

    private fun englandFamilyTripObjects(): List<TripObjectDraft> {
        val objects = mutableListOf<TripObjectDraft>()
        var nextId = -1L
        var nextPriority = 1

        fun addObject(
            type: TripObjectType,
            name: String,
            attributes: Map<TripObjectAttribute, String>,
            relatedObjectIds: Set<Long> = emptySet()
        ): Long {
            val id = nextId--
            objects += TripObjectDraft(
                id = id,
                type = type,
                name = name,
                priorityOrder = nextPriority++,
                attributes = attributes,
                relatedObjectIds = relatedObjectIds
            )
            return id
        }

        fun addFamilyMember(
            name: String,
            passportNumber: String,
            relation: String,
            age: Int,
            sex: String,
            notes: String
        ): Long {
            return addObject(
                type = TripObjectType.FAMILY_MEMBER,
                name = name,
                attributes = mapOf(
                    TripObjectAttribute.PASSPORT_NUMBER to passportNumber,
                    TripObjectAttribute.RELATION to relation,
                    TripObjectAttribute.AGE to age.toString(),
                    TripObjectAttribute.SEX to sex,
                    TripObjectAttribute.NOTES to notes
                )
            )
        }

        val fatherId = addFamilyMember(
            name = "Andrei Mock",
            passportNumber = "ENG-MOCK-1001",
            relation = "Father",
            age = 42,
            sex = "Male",
            notes = "Primary driver and booking contact"
        )
        val motherId = addFamilyMember(
            name = "Sarah Mock",
            passportNumber = "ENG-MOCK-1002",
            relation = "Mother",
            age = 39,
            sex = "Female",
            notes = "Keeps hotel confirmations and food preferences"
        )
        val sonId = addFamilyMember(
            name = "Noah Mock",
            passportNumber = "ENG-MOCK-1003",
            relation = "Child",
            age = 12,
            sex = "Male",
            notes = "Prefers museums, trains, and pizza"
        )
        val daughterId = addFamilyMember(
            name = "Maya Mock",
            passportNumber = "ENG-MOCK-1004",
            relation = "Child",
            age = 8,
            sex = "Female",
            notes = "Needs earlier dinners and short walking breaks"
        )
        val familyIds = setOf(fatherId, motherId, sonId, daughterId)

        fun familyAnd(vararg objectIds: Long): Set<Long> {
            return (familyIds + objectIds.toSet()).toSet()
        }

        fun addTransport(
            name: String,
            mode: String,
            departureLocation: String,
            departureLatitude: String,
            departureLongitude: String,
            departureDateTime: String,
            arrivalLocation: String,
            arrivalLatitude: String,
            arrivalLongitude: String,
            arrivalDateTime: String,
            price: String,
            stopsLocations: String,
            routeMap: String,
            relatedObjectIds: Set<Long>
        ): Long {
            return addObject(
                type = TripObjectType.TRANSPORTATION,
                name = name,
                attributes = mapOf(
                    TripObjectAttribute.TRANSPORTATION_MODE to mode,
                    TripObjectAttribute.DEPARTURE_LOCATION to departureLocation,
                    TripObjectAttribute.DEPARTURE_LATITUDE to departureLatitude,
                    TripObjectAttribute.DEPARTURE_LONGITUDE to departureLongitude,
                    TripObjectAttribute.DEPARTURE_DATE_TIME to departureDateTime,
                    TripObjectAttribute.ARRIVAL_LOCATION to arrivalLocation,
                    TripObjectAttribute.ARRIVAL_LATITUDE to arrivalLatitude,
                    TripObjectAttribute.ARRIVAL_LONGITUDE to arrivalLongitude,
                    TripObjectAttribute.ARRIVAL_DATE_TIME to arrivalDateTime,
                    TripObjectAttribute.PRICE to price,
                    TripObjectAttribute.STOPS_LOCATIONS to stopsLocations,
                    TripObjectAttribute.ROUTE_MAP to routeMap
                ),
                relatedObjectIds = relatedObjectIds
            )
        }

        fun addHotel(
            name: String,
            address: String,
            latitude: String,
            longitude: String,
            websiteUrl: String,
            phoneNumber: String,
            emailAddress: String,
            checkIn: String,
            checkOut: String,
            isWithBreakfast: String,
            roomNumber: String,
            relatedObjectIds: Set<Long>
        ): Long {
            return addObject(
                type = TripObjectType.HOTEL,
                name = name,
                attributes = mapOf(
                    TripObjectAttribute.ADDRESS to address,
                    TripObjectAttribute.LATITUDE to latitude,
                    TripObjectAttribute.LONGITUDE to longitude,
                    TripObjectAttribute.GOOGLE_MAPS_URL to "https://maps.google.com/?q=${name.safeUrlQuery()}",
                    TripObjectAttribute.WEBSITE_URL to websiteUrl,
                    TripObjectAttribute.PHONE_NUMBER to phoneNumber,
                    TripObjectAttribute.EMAIL_ADDRESS to emailAddress,
                    TripObjectAttribute.CHECK_IN to checkIn,
                    TripObjectAttribute.CHECK_OUT to checkOut,
                    TripObjectAttribute.IS_WITH_BREAKFAST to isWithBreakfast,
                    TripObjectAttribute.ROOM_NUMBER to roomNumber
                ),
                relatedObjectIds = relatedObjectIds
            )
        }

        fun addFoodPlace(
            name: String,
            foodType: String,
            address: String,
            latitude: String,
            longitude: String,
            websiteUrl: String,
            phoneNumber: String,
            emailAddress: String,
            workingHours: String,
            menuWithPrices: String,
            relatedObjectIds: Set<Long>
        ): Long {
            return addObject(
                type = TripObjectType.FOOD_PLACE,
                name = name,
                attributes = mapOf(
                    TripObjectAttribute.WEBSITE_URL to websiteUrl,
                    TripObjectAttribute.PHONE_NUMBER to phoneNumber,
                    TripObjectAttribute.EMAIL_ADDRESS to emailAddress,
                    TripObjectAttribute.FOOD_TYPE to foodType,
                    TripObjectAttribute.ADDRESS to address,
                    TripObjectAttribute.LATITUDE to latitude,
                    TripObjectAttribute.LONGITUDE to longitude,
                    TripObjectAttribute.GOOGLE_MAPS_URL to "https://maps.google.com/?q=${name.safeUrlQuery()}",
                    TripObjectAttribute.WORKING_HOURS to workingHours,
                    TripObjectAttribute.ENGLISH_MENU_WITH_PRICES to menuWithPrices
                ),
                relatedObjectIds = relatedObjectIds
            )
        }

        fun addAttraction(
            name: String,
            city: String,
            paid: Boolean,
            description: String,
            address: String,
            latitude: String,
            longitude: String,
            workingHours: String,
            websiteUrl: String,
            plannedVisitDateTime: String,
            ticketPrices: String = "Free",
            relatedObjectIds: Set<Long>
        ): Long {
            val attributes = mutableMapOf(
                TripObjectAttribute.DESCRIPTION to description,
                TripObjectAttribute.ADDRESS to address,
                TripObjectAttribute.LATITUDE to latitude,
                TripObjectAttribute.LONGITUDE to longitude,
                TripObjectAttribute.GOOGLE_MAPS_URL to "https://maps.google.com/?q=${name.safeUrlQuery()}+$city",
                TripObjectAttribute.WORKING_HOURS to workingHours,
                TripObjectAttribute.WEBSITE_URL to websiteUrl,
                TripObjectAttribute.PLANNED_VISIT_DATE_TIME to plannedVisitDateTime
            )
            if (paid) {
                attributes[TripObjectAttribute.TICKET_PRICES] = ticketPrices
            }
            return addObject(
                type = if (paid) TripObjectType.PAID_ATTRACTION else TripObjectType.FREE_ATTRACTION,
                name = name,
                attributes = attributes,
                relatedObjectIds = relatedObjectIds
            )
        }

        val arrivalFlightId = addTransport(
            name = "Flight TLV to London Heathrow",
            mode = "Commercial plane",
            departureLocation = "Ben Gurion Airport, Israel",
            departureLatitude = "32.0055",
            departureLongitude = "34.8854",
            departureDateTime = "2026-08-01 07:30",
            arrivalLocation = "London Heathrow Airport",
            arrivalLatitude = "51.4700",
            arrivalLongitude = "-0.4543",
            arrivalDateTime = "2026-08-01 11:05",
            price = "1800 GBP total",
            stopsLocations = "Direct flight",
            routeMap = "https://maps.google.com/?q=TLV+to+LHR",
            relatedObjectIds = familyIds
        )

        val londonHotelId = addHotel(
            name = "Mock London County Hall Hotel",
            address = "Belvedere Road, London SE1 7PB",
            latitude = "51.5010",
            longitude = "-0.1195",
            websiteUrl = "https://example.com/london-county-hall",
            phoneNumber = "+44 20 7000 0101",
            emailAddress = "london@example.com",
            checkIn = "2026-08-01 15:00",
            checkOut = "2026-08-03 10:00",
            isWithBreakfast = "Yes",
            roomNumber = "Family room LON-204",
            relatedObjectIds = familyAnd(arrivalFlightId)
        )
        val londonWalkId = addTransport(
            name = "London Westminster walking loop",
            mode = "By foot",
            departureLocation = "Mock London County Hall Hotel",
            departureLatitude = "51.5010",
            departureLongitude = "-0.1195",
            departureDateTime = "2026-08-01 15:30",
            arrivalLocation = "Westminster Bridge and South Bank",
            arrivalLatitude = "51.5007",
            arrivalLongitude = "-0.1246",
            arrivalDateTime = "2026-08-01 17:30",
            price = "Free",
            stopsLocations = "London Eye, Westminster Bridge, South Bank",
            routeMap = "https://maps.google.com/?q=London+South+Bank+walking+route",
            relatedObjectIds = familyAnd(londonHotelId)
        )
        val londonTowerId = addAttraction(
            name = "Tower of London",
            city = "London",
            paid = true,
            description = "Historic fortress and Crown Jewels visit",
            address = "Tower Hill, London EC3N 4AB",
            latitude = "51.5081",
            longitude = "-0.0759",
            workingHours = "09:00-17:30",
            websiteUrl = "https://www.hrp.org.uk/tower-of-london",
            plannedVisitDateTime = "2026-08-01 16:00",
            ticketPrices = "Mock: adult 36 GBP, child 18 GBP",
            relatedObjectIds = familyAnd(londonHotelId, londonWalkId)
        )
        val britishMuseumId = addAttraction(
            name = "British Museum",
            city = "London",
            paid = false,
            description = "World history museum visit focused on Egypt and Greece",
            address = "Great Russell Street, London WC1B 3DG",
            latitude = "51.5194",
            longitude = "-0.1270",
            workingHours = "10:00-17:00",
            websiteUrl = "https://www.britishmuseum.org",
            plannedVisitDateTime = "2026-08-02 10:00",
            relatedObjectIds = familyAnd(londonHotelId)
        )
        val londonEyeId = addAttraction(
            name = "London Eye",
            city = "London",
            paid = true,
            description = "Evening skyline ride",
            address = "Riverside Building, County Hall, London SE1 7PB",
            latitude = "51.5033",
            longitude = "-0.1195",
            workingHours = "10:00-20:30",
            websiteUrl = "https://www.londoneye.com",
            plannedVisitDateTime = "2026-08-02 17:00",
            ticketPrices = "Mock: adult 32 GBP, child 28 GBP",
            relatedObjectIds = familyAnd(londonHotelId, londonTowerId, britishMuseumId)
        )
        addFoodPlace(
            name = "Dishoom Covent Garden",
            foodType = "Indian",
            address = "12 Upper St Martin's Lane, London WC2H 9FB",
            latitude = "51.5129",
            longitude = "-0.1269",
            websiteUrl = "https://www.dishoom.com",
            phoneNumber = "+44 20 7420 9320",
            emailAddress = "coventgarden@example.com",
            workingHours = "08:00-23:00",
            menuWithPrices = "Mock: bacon naan 12 GBP, chicken ruby 17 GBP, kids plate 8 GBP",
            relatedObjectIds = familyAnd(londonHotelId, britishMuseumId)
        )
        addFoodPlace(
            name = "Borough Market Kitchen",
            foodType = "Market food",
            address = "8 Southwark Street, London SE1 1TL",
            latitude = "51.5055",
            longitude = "-0.0910",
            websiteUrl = "https://boroughmarket.org.uk",
            phoneNumber = "+44 20 7407 1002",
            emailAddress = "food@example.com",
            workingHours = "10:00-17:00",
            menuWithPrices = "Mock: fish sandwich 11 GBP, pie 9 GBP, lemonade 3 GBP",
            relatedObjectIds = familyAnd(londonHotelId, londonTowerId)
        )

        val trainToOxfordId = addTransport(
            name = "Train London Paddington to Oxford",
            mode = "Train",
            departureLocation = "London Paddington Station",
            departureLatitude = "51.5154",
            departureLongitude = "-0.1755",
            departureDateTime = "2026-08-03 09:20",
            arrivalLocation = "Oxford Station",
            arrivalLatitude = "51.7534",
            arrivalLongitude = "-1.2701",
            arrivalDateTime = "2026-08-03 10:15",
            price = "96 GBP total",
            stopsLocations = "Reading",
            routeMap = "https://maps.google.com/?q=London+Paddington+to+Oxford+Station",
            relatedObjectIds = familyAnd(londonHotelId)
        )
        val oxfordHotelId = addHotel(
            name = "Mock Oxford Riverside Hotel",
            address = "Park End Street, Oxford OX1 1HS",
            latitude = "51.7520",
            longitude = "-1.2670",
            websiteUrl = "https://example.com/oxford-riverside",
            phoneNumber = "+44 1865 000101",
            emailAddress = "oxford@example.com",
            checkIn = "2026-08-03 15:00",
            checkOut = "2026-08-05 10:00",
            isWithBreakfast = "Yes",
            roomNumber = "Family room OXF-118",
            relatedObjectIds = familyAnd(trainToOxfordId)
        )
        val oxfordBikeId = addTransport(
            name = "Oxford family bicycle loop",
            mode = "Bicycle",
            departureLocation = "Oxford Riverside Hotel",
            departureLatitude = "51.7520",
            departureLongitude = "-1.2670",
            departureDateTime = "2026-08-04 09:00",
            arrivalLocation = "Oxford Botanic Garden",
            arrivalLatitude = "51.7503",
            arrivalLongitude = "-1.2464",
            arrivalDateTime = "2026-08-04 09:30",
            price = "44 GBP bike rental",
            stopsLocations = "Radcliffe Camera, High Street, Botanic Garden",
            routeMap = "https://maps.google.com/?q=Oxford+bicycle+route",
            relatedObjectIds = familyAnd(oxfordHotelId)
        )
        val bodleianId = addAttraction(
            name = "Bodleian Library",
            city = "Oxford",
            paid = true,
            description = "Guided library and historic reading rooms tour",
            address = "Broad Street, Oxford OX1 3BG",
            latitude = "51.7541",
            longitude = "-1.2540",
            workingHours = "09:00-17:00",
            websiteUrl = "https://www.bodleian.ox.ac.uk",
            plannedVisitDateTime = "2026-08-03 14:00",
            ticketPrices = "Mock: adult 20 GBP, child 10 GBP",
            relatedObjectIds = familyAnd(oxfordHotelId)
        )
        val ashmoleanId = addAttraction(
            name = "Ashmolean Museum",
            city = "Oxford",
            paid = false,
            description = "Art and archaeology visit",
            address = "Beaumont Street, Oxford OX1 2PH",
            latitude = "51.7554",
            longitude = "-1.2600",
            workingHours = "10:00-17:00",
            websiteUrl = "https://www.ashmolean.org",
            plannedVisitDateTime = "2026-08-04 11:00",
            relatedObjectIds = familyAnd(oxfordHotelId, oxfordBikeId, bodleianId)
        )
        val botanicId = addAttraction(
            name = "Oxford Botanic Garden",
            city = "Oxford",
            paid = true,
            description = "Garden and glasshouse stop",
            address = "Rose Lane, Oxford OX1 4AZ",
            latitude = "51.7503",
            longitude = "-1.2464",
            workingHours = "10:00-17:00",
            websiteUrl = "https://www.obga.ox.ac.uk",
            plannedVisitDateTime = "2026-08-04 15:00",
            ticketPrices = "Mock: adult 8 GBP, child 4 GBP",
            relatedObjectIds = familyAnd(oxfordHotelId, oxfordBikeId, ashmoleanId)
        )
        addFoodPlace(
            name = "Vaults and Garden Cafe",
            foodType = "Cafe",
            address = "University Church, High Street, Oxford OX1 4BJ",
            latitude = "51.7528",
            longitude = "-1.2534",
            websiteUrl = "https://www.thevaultsandgarden.com",
            phoneNumber = "+44 1865 279112",
            emailAddress = "oxford-cafe@example.com",
            workingHours = "08:30-17:00",
            menuWithPrices = "Mock: soup 7 GBP, quiche 9 GBP, cake 4 GBP",
            relatedObjectIds = familyAnd(oxfordHotelId, bodleianId)
        )
        addFoodPlace(
            name = "The Eagle and Child",
            foodType = "Pub",
            address = "49 St Giles, Oxford OX1 3LU",
            latitude = "51.7584",
            longitude = "-1.2608",
            websiteUrl = "https://example.com/eagle-and-child",
            phoneNumber = "+44 1865 000202",
            emailAddress = "pub@example.com",
            workingHours = "12:00-22:00",
            menuWithPrices = "Mock: fish and chips 15 GBP, pie 14 GBP, kids pasta 7 GBP",
            relatedObjectIds = familyAnd(oxfordHotelId, ashmoleanId, botanicId)
        )

        val carToBathId = addTransport(
            name = "Rental car Oxford to Bath",
            mode = "Rental car",
            departureLocation = "Oxford Station car rental",
            departureLatitude = "51.7534",
            departureLongitude = "-1.2701",
            departureDateTime = "2026-08-05 09:00",
            arrivalLocation = "Bath city centre",
            arrivalLatitude = "51.3811",
            arrivalLongitude = "-2.3590",
            arrivalDateTime = "2026-08-05 11:15",
            price = "130 GBP car day plus fuel",
            stopsLocations = "Cotswolds viewpoint, Chippenham",
            routeMap = "https://maps.google.com/?q=Oxford+to+Bath",
            relatedObjectIds = familyAnd(oxfordHotelId)
        )
        val bathHotelId = addHotel(
            name = "Mock Bath Georgian Hotel",
            address = "Queen Square, Bath BA1 2HH",
            latitude = "51.3837",
            longitude = "-2.3630",
            websiteUrl = "https://example.com/bath-georgian",
            phoneNumber = "+44 1225 000303",
            emailAddress = "bath@example.com",
            checkIn = "2026-08-05 15:00",
            checkOut = "2026-08-07 10:00",
            isWithBreakfast = "No",
            roomNumber = "Family room BTH-305",
            relatedObjectIds = familyAnd(carToBathId)
        )
        val bathBusId = addTransport(
            name = "Bath city bus day pass",
            mode = "Bus",
            departureLocation = "Queen Square, Bath",
            departureLatitude = "51.3837",
            departureLongitude = "-2.3630",
            departureDateTime = "2026-08-06 09:30",
            arrivalLocation = "Royal Crescent, Bath",
            arrivalLatitude = "51.3867",
            arrivalLongitude = "-2.3670",
            arrivalDateTime = "2026-08-06 09:45",
            price = "18 GBP family pass",
            stopsLocations = "Bath Abbey, Pulteney Bridge, Royal Crescent",
            routeMap = "https://maps.google.com/?q=Bath+city+bus",
            relatedObjectIds = familyAnd(bathHotelId)
        )
        val romanBathsId = addAttraction(
            name = "Roman Baths",
            city = "Bath",
            paid = true,
            description = "Roman history and audio guide",
            address = "Abbey Churchyard, Bath BA1 1LZ",
            latitude = "51.3811",
            longitude = "-2.3590",
            workingHours = "09:00-18:00",
            websiteUrl = "https://www.romanbaths.co.uk",
            plannedVisitDateTime = "2026-08-05 15:30",
            ticketPrices = "Mock: adult 26 GBP, child 18 GBP",
            relatedObjectIds = familyAnd(bathHotelId)
        )
        val bathAbbeyId = addAttraction(
            name = "Bath Abbey",
            city = "Bath",
            paid = false,
            description = "Historic abbey and quiet rest stop",
            address = "Bath BA1 1LT",
            latitude = "51.3815",
            longitude = "-2.3586",
            workingHours = "10:00-17:30",
            websiteUrl = "https://www.bathabbey.org",
            plannedVisitDateTime = "2026-08-06 10:00",
            relatedObjectIds = familyAnd(bathHotelId, bathBusId, romanBathsId)
        )
        val royalCrescentId = addAttraction(
            name = "Royal Crescent",
            city = "Bath",
            paid = false,
            description = "Georgian architecture photo walk",
            address = "Royal Crescent, Bath BA1 2LR",
            latitude = "51.3867",
            longitude = "-2.3670",
            workingHours = "Open area",
            websiteUrl = "https://visitbath.co.uk",
            plannedVisitDateTime = "2026-08-06 15:30",
            relatedObjectIds = familyAnd(bathHotelId, bathBusId, bathAbbeyId)
        )
        addFoodPlace(
            name = "Sally Lunn's Historic Eating House",
            foodType = "Bakery",
            address = "4 North Parade Passage, Bath BA1 1NX",
            latitude = "51.3808",
            longitude = "-2.3582",
            websiteUrl = "https://www.sallylunns.co.uk",
            phoneNumber = "+44 1225 461634",
            emailAddress = "bath-buns@example.com",
            workingHours = "10:00-21:00",
            menuWithPrices = "Mock: Sally Lunn bun 8 GBP, cream tea 12 GBP, kids bun 5 GBP",
            relatedObjectIds = familyAnd(bathHotelId, romanBathsId)
        )
        addFoodPlace(
            name = "The Scallop Shell",
            foodType = "Seafood",
            address = "22 Monmouth Place, Bath BA1 2AY",
            latitude = "51.3845",
            longitude = "-2.3665",
            websiteUrl = "https://www.thescallopshell.co.uk",
            phoneNumber = "+44 1225 420928",
            emailAddress = "fish@example.com",
            workingHours = "12:00-22:00",
            menuWithPrices = "Mock: fish and chips 17 GBP, grilled fish 22 GBP, kids chips 5 GBP",
            relatedObjectIds = familyAnd(bathHotelId, royalCrescentId)
        )

        val trainToYorkId = addTransport(
            name = "Train Bath Spa to York",
            mode = "Train",
            departureLocation = "Bath Spa Station",
            departureLatitude = "51.3776",
            departureLongitude = "-2.3570",
            departureDateTime = "2026-08-07 08:45",
            arrivalLocation = "York Station",
            arrivalLatitude = "53.9579",
            arrivalLongitude = "-1.0932",
            arrivalDateTime = "2026-08-07 13:30",
            price = "210 GBP total",
            stopsLocations = "Bristol Temple Meads, Birmingham New Street",
            routeMap = "https://maps.google.com/?q=Bath+Spa+to+York+Station",
            relatedObjectIds = familyAnd(bathHotelId)
        )
        val yorkTaxiId = addTransport(
            name = "Taxi York station to hotel",
            mode = "Taxi",
            departureLocation = "York Station",
            departureLatitude = "53.9579",
            departureLongitude = "-1.0932",
            departureDateTime = "2026-08-07 13:45",
            arrivalLocation = "Mock York Minster Hotel",
            arrivalLatitude = "53.9623",
            arrivalLongitude = "-1.0819",
            arrivalDateTime = "2026-08-07 14:00",
            price = "16 GBP",
            stopsLocations = "Direct",
            routeMap = "https://maps.google.com/?q=York+Station+to+York+Minster",
            relatedObjectIds = familyAnd(trainToYorkId)
        )
        val yorkHotelId = addHotel(
            name = "Mock York Minster Hotel",
            address = "Duncombe Place, York YO1 7EF",
            latitude = "53.9623",
            longitude = "-1.0819",
            websiteUrl = "https://example.com/york-minster-hotel",
            phoneNumber = "+44 1904 000404",
            emailAddress = "york@example.com",
            checkIn = "2026-08-07 15:00",
            checkOut = "2026-08-09 10:00",
            isWithBreakfast = "Yes",
            roomNumber = "Family room YRK-210",
            relatedObjectIds = familyAnd(yorkTaxiId)
        )
        val yorkMinsterId = addAttraction(
            name = "York Minster",
            city = "York",
            paid = true,
            description = "Cathedral visit and tower option",
            address = "Deangate, York YO1 7HH",
            latitude = "53.9619",
            longitude = "-1.0819",
            workingHours = "09:30-16:00",
            websiteUrl = "https://yorkminster.org",
            plannedVisitDateTime = "2026-08-07 16:00",
            ticketPrices = "Mock: adult 18 GBP, child 9 GBP",
            relatedObjectIds = familyAnd(yorkHotelId)
        )
        val railMuseumId = addAttraction(
            name = "National Railway Museum",
            city = "York",
            paid = false,
            description = "Locomotives and engineering exhibits",
            address = "Leeman Road, York YO26 4XJ",
            latitude = "53.9609",
            longitude = "-1.0975",
            workingHours = "10:00-17:00",
            websiteUrl = "https://www.railwaymuseum.org.uk",
            plannedVisitDateTime = "2026-08-08 10:00",
            relatedObjectIds = familyAnd(yorkHotelId, yorkMinsterId)
        )
        val wallsId = addAttraction(
            name = "York City Walls",
            city = "York",
            paid = false,
            description = "Family walk along the medieval walls",
            address = "Bootham Bar, York YO1 7EW",
            latitude = "53.9631",
            longitude = "-1.0830",
            workingHours = "08:00-dusk",
            websiteUrl = "https://www.york.gov.uk",
            plannedVisitDateTime = "2026-08-08 15:30",
            relatedObjectIds = familyAnd(yorkHotelId, railMuseumId)
        )
        addFoodPlace(
            name = "Bettys Cafe Tea Rooms York",
            foodType = "Tea room",
            address = "6-8 St Helen's Square, York YO1 8QP",
            latitude = "53.9599",
            longitude = "-1.0840",
            websiteUrl = "https://www.bettys.co.uk",
            phoneNumber = "+44 1904 659142",
            emailAddress = "tea@example.com",
            workingHours = "09:00-21:00",
            menuWithPrices = "Mock: afternoon tea 29 GBP, cake 6 GBP, kids sandwich 7 GBP",
            relatedObjectIds = familyAnd(yorkHotelId, yorkMinsterId)
        )
        addFoodPlace(
            name = "Shambles Market Food Court",
            foodType = "Market food",
            address = "5 Silver Street, York YO1 8RY",
            latitude = "53.9601",
            longitude = "-1.0810",
            websiteUrl = "https://www.shamblesmarket.com",
            phoneNumber = "+44 1904 000505",
            emailAddress = "market@example.com",
            workingHours = "10:00-16:00",
            menuWithPrices = "Mock: bao 8 GBP, burger 10 GBP, churros 5 GBP",
            relatedObjectIds = familyAnd(yorkHotelId, wallsId)
        )

        val trainToManchesterId = addTransport(
            name = "Train York to Manchester",
            mode = "Train",
            departureLocation = "York Station",
            departureLatitude = "53.9579",
            departureLongitude = "-1.0932",
            departureDateTime = "2026-08-09 09:10",
            arrivalLocation = "Manchester Piccadilly Station",
            arrivalLatitude = "53.4774",
            arrivalLongitude = "-2.2309",
            arrivalDateTime = "2026-08-09 10:45",
            price = "88 GBP total",
            stopsLocations = "Leeds",
            routeMap = "https://maps.google.com/?q=York+to+Manchester+Piccadilly",
            relatedObjectIds = familyAnd(yorkHotelId)
        )
        val manchesterHotelId = addHotel(
            name = "Mock Manchester Piccadilly Hotel",
            address = "London Road, Manchester M1 2PG",
            latitude = "53.4776",
            longitude = "-2.2310",
            websiteUrl = "https://example.com/manchester-piccadilly",
            phoneNumber = "+44 161 000 0606",
            emailAddress = "manchester@example.com",
            checkIn = "2026-08-09 15:00",
            checkOut = "2026-08-10 09:30",
            isWithBreakfast = "Yes",
            roomNumber = "Family room MAN-090",
            relatedObjectIds = familyAnd(trainToManchesterId)
        )
        val tramId = addTransport(
            name = "Manchester Metrolink city route",
            mode = "Tram",
            departureLocation = "Piccadilly Metrolink",
            departureLatitude = "53.4776",
            departureLongitude = "-2.2310",
            departureDateTime = "2026-08-09 13:30",
            arrivalLocation = "Etihad Campus",
            arrivalLatitude = "53.4831",
            arrivalLongitude = "-2.2004",
            arrivalDateTime = "2026-08-09 13:55",
            price = "14 GBP family day ticket",
            stopsLocations = "Piccadilly Gardens, New Islington, Etihad Campus",
            routeMap = "https://maps.google.com/?q=Manchester+Metrolink+Piccadilly+to+Etihad+Campus",
            relatedObjectIds = familyAnd(manchesterHotelId)
        )
        val scienceMuseumId = addAttraction(
            name = "Science and Industry Museum",
            city = "Manchester",
            paid = false,
            description = "Science, transport, and industrial history visit",
            address = "Liverpool Road, Manchester M3 4FP",
            latitude = "53.4767",
            longitude = "-2.2546",
            workingHours = "10:00-17:00",
            websiteUrl = "https://www.scienceandindustrymuseum.org.uk",
            plannedVisitDateTime = "2026-08-09 11:30",
            relatedObjectIds = familyAnd(manchesterHotelId)
        )
        val etihadTourId = addAttraction(
            name = "Etihad Stadium Tour",
            city = "Manchester",
            paid = true,
            description = "Football stadium tour",
            address = "Etihad Stadium, Ashton New Road, Manchester M11 3FF",
            latitude = "53.4831",
            longitude = "-2.2004",
            workingHours = "10:00-16:00",
            websiteUrl = "https://www.mancity.com/tours",
            plannedVisitDateTime = "2026-08-09 14:30",
            ticketPrices = "Mock: adult 28 GBP, child 18 GBP",
            relatedObjectIds = familyAnd(manchesterHotelId, tramId, scienceMuseumId)
        )
        val libraryId = addAttraction(
            name = "John Rylands Library",
            city = "Manchester",
            paid = false,
            description = "Historic library and architecture stop",
            address = "150 Deansgate, Manchester M3 3EH",
            latitude = "53.4808",
            longitude = "-2.2487",
            workingHours = "10:00-17:00",
            websiteUrl = "https://www.library.manchester.ac.uk/rylands",
            plannedVisitDateTime = "2026-08-10 10:00",
            relatedObjectIds = familyAnd(manchesterHotelId, etihadTourId)
        )
        addFoodPlace(
            name = "Mackie Mayor",
            foodType = "Food hall",
            address = "1 Eagle Street, Manchester M4 5BU",
            latitude = "53.4854",
            longitude = "-2.2346",
            websiteUrl = "https://www.mackiemayor.co.uk",
            phoneNumber = "+44 161 000 0707",
            emailAddress = "foodhall@example.com",
            workingHours = "09:00-22:00",
            menuWithPrices = "Mock: tacos 9 GBP, steak sandwich 13 GBP, juice 4 GBP",
            relatedObjectIds = familyAnd(manchesterHotelId, scienceMuseumId)
        )
        addFoodPlace(
            name = "Rudy's Pizza Manchester",
            foodType = "Pizza",
            address = "9 Cotton Street, Manchester M4 5BF",
            latitude = "53.4842",
            longitude = "-2.2362",
            websiteUrl = "https://www.rudyspizza.co.uk",
            phoneNumber = "+44 161 000 0808",
            emailAddress = "pizza@example.com",
            workingHours = "12:00-22:00",
            menuWithPrices = "Mock: margherita 9 GBP, salami pizza 12 GBP, salad 6 GBP",
            relatedObjectIds = familyAnd(manchesterHotelId, libraryId)
        )
        addTransport(
            name = "Flight Manchester to Ben Gurion",
            mode = "Commercial plane",
            departureLocation = "Manchester Airport",
            departureLatitude = "53.3650",
            departureLongitude = "-2.2720",
            departureDateTime = "2026-08-10 18:20",
            arrivalLocation = "Ben Gurion Airport, Israel",
            arrivalLatitude = "32.0055",
            arrivalLongitude = "34.8854",
            arrivalDateTime = "2026-08-11 01:20",
            price = "1760 GBP total",
            stopsLocations = "Direct flight",
            routeMap = "https://maps.google.com/?q=MAN+to+TLV",
            relatedObjectIds = familyAnd(manchesterHotelId, libraryId)
        )

        return objects
    }

    private data class TripBackupJson(
        val json: JSONObject,
        val destination: String
    )

    private suspend fun buildWholeDatabaseBackupJson(): JSONObject {
        return database.withTransaction {
            JSONObject()
                .put("formatVersion", FORMAT_VERSION)
                .put("exportType", EXPORT_TYPE_WHOLE_DATABASE)
                .put("exportedAtMillis", System.currentTimeMillis())
                .put("tables", exportTables(database.openHelper.writableDatabase))
        }
    }

    private suspend fun importWholeDatabaseJson(backupJson: JSONObject) {
        require(backupJson.optString("exportType") == EXPORT_TYPE_WHOLE_DATABASE) {
            "Selected file is not a whole DB export"
        }
        database.withTransaction {
            importTables(
                db = database.openHelper.writableDatabase,
                tablesJson = backupJson.getJSONObject("tables")
            )
        }
    }

    private suspend fun buildTripBackupJson(tripId: Long): TripBackupJson? {
        val editableTrip = tripRepository.getEditableTrip(tripId) ?: return null
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
        return TripBackupJson(
            json = backupJson,
            destination = editableTrip.trip.destination
        )
    }

    private suspend fun importTripJson(backupJson: JSONObject): com.tripplanner.app.data.CreatedTripResult {
        require(backupJson.optString("exportType") == EXPORT_TYPE_TRIP) {
            "Selected file is not a trip export"
        }
        val tripJson = backupJson.getJSONObject("trip")
        val objects = parseTripObjects(backupJson.getJSONArray("objects"))
        val importedObjects = remapImportedObjects(objects)
        return tripRepository.createTrip(
            destination = tripJson.optString("destination", "Imported trip"),
            startDate = tripJson.optString("startDate"),
            endDate = tripJson.optString("endDate"),
            objects = importedObjects
        )
    }

    private fun writeJsonToUri(
        uri: Uri,
        json: JSONObject
    ) {
        val outputStream = appContext.contentResolver.openOutputStream(uri)
            ?: error("Could not open selected export file")
        outputStream.bufferedWriter().use { writer ->
            writer.write(json.toString(2))
        }
    }

    private fun readTextFromUri(uri: Uri): String {
        val inputStream = appContext.contentResolver.openInputStream(uri)
            ?: error("Could not open selected import file")
        return inputStream.bufferedReader().use { reader ->
            reader.readText()
        }
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
        clearAppDataTables(db)
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

    private fun clearAppDataTables(db: SupportSQLiteDatabase) {
        WHOLE_DATABASE_DELETE_ORDER.forEach { table ->
            db.execSQL("DELETE FROM `$table`")
        }
    }

    private fun recreateGeneralPool(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT OR IGNORE INTO `item_pools` (
                `id`, `name`, `type`, `trip_id`, `is_system`, `created_at_millis`, `updated_at_millis`
            )
            VALUES (1, 'General', 'GENERAL', NULL, 1, ?, ?)
            """.trimIndent(),
            arrayOf(now, now)
        )
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

    private fun String.safeUrlQuery(): String {
        return trim()
            .replace(Regex("\\s+"), "+")
            .replace(Regex("[^A-Za-z0-9+.-]+"), "")
            .ifBlank { "Trip+Planner" }
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
        const val MOCK_SEED_PREFERENCES_NAME = "trip-planner-mock-seed"
        const val MOCK_SEED_CLEARED_KEY = "mockSeedCleared"

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
