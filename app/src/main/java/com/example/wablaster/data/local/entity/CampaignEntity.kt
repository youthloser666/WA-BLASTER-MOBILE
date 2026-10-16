package com.example.wablaster.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val templateMessage: String = "",
    val variantsJson: String = "[]", // JSON array of 5 variant templates
    val variantMode: String = "SEQUENTIAL", // SEQUENTIAL or RANDOM
    val defaultName: String = "Kak",
    val status: String = "IDLE", // IDLE, RUNNING, PAUSED, COMPLETED, STOPPED
    val targetPackage: String = "com.whatsapp",
    val totalContacts: Int = 0,
    val sentCount: Int = 0,
    val failedCount: Int = 0,
    val skippedCount: Int = 0,
    val lastProcessedIndex: Int = 0,
    val delayConfigJson: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
