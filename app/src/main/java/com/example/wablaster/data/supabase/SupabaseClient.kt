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

        // Validasi koneksi Supabase
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Koneksi Supabase belum dikonfigurasi di local.properties."))
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

                val waStatus = if (obj.isNull("wa_status")) "NORMAL" else obj.optString("wa_status", "NORMAL")
                val waBannedAt = if (obj.isNull("wa_banned_at")) null else obj.optString("wa_banned_at")
                val waBannedReason = if (obj.isNull("wa_banned_reason")) null else obj.optString("wa_banned_reason")

                val appUser = AppUser(
                    id = id,
                    serialNumber = sn,
                    name = name,
                    role = role,
                    deviceId = finalDeviceId,
                    isActive = isActive,
                    createdAt = obj.optString("created_at"),
                    createdBy = obj.optString("created_by"),
                    lastLoginAt = nowIso(),
                    waStatus = waStatus,
                    waBannedAt = waBannedAt,
                    waBannedReason = waBannedReason
                )

                // Rekam log aktivitas login
                try {
                    logActivity(
                        serialNumber = sn,
                        userName = name,
                        userRole = role,
                        activityType = "LOGIN",
                        status = "INFO",
                        description = "Masuk ke aplikasi di perangkat $deviceId",
                        deviceId = deviceId
                    )
                } catch (_: Exception) {}

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
    suspend fun getUsers(callerRole: UserRole = UserRole.IT_SUPPORT): Result<List<AppUser>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(emptyList())
        }

        try {
            // Admin boleh melihat akun ADMIN dan AGENT. Akun & key IT Support 100% disembunyikan.
            val roleFilter = if (callerRole == UserRole.ADMIN) "&role=neq.IT_SUPPORT" else ""
            val url = "${getBaseUrl()}/rest/v1/app_users?select=*${roleFilter}&order=created_at.desc"
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
                    val parsedRole = UserRole.fromString(obj.optString("role", "AGENT"))

                    // Filter keamanan tambahan: Admin tidak boleh melihat key / akun IT Support sama sekali
                    if (callerRole == UserRole.ADMIN && parsedRole == UserRole.IT_SUPPORT) {
                        continue
                    }

                    val waStatus = if (obj.isNull("wa_status")) "NORMAL" else obj.optString("wa_status", "NORMAL")
                    val waBannedAt = if (obj.isNull("wa_banned_at")) null else obj.optString("wa_banned_at")
                    val waBannedReason = if (obj.isNull("wa_banned_reason")) null else obj.optString("wa_banned_reason")

                    list.add(
                        AppUser(
                            id = obj.getString("id"),
                            serialNumber = obj.getString("serial_number"),
                            name = obj.optString("name", "-"),
                            role = parsedRole,
                            deviceId = if (obj.isNull("device_id")) null else obj.optString("device_id"),
                            isActive = obj.optBoolean("is_active", true),
                            createdAt = obj.optString("created_at"),
                            createdBy = obj.optString("created_by"),
                            lastLoginAt = obj.optString("last_login_at"),
                            waStatus = waStatus,
                            waBannedAt = waBannedAt,
                            waBannedReason = waBannedReason
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
            return@withContext Result.failure(Exception("Koneksi Supabase belum dikonfigurasi."))
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

    /**
     * Mencatat log aktivitas ke tabel app_activity_logs.
     */
    suspend fun logActivity(
        serialNumber: String,
        userName: String,
        userRole: UserRole,
        activityType: String,
        status: String,
        description: String,
        targetPhone: String? = null,
        deviceId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(Unit)
        }

        try {
            val url = "${getBaseUrl()}/rest/v1/app_activity_logs"
            val payload = JSONObject().apply {
                put("serial_number", serialNumber)
                put("user_name", userName)
                put("user_role", userRole.name)
                put("activity_type", activityType)
                put("status", status)
                put("description", description)
                if (!targetPhone.isNullOrBlank()) put("target_phone", targetPhone)
                if (!deviceId.isNullOrBlank()) put("device_id", deviceId)
                put("created_at", nowIso())
            }

            val request = buildHeaders(
                Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .header("Prefer", "return=minimal")
            ).build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Gagal mencatat log aktivitas (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Melaporkan nomor WhatsApp terdeteksi BANNED ke Supabase.
     */
    suspend fun reportWaBanned(
        serialNumber: String,
        userName: String,
        userRole: UserRole,
        reason: String,
        targetPhone: String? = null,
        deviceId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val time = nowIso()
        if (isConfigured()) {
            try {
                val url = "${getBaseUrl()}/rest/v1/app_users?serial_number=eq.$serialNumber"
                val payload = JSONObject().apply {
                    put("wa_status", "BANNED")
                    put("wa_banned_at", time)
                    put("wa_banned_reason", reason)
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

        logActivity(
            serialNumber = serialNumber,
            userName = userName,
            userRole = userRole,
            activityType = "WA_BANNED",
            status = "BANNED",
            description = "Nomor WhatsApp terdeteksi DIBLOKIR/BANNED saat kirim: $reason",
            targetPhone = targetPhone,
            deviceId = deviceId
        )
    }

    /**
     * Mereset status BANNED WhatsApp kembali ke NORMAL.
     */
    suspend fun resetWaStatus(
        targetSerial: String,
        callerSerial: String,
        callerName: String,
        callerRole: UserRole
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isConfigured()) {
            try {
                val url = "${getBaseUrl()}/rest/v1/app_users?serial_number=eq.$targetSerial"
                val payload = JSONObject().apply {
                    put("wa_status", "NORMAL")
                    put("wa_banned_at", JSONObject.NULL)
                    put("wa_banned_reason", JSONObject.NULL)
                }
                val request = buildHeaders(
                    Request.Builder()
                        .url(url)
                        .patch(payload.toString().toRequestBody(jsonMediaType))
                        .header("Prefer", "return=minimal")
                ).build()
                httpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }

        logActivity(
            serialNumber = callerSerial,
            userName = callerName,
            userRole = callerRole,
            activityType = "WA_STATUS_RESET",
            status = "SUCCESS",
            description = "Mereset status Banned WA untuk akun $targetSerial ke NORMAL"
        )
        Result.success(Unit)
    }

    /**
     * Mengambil daftar log aktivitas dengan penyaringan hak akses:
     * - IT_SUPPORT: Bisa melihat seluruh aktivitas (IT Support, Admin, Agent)
     * - ADMIN: Bisa melihat aktivitas Admin lain dan Agent. Log IT_SUPPORT 100% dirahasiakan.
     */
    suspend fun getActivityLogs(callerRole: UserRole): Result<List<AppActivityLog>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(emptyList())
        }

        try {
            // Admin boleh melihat log ADMIN dan AGENT. Log IT Support disembunyikan total.
            val roleFilter = if (callerRole == UserRole.ADMIN) "&user_role=neq.IT_SUPPORT" else ""
            val url = "${getBaseUrl()}/rest/v1/app_activity_logs?select=*${roleFilter}&order=created_at.desc&limit=200"
            val request = buildHeaders(Request.Builder().url(url).get()).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.success(emptyList())
                }

                val body = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(body)
                val list = mutableListOf<AppActivityLog>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val logRole = UserRole.fromString(obj.optString("user_role", "AGENT"))

                    // Filter akses:
                    // IT Support bisa melihat semua (IT Support, Admin, Agent)
                    // Admin bisa melihat aktivitas Admin lain dan Agent, tetapi IT_SUPPORT tidak boleh ada yang tahu
                    val isVisible = when (callerRole) {
                        UserRole.IT_SUPPORT -> true
                        UserRole.ADMIN -> logRole != UserRole.IT_SUPPORT
                        UserRole.AGENT -> false
                    }

                    if (isVisible) {
                        list.add(
                            AppActivityLog(
                                id = obj.optString("id", UUID.randomUUID().toString()),
                                serialNumber = obj.optString("serial_number", "-"),
                                userName = obj.optString("user_name", "-"),
                                userRole = logRole,
                                activityType = obj.optString("activity_type", "INFO"),
                                status = obj.optString("status", "INFO"),
                                description = obj.optString("description", "-"),
                                targetPhone = if (obj.isNull("target_phone")) null else obj.optString("target_phone"),
                                deviceId = if (obj.isNull("device_id")) null else obj.optString("device_id"),
                                createdAt = obj.optString("created_at")
                            )
                        )
                    }
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
