package com.tripplanner.app.data.local

import androidx.room.TypeConverter
import com.tripplanner.app.data.local.entity.ItemPoolType
import com.tripplanner.app.model.TripObjectAttribute
import com.tripplanner.app.model.TripObjectType

class TripPlannerTypeConverters {
    @TypeConverter
    fun objectTypeToString(value: TripObjectType): String = value.name

    @TypeConverter
    fun stringToObjectType(value: String): TripObjectType = TripObjectType.valueOf(value)

    @TypeConverter
    fun attributeToString(value: TripObjectAttribute): String = value.name

    @TypeConverter
    fun stringToAttribute(value: String): TripObjectAttribute = TripObjectAttribute.valueOf(value)

    @TypeConverter
    fun itemPoolTypeToString(value: ItemPoolType): String = value.name

    @TypeConverter
    fun stringToItemPoolType(value: String): ItemPoolType = ItemPoolType.valueOf(value)
}
