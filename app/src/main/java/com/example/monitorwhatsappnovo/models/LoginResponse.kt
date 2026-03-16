package com.monitorwhatsapp.models

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    val access: String,
    @SerializedName("refresh")
    val refreshToken: String
    // Sem campo 'user' - o servidor não retorna
)