package com.example.wablaster.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.wablaster.data.supabase.AppUser
import com.example.wablaster.data.supabase.UserRole
import com.example.wablaster.ui.theme.SuccessGreen
import com.example.wablaster.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    currentRole: UserRole,
    currentUserSerial: String,
    viewModel: AdminViewModel,
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<AppUser?>(null) }
    var userToResetDevice by remember { mutableStateOf<AppUser?>(null) }

    // Dialog Tambah Akun
    if (showCreateDialog) {
        CreateUserDialog(
            creatorRole = currentRole,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, role, customSerial ->
                viewModel.createUser(name, role, customSerial, currentUserSerial)
                showCreateDialog = false
            }
        )
    }

    // Dialog Konfirmasi Hapus
    if (userToDelete != null) {
        val user = userToDelete!!
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Hapus Akun?", fontWeight = FontWeight.Bold) },
            text = { Text("Akun '${user.name}' (${user.serialNumber}) akan dihapus secara permanen.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUser(user)
                        userToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog Konfirmasi Reset Perangkat
    if (userToResetDevice != null) {
        val user = userToResetDevice!!
        AlertDialog(
            onDismissRequest = { userToResetDevice = null },
            title = { Text("Reset Perangkat?", fontWeight = FontWeight.Bold) },
            text = { Text("Ikatan perangkat untuk akun '${user.name}' (${user.serialNumber}) akan dilepas. Agent dapat login kembali di perangkat baru.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetDevice(user)
                        userToResetDevice = null
                    }
                ) {
                    Text("Reset Perangkat")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToResetDevice = null }) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Manajemen Akun",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (currentRole == UserRole.IT_SUPPORT) "Panel IT Support" else "Panel Admin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp, end = 6.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tambah Akun", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // NOTIFIKASI STATUS / ERROR
            if (uiState.statusMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SuccessGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(uiState.statusMessage ?: "", style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
                        }
                    }
                }
            }

            if (uiState.errorMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(uiState.errorMessage ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // SECTION KHUSUS IT SUPPORT: LOCK APLIKASI GLOBAL (MAINTENANCE MODE)
            if (currentRole == UserRole.IT_SUPPORT) {
                item {
                    MaintenanceControlCard(
                        isMaintenanceActive = uiState.isMaintenanceActive,
                        maintenanceMessage = uiState.maintenanceMessage,
                        onToggleMaintenance = { active, msg ->
                            viewModel.toggleMaintenance(active, msg, currentUserSerial)
                        }
                    )
                }
            }

            // SEARCH & ROLE FILTER PILLS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Cari nama atau serial number...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Bersihkan")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Role filter pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChipItem(
                            label = "Semua (${uiState.users.size})",
                            isSelected = uiState.filterRole == null,
                            onClick = { viewModel.setFilterRole(null) }
                        )
                        FilterChipItem(
                            label = "Agent",
                            isSelected = uiState.filterRole == UserRole.AGENT,
                            onClick = { viewModel.setFilterRole(UserRole.AGENT) }
                        )
                        if (currentRole == UserRole.IT_SUPPORT) {
                            FilterChipItem(
                                label = "Admin",
                                selectedColor = Color(0xFF673AB7),
                                isSelected = uiState.filterRole == UserRole.ADMIN,
                                onClick = { viewModel.setFilterRole(UserRole.ADMIN) }
                            )
                        }
                    }
                }
            }

            // LIST USERS
            if (uiState.isLoading && uiState.users.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            } else if (uiState.filteredUsers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tidak ada akun ditemukan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.filteredUsers, key = { it.id }) { user ->
                    UserCardItem(
                        user = user,
                        canManage = (currentRole == UserRole.IT_SUPPORT || user.role == UserRole.AGENT),
                        onToggleActive = { viewModel.toggleUserStatus(user) },
                        onResetDevice = { userToResetDevice = user },
                        onDelete = { userToDelete = user },
                        onCopySerial = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Serial Number", user.serialNumber))
                            Toast.makeText(context, "Serial number '${user.serialNumber}' disalin", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) selectedColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            1.dp,
            if (isSelected) selectedColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
fun MaintenanceControlCard(
    isMaintenanceActive: Boolean,
    maintenanceMessage: String,
    onToggleMaintenance: (Boolean, String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var messageInput by remember(maintenanceMessage) { mutableStateOf(maintenanceMessage) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMaintenanceActive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isMaintenanceActive) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isMaintenanceActive) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isMaintenanceActive) MaterialTheme.colorScheme.error else SuccessGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Kunci Aplikasi (Maintenance)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isMaintenanceActive) "Aplikasi sedang DIKUNCI untuk pengguna lain" else "Aplikasi normal (Bisa digunakan)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isMaintenanceActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isMaintenanceActive,
                    onCheckedChange = { active ->
                        onToggleMaintenance(active, messageInput)
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pesan: \"$maintenanceMessage\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { isEditing = true }) {
                        Text("Ubah Pesan", fontSize = 12.sp)
                    }
                }
            } else {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    label = { Text("Pesan Pemeliharaan untuk Pengguna") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { isEditing = false }) {
                        Text("Batal")
                    }
                    Button(
                        onClick = {
                            onToggleMaintenance(isMaintenanceActive, messageInput)
                            isEditing = false
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Simpan Pesan")
                    }
                }
            }
        }
    }
}

@Composable
fun UserCardItem(
    user: AppUser,
    canManage: Boolean,
    onToggleActive: () -> Unit,
    onResetDevice: () -> Unit,
    onDelete: () -> Unit,
    onCopySerial: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Card: Nama & Role Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.serialNumber,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = onCopySerial, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Badge Role
                val roleColor = when (user.role) {
                    UserRole.AGENT -> MaterialTheme.colorScheme.primary
                    UserRole.ADMIN -> Color(0xFF7E57C2)
                    UserRole.IT_SUPPORT -> WarningAmber
                }
                Surface(
                    color = roleColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, roleColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = user.role.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = roleColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Info Perangkat & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (user.role == UserRole.AGENT) {
                        if (!user.deviceId.isNullOrBlank()) {
                            Text(
                                text = "Perangkat: ${user.deviceId.take(12)}... (Terkunci)",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Perangkat: Belum terikat",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = SuccessGreen
                            )
                        }
                    } else {
                        Text(
                            text = "Multi-device (Bebas perangkat)",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Aktif Toggle
                if (canManage) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (user.isActive) "Aktif" else "Nonaktif",
                            fontSize = 12.sp,
                            color = if (user.isActive) SuccessGreen else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = user.isActive,
                            onCheckedChange = { onToggleActive() },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            // Tombol Aksi Tambahan
            if (canManage) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (user.role == UserRole.AGENT && !user.deviceId.isNullOrBlank()) {
                        TextButton(
                            onClick = onResetDevice,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Perangkat", fontSize = 12.sp)
                        }
                    }

                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hapus", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CreateUserDialog(
    creatorRole: UserRole,
    onDismiss: () -> Unit,
    onCreate: (name: String, role: UserRole, customSerial: String) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.AGENT) }
    var customSerialInput by remember { mutableStateOf("") }
    var autoGenerateSerial by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Tambah Akun Baru",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Nama Pengguna / Agen") },
                    placeholder = { Text("Contoh: Budi Santoso") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Pilihan Role (Admin hanya bisa buat AGENT, IT Support bisa buat AGENT & ADMIN)
                Text(
                    text = "Role Akun",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedRole = UserRole.AGENT },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedRole == UserRole.AGENT) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (selectedRole == UserRole.AGENT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text("Agent (1 Device)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (creatorRole == UserRole.IT_SUPPORT) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedRole = UserRole.ADMIN },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedRole == UserRole.ADMIN) Color(0xFF7E57C2).copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (selectedRole == UserRole.ADMIN) Color(0xFF7E57C2) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text("Admin (Multi)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                        text = "Serial Number",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { autoGenerateSerial = !autoGenerateSerial }) {
                        Text(if (autoGenerateSerial) "Atur Manual" else "Acak Otomatis", fontSize = 12.sp)
                    }
                }

                if (!autoGenerateSerial) {
                    OutlinedTextField(
                        value = customSerialInput,
                        onValueChange = { customSerialInput = it.uppercase() },
                        placeholder = { Text("Contoh: AGT-XYZ123") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Text(
                            text = "Akan dibuat otomatis dengan format ${if (selectedRole == UserRole.AGENT) "AGT" else "ADM"}-XXXXXXXX",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onCreate(nameInput, selectedRole, if (autoGenerateSerial) "" else customSerialInput)
                        },
                        enabled = nameInput.isNotBlank(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Buat Akun")
                    }
                }
            }
        }
    }
}
