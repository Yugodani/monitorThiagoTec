package com.monitorwhatsapp.models

data class CallData(
    val phone_number: String,
    val contact_name: String?,
    val call_type: String,  // incoming, outgoing, missed
    val duration: Long,
    val call_date: String,  // ISO 8601
    val imei: String? = null,
    val location: String? = null
)

data class SyncCallsRequest(
    val device_id: String,
    val calls: List<CallData>
)

data class SyncMessagesRequest(
    val device_id: String,
    val messages: List<MessageData>
)