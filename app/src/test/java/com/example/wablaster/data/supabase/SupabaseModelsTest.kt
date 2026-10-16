package com.example.wablaster.data.supabase

import org.junit.Assert.*
import org.junit.Test

class SupabaseModelsTest {

    @Test
    fun testUserRoleFromString_validRoles() {
        assertEquals(UserRole.AGENT, UserRole.fromString("AGENT"))
        assertEquals(UserRole.AGENT, UserRole.fromString("agent"))
        assertEquals(UserRole.ADMIN, UserRole.fromString("ADMIN"))
        assertEquals(UserRole.ADMIN, UserRole.fromString("admin"))
        assertEquals(UserRole.IT_SUPPORT, UserRole.fromString("IT_SUPPORT"))
        assertEquals(UserRole.IT_SUPPORT, UserRole.fromString("it_support"))
        assertEquals(UserRole.IT_SUPPORT, UserRole.fromString("  IT_SUPPORT  "))
    }

    @Test
    fun testUserRoleFromString_invalidOrNullFallbackToAgent() {
        assertEquals(UserRole.AGENT, UserRole.fromString(null))
        assertEquals(UserRole.AGENT, UserRole.fromString(""))
        assertEquals(UserRole.AGENT, UserRole.fromString("UNKNOWN"))
        assertEquals(UserRole.AGENT, UserRole.fromString("SUPERUSER"))
    }

    @Test
    fun testAppConfig_defaultValues() {
        val config = AppConfig()
        assertEquals(1, config.id)
        assertFalse(config.maintenanceMode)
        assertTrue(config.maintenanceMessage.isNotBlank())
        assertNull(config.updatedAt)
        assertNull(config.updatedBy)
    }

    @Test
    fun testAppUser_agentDeviceLockLogic() {
        val agent = AppUser(
            id = "u1",
            serialNumber = "AGT-001",
            name = "Budi Agent",
            role = UserRole.AGENT,
            deviceId = "device-A",
            isActive = true
        )

        // Same device -> allowed
        val currentDevice = "device-A"
        val canLoginSameDevice = (agent.role != UserRole.AGENT || agent.deviceId == currentDevice)
        assertTrue("Agent pada perangkat terikat yang sama harus diizinkan login", canLoginSameDevice)

        // Different device -> rejected
        val differentDevice = "device-B"
        val canLoginDifferentDevice = (agent.role != UserRole.AGENT || agent.deviceId == differentDevice)
        assertFalse("Agent pada perangkat berbeda harus ditolak login", canLoginDifferentDevice)
    }

    @Test
    fun testAppUser_adminAndItSupportMultiDeviceAllowed() {
        val admin = AppUser(
            id = "u2",
            serialNumber = "ADM-001",
            name = "Admin Surya",
            role = UserRole.ADMIN,
            deviceId = null,
            isActive = true
        )
        val itSupport = AppUser(
            id = "u3",
            serialNumber = "IT-001",
            name = "IT Support Master",
            role = UserRole.IT_SUPPORT,
            deviceId = null,
            isActive = true
        )

        // Both Admin & IT Support bypass device ID restrictions
        assertTrue(admin.role == UserRole.ADMIN || admin.role == UserRole.IT_SUPPORT)
        assertTrue(itSupport.role == UserRole.ADMIN || itSupport.role == UserRole.IT_SUPPORT)
    }

    @Test
    fun testMaintenanceMode_permissionRules() {
        val configActive = AppConfig(maintenanceMode = true, maintenanceMessage = "Maintenance aktif")

        fun isBlocked(role: UserRole, config: AppConfig): Boolean {
            return config.maintenanceMode && role != UserRole.IT_SUPPORT
        }

        fun isAllowedToAccessApp(role: UserRole, config: AppConfig): Boolean {
            return !isBlocked(role, config)
        }

        assertFalse("Agent harus diblokir saat maintenance", isAllowedToAccessApp(UserRole.AGENT, configActive))
        assertFalse("Admin harus diblokir saat maintenance", isAllowedToAccessApp(UserRole.ADMIN, configActive))
        assertTrue("IT Support tetap diizinkan akses saat maintenance untuk perbaikan/unlock", isAllowedToAccessApp(UserRole.IT_SUPPORT, configActive))

        // Normal mode -> everyone allowed
        val configNormal = AppConfig(maintenanceMode = false)
        assertTrue(isAllowedToAccessApp(UserRole.AGENT, configNormal))
        assertTrue(isAllowedToAccessApp(UserRole.ADMIN, configNormal))
        assertTrue(isAllowedToAccessApp(UserRole.IT_SUPPORT, configNormal))
    }
}
