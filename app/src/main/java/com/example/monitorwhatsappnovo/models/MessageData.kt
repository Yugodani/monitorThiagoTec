package com.monitorwhatsapp.models

data class MessageData(
    val phone_number: String,
    val contact_name: String?,
    val direction: String,  // sent, received
    val content: String,
    val message_date: String,  // ISO 8601
    val is_read: Boolean = false
)