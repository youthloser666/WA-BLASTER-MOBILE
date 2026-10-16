package com.example.wablaster.ui.log

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wablaster.data.local.dao.CampaignDao
import com.example.wablaster.data.local.dao.SendLogDao
import com.example.wablaster.data.local.entity.SendLogEntity
import com.example.wablaster.service.CampaignService
import com.example.wablaster.util.LogCsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val sendLogDao: SendLogDao,
    private val campaignDao: CampaignDao
) : ViewModel() {

    private val _selectedStatus = MutableStateFlow("ALL") // ALL, SENT, FAILED, SKIPPED
    val selectedStatus: StateFlow<String> = _selectedStatus.asStateFlow()

    private val allLogs = sendLogDao.getAllLogs()

    val filteredLogs: StateFlow<List<SendLogEntity>> = combine(
        allLogs,
        _selectedStatus
    ) { logs, status ->
        if (status == "ALL") {
            logs
        } else {
            logs.filter { it.status == status }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> = allLogs.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val sentCount: StateFlow<Int> = allLogs.map { logs -> logs.count { it.status == "SENT" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedCount: StateFlow<Int> = allLogs.map { logs -> logs.count { it.status == "FAILED" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val skippedCount: StateFlow<Int> = allLogs.map { logs -> logs.count { it.status == "SKIPPED" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _userFeedback = MutableStateFlow<String?>(null)
    val userFeedback: StateFlow<String?> = _userFeedback.asStateFlow()

    fun setStatusFilter(status: String) {
        _selectedStatus.value = status
    }

    fun clearFeedback() {
        _userFeedback.value = null
    }

    fun deleteAllLogs() {
        viewModelScope.launch {
            sendLogDao.deleteAllLogs()
            _userFeedback.value = "Semua riwayat pengiriman berhasil dihapus."
        }
    }

    fun exportToUri(context: Context, uri: Uri) {
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
                    _userFeedback.value = "Berhasil mengekspor ${logs.size} log ke CSV."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _userFeedback.value = "Gagal mengekspor file: ${e.message}"
                }
            }
        }
    }

    fun retryFailedContacts(context: Context) {
        viewModelScope.launch {
            val all = sendLogDao.getAllLogsList()
            val failedList = all.filter { it.status == "FAILED" }
            if (failedList.isEmpty()) {
                _userFeedback.value = "Tidak ada kontak dengan status gagal."
                return@launch
            }

            // Ambil kampanye terbaru yang memiliki kegagalan
            val targetCampaignId = failedList.first().campaignId
            val campaign = campaignDao.getById(targetCampaignId)
            if (campaign == null) {
                _userFeedback.value = "Kampanye terkait tidak ditemukan."
                return@launch
            }

            // Hapus log FAILED agar dicoba ulang bersih
            sendLogDao.deleteFailedLogsByCampaign(campaign.id)

            // Reset status kampanye agar memproses ulang dari awal (nomor SENT otomatis dilewati)
            val resetCampaign = campaign.copy(
                status = "IDLE",
                failedCount = 0,
                skippedCount = 0,
                lastProcessedIndex = 0,
                updatedAt = System.currentTimeMillis()
            )
            campaignDao.update(resetCampaign)

            // Jalankan ulang kampanye
            val intent = Intent(context, CampaignService::class.java).apply {
                action = CampaignService.ACTION_START
                putExtra(CampaignService.EXTRA_CAMPAIGN_ID, resetCampaign.id)
                putExtra(CampaignService.EXTRA_TITLE, resetCampaign.title)
                putExtra(CampaignService.EXTRA_TARGET_PACKAGE, resetCampaign.targetPackage)
            }
            ContextCompat.startForegroundService(context, intent)
            _userFeedback.value = "Memulai pengiriman ulang untuk nomor yang gagal..."
        }
    }
}
