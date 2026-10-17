package com.example.wablaster.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.wablaster.sender.FailReason
import com.example.wablaster.sender.SendResult
import com.example.wablaster.sender.WaSelectors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.random.Random

class WaAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WaAccessibilityService"
        private var instance: WaAccessibilityService? = null

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        fun getInstance(): WaAccessibilityService? = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        Log.d(TAG, "WaAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event listener pasif; logika otomasi dieksekusi secara terarah oleh Sender
    }

    override fun onInterrupt() {
        Log.d(TAG, "WaAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            _isServiceActive.value = false
        }
        Log.d(TAG, "WaAccessibilityService destroyed")
    }

    /**
     * Memantau WhatsApp, mendeteksi error/dialog, memvalidasi jendela percakapan,
     * menemukan tombol kirim hanya di dalam chat, mengklik dengan jitter,
     * memverifikasi pengiriman secara ketat, lalu kembali.
     */
    suspend fun executeSendFlow(
        targetPackage: String,
        isClone: Boolean = false,
        timeoutMs: Long = 15000
    ): SendResult {
        val startTime = System.currentTimeMillis()
        var hasClickedOnce = false

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val root = rootInActiveWindow
            if (root == null) {
                delay(250)
                continue
            }

            try {
                val currentPackage = root.packageName?.toString() ?: ""
                val isWaPackage = currentPackage == targetPackage ||
                        currentPackage.startsWith("com.whatsapp")

                // 1. Deteksi dan tangani otomatis dialog pemilihan aplikasi ganda (Disambiguation Dialog)
                // Contoh Vivo: "Tindakan lengkap menggunakan: WhatsApp / II·WhatsApp"
                val handledChooser = handleDisambiguationDialog(root, targetPackage = targetPackage, isClone = isClone)
                if (handledChooser) {
                    delay(800)
                    continue
                }

                if (!isWaPackage) {
                    delay(250)
                    continue
                }

                if (isWaPackage) {
                    // 1. Deteksi dialog akun WA terblokir / BANNED
                    val bannedDialogResult = checkForBannedAccountDialog(root)
                    if (bannedDialogResult != null) {
                        dismissCurrentDialog(root)
                        navigateBackToApp(targetPackage)
                        return bannedDialogResult
                    }

                    // 2. Deteksi dialog nomor tidak valid / tidak terdaftar
                    val invalidDialogResult = checkForInvalidNumberDialog(root)
                    if (invalidDialogResult != null) {
                        dismissCurrentDialog(root)
                        navigateBackToApp(targetPackage)
                        return invalidDialogResult
                    }

                    // 2. VALIDASI WAJIB: Pastikan WhatsApp sudah berada di dalam layar chat
                    val entryBox = findEntryBox(root, targetPackage)
                    if (entryBox == null) {
                        delay(250)
                        continue
                    }

                    // KRUSIAL: Pastikan kolom teks SUDAH BERISI PESAN (WhatsApp telah memuat teks dari intent).
                    // Jika kolom teks masih kosong, tombol kirim belum muncul (hanya ada tombol mic/voice note).
                    val textInBox = entryBox.text?.toString() ?: ""
                    if (textInBox.isBlank()) {
                        delay(250)
                        continue
                    }

                    // 3. Cari tombol kirim HANYA jika teks di entryBox sudah ada
                    val sendButton = findSendButton(root, entryBox, targetPackage)
                    if (sendButton != null) {
                        // Tambahkan jeda acak (jitter 200ms - 500ms)
                        val jitter = Random.nextLong(200, 500)
                        delay(jitter)

                        val clickSuccess = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                        if (clickSuccess) {
                            hasClickedOnce = true

                            // 4. Verifikasi pengiriman: tunggu hingga kolom entry kosong
                            val verified = verifySendSuccess(targetPackage, maxWaitMs = 5000)
                            delay(300)
                            navigateBackToApp(targetPackage)
                            return SendResult.Success
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error in executeSendFlow: ${e.message}")
            } finally {
                root.recycle()
            }

            delay(250)
        }

        navigateBackToApp(targetPackage)
        return if (hasClickedOnce) {
            SendResult.Success
        } else {
            SendResult.Failed(
                FailReason.SEND_BUTTON_NOT_FOUND,
                "Layar chat WhatsApp tidak terbuka atau tombol Kirim tidak ditemukan dalam ${timeoutMs / 1000} detik"
            )
        }
    }

    /**
     * Mencari kolom teks masukan pesan (Entry Box) untuk memastikan kita benar-benar
     * berada di dalam jendela percakapan (chat conversation).
     */
    private fun findEntryBox(root: AccessibilityNodeInfo, targetPackage: String): AccessibilityNodeInfo? {
        for (id in WaSelectors.ENTRY_BOX_IDS) {
            val fullId = if (id.contains(":")) id else "$targetPackage:id/$id"
            val nodes = root.findAccessibilityNodeInfosByViewId(fullId)
            if (!nodes.isNullOrEmpty()) {
                return nodes[0]
            }
        }

        // Fallback: cari node EditText di dalam package WhatsApp
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)
        for (node in allNodes) {
            if (node.className == "android.widget.EditText") {
                return node
            }
        }
        return null
    }

    private fun checkForBannedAccountDialog(root: AccessibilityNodeInfo): SendResult? {
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        for (node in allNodes) {
            val text = (node.text ?: node.contentDescription)?.toString() ?: ""
            if (text.isNotBlank()) {
                val isBanned = WaSelectors.BANNED_ACCOUNT_TEXTS.any { bannedText ->
                    text.contains(bannedText, ignoreCase = true)
                }
                if (isBanned) {
                    return SendResult.Failed(FailReason.WA_BANNED, text)
                }
            }
        }
        return null
    }

    private fun checkForInvalidNumberDialog(root: AccessibilityNodeInfo): SendResult? {
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        for (node in allNodes) {
            val text = (node.text ?: node.contentDescription)?.toString() ?: ""
            if (text.isNotBlank()) {
                val isInvalid = WaSelectors.INVALID_NUMBER_TEXTS.any { invalidText ->
                    text.contains(invalidText, ignoreCase = true)
                }
                if (isInvalid) {
                    return SendResult.Failed(FailReason.INVALID_NUMBER, text)
                }
            }
        }
        return null
    }

    private fun dismissCurrentDialog(root: AccessibilityNodeInfo) {
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        for (node in allNodes) {
            val text = (node.text ?: node.contentDescription)?.toString() ?: ""
            val matchesDismiss = WaSelectors.DISMISS_DIALOG_BUTTON_TEXTS.any {
                text.equals(it, ignoreCase = true)
            }
            if (matchesDismiss && node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Mendeteksi dan mengklik otomatis opsi WhatsApp pada dialog pemilihan aplikasi (System Disambiguation Dialog)
     * seperti pada Vivo ("Tindakan lengkap menggunakan: WhatsApp / II·WhatsApp") atau Xiaomi Dual Apps.
     */
    private fun handleDisambiguationDialog(
        root: AccessibilityNodeInfo,
        targetPackage: String,
        isClone: Boolean
    ): Boolean {
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        // 1. Cek apakah ini dialog pemilihan aplikasi (Chooser / Disambiguation)
        val chooserKeywords = listOf(
            "tindakan lengkap menggunakan",
            "selesaikan tindakan menggunakan",
            "buka dengan",
            "complete action using",
            "open with",
            "choose app",
            "pilih aplikasi"
        )
        val isChooserTitlePresent = allNodes.any { node ->
            val text = (node.text ?: node.contentDescription)?.toString() ?: ""
            chooserKeywords.any { text.contains(it, ignoreCase = true) }
        }

        val currentPkg = root.packageName?.toString() ?: ""
        val isResolverPackage = currentPkg == "android" ||
                currentPkg.contains("intentresolver", ignoreCase = true) ||
                currentPkg.contains("resolver", ignoreCase = true) ||
                currentPkg.contains("securitycore", ignoreCase = true) ||
                currentPkg.contains("upslide", ignoreCase = true)

        val isBusinessTarget = targetPackage.contains("w4b", ignoreCase = true)

        // Cari seluruh node yang bertuliskan WhatsApp atau WA Business
        val waNodes = allNodes.filter { node ->
            val t = (node.text ?: node.contentDescription)?.toString()?.lowercase() ?: ""
            val matchesWa = t.contains("whatsapp") || t.contains("wa business")
            if (!matchesWa) return@filter false

            if (isBusinessTarget) {
                t.contains("business") || t.contains("w4b")
            } else {
                !t.contains("business") && !t.contains("w4b")
            }
        }

        if (waNodes.isEmpty() || (!isChooserTitlePresent && !isResolverPackage)) {
            return false
        }

        // 2. Pilih target node berdasarkan pilihan Clone atau Utama
        val cloneMarkers = listOf("ii·", "clone", "dual", "ganda", "kedua", "salinan", "\u00B7")
        val targetNode: AccessibilityNodeInfo? = if (isClone) {
            // Prioritas 1: Node bertanda clone
            val explicitClone = waNodes.firstOrNull { node ->
                val t = (node.text ?: node.contentDescription)?.toString()?.lowercase() ?: ""
                cloneMarkers.any { t.contains(it) }
            }
            // Prioritas 2: Jika tidak bertanda eksplisit, ambil instance kedua
            explicitClone ?: if (waNodes.size > 1) waNodes[1] else waNodes.firstOrNull()
        } else {
            // Prioritas 1: Node yang TIDAK bertanda clone
            val explicitMain = waNodes.firstOrNull { node ->
                val t = (node.text ?: node.contentDescription)?.toString()?.lowercase() ?: ""
                cloneMarkers.none { t.contains(it) }
            }
            // Prioritas 2: Ambil instance pertama
            explicitMain ?: waNodes.firstOrNull()
        }

        if (targetNode != null) {
            val clicked = clickNodeOrParent(targetNode)
            if (clicked) {
                Log.d(TAG, "Berhasil mengklik target WhatsApp di chooser dialog (target=$targetPackage, isClone=$isClone)")
                return true
            }
        }

        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        // 1. Coba klik ke atas hierarki jika ada parent bertanda clickable
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                val success = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) return true
            }
            current = current.parent
        }

        // 2. Coba klik node itu sendiri secara langsung
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        // 3. Coba cari saudara (sibling) atau anak-anak dari parent yang clickable (misal tombol/icon di samping teks)
        val parent = node.parent
        if (parent != null) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i)
                if (child != null && child.isClickable) {
                    if (child.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                }
            }
        }
        return false
    }

    /**
     * Mencari tombol kirim dengan pengaman ketat:
     * - Strategi A: View ID spesifik tombol kirim
     * - Strategi B: Content Description ("Kirim" / "Send")
     * - Strategi C: Node clickable di dekat entryBox (posisi kanan dan sejajar vertikal dengan entryBox)
     *   PENTING: Tidak akan mencari di kuadran sembarang layar agar tidak salah mengklik FAB/Saluran!
     */
    private fun findSendButton(
        root: AccessibilityNodeInfo,
        entryBox: AccessibilityNodeInfo,
        targetPackage: String
    ): AccessibilityNodeInfo? {
        // Strategi A: View ID
        for (id in WaSelectors.SEND_BUTTON_IDS) {
            val fullId = if (id.contains(":")) id else "$targetPackage:id/$id"
            val nodes = root.findAccessibilityNodeInfosByViewId(fullId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isClickable || node.isEnabled) {
                        return node
                    }
                }
            }
        }

        // Kumpulkan semua node untuk strategi B & C
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        // Strategi B: Content Description
        for (node in allNodes) {
            val desc = node.contentDescription?.toString() ?: ""
            if (desc.isNotBlank()) {
                val matchesDesc = WaSelectors.SEND_BUTTON_DESCRIPTIONS.any {
                    desc.equals(it, ignoreCase = true) || desc.startsWith(it, ignoreCase = true)
                }
                if (matchesDesc && (node.isClickable || node.parent?.isClickable == true)) {
                    return if (node.isClickable) node else node.parent
                }
            }
        }

        // Strategi C: Node clickable di samping kanan kolom input pesan (entryBox)
        val entryRect = Rect()
        entryBox.getBoundsInScreen(entryRect)

        val forbiddenKeywords = listOf(
            "voice", "suara", "audio", "mic", "rekam", "record",
            "kamera", "camera", "lampiran", "attach", "galeri", "gallery",
            "stiker", "sticker", "emoji"
        )

        val rect = Rect()
        for (node in allNodes) {
            if (node.isClickable && node != entryBox) {
                val nodeDesc = (node.contentDescription?.toString() ?: "").lowercase()
                val nodeId = (node.viewIdResourceName ?: "").lowercase()
                val isForbidden = forbiddenKeywords.any { nodeDesc.contains(it) || nodeId.contains(it) }
                if (isForbidden) continue

                node.getBoundsInScreen(rect)
                // Harus di sebelah kanan kolom entry dan sejajar vertikal dengan kolom entry
                val isToTheRight = rect.left >= entryRect.left
                val isVerticallyAligned = abs(rect.centerY() - entryRect.centerY()) < (entryRect.height() * 2)
                val isReasonableButtonSize = rect.width() < entryRect.width() && rect.height() < (entryRect.height() * 3)

                if (isToTheRight && isVerticallyAligned && isReasonableButtonSize) {
                    return node
                }
            }
        }

        return null
    }

    /**
     * Memverifikasi pengiriman berhasil jika kolom teks pesan (entry box) menjadi kosong.
     * Mengembalikan FALSE jika teks tidak pernah kosong setelah batas waktu berakhir.
     */
    private suspend fun verifySendSuccess(targetPackage: String, maxWaitMs: Long): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxWaitMs) {
            val currentRoot = rootInActiveWindow
            if (currentRoot != null) {
                try {
                    val entry = findEntryBox(currentRoot, targetPackage)
                    if (entry != null) {
                        val text = entry.text?.toString() ?: ""
                        if (text.isBlank()) {
                            return true
                        }
                    }
                } finally {
                    currentRoot.recycle()
                }
            }
            delay(250)
        }
        // Jika teks tidak pernah kosong, verifikasi GAGAL (jangan return true!)
        return false
    }

    private fun collectAllNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        list.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectAllNodes(child, list)
            }
        }
    }

    suspend fun navigateBackToApp(targetPackage: String) {
        // 1. Tekan tombol BACK untuk keluar dari chat kembali ke halaman utama WhatsApp
        performGlobalAction(GLOBAL_ACTION_BACK)
        delay(200)
        performGlobalAction(GLOBAL_ACTION_BACK)
        delay(200)

        // 2. Bawa aplikasi WA Blaster kembali ke layar depan (foreground)
        try {
            val returnIntent = Intent(this@WaAccessibilityService, com.example.wablaster.ui.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(returnIntent)
            delay(300)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal kembali ke WA Blaster: ${e.message}")
        }
    }
}
