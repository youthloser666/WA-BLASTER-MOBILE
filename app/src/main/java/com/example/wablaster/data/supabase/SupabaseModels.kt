package com.example.wablaster.data.supabase

enum class UserRole {
    AGENT,
    ADMIN,
    IT_SUPPORT;

    companion object {
        fun fromString(role: String?): UserRole {
            return when (role?.trim()?.uppercase()) {
                "ADMIN" -> ADMIN
                "IT_SUPPORT" -> IT_SUPPORT
                else -> AGENT
            }
        }
    }
}

data class AppUser(
    val id: String,
    val serialNumber: String,
    val name: String,
    val role: UserRole,
    val deviceId: String? = null,
    val isActive: Boolean = true,
    val createdAt: String? = null,
    val createdBy: String? = null,
    val lastLoginAt: String? = null
)

data class AppConfig(
    val id: Int = 1,
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "Sistem sedang dalam pemeliharaan oleh tim IT Support.",
    val updatedAt: String? = null,
    val updatedBy: String? = null
)
