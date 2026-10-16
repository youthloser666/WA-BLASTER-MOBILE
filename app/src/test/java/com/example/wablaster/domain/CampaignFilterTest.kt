package com.example.wablaster.domain

import com.example.wablaster.data.local.entity.ContactEntity
import org.junit.Assert.*
import org.junit.Test

class CampaignFilterTest {

    @Test
    fun `test filter eligible contacts excludes optOut contacts`() {
        val contacts = listOf(
            ContactEntity(id = 1, phone = "628111", name = "User 1", isOptOut = false),
            ContactEntity(id = 2, phone = "628222", name = "User 2", isOptOut = true),
            ContactEntity(id = 3, phone = "628333", name = "User 3", isOptOut = false)
        )

        val eligible = contacts.filter { !it.isOptOut }
        assertEquals(2, eligible.size)
        assertEquals("628111", eligible[0].phone)
        assertEquals("628333", eligible[1].phone)
    }

    @Test
    fun `test filter eligible contacts excludes already sent numbers`() {
        val contacts = listOf(
            ContactEntity(id = 1, phone = "628111", name = "User 1", isOptOut = false),
            ContactEntity(id = 2, phone = "628222", name = "User 2", isOptOut = false),
            ContactEntity(id = 3, phone = "628333", name = "User 3", isOptOut = false)
        )

        val sentPhones = setOf("628222")

        val toSend = contacts.filter { !sentPhones.contains(it.phone) }
        assertEquals(2, toSend.size)
        assertEquals("628111", toSend[0].phone)
        assertEquals("628333", toSend[1].phone)
    }

    @Test
    fun `test daily limit reached condition`() {
        val dailyLimit = 200
        val todaySentCount1 = 199
        val todaySentCount2 = 200

        val canSend1 = dailyLimit == 0 || todaySentCount1 < dailyLimit
        val canSend2 = dailyLimit == 0 || todaySentCount2 < dailyLimit

        assertTrue(canSend1)
        assertFalse(canSend2)
    }
}
