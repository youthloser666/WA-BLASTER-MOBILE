package com.example.wablaster.data.supabase

import com.example.wablaster.BuildConfig
import com.example.wablaster.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseClient @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getBaseUrl(): String {
        val custom = settingsDataStore.customSupabaseUrlFlow.first().trim()
        val base = if (custom.isNotBlank()) custom else BuildConfig.SUPABASE_URL
        return base.trim().removeSuffix("/")
    }

    suspend fun getApiKey(): String {
        val custom = settingsDataStore.customSupabaseKeyFlow.first().trim()
        return if (custom.isNotBlank()) custom else BuildConfig.SUPABASE_ANON_KEY
    }

    suspend fun isConfigured(): Boolean {
        return getBaseUrl().isNotBlank() && getApiKey().isNotBlank()
    }

    private suspend fun buildHeaders(requestBuilder: Request.Builder): Request.Builder {
        val key = getApiKey()
        return requestBuilder
            .header("apikey", key)
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
    }

    private fun nowIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    /**
     * Memeriksa status Maintenance Mode global dari Supabase.
     */
    suspend fun checkMaintenanceMode(): Result<AppConfig> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(AppConfig(maintenanceMode = false))
        }

        try {
            val url = "${getBaseUrl()}/rest/v1/app_config?id=eq.1&select=*"
            val request = buildHeaders(Request.Builder().url(url).get()).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Gagal mengambil status server (HTTP ${response.code})"))
                }

                val body = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(body)
                if (jsonArray.length() == 0) {
                    return@withContext Result.success(AppConfig(maintenanceMode = false))
                }

                val obj = jsonArray.getJSONObject(0)
                val config = AppConfig(
                    id = obj.optInt("id", 1),
                    maintenanceMode = obj.optBoolean("maintenance_mode", false),
                    maintenanceMessage = obj.optString("maintenance_message", "Sistem sedang dalam pemeliharaan oleh IT Support."),
                    updatedAt = obj.optString("updated_at"),
                    updatedBy = obj.optString("updated_by")
                )
                Result.success(config)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengatur status Maintenance Mode global (Khusus IT Support).
     */
    suspend fun setMaintenanceMode(active: Boolean, message: String, updatedBy: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Supabase belum dikonfigurasi."))
        }

        try {
            val url = "${getBaseUrl()}/rest/v1/app_config?id=eq.1"
            val payload = JSONObject().apply {
                put("maintenance_mode", active)
                put("maintenance_message", message)
                put("updated_by", updatedBy)
                put("updated_at", nowIso())
            }

            val request = buildHeaders(
                Request.Builder()
                    .url(url)
                    .patch(payload.toString().toRequestBody(jsonMediaType))
                    .header("Prefer", "return=minimal")
            ).build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Gagal mengubah mode maintenance (HTTP ${response.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Autentikasi menggunakan Serial Number & validasi penguncian perangkat (Device Lock).
     */
    suspend fun loginWithSerialNumber(serialNumber: String, deviceId: String): Result<AppUser> = withContext(Dispatchers.IO) {
        val cleanSerial = serialNumber.trim().uppercase()
        if (cleanSerial.isBlank()) {
            return@withContext Result.failure(Exception("Masukkan serial number terlebih dahulu."))
        }

        // Demo / Fallback Offline jika Supabase belum dihubungkan
        if (!isConfigured()) {
            val demoUser = when {
                cleanSerial.startsWith("IT") || cleanSerial.contains("IT") -> AppUser(
                    id = "demo-it",
                    serialNumber = cleanSerial,
                    name = "Demo IT Support",
                    role = UserRole.IT_SUPPORT
                )
                cleanSerial.startsWith("ADM") || cleanSerial.contains("ADMIN") -> AppUser(
                    id = "demo-admin",
                    serialNumber = cleanSerial,
                    name = "Demo Admin",
                    role = UserRole.ADMIN
                )
                else -> AppUser(
                    id = "demo-agent",
                    serialNumber = cleanSerial,
                    name = "Demo Agent",
                    role = UserRole.AGENT,
                    deviceId = deviceId
                )
            }
            return@withContext Result.success(demoUser)
        }

        try {
            val url = "${getBaseUrl()}/rest/v1/app_users?serial_number=eq.$cleanSerial&select=*"
            val request = buildHeaders(Request.Builder().url(url).get()).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Gagal menghubungi server Supabase (HTTP ${response.code})"))
                }

                val body = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(body)
                if (jsonArray.length() == 0) {
                    return@withContext Result.failure(Exception("Serial number '$cleanSerial' tidak terdaftar."))
                }

                val obj = jsonArray.getJSONObject(0)
                val id = obj.getString("id")
                val sn = obj.getString("serial_number")
                val name = obj.optString("name", sn)
                val role = UserRole.fromString(obj.optString("role", "AGENT"))
                val existingDeviceId = if (obj.isNull("device_id")) null else obj.optString("device_id")
                val isActive = obj.optBoolean("is_active", true)

                if (!isActive) {
                    return@withContext Result.failure(Exception("Akun ini telah dinonaktifkan. Hubungi Admin atau IT Support."))
                }

                // VALIDASI ATURAN ROLE:
                var finalDeviceId = existingDeviceId
                if (role == UserRole.AGENT) {
                    // 1 Serial Number hanya untuk 1 Device
                    if (existingDeviceId.isNullOrBlank()) {
                        // Pertama kali login di perangkat ini -> Ikat (Bind) ke device ini
                        updateDeviceAndLoginTime(id, deviceId)
                        finalDeviceId = deviceId
                    } else if (existingDeviceId != deviceId) {
                        // Perangkat berbeda -> Tolak login
                        return@withContext Result.failure(
                            Exception("Serial number ini sudah terikat pada perangkat lain! Hubungi Admin atau IT Support untuk mereset perangkat.")
                        )
                    } else {
                        // Perangkat sama -> Perbarui waktu login
                        updateLoginTime(id)
                    }
                } else {
                    // ADMIN & IT SUPPORT: Bebas login di perangkat berbeda (Multi-device)
                    updateLoginTime(id)
                }

                val appUser = AppUser(
                    id = id,
                    serialNumber = sn,
                    name = name,
                    role = role,
                    deviceId = finalDeviceId,
                    isActive = isActive,
                    createdAt = obj.optString("created_at"),
                    createdBy = obj.optString("created_by"),
                    lastLoginAt = nowIso()
                )
                Result.success(appUser)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateDeviceAndLoginTime(userId: String, deviceId: String) {
        try {
            val url = "${getBaseUrl()}/rest/v1/app_users?id=eq.$userId"
            val payload = JSONObject().apply {
                put("device_id", deviceId)
                put("last_login_at", nowIso())
            }
            val request = buildHeaders(
                Request.Builder()
                    .url(url)
                    .patch(payload.toString().toRequestBody(jsonMediaType))
                    .header("Prefer", "return=minimal")
            ).build()
            httpClient.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    private suspend fun updateLoginTime(userId: String) {
        try {
            val url = "${getBaseUrl()}/rest/v1/app_users?id=eq.$userId"
            val payload = JSONObject().apply {
                put("last_login_at", nowIso())
            }
            val request = buildHeaders(
                Request.Builder()
                    .url(url)
                    .patch(payload.toString().toRequestBody(jsonMediaType))
                    .header("Prefer", "return=minimal")
            ).build()
            httpClient.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    /**
     * Mengambil daftar semua akun (untuk panel Admin / IT Support).
     */
    suspend fun getUsers(): Result<List<AppUser>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            // Data demo lokal jika belum konek Supabase
            val demoList = listOf(
                AppUser("demo-1", "AGT-DEMO-001", "Agent Budi", UserRole.AGENT, "dev-34c9a9f6", true),
                AppUser("demo-2", "AGT-DEMO-002", "Agent Siti", UserRole.AGENT, null, true),
                AppUser("demo-3", "ADM-DEMO-101", "Admin Surya", UserRole.ADMIN, null, true),
                AppUser("demo-4", "IT-MASTER-999", "IT Support Master", UserRole.IT_SUPPORT, null, true)
            )
            return@withContext Result.success(demoList)
        }

        try {
            val url = "${getBaseUrl()}/rest/v1/app_users?select=*&order=created_at.desc"
            val request = buildHeaders(Request.Builder().url(url).get()).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Gagal mengambil data akun (HTTP ${response.code})"))
                }

                val body = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(body)
                val list = mutableListOf<AppUser>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        AppUser(
                            id = obj.getString("id"),
                            serialNumber = obj.getString("serial_number"),
                            name = obj.optString("name", "-"),
                            role = UserRole.fromString(obj.optString("role", "AGENT")),
                            deviceId = if (obj.isNull("device_id")) null else obj.optString("device_id"),
                            isActive = obj.optBoolean("is_active", true),
                            createdAt = obj.optString("created_at"),
                            createdBy = obj.optString("created_by"),
                            lastLoginAt = obj.optString("last_login_at")
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Membuat akun / serial number baru.
     * Admin hanya boleh membuat AGENT.
     * IT Support boleh membuat AGENT dan ADMIN.
     */
    suspend fun createUser(serialNumber: String, name: String, role: UserRole, createdBy: String): Result<AppUser> = withContext(Dispatchers.IO) {
        val cleanSerial = serialNumber.trim().uppercase()
        if (cleanSerial.isBlank() || name.isBlank()) {
            return@withContext Result.failure(Exception("Nama dan Serial Number wajib diisi."))
        }

        if (!isConfigured()) {
            return@withContext Result.success(
                AppUser(UUID.randomUUID().toString(), cleanSerial, name, role, null, true, nowIso(), createdBy)
            )
        }

        try {
            val url = "${getBaseUrl()}/rest/v1/app_users"
            val payload = JSONObject().apply {
                put("serial_number", cleanSerial)
                put("name", name.trim())
                put("role", role.name)
                put("created_by", createdBy)
                put("is_active", true)
            }

            val request = buildHeaders(
                Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .header("Prefer", "return=representation")
            ).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    return@withContext if (err.contains("duplicate") || err.contains("unique")) {
                        Result.failure(Exception("Serial number '$cleanSerial' sudah digunakan akun lain."))
                    } else {
                        Result.failure(Exception("Gagal membuat akun (HTTP ${response.code})"))
                    }
                }

                val body = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(body)
                if (jsonArray.length() == 0) {
                    return@withContext Result.failure(Exception("Gagal memproses data akun."))
                }

                val obj = jsonArray.getJSONObject(0)
                val newUser = AppUser(
                    id = obj.getString("id"),
                    serialNumber = obj.getString("serial_number"),
                    name = obj.optString("name", name),
                    role = role,
                    deviceId = null,
                    isActive = true,
                    createdAt = obj.optString("created_at"),
                    createdBy = createdBy
                )
                Result.success(newUser)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengubah status aktif / nonaktif akun.
     */
    suspend fun updateUserStatus(userId: String, isActive: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(true)

        try {
            val url = "${getBaseUrl()}/rest/v1/app_users?id=eq.$userId"
            val payload = JSONObject().apply {
                put("is_active", isActive)
            }
            val request = buildHeaders(
                Request.Builder()
                    .url(url)
                    .patch(payload.toString().toRequestBody(jsonMediaType))
                    .header("Prefer", "return=minimal")
            ).build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(true)
                else Result.failure(Exception("Gagal memperbarui status akun (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reset ikatan perangkat (Device Lock) agar Agent bisa login di perangkat baru.
     */
    suspend fun resetUserDevice(userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(true)

        try {
            val url = "${getBaseUrl()}/rest/v1/app_users?id=eq.$userId"
            val payload = JSONObject().apply {
                put("device_id", JSONObject.NULL)
            }
            val request = buildHeaders(
                Request.Builder()
                    .url(url)
                    .patch(payload.toString().toRequestBody(jsonMediaType))
                    .header("Prefer", "return=minimal")
            ).build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(true)
                else Result.failure(Exception("Gagal mereset perangkat akun (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Menghapus akun.
     */
    suspend fun deleteUser(userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(true)

        try {
            val url = "${getBaseUrl()}/rest/v1/app_users?id=eq.$userId"
            val request = buildHeaders(
                Request.Builder()
                    .url(url)
                    .delete()
                    .header("Prefer", "return=minimal")
            ).build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(true)
                else Result.failure(Exception("Gagal menghapus akun (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
