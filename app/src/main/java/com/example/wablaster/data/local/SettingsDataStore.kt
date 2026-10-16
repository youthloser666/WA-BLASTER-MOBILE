package com.example.wablaster.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.wablaster.domain.VariantMode
import com.example.wablaster.util.DelayConfig
import com.example.wablaster.util.DelayMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wa_blaster_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object PreferencesKeys {
        val TEMPLATE_MESSAGE = stringPreferencesKey("template_message")
        val VARIANT_1 = stringPreferencesKey("variant_1")
        val VARIANT_2 = stringPreferencesKey("variant_2")
        val VARIANT_3 = stringPreferencesKey("variant_3")
        val VARIANT_4 = stringPreferencesKey("variant_4")
        val VARIANT_5 = stringPreferencesKey("variant_5")
        val VARIANT_MODE = stringPreferencesKey("variant_mode")
        val DEFAULT_NAME = stringPreferencesKey("default_name")

        val DELAY_MODE = stringPreferencesKey("delay_mode")
        val DELAY_FIXED_SECONDS = intPreferencesKey("delay_fixed_seconds")
        val DELAY_MIN_SECONDS = intPreferencesKey("delay_min_seconds")
        val DELAY_MAX_SECONDS = intPreferencesKey("delay_max_seconds")
        val DELAY_BATCH_SIZE = intPreferencesKey("delay_batch_size")
        val DELAY_BATCH_PAUSE_MINUTES = intPreferencesKey("delay_batch_pause_minutes")
        val DELAY_DAILY_LIMIT = intPreferencesKey("delay_daily_limit")
        val RECIPIENT_INPUT_DRAFT = stringPreferencesKey("recipient_input_draft")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val HAS_ACCEPTED_RISK_AGREEMENT = booleanPreferencesKey("has_accepted_risk_agreement")
        val SEND_MODE = stringPreferencesKey("send_mode") // ACCESSIBILITY or INTENT_MANUAL
        val DEFAULT_TARGET_PACKAGE = stringPreferencesKey("default_target_package")
        val DEFAULT_TARGET_CLASS = stringPreferencesKey("default_target_class")
        val DEFAULT_TARGET_LABEL = stringPreferencesKey("default_target_label")

        val USER_SERIAL_NUMBER = stringPreferencesKey("user_serial_number")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_ROLE = stringPreferencesKey("user_role")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val CUSTOM_SUPABASE_URL = stringPreferencesKey("custom_supabase_url")
        val CUSTOM_SUPABASE_ANON_KEY = stringPreferencesKey("custom_supabase_anon_key")
        val PERSISTENT_DEVICE_ID = stringPreferencesKey("persistent_device_id")
    }

    val templateMessageFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.VARIANT_1]
                ?: preferences[PreferencesKeys.TEMPLATE_MESSAGE]
                ?: "Halo {nama}, kami ingin menginfokan promo spesial untuk Anda hari ini!"
        }

    val variantsFlow: Flow<List<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val v1 = preferences[PreferencesKeys.VARIANT_1]
                ?: preferences[PreferencesKeys.TEMPLATE_MESSAGE]
                ?: "Halo {nama}, kami ingin menginfokan promo spesial untuk Anda hari ini!"
            val v2 = preferences[PreferencesKeys.VARIANT_2] ?: ""
            val v3 = preferences[PreferencesKeys.VARIANT_3] ?: ""
            val v4 = preferences[PreferencesKeys.VARIANT_4] ?: ""
            val v5 = preferences[PreferencesKeys.VARIANT_5] ?: ""
            listOf(v1, v2, v3, v4, v5)
        }

    val variantModeFlow: Flow<VariantMode> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val modeStr = preferences[PreferencesKeys.VARIANT_MODE] ?: VariantMode.SEQUENTIAL.name
            try {
                VariantMode.valueOf(modeStr)
            } catch (e: Exception) {
                VariantMode.SEQUENTIAL
            }
        }

    val defaultNameFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.DEFAULT_NAME] ?: "Kak"
        }

    val delayConfigFlow: Flow<DelayConfig> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val modeStr = preferences[PreferencesKeys.DELAY_MODE] ?: DelayMode.RANDOM.name
            val mode = try {
                DelayMode.valueOf(modeStr)
            } catch (e: Exception) {
                DelayMode.RANDOM
            }

            DelayConfig(
                mode = mode,
                fixedSeconds = preferences[PreferencesKeys.DELAY_FIXED_SECONDS] ?: 10,
                minSeconds = preferences[PreferencesKeys.DELAY_MIN_SECONDS] ?: 8,
                maxSeconds = preferences[PreferencesKeys.DELAY_MAX_SECONDS] ?: 25,
                batchSize = preferences[PreferencesKeys.DELAY_BATCH_SIZE] ?: 20,
                batchPauseMinutes = preferences[PreferencesKeys.DELAY_BATCH_PAUSE_MINUTES] ?: 5,
                dailyLimit = preferences[PreferencesKeys.DELAY_DAILY_LIMIT] ?: 200
            )
        }

    val recipientInputDraftFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.RECIPIENT_INPUT_DRAFT] ?: ""
        }

    suspend fun saveRecipientInputDraft(draft: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECIPIENT_INPUT_DRAFT] = draft
        }
    }

    suspend fun saveTemplateMessage(template: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEMPLATE_MESSAGE] = template
            preferences[PreferencesKeys.VARIANT_1] = template
        }
    }

    suspend fun saveVariants(variants: List<String>) {
        context.dataStore.edit { preferences ->
            if (variants.isNotEmpty()) {
                preferences[PreferencesKeys.VARIANT_1] = variants[0]
                preferences[PreferencesKeys.TEMPLATE_MESSAGE] = variants[0]
            }
            if (variants.size > 1) preferences[PreferencesKeys.VARIANT_2] = variants[1]
            if (variants.size > 2) preferences[PreferencesKeys.VARIANT_3] = variants[2]
            if (variants.size > 3) preferences[PreferencesKeys.VARIANT_4] = variants[3]
            if (variants.size > 4) preferences[PreferencesKeys.VARIANT_5] = variants[4]
        }
    }

    suspend fun saveVariantMode(mode: VariantMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VARIANT_MODE] = mode.name
        }
    }

    suspend fun saveDefaultName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_NAME] = name
        }
    }

    val hasCompletedOnboardingFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] ?: false
        }

    val hasAcceptedRiskAgreementFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.HAS_ACCEPTED_RISK_AGREEMENT] ?: false
        }

    val sendModeFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.SEND_MODE] ?: "ACCESSIBILITY"
        }

    suspend fun saveHasCompletedOnboarding(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] = completed
        }
    }

    suspend fun saveHasAcceptedRiskAgreement(accepted: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_ACCEPTED_RISK_AGREEMENT] = accepted
        }
    }

    suspend fun saveSendMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SEND_MODE] = mode
        }
    }

    val defaultTargetPackageFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.DEFAULT_TARGET_PACKAGE] ?: "com.whatsapp"
        }

    val defaultTargetClassFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.DEFAULT_TARGET_CLASS] ?: ""
        }

    val defaultTargetLabelFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.DEFAULT_TARGET_LABEL] ?: "WhatsApp"
        }

    suspend fun saveDefaultTargetWhatsApp(packageName: String, className: String? = null, label: String = "WhatsApp") {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_TARGET_PACKAGE] = packageName
            preferences[PreferencesKeys.DEFAULT_TARGET_CLASS] = className ?: ""
            preferences[PreferencesKeys.DEFAULT_TARGET_LABEL] = label
        }
    }

    suspend fun saveDelayConfig(config: DelayConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DELAY_MODE] = config.mode.name
            preferences[PreferencesKeys.DELAY_FIXED_SECONDS] = config.fixedSeconds
            preferences[PreferencesKeys.DELAY_MIN_SECONDS] = config.minSeconds
            preferences[PreferencesKeys.DELAY_MAX_SECONDS] = config.maxSeconds
            preferences[PreferencesKeys.DELAY_BATCH_SIZE] = config.batchSize
            preferences[PreferencesKeys.DELAY_BATCH_PAUSE_MINUTES] = config.batchPauseMinutes
            preferences[PreferencesKeys.DELAY_DAILY_LIMIT] = config.dailyLimit
        }
    }

    val userSessionSerialNumberFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.USER_SERIAL_NUMBER] ?: "" }

    val userSessionNameFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.USER_NAME] ?: "" }

    val userSessionRoleFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.USER_ROLE] ?: "AGENT" }

    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.IS_LOGGED_IN] ?: false }

    val customSupabaseUrlFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.CUSTOM_SUPABASE_URL] ?: "" }

    val customSupabaseKeyFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.CUSTOM_SUPABASE_ANON_KEY] ?: "" }

    val deviceIdFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.PERSISTENT_DEVICE_ID] ?: "" }

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.dataStore.data.map { it[PreferencesKeys.PERSISTENT_DEVICE_ID] }.first()
        if (!existing.isNullOrBlank()) return existing

        val hardwareId = try {
            android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }
        val finalId = if (!hardwareId.isNullOrBlank() && hardwareId != "9774d56d682e549c") {
            hardwareId
        } else {
            java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        }

        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.PERSISTENT_DEVICE_ID] = finalId
        }
        return finalId
    }

    suspend fun saveUserSession(serialNumber: String, name: String, role: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.USER_SERIAL_NUMBER] = serialNumber
            prefs[PreferencesKeys.USER_NAME] = name
            prefs[PreferencesKeys.USER_ROLE] = role
            prefs[PreferencesKeys.IS_LOGGED_IN] = true
        }
    }

    suspend fun clearUserSession() {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.USER_SERIAL_NUMBER] = ""
            prefs[PreferencesKeys.USER_NAME] = ""
            prefs[PreferencesKeys.USER_ROLE] = "AGENT"
            prefs[PreferencesKeys.IS_LOGGED_IN] = false
        }
    }

    suspend fun saveCustomSupabaseConfig(url: String, key: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.CUSTOM_SUPABASE_URL] = url.trim()
            prefs[PreferencesKeys.CUSTOM_SUPABASE_ANON_KEY] = key.trim()
        }
    }
}

