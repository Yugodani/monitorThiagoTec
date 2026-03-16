package com.monitorwhatsapp.models

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val password2: String,
    val company: String,
    val phone: String = ""
)