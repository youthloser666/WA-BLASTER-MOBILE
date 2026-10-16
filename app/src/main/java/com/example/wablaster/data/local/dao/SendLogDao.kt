package com.example.wablaster.data.local.dao

import androidx.room.*
import com.example.wablaster.data.local.entity.SendLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SendLogDao {

    @Query("SELECT * FROM send_logs WHERE campaignId = :campaignId ORDER BY sentAt DESC")
    fun getLogsByCampaign(campaignId: Long): Flow<List<SendLogEntity>>

    @Query("SELECT * FROM send_logs ORDER BY sentAt DESC")
    fun getAllLogs(): Flow<List<SendLogEntity>>

    @Query("SELECT * FROM send_logs WHERE status = :status ORDER BY sentAt DESC")
    fun getLogsByStatus(status: String): Flow<List<SendLogEntity>>

    @Query("SELECT * FROM send_logs ORDER BY sentAt DESC")
    suspend fun getAllLogsList(): List<SendLogEntity>

    @Query("SELECT * FROM send_logs WHERE campaignId = :campaignId AND status = :status ORDER BY sentAt DESC")
    suspend fun getLogsByCampaignAndStatus(campaignId: Long, status: String): List<SendLogEntity>

    @Query("SELECT COUNT(*) FROM send_logs WHERE campaignId = :campaignId AND status = 'SENT'")
    suspend fun getSentCount(campaignId: Long): Int

    @Query("SELECT phone FROM send_logs WHERE campaignId = :campaignId AND status = 'SENT'")
    suspend fun getSentPhones(campaignId: Long): List<String>

    @Query("SELECT COUNT(*) FROM send_logs WHERE sentAt >= :startOfDayTimestamp AND status = 'SENT'")
    suspend fun getTodaySentCount(startOfDayTimestamp: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SendLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<SendLogEntity>)

    @Query("DELETE FROM send_logs WHERE campaignId = :campaignId")
    suspend fun deleteByCampaign(campaignId: Long)

    @Query("DELETE FROM send_logs WHERE campaignId = :campaignId AND status = 'FAILED'")
    suspend fun deleteFailedLogsByCampaign(campaignId: Long)

    @Query("DELETE FROM send_logs")
    suspend fun deleteAllLogs()
}
