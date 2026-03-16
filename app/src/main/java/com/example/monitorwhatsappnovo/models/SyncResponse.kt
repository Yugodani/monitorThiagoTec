package com.monitorwhatsapp.models

data class SyncResponse(
    val success: Boolean? = null,
    val created: Int? = null,
    val updated: Int? = null,
    val deleted: Int? = null,
    val total: Int? = null,
    val errors: List<Map<String, Any>>? = null
)