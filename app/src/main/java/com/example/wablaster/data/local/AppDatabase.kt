package com.example.wablaster.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.wablaster.data.local.dao.CampaignDao
import com.example.wablaster.data.local.dao.ContactDao
import com.example.wablaster.data.local.dao.SendLogDao
import com.example.wablaster.data.local.entity.CampaignEntity
import com.example.wablaster.data.local.entity.ContactEntity
import com.example.wablaster.data.local.entity.SendLogEntity

@Database(
    entities = [
        ContactEntity::class,
        CampaignEntity::class,
        SendLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun campaignDao(): CampaignDao
    abstract fun sendLogDao(): SendLogDao
}
