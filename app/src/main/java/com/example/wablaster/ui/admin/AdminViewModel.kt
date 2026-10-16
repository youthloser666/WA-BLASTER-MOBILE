package com.example.wablaster.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wablaster.data.supabase.AppConfig
import com.example.wablaster.data.supabase.AppUser
import com.example.wablaster.data.supabase.SupabaseClient
import com.example.wablaster.data.supabase.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AdminUiState(
    val users: List<AppUser> = emptyList(),
    val filteredUsers: List<AppUser> = emptyList(),
    val searchQuery: String = "",
    val filterRole: UserRole? = null,
    val isLoading: Boolean = false,
    val isMaintenanceActive: Boolean = false,
    val maintenanceMessage: String = "",
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val usersRes = supabaseClient.getUsers()
            val configRes = supabaseClient.checkMaintenanceMode()

            var usersList = _uiState.value.users
            usersRes.onSuccess { list ->
                usersList = list
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = "Gagal memuat pengguna: ${err.message}") }
            }

            var isMaint = _uiState.value.isMaintenanceActive
            var maintMsg = _uiState.value.maintenanceMessage
            configRes.onSuccess { cfg ->
                isMaint = cfg.maintenanceMode
                maintMsg = cfg.maintenanceMessage
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    users = usersList,
                    isMaintenanceActive = isMaint,
                    maintenanceMessage = maintMsg
                )
            }
            applyFilters()
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun setFilterRole(role: UserRole?) {
        _uiState.update { it.copy(filterRole = role) }
        applyFilters()
    }

    private fun applyFilters() {
        val q = _uiState.value.searchQuery.trim().lowercase()
        val roleFilter = _uiState.value.filterRole

        val filtered = _uiState.value.users.filter { user ->
            val matchRole = (roleFilter == null || user.role == roleFilter)
            val matchQuery = q.isEmpty() ||
                    user.name.lowercase().contains(q) ||
                    user.serialNumber.lowercase().contains(q)
            matchRole && matchQuery
        }
        _uiState.update { it.copy(filteredUsers = filtered) }
    }

    fun createUser(name: String, role: UserRole, customSerial: String = "", createdBy: String) {
        val finalSerial = if (customSerial.isNotBlank()) {
            customSerial.trim().uppercase()
        } else {
            val prefix = when (role) {
                UserRole.AGENT -> "AGT"
                UserRole.ADMIN -> "ADM"
                UserRole.IT_SUPPORT -> "IT"
            }
            val rand = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
            "$prefix-$rand"
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val res = supabaseClient.createUser(finalSerial, name, role, createdBy)
            res.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Akun '$name' ($finalSerial) berhasil dibuat!"
                    )
                }
                loadData()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Gagal membuat akun."
                    )
                }
            }
        }
    }

    fun toggleUserStatus(user: AppUser) {
        viewModelScope.launch {
            val newStatus = !user.isActive
            val res = supabaseClient.updateUserStatus(user.id, newStatus)
            res.onSuccess {
                _uiState.update {
                    it.copy(statusMessage = "Status akun '${user.name}' diubah menjadi ${if (newStatus) "Aktif" else "Nonaktif"}.")
                }
                loadData()
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun resetDevice(user: AppUser) {
        viewModelScope.launch {
            val res = supabaseClient.resetUserDevice(user.id)
            res.onSuccess {
                _uiState.update {
                    it.copy(statusMessage = "Perangkat akun '${user.name}' berhasil di-reset. Agent dapat login di perangkat baru.")
                }
                loadData()
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun deleteUser(user: AppUser) {
        viewModelScope.launch {
            val res = supabaseClient.deleteUser(user.id)
            res.onSuccess {
                _uiState.update {
                    it.copy(statusMessage = "Akun '${user.name}' (${user.serialNumber}) berhasil dihapus.")
                }
                loadData()
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun toggleMaintenance(active: Boolean, message: String, updatedBy: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val res = supabaseClient.setMaintenanceMode(active, message, updatedBy)
            res.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isMaintenanceActive = active,
                        maintenanceMessage = message,
                        statusMessage = if (active) "Aplikasi DIKUNCI (Maintenance Mode Aktif)" else "Aplikasi DIBUKA (Maintenance Mode Selesai)"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = err.message)
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}
