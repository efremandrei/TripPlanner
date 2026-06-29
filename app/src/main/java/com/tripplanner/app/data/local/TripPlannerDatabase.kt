package com.tripplanner.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tripplanner.app.data.local.dao.GooglePlaceCacheDao
import com.tripplanner.app.data.local.dao.MapPresetDao
import com.tripplanner.app.data.local.dao.TripDao
import com.tripplanner.app.data.local.dao.TripObjectDao
import com.tripplanner.app.data.local.entity.GooglePlaceCacheEntity
import com.tripplanner.app.data.local.entity.MapPresetEntity
import com.tripplanner.app.data.local.entity.MapPresetItemEntity
import com.tripplanner.app.data.local.entity.TripEntity
import com.tripplanner.app.data.local.entity.TripObjectAttributeEntity
import com.tripplanner.app.data.local.entity.TripObjectEntity
import com.tripplanner.app.data.local.entity.TripObjectRelationEntity

@Database(
    entities = [
        TripEntity::class,
        TripObjectEntity::class,
        TripObjectAttributeEntity::class,
        TripObjectRelationEntity::class,
        GooglePlaceCacheEntity::class,
        MapPresetEntity::class,
        MapPresetItemEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(TripPlannerTypeConverters::class)
abstract class TripPlannerDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun tripObjectDao(): TripObjectDao
    abstract fun googlePlaceCacheDao(): GooglePlaceCacheDao
    abstract fun mapPresetDao(): MapPresetDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `google_place_cache` (
                        `place_id` TEXT NOT NULL,
                        `display_name` TEXT,
                        `formatted_address` TEXT,
                        `latitude` REAL,
                        `longitude` REAL,
                        `phone_number` TEXT,
                        `website_url` TEXT,
                        `google_maps_uri` TEXT,
                        `opening_hours` TEXT,
                        `fetched_at_millis` INTEGER NOT NULL,
                        PRIMARY KEY(`place_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `map_presets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `trip_id` INTEGER,
                        `name` TEXT NOT NULL,
                        `provider` TEXT NOT NULL,
                        `visibility` TEXT NOT NULL,
                        `sync_status` TEXT NOT NULL,
                        `external_map_id` TEXT,
                        `created_at_millis` INTEGER NOT NULL,
                        `updated_at_millis` INTEGER NOT NULL,
                        FOREIGN KEY(`trip_id`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_map_presets_trip_id` ON `map_presets` (`trip_id`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `map_preset_items` (
                        `preset_id` INTEGER NOT NULL,
                        `object_id` INTEGER NOT NULL,
                        `priority_order` INTEGER NOT NULL,
                        PRIMARY KEY(`preset_id`, `object_id`),
                        FOREIGN KEY(`preset_id`) REFERENCES `map_presets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`object_id`) REFERENCES `trip_objects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_map_preset_items_object_id` ON `map_preset_items` (`object_id`)")
            }
        }

        fun create(context: Context): TripPlannerDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                TripPlannerDatabase::class.java,
                "trip-planner.db"
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
