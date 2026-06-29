package com.tripplanner.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "pool_memberships",
    primaryKeys = ["pool_id", "item_id"],
    foreignKeys = [
        ForeignKey(
            entity = ItemPoolEntity::class,
            parentColumns = ["id"],
            childColumns = ["pool_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PoolItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("pool_id"),
        Index("item_id"),
        Index(value = ["pool_id", "priority_order"], unique = true)
    ]
)
data class PoolMembershipEntity(
    @ColumnInfo(name = "pool_id")
    val poolId: Long,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "priority_order")
    val priorityOrder: Int,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis")
    val updatedAtMillis: Long
)
