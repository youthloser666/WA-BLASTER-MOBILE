package com.example.wablaster.ui.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wablaster.data.local.SettingsDataStore
import com.example.wablaster.data.local.entity.ContactEntity
import com.example.wablaster.data.repo.ContactRepository
import com.example.wablaster.domain.RenderResult
import com.example.wablaster.domain.RenderTemplate
import com.example.wablaster.domain.VariantDistributor
import com.example.wablaster.domain.VariantMode
import com.example.wablaster.util.DelayCalculator
import com.example.wablaster.util.DelayConfig
import com.example.wablaster.util.DelayValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class TemplateViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val contactRepository: ContactRepository
) : ViewModel() {

    // 5 slot varian pesan (Pesan 1 s.d. Pesan 5)
    private val _variants = MutableStateFlow(
        listOf(
            "Halo {nama}, kami ingin menginfokan promo spesial untuk Anda hari ini!",
            "",
            "",
            "",
            ""
        )
    )
    val variants: StateFlow<List<String>> = _variants.asStateFlow()

    private val _selectedVariantIndex = MutableStateFlow(0)
    val selectedVariantIndex: StateFlow<Int> = _selectedVariantIndex.asStateFlow()

    private val _variantMode = MutableStateFlow(VariantMode.SEQUENTIAL)
    val variantMode: StateFlow<VariantMode> = _variantMode.asStateFlow()

    private val _defaultName = MutableStateFlow("Kak")
    val defaultName: StateFlow<String> = _defaultName.asStateFlow()

    private val _delayConfig = MutableStateFlow(DelayConfig())
    val delayConfig: StateFlow<DelayConfig> = _delayConfig.asStateFlow()

    val firstContact: StateFlow<ContactEntity?> = contactRepository.getAllContacts()
        .map { list -> list.firstOrNull { !it.isOptOut } ?: list.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allContacts: StateFlow<List<ContactEntity>> = contactRepository.getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _randomSeed = MutableStateFlow(Random.nextInt())

    // Pratinjau varian yang sedang aktif dipilih
    val previewResult: StateFlow<RenderResult> = combine(
        _variants,
        _selectedVariantIndex,
        firstContact,
        _defaultName,
        _randomSeed
    ) { variantList, index, contact, defName, seed ->
        val currentText = variantList.getOrElse(index) { "" }
        RenderTemplate.render(
            template = currentText,
            contact = contact,
            defaultName = defName,
            random = Random(seed)
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        RenderResult(renderedText = "")
    )

    // Peringatan UI: varian identik atau nomor tanpa nama
    val warnings: StateFlow<List<String>> = combine(
        _variants,
        allContacts,
        _defaultName
    ) { variantList, contacts, defName ->
        val warningList = mutableListOf<String>()

        // 1. Cek varian yang identik
        val activeVariants = VariantDistributor.getActiveVariants(variantList)
        for (i in 0 until activeVariants.size) {
            for (j in i + 1 until activeVariants.size) {
                if (activeVariants[i].text.trim() == activeVariants[j].text.trim()) {
                    warningList.add("Pesan ${activeVariants[i].index + 1} dan Pesan ${activeVariants[j].index + 1} memiliki isi yang identik.")
                }
            }
        }

        // 2. Cek varian memakai {nama} sementara ada penerima tanpa nama
        val usesNameVariable = activeVariants.any {
            it.text.contains("{nama}", ignoreCase = true) || it.text.contains("{name}", ignoreCase = true)
        }
        val hasNamelessContact = contacts.any { it.name.isBlank() }
        if (usesNameVariable && hasNamelessContact) {
            warningList.add("Terdapat penerima tanpa nama di database; nama default '$defName' akan digunakan.")
        }

        warningList
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val delayValidation: StateFlow<DelayValidationResult> = _delayConfig
        .map { DelayCalculator.validate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DelayValidationResult.Valid)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        // Muat dari DataStore
        viewModelScope.launch {
            settingsDataStore.variantsFlow.firstOrNull()?.let { loaded ->
                if (loaded.isNotEmpty()) {
                    val padded = loaded.toMutableList()
                    while (padded.size < 5) padded.add("")
                    _variants.value = padded.take(5)
                }
            }
            settingsDataStore.variantModeFlow.firstOrNull()?.let {
                _variantMode.value = it
            }
            settingsDataStore.defaultNameFlow.firstOrNull()?.let {
                _defaultName.value = it
            }
            settingsDataStore.delayConfigFlow.firstOrNull()?.let {
                _delayConfig.value = it
            }
        }
    }

    fun onVariantTextChanged(index: Int, newText: String) {
        val current = _variants.value.toMutableList()
        if (index in 0 until current.size) {
            current[index] = newText
            _variants.value = current
        }
    }

    fun onSelectVariantIndex(index: Int) {
        if (index in 0..4) {
            _selectedVariantIndex.value = index
        }
    }

    fun insertTextAtCurrentVariant(snippet: String) {
        val index = _selectedVariantIndex.value
        val currentList = _variants.value.toMutableList()
        val currentText = currentList.getOrElse(index) { "" }
        currentList[index] = if (currentText.isBlank()) snippet else "$currentText $snippet"
        _variants.value = currentList
    }

    fun onVariantModeChanged(mode: VariantMode) {
        _variantMode.value = mode
    }

    fun onDefaultNameChanged(name: String) {
        _defaultName.value = name
    }

    fun onDelayConfigChanged(newConfig: DelayConfig) {
        _delayConfig.value = newConfig
    }

    fun regenerateSpintaxPreview() {
        _randomSeed.value = Random.nextInt()
    }

    fun saveSettings() {
        val active = VariantDistributor.getActiveVariants(_variants.value)
        if (active.isEmpty()) {
            _statusMessage.value = "Minimal 1 varian pesan harus diisi!"
            return
        }

        val validation = DelayCalculator.validate(_delayConfig.value)
        if (validation is DelayValidationResult.Invalid) {
            _statusMessage.value = "Pengaturan jeda tidak valid: ${validation.errors.first()}"
            return
        }

        viewModelScope.launch {
            settingsDataStore.saveVariants(_variants.value)
            settingsDataStore.saveVariantMode(_variantMode.value)
            settingsDataStore.saveDefaultName(_defaultName.value)
            settingsDataStore.saveDelayConfig(_delayConfig.value)
            _statusMessage.value = "Varian pesan & pengaturan jeda berhasil disimpan!"
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
