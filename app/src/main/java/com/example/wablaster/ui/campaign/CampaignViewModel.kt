package com.example.wablaster.ui.campaign

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wablaster.data.local.SettingsDataStore
import com.example.wablaster.data.local.dao.CampaignDao
import com.example.wablaster.data.local.dao.SendLogDao
import com.example.wablaster.data.local.entity.CampaignEntity
import com.example.wablaster.data.repo.ContactRepository
import com.example.wablaster.domain.CampaignHealthStatus
import com.example.wablaster.service.CampaignRunningState
import com.example.wablaster.service.CampaignService
import com.example.wablaster.service.WaAccessibilityService
import com.example.wablaster.util.PackageUtils
import com.example.wablaster.util.WhatsAppPackageInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class CampaignViewModel @Inject constructor(
    private val campaignDao: CampaignDao,
    private val sendLogDao: SendLogDao,
    private val contactRepository: ContactRepository,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val campaignState: StateFlow<CampaignRunningState> = CampaignService.campaignState

    val activeCampaignFromDb: StateFlow<CampaignEntity?> = campaignDao.observeActiveCampaign()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalContacts: StateFlow<Int> = contactRepository.getTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val optOutCount: StateFlow<Int> = contactRepository.getOptOutCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isAccessibilityActive: StateFlow<Boolean> = WaAccessibilityService.isServiceActive

    val sendMode: StateFlow<String> = settingsDataStore.sendModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ACCESSIBILITY")

    val defaultTargetPackage: StateFlow<String> = settingsDataStore.defaultTargetPackageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "com.whatsapp")

    private val variants: StateFlow<List<String>> = settingsDataStore.variantsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _targetPackage = MutableStateFlow("com.whatsapp")
    val targetPackage: StateFlow<String> = _targetPackage.asStateFlow()

    private val _campaignTitle = MutableStateFlow("")
    val campaignTitle: StateFlow<String> = _campaignTitle.asStateFlow()

    private val _installedWhatsAppApps = MutableStateFlow<List<WhatsAppPackageInfo>>(emptyList())
    val installedWhatsAppApps: StateFlow<List<WhatsAppPackageInfo>> = _installedWhatsAppApps.asStateFlow()

    val healthStatus: StateFlow<CampaignHealthStatus> = combine(
        combine(isAccessibilityActive, sendMode) { acc, mode -> acc to mode },
        combine(totalContacts, optOutCount) { total, optOut -> (total - optOut).coerceAtLeast(0) },
        variants,
        _targetPackage
    ) { (accessibility, mode), activeCount, varList, targetPkg ->
        val hasVariants = varList.any { it.isNotBlank() }
        val isWaInstalled = PackageUtils.isPackageInstalled(appContext, targetPkg)

        CampaignHealthStatus(
            isAccessibilityActive = accessibility,
            isWhatsAppInstalled = isWaInstalled,
            hasValidVariants = hasVariants,
            hasValidRecipients = activeCount > 0,
            sendMode = mode
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CampaignHealthStatus(
            isAccessibilityActive = false,
            isWhatsAppInstalled = true,
            hasValidVariants = true,
            hasValidRecipients = false
        )
    )

    init {
        val defaultTitle = "Broadcast " + SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date())
        _campaignTitle.value = defaultTitle
        refreshWhatsAppApps()

        viewModelScope.launch {
            settingsDataStore.defaultTargetPackageFlow.collect { defaultPkg ->
                _targetPackage.value = defaultPkg
            }
        }
    }

    fun refreshWhatsAppApps() {
        _installedWhatsAppApps.value = PackageUtils.getInstalledWhatsAppApps(appContext)
    }

    fun onTargetPackageChanged(pkg: String) {
        _targetPackage.value = pkg
        viewModelScope.launch {
            val appInfo = _installedWhatsAppApps.value.find { it.appId == pkg }
            val label = appInfo?.label ?: if (pkg.endsWith(":clone")) "WhatsApp (Clone)" else "WhatsApp"
            settingsDataStore.saveDefaultTargetWhatsApp(pkg, appInfo?.className, label)
        }
    }

    fun onTitleChanged(newTitle: String) {
        _campaignTitle.value = newTitle
    }

    fun startNewCampaign(context: Context) {
        val defaultTitle = "Broadcast " + SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date())
        val title = _campaignTitle.value.ifBlank { defaultTitle }
        val intent = Intent(context, CampaignService::class.java).apply {
            action = CampaignService.ACTION_START
            putExtra(CampaignService.EXTRA_TITLE, title)
            putExtra(CampaignService.EXTRA_TARGET_PACKAGE, _targetPackage.value)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun resumeSavedCampaign(context: Context, campaign: CampaignEntity) {
        val intent = Intent(context, CampaignService::class.java).apply {
            action = CampaignService.ACTION_START
            putExtra(CampaignService.EXTRA_CAMPAIGN_ID, campaign.id)
            putExtra(CampaignService.EXTRA_TITLE, campaign.title)
            putExtra(CampaignService.EXTRA_TARGET_PACKAGE, campaign.targetPackage)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun retryFailedContacts(context: Context, campaign: CampaignEntity) {
        viewModelScope.launch {
            sendLogDao.deleteFailedLogsByCampaign(campaign.id)
            val resetCampaign = campaign.copy(
                status = "IDLE",
                failedCount = 0,
                skippedCount = 0,
                lastProcessedIndex = 0,
                updatedAt = System.currentTimeMillis()
            )
            campaignDao.update(resetCampaign)
            resumeSavedCampaign(context, resetCampaign)
        }
    }

    fun pauseCampaign(context: Context) {
        val intent = Intent(context, CampaignService::class.java).apply {
            action = CampaignService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeCampaign(context: Context) {
        val intent = Intent(context, CampaignService::class.java).apply {
            action = CampaignService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopCampaign(context: Context) {
        val intent = Intent(context, CampaignService::class.java).apply {
            action = CampaignService.ACTION_STOP
        }
        context.startService(intent)
    }
}
