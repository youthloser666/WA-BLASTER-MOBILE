package com.example.wablaster.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wablaster.sender.AccessibilitySender
import com.example.wablaster.sender.SendResult
import com.example.wablaster.service.WaAccessibilityService
import com.example.wablaster.ui.theme.SuccessGreen
import com.example.wablaster.ui.theme.WABlasterTheme
import com.example.wablaster.ui.theme.WarningAmber
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

import androidx.activity.viewModels
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.wablaster.data.supabase.UserRole
import com.example.wablaster.ui.auth.AuthViewModel
import com.example.wablaster.ui.auth.LoginScreen
import com.example.wablaster.ui.maintenance.MaintenanceScreen
import com.example.wablaster.ui.admin.AdminViewModel
import com.example.wablaster.ui.admin.UserManagementScreen
import com.example.wablaster.ui.contacts.ContactsScreen
import com.example.wablaster.ui.contacts.ContactsViewModel
import com.example.wablaster.ui.campaign.CampaignScreen
import com.example.wablaster.ui.campaign.CampaignViewModel
import com.example.wablaster.ui.template.TemplateScreen
import com.example.wablaster.ui.template.TemplateViewModel
import com.example.wablaster.ui.log.LogScreen
import com.example.wablaster.ui.log.LogViewModel
import com.example.wablaster.ui.settings.SettingsScreen
import com.example.wablaster.ui.settings.SettingsViewModel
import com.example.wablaster.ui.onboarding.OnboardingDialog

data class LogEntry(
    val timestamp: String,
    val phone: String,
    val isSuccess: Boolean,
    val detail: String
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val campaignViewModel: CampaignViewModel by viewModels()
    private val contactsViewModel: ContactsViewModel by viewModels()
    private val templateViewModel: TemplateViewModel by viewModels()
    private val logViewModel: LogViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val adminViewModel: AdminViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WABlasterTheme {
                val authUiState by authViewModel.uiState.collectAsState()

                if (!authUiState.isLoggedIn) {
                    LoginScreen(viewModel = authViewModel)
                } else if (authUiState.isMaintenanceActive && authUiState.role != UserRole.IT_SUPPORT) {
                    MaintenanceScreen(
                        message = authUiState.maintenanceMessage,
                        onCheckStatus = { authViewModel.checkMaintenance() },
                        onLogout = { authViewModel.logout() }
                    )
                } else {
                    MainAppNavigation(
                        campaignViewModel = campaignViewModel,
                        contactsViewModel = contactsViewModel,
                        templateViewModel = templateViewModel,
                        logViewModel = logViewModel,
                        settingsViewModel = settingsViewModel,
                        authViewModel = authViewModel,
                        adminViewModel = adminViewModel
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavigation(
    campaignViewModel: CampaignViewModel,
    contactsViewModel: ContactsViewModel,
    templateViewModel: TemplateViewModel,
    logViewModel: LogViewModel,
    settingsViewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    adminViewModel: AdminViewModel
) {
    val authUiState by authViewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0: Kampanye, 1: Nomor, 2: Pesan & Jeda, 3: Log, 4: Setelan
    var showSpikeTest by remember { mutableStateOf(false) }
    var showUserManagement by remember { mutableStateOf(false) }

    if (showUserManagement) {
        Dialog(
            onDismissRequest = { showUserManagement = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                UserManagementScreen(
                    currentRole = authUiState.role,
                    currentUserSerial = authUiState.serialNumber,
                    currentUserName = authUiState.name,
                    viewModel = adminViewModel,
                    onClose = { showUserManagement = false }
                )
            }
        }
    }

    // Onboarding dialog is accessible from Settings instead of interrupting user on launch
    if (showSpikeTest) {
        Dialog(
            onDismissRequest = { showSpikeTest = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                SpikeScreen(onClose = { showSpikeTest = false })
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Konten Tab Utama (Bleeding penuh tembus di bawah floating navbar)
        when (selectedTab) {
            0 -> {
                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = "Eksekusi Broadcast",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = innerPadding.calculateTopPadding())
                    ) {
                        CampaignScreen(viewModel = campaignViewModel)
                    }
                }
            }
            1 -> {
                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = "Daftar Nomor",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = innerPadding.calculateTopPadding())
                    ) {
                        ContactsScreen(viewModel = contactsViewModel)
                    }
                }
            }
            2 -> {
                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = "Pesan",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = innerPadding.calculateTopPadding())
                    ) {
                        TemplateScreen(viewModel = templateViewModel)
                    }
                }
            }
            3 -> {
                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = "Riwayat Log",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = innerPadding.calculateTopPadding())
                    ) {
                        LogScreen(viewModel = logViewModel)
                    }
                }
            }
            4 -> {
                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = "Pengaturan",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = innerPadding.calculateTopPadding())
                    ) {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            currentUserSerial = authUiState.serialNumber,
                            currentUserName = authUiState.name,
                            currentUserRole = authUiState.role,
                            onOpenUserManagement = { showUserManagement = true },
                            onLogout = { authViewModel.logout() },
                            onOpenSpikeTest = { showSpikeTest = true }
                        )
                    }
                }
            }
        }

        // Floating Navbar (Tanpa string text, mengambang di atas konten yang bleeding)
        FloatingBottomNavigationBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun FloatingBottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Icons.Default.RocketLaunch to "Broadcast",
        Icons.Default.FormatListNumbered to "Nomor",
        Icons.Default.EditNote to "Pesan",
        Icons.Default.History to "Log",
        Icons.Default.Settings to "Setelan"
    )

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, (icon, contentDesc) ->
                val isSelected = selectedTab == index
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onTabSelected(index) },
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = contentDesc,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpikeScreen(
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isServiceActive by WaAccessibilityService.isServiceActive.collectAsState()

    var targetPackage by remember { mutableStateOf("com.whatsapp") }
    var numbersInput by remember {
        mutableStateOf(
            "6281234567890\n6281234567891\n6281234567892\n6281234567893\n6281234567894"
        )
    }
    var messageInput by remember {
        mutableStateOf("Halo, ini pesan uji coba otomatis dari WA Blaster Spike Test.")
    }

    var isRunning by remember { mutableStateOf(false) }
    var currentProgressText by remember { mutableStateOf("Siap memulai") }
    var currentCountdown by remember { mutableStateOf(0) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    val logs = remember { mutableStateListOf<LogEntry>() }

    val successCount = logs.count { it.isSuccess }
    val failCount = logs.count { !it.isSuccess }

    fun addLog(phone: String, isSuccess: Boolean, detail: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logs.add(0, LogEntry(time, phone, isSuccess, detail))
    }

    fun startSpike() {
        val numbers = numbersInput.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (numbers.isEmpty()) {
            Toast.makeText(context, "Daftar nomor tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isServiceActive) {
            Toast.makeText(context, "Aktifkan Layanan Aksesibilitas terlebih dahulu!", Toast.LENGTH_LONG).show()
            return
        }

        isRunning = true
        activeJob = coroutineScope.launch(Dispatchers.IO) {
            val sender = AccessibilitySender(context, targetPackage)

            for ((index, number) in numbers.withIndex()) {
                if (!isActive) break

                withContext(Dispatchers.Main) {
                    currentProgressText = "Mengirim nomor ${index + 1}/${numbers.size}: $number"
                }

                val result = sender.send(number, messageInput)

                withContext(Dispatchers.Main) {
                    when (result) {
                        is SendResult.Success -> {
                            addLog(number, true, "Tombol Kirim berhasil diklik & chat diproses")
                        }
                        is SendResult.Failed -> {
                            addLog(number, false, "Gagal: ${result.reason} (${result.detail ?: "-"})")
                        }
                    }
                }

                // Jika masih ada nomor berikutnya, beri jeda acak 5-10 detik sesuai spek Fase 0
                if (index < numbers.size - 1 && isActive) {
                    val delaySeconds = Random.nextInt(5, 11)
                    for (sec in delaySeconds downTo 1) {
                        if (!isActive) break
                        withContext(Dispatchers.Main) {
                            currentCountdown = sec
                            currentProgressText = "Jeda acak ($delaySeconds detik): sisa $sec detik..."
                        }
                        delay(1000)
                    }
                    withContext(Dispatchers.Main) {
                        currentCountdown = 0
                    }
                }
            }

            withContext(Dispatchers.Main) {
                isRunning = false
                currentProgressText = "Selesai. Sukses: $successCount, Gagal: $failCount"
                Toast.makeText(context, "Pengujian spike selesai!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun stopSpike() {
        activeJob?.cancel()
        isRunning = false
        currentCountdown = 0
        currentProgressText = "Pengujian dihentikan oleh pengguna."
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Alat Debug: Spike Test",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Uji Selektor & Verifikasi Otomasi WA",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Tutup")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Status Aksesibilitas
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isServiceActive) SuccessGreen.copy(alpha = 0.12f)
                        else WarningAmber.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isServiceActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isServiceActive) SuccessGreen else WarningAmber
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isServiceActive) "Layanan Aksesibilitas Aktif" else "Aksesibilitas Belum Aktif",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (!isServiceActive) {
                            Text(
                                text = "Aplikasi membutuhkan izin Aksesibilitas untuk otomatisasi klik tombol Kirim di WhatsApp.",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            if (Build.VERSION.SDK_INT >= 33) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Catatan Android 13+ (Restricted Settings): Jika menu abu-abu, buka Pengaturan HP > Aplikasi > WA Blaster > Titik tiga (kanan atas) > 'Izinkan setelan terbatas', lalu aktifkan di Aksesibilitas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buka Pengaturan Aksesibilitas")
                            }
                        } else {
                            Text(
                                text = "Siap melakukan pengetikan dan klik otomatis pada jendela WhatsApp.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }

            // Card 2: Pengaturan Target & Nomor
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "1. Pilih WhatsApp Target",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = targetPackage == "com.whatsapp",
                                onClick = { targetPackage = "com.whatsapp" }
                            )
                            Text("WhatsApp Biasa")
                            Spacer(modifier = Modifier.width(16.dp))
                            RadioButton(
                                selected = targetPackage == "com.whatsapp.w4b",
                                onClick = { targetPackage = "com.whatsapp.w4b" }
                            )
                            Text("WA Business")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "2. Masukkan 5 Nomor Pengujian (1 baris per nomor)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = numbersInput,
                            onValueChange = { numbersInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 6,
                            enabled = !isRunning,
                            placeholder = { Text("Contoh:\n6281234567890\n6281234567891") }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "3. Pesan Uji Coba",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3,
                            enabled = !isRunning
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { startSpike() },
                                modifier = Modifier.weight(1f),
                                enabled = !isRunning && isServiceActive,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mulai Uji Spike")
                            }

                            if (isRunning) {
                                Button(
                                    onClick = { stopSpike() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Berhenti")
                                }
                            }
                        }
                    }
                }
            }

            // Card 3: Status Progres & Countdown
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Status Pengujian",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentProgressText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (isRunning) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Sukses", color = SuccessGreen, fontWeight = FontWeight.Bold)
                                Text("$successCount", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Gagal", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                Text("$failCount", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Countdown Jeda", color = WarningAmber, fontWeight = FontWeight.Bold)
                                Text("${currentCountdown}s", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Bagian Log
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Log Hasil Eksekusi",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (logs.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                val summary = buildString {
                                    appendLine("=== HASIL SPIKE VALIDASI WA BLASTER ===")
                                    appendLine("Waktu Uji: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                                    appendLine("Total Sukses: $successCount, Total Gagal: $failCount")
                                    appendLine("Detail:")
                                    logs.forEach {
                                        appendLine("[${it.timestamp}] ${it.phone} -> ${if (it.isSuccess) "SUKSES" else "GAGAL"}: ${it.detail}")
                                    }
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Spike Result", summary))
                                Toast.makeText(context, "Ringkasan disalin ke clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salin Ringkasan")
                        }
                    }
                }
            }

            if (logs.isEmpty()) {
                item {
                    Text(
                        text = "Belum ada log eksekusi. Klik 'Mulai Uji Spike' untuk memulai.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                items(logs) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (log.isSuccess) SuccessGreen.copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = log.phone, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (log.isSuccess) "SUKSES" else "GAGAL",
                                    color = if (log.isSuccess) SuccessGreen else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${log.timestamp} - ${log.detail}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
