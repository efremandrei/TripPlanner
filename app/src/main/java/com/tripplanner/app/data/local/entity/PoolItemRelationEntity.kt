package com.tripplanner.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "pool_item_relations",
    primaryKeys = ["item_id", "related_item_id"],
    foreignKeys = [
        ForeignKey(
            entity = PoolItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PoolItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["related_item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("item_id"),
        Index("related_item_id")
    ]
)
data class PoolItemRelationEntity(
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "related_item_id")
    val relatedItemId: Long
)
