package com.example.wablaster.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "send_logs",
    indices = [
        Index(value = ["campaignId"]),
        Index(value = ["phone"])
    ]
)
data class SendLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val campaignId: Long,
    val phone: String,
    val contactName: String = "",
    val variantIndex: Int = 0, // 0..4 (Pesan 1..5)
    val renderedMessage: String = "",
    val status: String = "PENDING", // PENDING, SENT, FAILED, SKIPPED
    val errorMessage: String? = null,
    val sentAt: Long = System.currentTimeMillis()
)
