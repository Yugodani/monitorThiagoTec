package com.monitorwhatsapp.models

import com.google.gson.annotations.SerializedName

data class WhatsAppMessage(
    @SerializedName("phone_number")
    val phone_number: String,
    @SerializedName("contact_name")
    val contact_name: String?,
    val content: String,
    @SerializedName("message_date")
    val message_date: String,
    val direction: String,
    @SerializedName("is_read")
    val is_read: Boolean,
    @SerializedName("is_deleted")
    val is_deleted: Boolean = false,
    @SerializedName("chat_id")
    val chat_id: String = ""  // ← NOVO CAMPO
)