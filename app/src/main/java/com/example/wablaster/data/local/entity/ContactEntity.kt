package com.example.wablaster.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [Index(value = ["phone"], unique = true)]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phone: String,
    val name: String = "",
    val isOptOut: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
