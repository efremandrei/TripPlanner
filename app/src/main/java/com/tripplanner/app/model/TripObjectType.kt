package com.tripplanner.app.model

enum class TripObjectAttributeInputKind {
    TEXT,
    LONG_TEXT,
    URL,
    PHONE,
    EMAIL,
    NUMBER,
    MONEY,
    DATE_TIME,
    BOOLEAN,
    IMAGE,
    MAP
}

enum class TripObjectAttribute(
    val displayName: String,
    val inputKind: TripObjectAttributeInputKind = TripObjectAttributeInputKind.TEXT,
    val options: List<String> = emptyList()
) {
    RELATION("Relation"),
    AGE("Age", TripObjectAttributeInputKind.NUMBER),
    PASSPORT_NUMBER("Passport number"),
    SEX("Sex"),
    NOTES("Notes", TripObjectAttributeInputKind.LONG_TEXT),

    ICONIC_PICTURE("Iconic picture", TripObjectAttributeInputKind.IMAGE),
    ADDRESS("Address", TripObjectAttributeInputKind.LONG_TEXT),
    LATITUDE("Latitude", TripObjectAttributeInputKind.NUMBER),
    LONGITUDE("Longitude", TripObjectAttributeInputKind.NUMBER),
    GOOGLE_PLACE_ID("Google place ID"),
    GOOGLE_MAPS_URL("Google Maps URL", TripObjectAttributeInputKind.URL),
    WEBSITE_URL("Website URL", TripObjectAttributeInputKind.URL),
    PHONE_NUMBER("Phone number", TripObjectAttributeInputKind.PHONE),
    EMAIL_ADDRESS("Email address", TripObjectAttributeInputKind.EMAIL),
    CHECK_IN("Check-in", TripObjectAttributeInputKind.DATE_TIME),
    CHECK_OUT("Check-out", TripObjectAttributeInputKind.DATE_TIME),
    IS_WITH_BREAKFAST(
        displayName = "Breakfast included",
        inputKind = TripObjectAttributeInputKind.BOOLEAN,
        options = listOf("Yes", "No")
    ),
    ROOM_NUMBER("Room number"),

    FOOD_TYPE("Type"),
    WORKING_HOURS("Working hours", TripObjectAttributeInputKind.LONG_TEXT),
    ENGLISH_MENU_WITH_PRICES(
        "Menu with prices in English",
        TripObjectAttributeInputKind.LONG_TEXT
    ),

    TRANSPORTATION_MODE(
        displayName = "Mode",
        options = listOf(
            "By foot",
            "Bicycle",
            "Motorcycle",
            "Rental car",
            "Taxi",
            "Uber",
            "Bus",
            "Tram",
            "Train",
            "Maglev",
            "Ferry",
            "Boat",
            "Kayak",
            "Ship",
            "Yacht",
            "Light plane",
            "Commercial plane",
            "Helicopter",
            "Parachute"
        )
    ),
    DEPARTURE_LOCATION("Departure location", TripObjectAttributeInputKind.LONG_TEXT),
    DEPARTURE_LATITUDE("Departure latitude", TripObjectAttributeInputKind.NUMBER),
    DEPARTURE_LONGITUDE("Departure longitude", TripObjectAttributeInputKind.NUMBER),
    DEPARTURE_DATE_TIME("Departure date/time", TripObjectAttributeInputKind.DATE_TIME),
    ARRIVAL_LOCATION("Arrival location", TripObjectAttributeInputKind.LONG_TEXT),
    ARRIVAL_LATITUDE("Arrival latitude", TripObjectAttributeInputKind.NUMBER),
    ARRIVAL_LONGITUDE("Arrival longitude", TripObjectAttributeInputKind.NUMBER),
    ARRIVAL_DATE_TIME("Arrival date/time", TripObjectAttributeInputKind.DATE_TIME),
    PRICE("Price", TripObjectAttributeInputKind.MONEY),
    STOPS_LOCATIONS("Stops locations", TripObjectAttributeInputKind.LONG_TEXT),
    ROUTE_MAP("Map of route", TripObjectAttributeInputKind.MAP),

    CATEGORY("Category"),
    LOCATION_MAP_PICTURE("Map picture with location", TripObjectAttributeInputKind.MAP),

    DESCRIPTION("Description", TripObjectAttributeInputKind.LONG_TEXT),
    TICKET_PRICES("Ticket prices", TripObjectAttributeInputKind.LONG_TEXT),
    PLANNED_VISIT_DATE_TIME("Planned visit date/time", TripObjectAttributeInputKind.DATE_TIME)
}

enum class TripObjectType(
    val displayName: String,
    val attributes: List<TripObjectAttribute>
) {
    FAMILY_MEMBER(
        displayName = "Family member",
        attributes = listOf(
            TripObjectAttribute.PASSPORT_NUMBER,
            TripObjectAttribute.RELATION,
            TripObjectAttribute.AGE,
            TripObjectAttribute.SEX,
            TripObjectAttribute.NOTES
        )
    ),
    HOTEL(
        displayName = "Hotel",
        attributes = listOf(
            TripObjectAttribute.ICONIC_PICTURE,
            TripObjectAttribute.ADDRESS,
            TripObjectAttribute.LATITUDE,
            TripObjectAttribute.LONGITUDE,
            TripObjectAttribute.GOOGLE_PLACE_ID,
            TripObjectAttribute.GOOGLE_MAPS_URL,
            TripObjectAttribute.WEBSITE_URL,
            TripObjectAttribute.PHONE_NUMBER,
            TripObjectAttribute.EMAIL_ADDRESS,
            TripObjectAttribute.CHECK_IN,
            TripObjectAttribute.CHECK_OUT,
            TripObjectAttribute.IS_WITH_BREAKFAST,
            TripObjectAttribute.ROOM_NUMBER
        )
    ),
    FOOD_PLACE(
        displayName = "Food place",
        attributes = listOf(
            TripObjectAttribute.ICONIC_PICTURE,
            TripObjectAttribute.WEBSITE_URL,
            TripObjectAttribute.PHONE_NUMBER,
            TripObjectAttribute.EMAIL_ADDRESS,
            TripObjectAttribute.FOOD_TYPE,
            TripObjectAttribute.ADDRESS,
            TripObjectAttribute.LATITUDE,
            TripObjectAttribute.LONGITUDE,
            TripObjectAttribute.GOOGLE_PLACE_ID,
            TripObjectAttribute.GOOGLE_MAPS_URL,
            TripObjectAttribute.WORKING_HOURS,
            TripObjectAttribute.ENGLISH_MENU_WITH_PRICES
        )
    ),
    TRANSPORTATION(
        displayName = "Transportation",
        attributes = listOf(
            TripObjectAttribute.ICONIC_PICTURE,
            TripObjectAttribute.TRANSPORTATION_MODE,
            TripObjectAttribute.DEPARTURE_LOCATION,
            TripObjectAttribute.DEPARTURE_LATITUDE,
            TripObjectAttribute.DEPARTURE_LONGITUDE,
            TripObjectAttribute.DEPARTURE_DATE_TIME,
            TripObjectAttribute.ARRIVAL_LOCATION,
            TripObjectAttribute.ARRIVAL_LATITUDE,
            TripObjectAttribute.ARRIVAL_LONGITUDE,
            TripObjectAttribute.ARRIVAL_DATE_TIME,
            TripObjectAttribute.PRICE,
            TripObjectAttribute.STOPS_LOCATIONS,
            TripObjectAttribute.ROUTE_MAP
        )
    ),
    SHOP(
        displayName = "Shop",
        attributes = listOf(
            TripObjectAttribute.ICONIC_PICTURE,
            TripObjectAttribute.CATEGORY,
            TripObjectAttribute.ADDRESS,
            TripObjectAttribute.LATITUDE,
            TripObjectAttribute.LONGITUDE,
            TripObjectAttribute.GOOGLE_PLACE_ID,
            TripObjectAttribute.GOOGLE_MAPS_URL,
            TripObjectAttribute.WORKING_HOURS,
            TripObjectAttribute.PHONE_NUMBER,
            TripObjectAttribute.EMAIL_ADDRESS,
            TripObjectAttribute.LOCATION_MAP_PICTURE,
            TripObjectAttribute.NOTES
        )
    ),
    PAID_ATTRACTION(
        displayName = "Paid attraction",
        attributes = listOf(
            TripObjectAttribute.DESCRIPTION,
            TripObjectAttribute.ICONIC_PICTURE,
            TripObjectAttribute.ADDRESS,
            TripObjectAttribute.LATITUDE,
            TripObjectAttribute.LONGITUDE,
            TripObjectAttribute.GOOGLE_PLACE_ID,
            TripObjectAttribute.GOOGLE_MAPS_URL,
            TripObjectAttribute.TICKET_PRICES,
            TripObjectAttribute.WORKING_HOURS,
            TripObjectAttribute.WEBSITE_URL,
            TripObjectAttribute.PLANNED_VISIT_DATE_TIME
        )
    ),
    FREE_ATTRACTION(
        displayName = "Free attraction",
        attributes = listOf(
            TripObjectAttribute.DESCRIPTION,
            TripObjectAttribute.ICONIC_PICTURE,
            TripObjectAttribute.ADDRESS,
            TripObjectAttribute.LATITUDE,
            TripObjectAttribute.LONGITUDE,
            TripObjectAttribute.GOOGLE_PLACE_ID,
            TripObjectAttribute.GOOGLE_MAPS_URL,
            TripObjectAttribute.WORKING_HOURS,
            TripObjectAttribute.WEBSITE_URL,
            TripObjectAttribute.PLANNED_VISIT_DATE_TIME
        )
    )
}
