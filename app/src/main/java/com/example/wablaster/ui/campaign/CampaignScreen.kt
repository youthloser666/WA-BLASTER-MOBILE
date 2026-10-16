package com.example.wablaster.ui.campaign

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wablaster.data.local.entity.CampaignEntity
import com.example.wablaster.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignScreen(
    viewModel: CampaignViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val campaignState by viewModel.campaignState.collectAsState()
    val activeDbCampaign by viewModel.activeCampaignFromDb.collectAsState()
    val totalContacts by viewModel.totalContacts.collectAsState()
    val isAccessibilityActive by viewModel.isAccessibilityActive.collectAsState()
    val targetPackage by viewModel.targetPackage.collectAsState()
    val campaignTitle by viewModel.campaignTitle.collectAsState()
    val healthStatus by viewModel.healthStatus.collectAsState()
    val installedWhatsAppApps by viewModel.installedWhatsAppApps.collectAsState()

    var showStopConfirmation by remember { mutableStateOf(false) }

    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = { Text("Hentikan Broadcast?", fontWeight = FontWeight.SemiBold) },
            text = { Text("Proses pengiriman yang sedang berjalan akan dihentikan.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.stopCampaign(context)
                        showStopConfirmation = false
                    }
                ) {
                    Text("Hentikan", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }) {
                    Text("Batal")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // RESUME KAMPANYE TERSIMPAN (JIKA ADA KAMPANYE BELUM SELESAI & SERVICE IDLE)
        if (!campaignState.isServiceRunning && activeDbCampaign != null) {
            val saved = activeDbCampaign!!
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lanjutkan Broadcast",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = saved.status,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = saved.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Normal)
                    Text(
                        text = "${saved.sentCount} terkirim · ${saved.failedCount} gagal · Total ${saved.totalContacts} kontak",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.resumeSavedCampaign(context, saved) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Lanjutkan", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (saved.failedCount > 0) {
                            OutlinedButton(
                                onClick = { viewModel.retryFailedContacts(context, saved) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Coba Lagi Gagal (${saved.failedCount})", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // KONDISI 1: KAMPANYE SEDANG BERJALAN / DIJEDA (ACTIVE DASHBOARD)
        if (campaignState.isServiceRunning || campaignState.status == "RUNNING" || campaignState.status == "PAUSED") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Header Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = campaignState.title.ifBlank { "Broadcast Berjalan" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = campaignState.statusDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Surface(
                            color = if (campaignState.status == "RUNNING") SuccessGreen.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (campaignState.status == "RUNNING") "Berjalan" else "Dijeda",
                                color = if (campaignState.status == "RUNNING") SuccessGreen else MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress Bar Minimalis
                    val progressFraction = if (campaignState.totalContacts > 0) {
                        campaignState.sentCount.toFloat() / campaignState.totalContacts.toFloat()
                    } else 0f

                    LinearProgressIndicator(
                        progress = { progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Metrik Counter Bersih
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${campaignState.totalContacts}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                        Column {
                            Text("Terkirim", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${campaignState.sentCount}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = SuccessGreen)
                        }
                        Column {
                            Text("Gagal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${campaignState.failedCount}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                        }
                        Column {
                            Text("Dilewati", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${campaignState.skippedCount}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                    }

                    if (campaignState.delayCountdown > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Jeda berikutnya: ${campaignState.delayCountdown}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Tombol Kontrol: Pause / Resume / Stop
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (campaignState.status == "RUNNING") {
                            OutlinedButton(
                                onClick = { viewModel.pauseCampaign(context) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Jeda")
                            }
                        } else if (campaignState.status == "PAUSED") {
                            Button(
                                onClick = { viewModel.resumeCampaign(context) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Lanjutkan")
                            }
                        }

                        OutlinedButton(
                            onClick = { showStopConfirmation = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Hentikan")
                        }
                    }
                }
            }
        }

        // KONDISI 2: FORM MEMULAI KAMPANYE BARU
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Pengaturan Broadcast",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = campaignTitle,
                    onValueChange = { viewModel.onTitleChanged(it) },
                    label = { Text("Nama Broadcast") },
                    placeholder = { Text("Otomatis: Tanggal & Jam") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Pemilihan Aplikasi WhatsApp (Otomatis Mendeteksi WhatsApp Biasa, Business, dan Clone)
                Text(
                    text = "Aplikasi WhatsApp Pengirim",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                val apps = installedWhatsAppApps.ifEmpty {
                    listOf(
                        com.example.wablaster.util.WhatsAppPackageInfo("com.whatsapp", null, "WhatsApp"),
                        com.example.wablaster.util.WhatsAppPackageInfo("com.whatsapp.w4b", null, "WhatsApp Business")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (app in apps) {
                        val isSelected = targetPackage == app.packageName
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.onTargetPackageChanged(app.packageName) },
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            else null
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = app.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nomor Penerima",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$totalContacts nomor tersimpan",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (!isAccessibilityActive && healthStatus.sendMode == "ACCESSIBILITY") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Layanan Aksesibilitas nonaktif",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("Aktifkan", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = { viewModel.startNewCampaign(context) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = healthStatus.isHealthy && !campaignState.isServiceRunning,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Mulai Broadcast", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
                }

                if (!healthStatus.isHealthy && !campaignState.isServiceRunning) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (totalContacts == 0) "Masukkan nomor di tab Nomor untuk memulai."
                        else if (!isAccessibilityActive && healthStatus.sendMode == "ACCESSIBILITY") "Aktifkan aksesibilitas untuk memulai."
                        else "Lengkapi data pengiriman untuk memulai.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(120.dp))
    }
}
