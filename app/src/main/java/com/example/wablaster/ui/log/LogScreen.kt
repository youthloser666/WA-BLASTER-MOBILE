package com.example.wablaster.ui.log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wablaster.data.local.entity.SendLogEntity
import com.example.wablaster.sender.FailReasonHelper
import com.example.wablaster.ui.theme.SuccessGreen
import com.example.wablaster.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    viewModel: LogViewModel
) {
    val context = LocalContext.current
    val logs by viewModel.filteredLogs.collectAsState()
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val sentCount by viewModel.sentCount.collectAsState()
    val failedCount by viewModel.failedCount.collectAsState()
    val skippedCount by viewModel.skippedCount.collectAsState()
    val userFeedback by viewModel.userFeedback.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userFeedback) {
        userFeedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Hapus Semua Log?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Seluruh riwayat pengiriman pesan akan dihapus. Tindakan ini tidak dapat dibatalkan.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllLogs()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Hapus Semua")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ACTION HEADER (Hapus Log jika ada riwayat)
            if (totalCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showDeleteConfirmDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Hapus Semua Log", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }

            if (failedCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.retryFailedContacts(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Coba Kirim Ulang Nomor Gagal ($failedCount)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // TAB FILTER SEGMENTED CONTROL - APPLE STYLE (RAPI & ELEGAN)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Segment 1: Semua
                    val isAllSelected = selectedStatus == "ALL"
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setStatusFilter("ALL") },
                        shape = RoundedCornerShape(9.dp),
                        color = if (isAllSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (isAllSelected) 1.dp else 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Semua",
                                fontSize = 11.sp,
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isAllSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$totalCount",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Segment 2: Terkirim
                    val isSentSelected = selectedStatus == "SENT"
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setStatusFilter("SENT") },
                        shape = RoundedCornerShape(9.dp),
                        color = if (isSentSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (isSentSelected) 1.dp else 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Terkirim",
                                fontSize = 11.sp,
                                fontWeight = if (isSentSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSentSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$sentCount",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                    }

                    // Segment 3: Gagal
                    val isFailedSelected = selectedStatus == "FAILED"
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setStatusFilter("FAILED") },
                        shape = RoundedCornerShape(9.dp),
                        color = if (isFailedSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (isFailedSelected) 1.dp else 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Gagal",
                                fontSize = 11.sp,
                                fontWeight = if (isFailedSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isFailedSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$failedCount",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (failedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Segment 4: Dilewati
                    val isSkippedSelected = selectedStatus == "SKIPPED"
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setStatusFilter("SKIPPED") },
                        shape = RoundedCornerShape(9.dp),
                        color = if (isSkippedSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (isSkippedSelected) 1.dp else 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Dilewati",
                                fontSize = 11.sp,
                                fontWeight = if (isSkippedSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSkippedSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$skippedCount",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (skippedCount > 0) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // DAFTAR LOG ITEM
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (totalCount == 0) "Belum ada riwayat pengiriman." else "Tidak ada log pada filter ini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        LogCardItem(log = log)
                    }
                }
            }
        }
    }
}

@Composable
fun LogCardItem(log: SendLogEntity) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(log.sentAt) { dateFormat.format(Date(log.sentAt)) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Baris 1: Nomor, Nama, Badge Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = log.phone,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        if (log.contactName.isNotBlank()) {
                            Text(
                                text = " · ${log.contactName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = "$formattedTime · Varian ${log.variantIndex + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = when (log.status) {
                        "SENT" -> SuccessGreen.copy(alpha = 0.12f)
                        "FAILED" -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        "SKIPPED" -> WarningAmber.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = when (log.status) {
                            "SENT" -> "TERKIRIM"
                            "FAILED" -> "GAGAL"
                            "SKIPPED" -> "DILEWATI"
                            else -> log.status
                        },
                        color = when (log.status) {
                            "SENT" -> SuccessGreen
                            "FAILED" -> MaterialTheme.colorScheme.error
                            "SKIPPED" -> WarningAmber
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Keterangan Gagal (Jika status FAILED)
            if (log.status == "FAILED" && !log.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = FailReasonHelper.formatErrorMessage(log.errorMessage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Keterangan Dilewati (Jika status SKIPPED)
            if (log.status == "SKIPPED" && !log.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Alasan: ${log.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = WarningAmber
                )
            }

            // Pesan Teks (dapat dibuka/tutup)
            if (log.renderedMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Isi Pesan:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = log.renderedMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp
                        )
                    }
                }

                if (!expanded) {
                    Text(
                        text = "Ketuk untuk melihat isi pesan...",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
