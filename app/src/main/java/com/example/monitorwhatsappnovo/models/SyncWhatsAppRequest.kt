package com.monitorwhatsapp.models

data class SyncWhatsAppRequest(
    val device_id: String,
    val messages: List<WhatsAppMessage>
)