package com.example.wablaster.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.wablaster.data.local.dao.CampaignDao
import com.example.wablaster.data.local.dao.ContactDao
import com.example.wablaster.data.local.dao.SendLogDao
import com.example.wablaster.data.local.entity.CampaignEntity
import com.example.wablaster.data.local.entity.ContactEntity
import com.example.wablaster.data.local.entity.SendLogEntity
import com.example.wablaster.data.local.SettingsDataStore
import com.example.wablaster.domain.RenderTemplate
import com.example.wablaster.domain.CampaignCircuitBreaker
import com.example.wablaster.sender.AccessibilitySender
import com.example.wablaster.sender.IntentSender
import com.example.wablaster.sender.FailReason
import com.example.wablaster.sender.MessageSender
import com.example.wablaster.sender.SendResult
import com.example.wablaster.data.supabase.SupabaseClient
import com.example.wablaster.data.supabase.UserRole
import com.example.wablaster.ui.MainActivity
import com.example.wablaster.util.DelayCalculator
import com.example.wablaster.util.DelayConfig
import com.example.wablaster.BuildConfig
import com.example.wablaster.data.local.Converters
import com.example.wablaster.domain.ActiveVariant
import com.example.wablaster.domain.VariantDistributor
import com.example.wablaster.domain.VariantMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import java.util.*
import kotlin.random.Random
import javax.inject.Inject

data class CampaignRunningState(
    val isServiceRunning: Boolean = false,
    val campaignId: Long = 0,
    val title: String = "",
    val status: String = "IDLE", // IDLE, RUNNING, PAUSED, STOPPED, COMPLETED
    val totalContacts: Int = 0,
    val sentCount: Int = 0,
    val failedCount: Int = 0,
    val skippedCount: Int = 0,
    val currentPhone: String = "",
    val currentContactName: String = "",
    val delayCountdown: Int = 0,
    val isBatchPausing: Boolean = false,
    val statusDescription: String = "Siap",
    val errorMessage: String? = null
)

@AndroidEntryPoint
class CampaignService : Service() {

    companion object {
        private const val TAG = "CampaignService"
        const val CHANNEL_ID = "channel_campaign_service"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.wablaster.ACTION_START"
        const val ACTION_PAUSE = "com.example.wablaster.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.wablaster.ACTION_RESUME"
        const val ACTION_STOP = "com.example.wablaster.ACTION_STOP"

        const val EXTRA_CAMPAIGN_ID = "extra_campaign_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"

        private val _campaignState = MutableStateFlow(CampaignRunningState())
        val campaignState: StateFlow<CampaignRunningState> = _campaignState.asStateFlow()
    }

    @Inject lateinit var contactDao: ContactDao
    @Inject lateinit var campaignDao: CampaignDao
    @Inject lateinit var sendLogDao: SendLogDao
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var supabaseClient: SupabaseClient

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var executionJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var activeCampaignId: Long = 0
    private var isPaused = false
    private var isStopped = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WABlaster:CampaignWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val campaignId = intent.getLongExtra(EXTRA_CAMPAIGN_ID, 0L)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Kampanye WhatsApp"
                val targetPkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: "com.whatsapp"
                startCampaign(campaignId, title, targetPkg)
            }
            ACTION_PAUSE -> pauseCampaign()
            ACTION_RESUME -> resumeCampaign()
            ACTION_STOP -> stopCampaign()
        }
        return START_STICKY
    }

    private fun startCampaign(campaignId: Long, title: String, targetPackage: String) {
        if (_campaignState.value.status == "RUNNING") {
            Log.w(TAG, "Kampanye sudah berjalan")
            return
        }

        wakeLock?.acquire(60 * 60 * 1000L) // 1 jam maks
        isPaused = false
        isStopped = false
        activeCampaignId = campaignId

        startForeground(NOTIFICATION_ID, buildNotification("Memulai kampanye...", 0, 0, false))

        executionJob?.cancel()
        executionJob = serviceScope.launch {
            runCampaignLoop(campaignId, title, targetPackage)
        }
    }

    private suspend fun CoroutineScope.runCampaignLoop(campaignId: Long, title: String, targetPackage: String) {
        val delayConfig = settingsDataStore.delayConfigFlow.firstOrNull() ?: DelayConfig()

        // Muat atau buat entitas kampanye di Room
        var campaign = if (campaignId > 0) campaignDao.getById(campaignId) else null
        if (campaign == null) {
            val variants = settingsDataStore.variantsFlow.firstOrNull() ?: emptyList()
            val variantMode = settingsDataStore.variantModeFlow.firstOrNull() ?: VariantMode.SEQUENTIAL
            val defaultName = settingsDataStore.defaultNameFlow.firstOrNull() ?: "Kak"
            val variantsJson = Converters().fromListToString(variants)

            val allContacts = contactDao.getActiveContacts()
            val newCampaign = CampaignEntity(
                title = title,
                templateMessage = variants.firstOrNull { it.isNotBlank() } ?: "",
                variantsJson = variantsJson,
                variantMode = variantMode.name,
                defaultName = defaultName,
                status = "RUNNING",
                targetPackage = targetPackage,
                totalContacts = allContacts.size,
                sentCount = 0,
                failedCount = 0,
                skippedCount = 0,
                lastProcessedIndex = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val newId = campaignDao.insert(newCampaign)
            activeCampaignId = newId
            campaign = newCampaign.copy(id = newId)
        } else {
            campaign = campaign.copy(status = "RUNNING", updatedAt = System.currentTimeMillis())
            campaignDao.update(campaign)
            activeCampaignId = campaign.id
        }

        // Ambil snapshot varian dari kampanye (bukan dari setting baru yang mungkin diedit)
        val snapshottedVariants = Converters().fromStringToList(campaign.variantsJson).let { list ->
            if (list.isEmpty() && campaign.templateMessage.isNotBlank()) listOf(campaign.templateMessage) else list
        }
        val activeVariants = VariantDistributor.getActiveVariants(snapshottedVariants).ifEmpty {
            listOf(ActiveVariant(index = 0, text = "Halo {nama}, terima kasih telah menghubungi kami!"))
        }
        val campaignVariantMode = try {
            VariantMode.valueOf(campaign.variantMode)
        } catch (e: Exception) {
            VariantMode.SEQUENTIAL
        }
        val campaignDefaultName = campaign.defaultName.ifBlank { "Kak" }

        val allContacts = contactDao.getAll().firstOrNull() ?: emptyList()
        val sentPhones = sendLogDao.getSentPhones(campaign.id).toSet()

        var sentCount = campaign.sentCount
        var failedCount = campaign.failedCount
        var skippedCount = campaign.skippedCount

        _campaignState.value = CampaignRunningState(
            isServiceRunning = true,
            campaignId = campaign.id,
            title = campaign.title,
            status = "RUNNING",
            totalContacts = allContacts.size,
            sentCount = sentCount,
            failedCount = failedCount,
            skippedCount = skippedCount,
            statusDescription = "Menyiapkan pengiriman..."
        )

        val sendMode = settingsDataStore.sendModeFlow.firstOrNull() ?: "ACCESSIBILITY"
        val targetClass = settingsDataStore.defaultTargetClassFlow.firstOrNull()
        val sender: MessageSender = if (sendMode == "INTENT_MANUAL") {
            IntentSender(this@CampaignService, targetPackage, targetClass)
        } else {
            AccessibilitySender(this@CampaignService, targetPackage, targetClass)
        }
        val circuitBreaker = CampaignCircuitBreaker(maxConsecutiveUiFailures = 5)
        val startIndex = campaign.lastProcessedIndex

        val currentSerial = settingsDataStore.userSessionSerialNumberFlow.firstOrNull() ?: "UNKNOWN"
        val currentName = settingsDataStore.userSessionNameFlow.firstOrNull() ?: "Pengguna"
        val currentRole = UserRole.fromString(settingsDataStore.userSessionRoleFlow.firstOrNull())
        val currentDeviceId = settingsDataStore.getOrCreateDeviceId()

        serviceScope.launch {
            try {
                supabaseClient.logActivity(
                    serialNumber = currentSerial,
                    userName = currentName,
                    userRole = currentRole,
                    activityType = "BROADCAST_START",
                    status = "INFO",
                    description = "Memulai broadcast '${campaign.title}' ke ${allContacts.size} kontak",
                    deviceId = currentDeviceId
                )
            } catch (_: Exception) {}
        }

        for (i in startIndex until allContacts.size) {
            if (!isActive || isStopped) break

            // Tangani status jeda (PAUSED)
            while (isPaused && !isStopped && isActive) {
                delay(500)
            }
            if (isStopped || !isActive) break

            val contact = allContacts[i]

            // 1. Cek batas harian (Daily Limit)
            if (delayConfig.dailyLimit > 0) {
                val startOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val todaySent = sendLogDao.getTodaySentCount(startOfDay)
                if (todaySent >= delayConfig.dailyLimit) {
                    val msg = "Batas harian (${delayConfig.dailyLimit} pesan) tercapai."
                    finishCampaign(campaign, "STOPPED", msg, sentCount, failedCount, skippedCount, i)
                    return
                }
            }

            // 2. Cek apakah kontak Opt-Out
            if (contact.isOptOut) {
                skippedCount++
                recordLog(campaign.id, contact, 0, "", "SKIPPED", "Kontak bertanda Opt-Out")
                updateCampaignProgress(campaign, sentCount, failedCount, skippedCount, i + 1)
                continue
            }

            // 3. Cek apakah nomor sudah terkirim pada kampanye ini
            if (sentPhones.contains(contact.phone)) {
                skippedCount++
                recordLog(campaign.id, contact, 0, "", "SKIPPED", "Nomor sudah berhasil dikirim sebelumnya")
                updateCampaignProgress(campaign, sentCount, failedCount, skippedCount, i + 1)
                continue
            }

            // 4. Pilih varian deterministik & render pesan
            val chosenVariant = VariantDistributor.selectVariant(
                activeVariants = activeVariants,
                recipientIndex = i,
                mode = campaignVariantMode,
                random = Random(campaign.id * 31 + i)
            ) ?: activeVariants.first()

            val renderResult = RenderTemplate.render(
                template = chosenVariant.text,
                contact = contact,
                defaultName = campaignDefaultName
            )
            val messageToSend = renderResult.renderedText

            _campaignState.value = _campaignState.value.copy(
                currentPhone = contact.phone,
                currentContactName = contact.name,
                statusDescription = "Mengirim (Varian ${chosenVariant.index + 1}) ke ${contact.name.ifBlank { contact.phone }} (${i + 1}/${allContacts.size})"
            )

            updateNotificationProgress("Mengirim ke ${contact.phone}", sentCount, allContacts.size, false)

            // 5. Kirim via MessageSender
            val sendResult = sender.send(contact.phone, messageToSend)

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Sent variant ${chosenVariant.index + 1} to ${contact.phone}: $messageToSend")
            }

            val shouldTrip = circuitBreaker.recordResult(sendResult)

            when (sendResult) {
                is SendResult.Success -> {
                    sentCount++
                    recordLog(campaign.id, contact, chosenVariant.index, messageToSend, "SENT", null)
                }
                is SendResult.Failed -> {
                    failedCount++
                    val errorDetail = "${sendResult.reason}: ${sendResult.detail ?: "-"}"
                    recordLog(campaign.id, contact, chosenVariant.index, messageToSend, "FAILED", errorDetail)

                    if (sendResult.reason == FailReason.WA_BANNED) {
                        serviceScope.launch {
                            try {
                                supabaseClient.reportWaBanned(
                                    serialNumber = currentSerial,
                                    userName = currentName,
                                    userRole = currentRole,
                                    reason = sendResult.detail ?: "Terdeteksi dialog pemblokiran akun dari WhatsApp",
                                    targetPhone = contact.phone,
                                    deviceId = currentDeviceId
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            updateCampaignProgress(campaign, sentCount, failedCount, skippedCount, i + 1)

            // Auto-hentikan kampanye bila 5 kegagalan UI berturut-turut terdeteksi
            if (shouldTrip) {
                val circuitMsg = "Kampanye dihentikan otomatis: 5 pesan berturut-turut gagal mendeteksi tombol kirim atau batas waktu habis (timeout). Kemungkinan tampilan/selector WhatsApp telah berubah."
                Log.w(TAG, circuitMsg)
                finishCampaign(campaign, "STOPPED", circuitMsg, sentCount, failedCount, skippedCount, i + 1)
                return
            }

            // 6. Jalankan jeda jika masih ada kontak berikutnya
            if (i < allContacts.size - 1 && isActive && !isStopped) {
                val delayStep = DelayCalculator.calculateNextDelay(delayConfig, i)
                val totalDelaySec = delayStep.delaySeconds

                _campaignState.value = _campaignState.value.copy(
                    isBatchPausing = delayStep.isBatchPause,
                    statusDescription = delayStep.description
                )

                for (sec in totalDelaySec downTo 1) {
                    if (!isActive || isStopped) break
                    while (isPaused && !isStopped && isActive) {
                        delay(500)
                    }

                    _campaignState.value = _campaignState.value.copy(delayCountdown = sec)
                    val countdownDesc = if (delayStep.isBatchPause) {
                        "Istirahat Batch: sisa ${sec / 60}m ${sec % 60}s"
                    } else {
                        "Jeda: sisa ${sec}s"
                    }
                    updateNotificationProgress(countdownDesc, sentCount, allContacts.size, false)
                    delay(1000)
                }
                _campaignState.value = _campaignState.value.copy(delayCountdown = 0)
            }
        }

        if (!isStopped) {
            finishCampaign(campaign, "COMPLETED", "Kampanye selesai seluruhnya!", sentCount, failedCount, skippedCount, allContacts.size)
        }
    }

    private suspend fun recordLog(
        campaignId: Long,
        contact: ContactEntity,
        variantIndex: Int,
        message: String,
        status: String,
        errorMessage: String?
    ) {
        val log = SendLogEntity(
            campaignId = campaignId,
            phone = contact.phone,
            contactName = contact.name,
            variantIndex = variantIndex,
            renderedMessage = message,
            status = status,
            errorMessage = errorMessage,
            sentAt = System.currentTimeMillis()
        )
        sendLogDao.insert(log)
    }

    private suspend fun updateCampaignProgress(
        campaign: CampaignEntity,
        sent: Int,
        failed: Int,
        skipped: Int,
        nextIndex: Int
    ) {
        val updated = campaign.copy(
            sentCount = sent,
            failedCount = failed,
            skippedCount = skipped,
            lastProcessedIndex = nextIndex,
            updatedAt = System.currentTimeMillis()
        )
        campaignDao.update(updated)

        _campaignState.value = _campaignState.value.copy(
            sentCount = sent,
            failedCount = failed,
            skippedCount = skipped
        )
    }

    private suspend fun finishCampaign(
        campaign: CampaignEntity,
        status: String,
        summary: String,
        sent: Int,
        failed: Int,
        skipped: Int,
        lastIndex: Int
    ) {
        val finished = campaign.copy(
            status = status,
            sentCount = sent,
            failedCount = failed,
            skippedCount = skipped,
            lastProcessedIndex = lastIndex,
            updatedAt = System.currentTimeMillis()
        )
        campaignDao.update(finished)

        _campaignState.value = _campaignState.value.copy(
            status = status,
            statusDescription = summary,
            errorMessage = if (status == "STOPPED" && summary.contains("otomatis")) summary else null,
            isServiceRunning = false
        )

        serviceScope.launch {
            try {
                val currentSerial = settingsDataStore.userSessionSerialNumberFlow.firstOrNull() ?: "UNKNOWN"
                val currentName = settingsDataStore.userSessionNameFlow.firstOrNull() ?: "Pengguna"
                val currentRole = UserRole.fromString(settingsDataStore.userSessionRoleFlow.firstOrNull())
                val currentDeviceId = settingsDataStore.getOrCreateDeviceId()

                supabaseClient.logActivity(
                    serialNumber = currentSerial,
                    userName = currentName,
                    userRole = currentRole,
                    activityType = "BROADCAST_COMPLETED",
                    status = if (status == "COMPLETED") "SUCCESS" else if (status == "STOPPED") "WARNING" else "INFO",
                    description = "Broadcast '${campaign.title}' $status: $sent terkirim, $failed gagal, $skipped dilewati",
                    deviceId = currentDeviceId
                )
            } catch (_: Exception) {}
        }

        updateNotificationProgress("Selesai: $sent terkirim, $failed gagal", sent, campaign.totalContacts, true)
        stopForeground(STOP_FOREGROUND_DETACH)
        safeReleaseWakeLock()
        stopSelf()
    }

    private fun pauseCampaign() {
        isPaused = true
        serviceScope.launch {
            val campaign = campaignDao.getById(activeCampaignId)
            if (campaign != null) {
                campaignDao.update(campaign.copy(status = "PAUSED", updatedAt = System.currentTimeMillis()))
            }
        }
        _campaignState.value = _campaignState.value.copy(
            status = "PAUSED",
            statusDescription = "Kampanye dijeda oleh pengguna"
        )
        updateNotificationProgress("Kampanye Dijeda", _campaignState.value.sentCount, _campaignState.value.totalContacts, false)
    }

    private fun resumeCampaign() {
        isPaused = false
        serviceScope.launch {
            val campaign = campaignDao.getById(activeCampaignId)
            if (campaign != null) {
                campaignDao.update(campaign.copy(status = "RUNNING", updatedAt = System.currentTimeMillis()))
            }
        }
        _campaignState.value = _campaignState.value.copy(
            status = "RUNNING",
            statusDescription = "Melanjutkan pengiriman..."
        )
        updateNotificationProgress("Melanjutkan...", _campaignState.value.sentCount, _campaignState.value.totalContacts, false)
    }

    private fun stopCampaign() {
        isStopped = true
        isPaused = false
        executionJob?.cancel()

        serviceScope.launch {
            val campaign = campaignDao.getById(activeCampaignId)
            if (campaign != null) {
                campaignDao.update(campaign.copy(status = "STOPPED", updatedAt = System.currentTimeMillis()))
            }
        }

        _campaignState.value = _campaignState.value.copy(
            status = "STOPPED",
            statusDescription = "Kampanye dihentikan oleh pengguna",
            isServiceRunning = false
        )

        safeReleaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun safeReleaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Eksekusi Kampanye WA Blaster",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status pengiriman pesan massal WhatsApp"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        statusText: String,
        currentProgress: Int,
        maxProgress: Int,
        isCompleted: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WA Blaster: ${_campaignState.value.title.ifBlank { "Kampanye" }}")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(pendingOpenApp)
            .setOngoing(!isCompleted)
            .setOnlyAlertOnce(true)

        if (maxProgress > 0 && !isCompleted) {
            builder.setProgress(maxProgress, currentProgress, false)
        }

        if (!isCompleted) {
            if (isPaused) {
                val resumeIntent = Intent(this, CampaignService::class.java).apply { action = ACTION_RESUME }
                val pResume = PendingIntent.getService(this, 1, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(android.R.drawable.ic_media_play, "Lanjutkan", pResume)
            } else {
                val pauseIntent = Intent(this, CampaignService::class.java).apply { action = ACTION_PAUSE }
                val pPause = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(android.R.drawable.ic_media_pause, "Jeda", pPause)
            }

            val stopIntent = Intent(this, CampaignService::class.java).apply { action = ACTION_STOP }
            val pStop = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hentikan", pStop)
        }

        return builder.build()
    }

    private fun updateNotificationProgress(
        statusText: String,
        currentProgress: Int,
        maxProgress: Int,
        isCompleted: Boolean
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(statusText, currentProgress, maxProgress, isCompleted))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        safeReleaseWakeLock()
        serviceScope.cancel()
    }
}
