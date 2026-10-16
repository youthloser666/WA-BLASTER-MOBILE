package com.example.wablaster.sender

interface MessageSender {
    suspend fun send(phone: String, message: String): SendResult
}

sealed interface SendResult {
    data object Success : SendResult
    data class Failed(val reason: FailReason, val detail: String? = null) : SendResult
}

enum class FailReason {
    INVALID_NUMBER,
    WA_NOT_INSTALLED,
    SEND_BUTTON_NOT_FOUND,
    TIMEOUT,
    SERVICE_NOT_ENABLED,
    WA_BANNED,
    UNKNOWN
}

