package com.example.wablaster.ui.template

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wablaster.domain.VariantMode
import com.example.wablaster.ui.theme.SuccessGreen
import com.example.wablaster.util.DelayConfig
import com.example.wablaster.util.DelayMode
import com.example.wablaster.util.DelayValidationResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    viewModel: TemplateViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val variants by viewModel.variants.collectAsState()
    val selectedIndex by viewModel.selectedVariantIndex.collectAsState()
    val variantMode by viewModel.variantMode.collectAsState()
    val defaultName by viewModel.defaultName.collectAsState()
    val delayConfig by viewModel.delayConfig.collectAsState()
    val firstContact by viewModel.firstContact.collectAsState()
    val previewResult by viewModel.previewResult.collectAsState()
    val delayValidation by viewModel.delayValidation.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    val currentVariantText = variants.getOrElse(selectedIndex) { "" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // CARD 1: 5 VARIAN PESAN (CLEAN SEGMENTED CONTROL)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Varian Pesan (1 s.d. 5)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pesan dibagi bergilir atau acak ke penerima. Varian kosong diabaikan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Apple-style Segmented Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 0..4) {
                        val isSelected = (selectedIndex == i)
                        val isFilled = variants.getOrElse(i) { "" }.isNotBlank()

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.onSelectVariantIndex(i) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (isSelected) 1.dp else 0.dp
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${i + 1}",
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isFilled) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(
                                                    color = SuccessGreen,
                                                    shape = RoundedCornerShape(3.dp)
                                                )
                                        )
                                    }
                                }
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
                        text = "Isi Pesan ${selectedIndex + 1}",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${currentVariantText.length} karakter",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = currentVariantText,
                    onValueChange = { viewModel.onVariantTextChanged(selectedIndex, it) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text(
                            if (selectedIndex == 0) "Halo {nama}, terima kasih telah menghubungi kami..."
                            else "Alternatif pesan ${selectedIndex + 1} (opsional)..."
                        )
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Sisipkan Variabel:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = { viewModel.insertTextAtCurrentVariant("{nama}") },
                        label = { Text("{nama}") }
                    )
                    SuggestionChip(
                        onClick = { viewModel.insertTextAtCurrentVariant("{phone}") },
                        label = { Text("{phone}") }
                    )
                    SuggestionChip(
                        onClick = { viewModel.insertTextAtCurrentVariant("{Halo|Hai|Selamat siang}") },
                        label = { Text("{Halo|Hai|Selamat siang}") }
                    )
                    SuggestionChip(
                        onClick = { viewModel.insertTextAtCurrentVariant("{Kak|Kakak|Sahabat}") },
                        label = { Text("{Kak|Kakak|Sahabat}") }
                    )
                }
            }
        }

        // CARD 2: PRATINJAU PESAN (CLEAN BUBBLE)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pratinjau Pesan ${selectedIndex + 1}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (firstContact != null) {
                                "Penerima: ${firstContact!!.name.ifBlank { "Tanpa Nama" }} (${firstContact!!.phone})"
                            } else {
                                "Simulasi nama default: '$defaultName'"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { viewModel.regenerateSpintaxPreview() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Acak Spintax",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (previewResult.renderedText.isBlank()) {
                            Text(
                                text = "Pratinjau kosong. Ketik teks pesan di atas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = previewResult.renderedText,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }

        // CARD 3: PEMBAGIAN VARIAN & NAMA DEFAULT
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Mode Pembagian & Nama Default",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onVariantModeChanged(VariantMode.SEQUENTIAL) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = variantMode == VariantMode.SEQUENTIAL,
                        onClick = { viewModel.onVariantModeChanged(VariantMode.SEQUENTIAL) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Bergilir (Sequential)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("Pesan 1 ke kontak 1, Pesan 2 ke kontak 2, dan seterusnya", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onVariantModeChanged(VariantMode.RANDOM) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = variantMode == VariantMode.RANDOM,
                        onClick = { viewModel.onVariantModeChanged(VariantMode.RANDOM) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Acak (Random)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("Varian dipilih secara acak untuk setiap kontak", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Nama Panggilan Default ({nama}):",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Digunakan apabila data kontak tidak menyertakan nama",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = defaultName,
                    onValueChange = { viewModel.onDefaultNameChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("Contoh: Kak / Pelanggan / Sahabat") }
                )
            }
        }

        // CARD 4: JEDA & INTERVAL PENGIRIMAN
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Pengaturan Jeda & Interval",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onDelayConfigChanged(delayConfig.copy(mode = DelayMode.RANDOM)) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = delayConfig.mode == DelayMode.RANDOM,
                        onClick = { viewModel.onDelayConfigChanged(delayConfig.copy(mode = DelayMode.RANDOM)) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Jeda Acak (Disarankan)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("Mencegah pola seragam agar pengiriman terlihat alami", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onDelayConfigChanged(delayConfig.copy(mode = DelayMode.FIXED)) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = delayConfig.mode == DelayMode.FIXED,
                        onClick = { viewModel.onDelayConfigChanged(delayConfig.copy(mode = DelayMode.FIXED)) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Jeda Tetap (Fixed)", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (delayConfig.mode == DelayMode.RANDOM) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = delayConfig.minSeconds.toString(),
                            onValueChange = {
                                it.toIntOrNull()?.let { min -> viewModel.onDelayConfigChanged(delayConfig.copy(minSeconds = min)) }
                            },
                            label = { Text("Min (detik)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = delayConfig.maxSeconds.toString(),
                            onValueChange = {
                                it.toIntOrNull()?.let { max -> viewModel.onDelayConfigChanged(delayConfig.copy(maxSeconds = max)) }
                            },
                            label = { Text("Maks (detik)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = delayConfig.fixedSeconds.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { fixed -> viewModel.onDelayConfigChanged(delayConfig.copy(fixedSeconds = fixed)) }
                        },
                        label = { Text("Jeda Tetap (detik)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Jeda Istirahat Batch & Batas Harian",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = delayConfig.batchSize.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { size -> viewModel.onDelayConfigChanged(delayConfig.copy(batchSize = size)) }
                        },
                        label = { Text("Setiap N pesan") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = delayConfig.batchPauseMinutes.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { pause -> viewModel.onDelayConfigChanged(delayConfig.copy(batchPauseMinutes = pause)) }
                        },
                        label = { Text("Istirahat (menit)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = delayConfig.dailyLimit.toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { limit -> viewModel.onDelayConfigChanged(delayConfig.copy(dailyLimit = limit)) }
                    },
                    label = { Text("Batas Maksimal Harian (0 = tanpa batas)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp)
                )

                if (delayValidation is DelayValidationResult.Invalid) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = (delayValidation as DelayValidationResult.Invalid).errors.first(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // TOMBOL SIMPAN - APPLE STYLE BUTTON
        Button(
            onClick = { viewModel.saveSettings() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Simpan Pengaturan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(120.dp))
    }
}
