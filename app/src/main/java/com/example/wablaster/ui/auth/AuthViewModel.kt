package com.example.wablaster.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wablaster.data.local.SettingsDataStore
import com.example.wablaster.data.supabase.AppConfig
import com.example.wablaster.data.supabase.SupabaseClient
import com.example.wablaster.data.supabase.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val serialNumber: String = "",
    val name: String = "",
    val role: UserRole = UserRole.AGENT,
    val deviceId: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isMaintenanceActive: Boolean = false,
    val maintenanceMessage: String = "",
    val customUrl: String = "",
    val customKey: String = "",
    val isSupabaseConfigured: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        loadSession()
        checkMaintenance()
    }

    private fun loadSession() {
        viewModelScope.launch {
            val deviceId = settingsDataStore.getOrCreateDeviceId()
            val isLoggedIn = settingsDataStore.isLoggedInFlow.first()
            val serial = settingsDataStore.userSessionSerialNumberFlow.first()
            val name = settingsDataStore.userSessionNameFlow.first()
            val roleStr = settingsDataStore.userSessionRoleFlow.first()
            val url = settingsDataStore.customSupabaseUrlFlow.first()
            val key = settingsDataStore.customSupabaseKeyFlow.first()
            val isConfigured = supabaseClient.isConfigured()

            _uiState.update {
                it.copy(
                    deviceId = deviceId,
                    isLoggedIn = isLoggedIn,
                    serialNumber = serial,
                    name = name,
                    role = UserRole.fromString(roleStr),
                    customUrl = url,
                    customKey = key,
                    isSupabaseConfigured = isConfigured
                )
            }
        }
    }

    fun checkMaintenance() {
        viewModelScope.launch {
            val res = supabaseClient.checkMaintenanceMode()
            res.onSuccess { config ->
                _uiState.update {
                    it.copy(
                        isMaintenanceActive = config.maintenanceMode,
                        maintenanceMessage = config.maintenanceMessage
                    )
                }
            }
        }
    }

    fun login(serialNumber: String) {
        val cleanSerial = serialNumber.trim().uppercase()
        if (cleanSerial.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Silakan masukkan Serial Number.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val deviceId = settingsDataStore.getOrCreateDeviceId()

            val result = supabaseClient.loginWithSerialNumber(cleanSerial, deviceId)
            result.onSuccess { user ->
                settingsDataStore.saveUserSession(user.serialNumber, user.name, user.role.name)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        serialNumber = user.serialNumber,
                        name = user.name,
                        role = user.role,
                        errorMessage = null
                    )
                }
                checkMaintenance()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Gagal login. Periksa serial number Anda."
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            settingsDataStore.clearUserSession()
            _uiState.update {
                it.copy(
                    isLoggedIn = false,
                    serialNumber = "",
                    name = "",
                    role = UserRole.AGENT,
                    errorMessage = null
                )
            }
        }
    }

    fun saveSupabaseConfig(url: String, key: String) {
        viewModelScope.launch {
            settingsDataStore.saveCustomSupabaseConfig(url, key)
            val isConfigured = supabaseClient.isConfigured()
            _uiState.update {
                it.copy(
                    customUrl = url.trim(),
                    customKey = key.trim(),
                    isSupabaseConfigured = isConfigured
                )
            }
            checkMaintenance()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
