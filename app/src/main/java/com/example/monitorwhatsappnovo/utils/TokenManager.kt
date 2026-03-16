package com.monitorwhatsapp.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class TokenManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
    }

    fun saveTokens(access: String, refresh: String) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, access)
            putString(KEY_REFRESH_TOKEN, refresh)
            apply()
        }
        Log.d("TokenManager", "✅ Tokens salvos")
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        Log.d("TokenManager", "✅ Device ID salvo: $deviceId")
    }

    fun getDeviceId(): String? {
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)
        Log.d("TokenManager", "📱 Device ID recuperado: $deviceId")
        return deviceId
    }

    fun saveUserInfo(userId: String, email: String) {
        prefs.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_EMAIL, email)
            apply()
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
        Log.d("TokenManager", "✅ Todos os dados limpos")
    }

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    // 🔴 FUNÇÃO DE LOGOUT - LIMPA TODOS OS DADOS
    fun logout() {
        prefs.edit().clear().apply()
        Log.d("TokenManager", "✅ Logout realizado - todos os dados limpos")
    }

    fun isLoggedIn(): Boolean = !getAccessToken().isNullOrEmpty()
}