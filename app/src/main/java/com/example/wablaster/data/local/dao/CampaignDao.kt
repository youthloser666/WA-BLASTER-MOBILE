package com.example.wablaster.data.local.dao

import androidx.room.*
import com.example.wablaster.data.local.entity.CampaignEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {

    @Query("SELECT * FROM campaigns ORDER BY createdAt DESC")
    fun getAll(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns WHERE id = :id")
    suspend fun getById(id: Long): CampaignEntity?

    @Query("SELECT * FROM campaigns WHERE status IN ('RUNNING', 'PAUSED') LIMIT 1")
    suspend fun getActiveCampaign(): CampaignEntity?

    @Query("SELECT * FROM campaigns WHERE status IN ('RUNNING', 'PAUSED') ORDER BY updatedAt DESC LIMIT 1")
    fun observeActiveCampaign(): Flow<CampaignEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(campaign: CampaignEntity): Long

    @Update
    suspend fun update(campaign: CampaignEntity)

    @Delete
    suspend fun delete(campaign: CampaignEntity)
}
