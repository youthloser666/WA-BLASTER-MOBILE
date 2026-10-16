package com.example.wablaster.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wablaster.data.local.SettingsDataStore
import com.example.wablaster.data.local.entity.ContactEntity
import com.example.wablaster.data.parser.RecipientInputParser
import com.example.wablaster.data.parser.RecipientParseResult
import com.example.wablaster.data.repo.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _rawInputText = MutableStateFlow("")
    val rawInputText: StateFlow<String> = _rawInputText.asStateFlow()

    // Validasi live: debounce 300ms di Dispatchers.Default (bukan main thread)
    val parseResult: StateFlow<RecipientParseResult> = _rawInputText
        .debounce(300)
        .map { text ->
            withContext(Dispatchers.Default) {
                RecipientInputParser.parse(text)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RecipientParseResult(0, emptyList(), emptyList(), emptyList())
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val contacts: StateFlow<List<ContactEntity>> = _searchQuery
        .debounce(200)
        .flatMapLatest { query ->
            contactRepository.searchContacts(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> = contactRepository.getTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val optOutCount: StateFlow<Int> = contactRepository.getOptOutCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _showInvalidDialog = MutableStateFlow(false)
    val showInvalidDialog: StateFlow<Boolean> = _showInvalidDialog.asStateFlow()

    init {
        // Load draft dari DataStore saat inisialisasi
        viewModelScope.launch {
            val draft = settingsDataStore.recipientInputDraftFlow.first()
            if (draft.isNotBlank() && _rawInputText.value.isBlank()) {
                _rawInputText.value = draft
            }
        }

        // Auto-save draft ke DataStore dengan debounce 500ms
        viewModelScope.launch {
            _rawInputText
                .debounce(500)
                .collect { draft ->
                    settingsDataStore.saveRecipientInputDraft(draft)
                }
        }
    }

    fun onInputTextChanged(newText: String) {
        _rawInputText.value = newText
    }

    fun setShowInvalidDialog(show: Boolean) {
        _showInvalidDialog.value = show
    }

    fun removeInvalidLines() {
        val currentText = _rawInputText.value
        val result = parseResult.value
        if (result.invalidRecipients.isEmpty()) return

        val cleaned = RecipientInputParser.removeInvalidLines(currentText, result)
        _rawInputText.value = cleaned
        _userMessage.value = "${result.invalidCount} baris tidak valid telah dihapus"
    }

    fun removeDuplicateLines() {
        val currentText = _rawInputText.value
        val result = parseResult.value
        if (result.duplicateRecipients.isEmpty()) return

        val cleaned = RecipientInputParser.removeDuplicateLines(currentText, result)
        _rawInputText.value = cleaned
        _userMessage.value = "${result.duplicateCount} baris duplikat telah dihapus"
    }

    fun clearInputText() {
        _rawInputText.value = ""
        viewModelScope.launch {
            settingsDataStore.saveRecipientInputDraft("")
        }
    }

    fun saveToDatabase() {
        val result = parseResult.value
        if (result.validRecipients.isEmpty()) {
            _userMessage.value = "Tidak ada nomor valid untuk disimpan!"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val entities = result.validRecipients.map {
                    ContactEntity(
                        phone = it.phone,
                        name = it.name,
                        isOptOut = false
                    )
                }
                withContext(Dispatchers.IO) {
                    contactRepository.insertContacts(entities)
                }
                _userMessage.value = "Berhasil menyimpan ${entities.size} nomor ke database!"
            } catch (e: Exception) {
                _userMessage.value = "Gagal menyimpan nomor: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun toggleOptOut(contact: ContactEntity) {
        viewModelScope.launch {
            contactRepository.toggleOptOut(contact.id, !contact.isOptOut)
        }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch {
            contactRepository.deleteContact(contact)
        }
    }

    fun deleteAllContacts() {
        viewModelScope.launch {
            contactRepository.deleteAllContacts()
            _userMessage.value = "Semua nomor di database berhasil dihapus"
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
