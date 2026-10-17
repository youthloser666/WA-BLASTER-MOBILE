package com.example.wablaster.util

import org.junit.Assert.*
import org.junit.Test

class PackageUtilsTest {

    @Test
    fun testWhatsAppPackageInfo_appIdGeneration() {
        val normalWa = WhatsAppPackageInfo(
            packageName = "com.whatsapp",
            className = null,
            label = "WhatsApp",
            isClone = false
        )
        assertEquals("com.whatsapp", normalWa.appId)
        assertFalse(normalWa.isClone)

        val cloneWa = WhatsAppPackageInfo(
            packageName = "com.whatsapp",
            className = null,
            label = "II·WhatsApp",
            isClone = true
        )
        assertEquals("com.whatsapp:clone", cloneWa.appId)
        assertTrue(cloneWa.isClone)

        val normalBusiness = WhatsAppPackageInfo(
            packageName = "com.whatsapp.w4b",
            className = null,
            label = "WhatsApp Business",
            isClone = false
        )
        assertEquals("com.whatsapp.w4b", normalBusiness.appId)

        val cloneBusiness = WhatsAppPackageInfo(
            packageName = "com.whatsapp.w4b",
            className = null,
            label = "II·WA Business",
            isClone = true
        )
        assertEquals("com.whatsapp.w4b:clone", cloneBusiness.appId)
    }

    @Test
    fun testDistinctAppId_preservesBothMainAndClone() {
        val list = listOf(
            WhatsAppPackageInfo("com.whatsapp", null, "WhatsApp", isClone = false),
            WhatsAppPackageInfo("com.whatsapp", null, "II·WhatsApp", isClone = true),
            WhatsAppPackageInfo("com.whatsapp.w4b", null, "WhatsApp Business", isClone = false),
            WhatsAppPackageInfo("com.whatsapp.w4b", null, "II·WA Business", isClone = true),
            WhatsAppPackageInfo("com.whatsapp", null, "WhatsApp Duplikat", isClone = false) // duplikat
        )

        val distinctList = list.distinctBy { it.appId }
        assertEquals(4, distinctList.size)
        assertTrue(distinctList.any { it.appId == "com.whatsapp" && !it.isClone })
        assertTrue(distinctList.any { it.appId == "com.whatsapp:clone" && it.isClone })
        assertTrue(distinctList.any { it.appId == "com.whatsapp.w4b" && !it.isClone })
        assertTrue(distinctList.any { it.appId == "com.whatsapp.w4b:clone" && it.isClone })
    }
}
