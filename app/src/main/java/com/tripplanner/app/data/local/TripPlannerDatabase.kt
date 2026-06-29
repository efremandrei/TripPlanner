package com.tripplanner.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tripplanner.app.data.local.dao.GooglePlaceCacheDao
import com.tripplanner.app.data.local.dao.ItemPoolDao
import com.tripplanner.app.data.local.dao.MapPresetDao
import com.tripplanner.app.data.local.dao.PoolItemDao
import com.tripplanner.app.data.local.dao.TripDao
import com.tripplanner.app.data.local.dao.TripObjectDao
import com.tripplanner.app.data.local.entity.GooglePlaceCacheEntity
import com.tripplanner.app.data.local.entity.ItemPoolEntity
import com.tripplanner.app.data.local.entity.MapPresetEntity
import com.tripplanner.app.data.local.entity.MapPresetItemEntity
import com.tripplanner.app.data.local.entity.PoolItemAttributeEntity
import com.tripplanner.app.data.local.entity.PoolItemEntity
import com.tripplanner.app.data.local.entity.PoolItemRelationEntity
import com.tripplanner.app.data.local.entity.PoolMembershipEntity
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
        MapPresetItemEntity::class,
        ItemPoolEntity::class,
        PoolItemEntity::class,
        PoolItemAttributeEntity::class,
        PoolItemRelationEntity::class,
        PoolMembershipEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(TripPlannerTypeConverters::class)
abstract class TripPlannerDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun tripObjectDao(): TripObjectDao
    abstract fun googlePlaceCacheDao(): GooglePlaceCacheDao
    abstract fun mapPresetDao(): MapPresetDao
    abstract fun itemPoolDao(): ItemPoolDao
    abstract fun poolItemDao(): PoolItemDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `item_pools` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `trip_id` INTEGER,
                        `is_system` INTEGER NOT NULL,
                        `created_at_millis` INTEGER NOT NULL,
                        `updated_at_millis` INTEGER NOT NULL,
                        FOREIGN KEY(`trip_id`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_pools_type` ON `item_pools` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_pools_trip_id` ON `item_pools` (`trip_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_item_pools_type_trip_id` ON `item_pools` (`type`, `trip_id`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pool_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `created_at_millis` INTEGER NOT NULL,
                        `updated_at_millis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pool_item_attributes` (
                        `item_id` INTEGER NOT NULL,
                        `attribute` TEXT NOT NULL,
                        `value` TEXT NOT NULL,
                        PRIMARY KEY(`item_id`, `attribute`),
                        FOREIGN KEY(`item_id`) REFERENCES `pool_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pool_item_attributes_item_id` ON `pool_item_attributes` (`item_id`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pool_item_relations` (
                        `item_id` INTEGER NOT NULL,
                        `related_item_id` INTEGER NOT NULL,
                        PRIMARY KEY(`item_id`, `related_item_id`),
                        FOREIGN KEY(`item_id`) REFERENCES `pool_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`related_item_id`) REFERENCES `pool_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pool_item_relations_item_id` ON `pool_item_relations` (`item_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pool_item_relations_related_item_id` ON `pool_item_relations` (`related_item_id`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pool_memberships` (
                        `pool_id` INTEGER NOT NULL,
                        `item_id` INTEGER NOT NULL,
                        `priority_order` INTEGER NOT NULL,
                        `created_at_millis` INTEGER NOT NULL,
                        `updated_at_millis` INTEGER NOT NULL,
                        PRIMARY KEY(`pool_id`, `item_id`),
                        FOREIGN KEY(`pool_id`) REFERENCES `item_pools`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`item_id`) REFERENCES `pool_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pool_memberships_pool_id` ON `pool_memberships` (`pool_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pool_memberships_item_id` ON `pool_memberships` (`item_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pool_memberships_pool_id_priority_order` ON `pool_memberships` (`pool_id`, `priority_order`)")

                val nowMillisSql = "CAST(strftime('%s', 'now') AS INTEGER) * 1000"
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `item_pools` (
                        `id`, `name`, `type`, `trip_id`, `is_system`, `created_at_millis`, `updated_at_millis`
                    )
                    VALUES (1, 'General', 'GENERAL', NULL, 1, $nowMillisSql, $nowMillisSql)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `pool_items` (
                        `id`, `type`, `name`, `created_at_millis`, `updated_at_millis`
                    )
                    SELECT `id`, `type`, `name`, `created_at_millis`, `updated_at_millis`
                    FROM `trip_objects`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `pool_item_attributes` (`item_id`, `attribute`, `value`)
                    SELECT `object_id`, `attribute`, `value`
                    FROM `trip_object_attributes`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `pool_item_relations` (`item_id`, `related_item_id`)
                    SELECT `object_id`, `related_object_id`
                    FROM `trip_object_relations`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `item_pools` (
                        `name`, `type`, `trip_id`, `is_system`, `created_at_millis`, `updated_at_millis`
                    )
                    SELECT `title` || ' trip pool', 'TRIP', `id`, 0, `created_at_millis`, `updated_at_millis`
                    FROM `trips`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `pool_memberships` (
                        `pool_id`, `item_id`, `priority_order`, `created_at_millis`, `updated_at_millis`
                    )
                    SELECT 1, `id`, `id`, `created_at_millis`, `updated_at_millis`
                    FROM `pool_items`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `pool_memberships` (
                        `pool_id`, `item_id`, `priority_order`, `created_at_millis`, `updated_at_millis`
                    )
                    SELECT `item_pools`.`id`, `trip_objects`.`id`, `trip_objects`.`priority_order`,
                        `trip_objects`.`created_at_millis`, `trip_objects`.`updated_at_millis`
                    FROM `trip_objects`
                    INNER JOIN `item_pools`
                        ON `item_pools`.`trip_id` = `trip_objects`.`trip_id`
                        AND `item_pools`.`type` = 'TRIP'
                    """.trimIndent()
                )
            }
        }

        fun create(context: Context): TripPlannerDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                TripPlannerDatabase::class.java,
                "trip-planner.db"
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
