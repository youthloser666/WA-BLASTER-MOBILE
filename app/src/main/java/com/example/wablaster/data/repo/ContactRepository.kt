package com.example.wablaster.data.repo

import com.example.wablaster.data.local.dao.ContactDao
import com.example.wablaster.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface ContactRepository {
    fun getAllContacts(): Flow<List<ContactEntity>>
    fun searchContacts(query: String): Flow<List<ContactEntity>>
    fun getOptOutContacts(): Flow<List<ContactEntity>>
    fun getTotalCount(): Flow<Int>
    fun getOptOutCount(): Flow<Int>
    suspend fun insertContacts(contacts: List<ContactEntity>)
    suspend fun insertContact(contact: ContactEntity): Long
    suspend fun updateContact(contact: ContactEntity)
    suspend fun deleteContact(contact: ContactEntity)
    suspend fun deleteContactById(id: Long)
    suspend fun deleteAllContacts()
    suspend fun toggleOptOut(id: Long, isOptOut: Boolean)
    suspend fun blockNumbers(phones: List<String>): Int
}

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao
) : ContactRepository {

    override fun getAllContacts(): Flow<List<ContactEntity>> = contactDao.getAll()

    override fun searchContacts(query: String): Flow<List<ContactEntity>> =
        if (query.isBlank()) contactDao.getAll() else contactDao.searchContacts(query.trim())

    override fun getOptOutContacts(): Flow<List<ContactEntity>> = contactDao.getOptOutContacts()

    override fun getTotalCount(): Flow<Int> = contactDao.getTotalCount()

    override fun getOptOutCount(): Flow<Int> = contactDao.getOptOutCount()

    override suspend fun insertContacts(contacts: List<ContactEntity>) = contactDao.insertAll(contacts)

    override suspend fun insertContact(contact: ContactEntity): Long = contactDao.insert(contact)

    override suspend fun updateContact(contact: ContactEntity) = contactDao.update(contact)

    override suspend fun deleteContact(contact: ContactEntity) = contactDao.delete(contact)

    override suspend fun deleteContactById(id: Long) = contactDao.deleteById(id)

    override suspend fun deleteAllContacts() = contactDao.deleteAll()

    override suspend fun toggleOptOut(id: Long, isOptOut: Boolean) = contactDao.setOptOut(id, isOptOut)

    override suspend fun blockNumbers(phones: List<String>): Int {
        var count = 0
        for (phone in phones) {
            val existing = contactDao.getByPhone(phone)
            if (existing != null) {
                if (!existing.isOptOut) {
                    contactDao.setOptOut(existing.id, true)
                    count++
                }
            } else {
                contactDao.insert(
                    ContactEntity(
                        phone = phone,
                        name = "Blokir (Opt-Out)",
                        isOptOut = true
                    )
                )
                count++
            }
        }
        return count
    }
}
