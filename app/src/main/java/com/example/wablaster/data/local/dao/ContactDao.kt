package com.example.wablaster.data.local.dao

import androidx.room.*
import com.example.wablaster.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ContactEntity>>

    @Query("""
        SELECT * FROM contacts 
        WHERE phone LIKE '%' || :query || '%' 
           OR name LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    fun searchContacts(query: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isOptOut = 0 ORDER BY createdAt DESC")
    suspend fun getActiveContacts(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE isOptOut = 1 ORDER BY createdAt DESC")
    fun getOptOutContacts(): Flow<List<ContactEntity>>

    @Query("SELECT COUNT(*) FROM contacts")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM contacts WHERE isOptOut = 1")
    fun getOptOutCount(): Flow<Int>

    @Query("SELECT * FROM contacts WHERE phone = :phone LIMIT 1")
    suspend fun getByPhone(phone: String): ContactEntity?

    @Query("UPDATE contacts SET isOptOut = 1 WHERE phone = :phone")
    suspend fun markAsOptOutByPhone(phone: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity): Long

    @Update
    suspend fun update(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM contacts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()

    @Query("UPDATE contacts SET isOptOut = :isOptOut WHERE id = :id")
    suspend fun setOptOut(id: Long, isOptOut: Boolean)
}
