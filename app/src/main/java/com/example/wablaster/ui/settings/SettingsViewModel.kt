package com.example.wablaster.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wablaster.data.local.SettingsDataStore
import com.example.wablaster.data.local.entity.ContactEntity
import com.example.wablaster.data.repo.ContactRepository
import com.example.wablaster.util.DelayConfig
import com.example.wablaster.util.PackageUtils
import com.example.wablaster.util.PhoneNormalizer
import com.example.wablaster.util.PhoneValidationResult
import com.example.wablaster.util.WhatsAppPackageInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.net.Uri
import com.example.wablaster.data.local.dao.SendLogDao
import com.example.wablaster.util.LogCsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val contactRepository: ContactRepository,
    private val sendLogDao: SendLogDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val sendMode: StateFlow<String> = settingsDataStore.sendModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ACCESSIBILITY")

    val defaultName: StateFlow<String> = settingsDataStore.defaultNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Kak")

    val delayConfig: StateFlow<DelayConfig> = settingsDataStore.delayConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DelayConfig())

    val optOutCount: StateFlow<Int> = contactRepository.getOptOutCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val optOutContacts: StateFlow<List<ContactEntity>> = contactRepository.getOptOutContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasCompletedOnboarding: StateFlow<Boolean> = settingsDataStore.hasCompletedOnboardingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val defaultTargetPackage: StateFlow<String> = settingsDataStore.defaultTargetPackageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "com.whatsapp")

    val defaultTargetLabel: StateFlow<String> = settingsDataStore.defaultTargetLabelFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "WhatsApp")

    private val _installedWhatsAppApps = MutableStateFlow<List<WhatsAppPackageInfo>>(emptyList())
    val installedWhatsAppApps: StateFlow<List<WhatsAppPackageInfo>> = _installedWhatsAppApps.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    init {
        refreshInstalledWhatsAppApps()
    }

    fun refreshInstalledWhatsAppApps() {
        val apps = PackageUtils.getInstalledWhatsAppApps(context)
        _installedWhatsAppApps.value = apps
    }

    fun setDefaultTargetWhatsApp(pkg: String, cls: String?, label: String) {
        viewModelScope.launch {
            settingsDataStore.saveDefaultTargetWhatsApp(pkg, cls, label)
            _feedback.value = "$label ditetapkan sebagai WhatsApp default."
        }
    }

    fun setSendMode(mode: String) {
        viewModelScope.launch {
            settingsDataStore.saveSendMode(mode)
            _feedback.value = if (mode == "INTENT_MANUAL") {
                "Mode Semi-Otomatis (Intent WA) diaktifkan."
            } else {
                "Mode Otomatis (Layanan Aksesibilitas) diaktifkan."
            }
        }
    }

    fun setDefaultName(name: String) {
        viewModelScope.launch {
            settingsDataStore.saveDefaultName(name)
        }
    }

    fun saveDelayConfig(config: DelayConfig) {
        viewModelScope.launch {
            settingsDataStore.saveDelayConfig(config)
            _feedback.value = "Pengaturan jeda berhasil disimpan."
        }
    }

    fun addBlockedNumbers(rawInput: String) {
        viewModelScope.launch {
            val lines = rawInput.split("\n", ",", ";", "|")
            val validPhones = mutableListOf<String>()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotBlank()) {
                    when (val result = PhoneNormalizer.normalize(trimmed)) {
                        is PhoneValidationResult.Valid -> {
                            if (!validPhones.contains(result.normalizedPhone)) {
                                validPhones.add(result.normalizedPhone)
                            }
                        }
                        is PhoneValidationResult.Invalid -> {
                            // Abaikan nomor tidak valid
                        }
                    }
                }
            }

            if (validPhones.isEmpty()) {
                _feedback.value = "Tidak ada nomor valid yang dapat diblokir."
                return@launch
            }

            val count = contactRepository.blockNumbers(validPhones)
            _feedback.value = "$count nomor berhasil ditambahkan ke daftar blokir."
        }
    }

    fun unblockContact(contactId: Long) {
        viewModelScope.launch {
            contactRepository.toggleOptOut(contactId, false)
            _feedback.value = "Nomor berhasil dikeluarkan dari daftar blokir."
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun setCompletedOnboarding(completed: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveHasCompletedOnboarding(completed)
        }
    }

    fun setAcceptedRiskAgreement(accepted: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveHasAcceptedRiskAgreement(accepted)
        }
    }

    fun exportLogsToUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logs = sendLogDao.getAllLogsList()
                val csvContent = LogCsvExporter.toCsvString(logs)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(csvContent)
                        writer.flush()
                    }
                }
                withContext(Dispatchers.Main) {
                    _feedback.value = "Berhasil mengekspor ${logs.size} log riwayat ke CSV."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _feedback.value = "Gagal mengekspor file: ${e.message}"
                }
            }
        }
    }
}
