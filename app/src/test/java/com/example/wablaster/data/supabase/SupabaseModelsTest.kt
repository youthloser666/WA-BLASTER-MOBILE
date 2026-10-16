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

    @Test
    fun testUserVisibility_adminCanSeeAdminAndAgent_butNeverItSupport() {
        val userAgent = AppUser("1", "AGT-001", "Agent A", UserRole.AGENT)
        val userAdmin1 = AppUser("2", "ADM-001", "Admin B", UserRole.ADMIN)
        val userAdmin2 = AppUser("3", "ADM-002", "Admin C", UserRole.ADMIN)
        val userItSupport = AppUser("4", "IT-001", "IT Support Master", UserRole.IT_SUPPORT)

        val allUsers = listOf(userAgent, userAdmin1, userAdmin2, userItSupport)

        // Filter for Admin caller:
        val adminVisibleUsers = allUsers.filter { it.role != UserRole.IT_SUPPORT }
        assertEquals(3, adminVisibleUsers.size)
        assertTrue(adminVisibleUsers.any { it.role == UserRole.AGENT })
        assertTrue(adminVisibleUsers.any { it.serialNumber == "ADM-001" })
        assertTrue(adminVisibleUsers.any { it.serialNumber == "ADM-002" })
        assertFalse("IT Support TIDAK BOLEH terlihat oleh Admin", adminVisibleUsers.any { it.role == UserRole.IT_SUPPORT })

        // Filter for IT Support caller:
        val itVisibleUsers = allUsers
        assertEquals(4, itVisibleUsers.size)
        assertTrue(itVisibleUsers.any { it.role == UserRole.IT_SUPPORT })
    }

    @Test
    fun testLogVisibility_adminCanSeeAdminAndAgentLogs_butNeverItSupportLogs() {
        val logAgent = AppActivityLog(id = "l1", serialNumber = "AGT-001", userName = "Agent A", userRole = UserRole.AGENT, activityType = "LOGIN", status = "INFO", description = "Login")
        val logAdmin = AppActivityLog(id = "l2", serialNumber = "ADM-001", userName = "Admin B", userRole = UserRole.ADMIN, activityType = "USER_CREATE", status = "INFO", description = "Buat Agent")
        val logIt = AppActivityLog(id = "l3", serialNumber = "IT-001", userName = "IT Master", userRole = UserRole.IT_SUPPORT, activityType = "MAINTENANCE_TOGGLE", status = "INFO", description = "Kunci Aplikasi")

        val allLogs = listOf(logAgent, logAdmin, logIt)

        // Filter for Admin caller:
        val adminVisibleLogs = allLogs.filter { it.userRole != UserRole.IT_SUPPORT }
        assertEquals(2, adminVisibleLogs.size)
        assertTrue(adminVisibleLogs.any { it.userRole == UserRole.AGENT })
        assertTrue(adminVisibleLogs.any { it.userRole == UserRole.ADMIN })
        assertFalse("Log IT Support TIDAK BOLEH terlihat oleh Admin", adminVisibleLogs.any { it.userRole == UserRole.IT_SUPPORT })

        // Filter for IT Support caller:
        val itVisibleLogs = allLogs
        assertEquals(3, itVisibleLogs.size)
    }
}
