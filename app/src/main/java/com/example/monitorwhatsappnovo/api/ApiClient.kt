package com.monitorwhatsapp.api

import android.util.Log
import com.monitorwhatsapp.utils.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

object ApiClient {

    private const val BASE_URL = "https://monitor-whats-53jh.onrender.com/api/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    fun create(tokenManager: TokenManager): ApiService {

        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val token = tokenManager.getAccessToken()

            token?.let {
                Log.d("ApiClient", "Token sendo enviado: ${it.substring(0, 20)}...")
            }

            var response = chain.proceed(
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            )

            if (response.code == 401) {
                synchronized(this) {
                    Log.d("ApiClient", "⚠️ Token expirado, tentando renovar...")

                    val newToken = refreshToken(tokenManager)

                    if (newToken != null) {
                        Log.d("ApiClient", "✅ Token renovado com sucesso!")
                        response.close()

                        response = chain.proceed(
                            originalRequest.newBuilder()
                                .header("Authorization", "Bearer $newToken")
                                .build()
                        )
                    } else {
                        Log.e("ApiClient", "❌ Falha ao renovar token")
                    }
                }
            }

            response
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }

    private fun createRefreshClient(): ApiService {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }

    private fun refreshToken(tokenManager: TokenManager): String? {
        return runBlocking {
            try {
                val refreshToken = tokenManager.getRefreshToken()
                if (refreshToken.isNullOrEmpty()) {
                    Log.e("ApiClient", "❌ Refresh token não encontrado")
                    return@runBlocking null
                }

                Log.d("ApiClient", "🔄 Solicitando novo token...")

                val apiService = createRefreshClient()
                val response = apiService.refreshToken(mapOf("refresh" to refreshToken))

                // Verificar sucesso
                if (response.isSuccessful) {
                    val body = response.body()
                    val newAccessToken = body?.get("access")

                    if (newAccessToken != null) {
                        tokenManager.saveTokens(newAccessToken, refreshToken)
                        Log.d("ApiClient", "✅ Novo token salvo!")
                        return@runBlocking newAccessToken
                    }
                }

                // TRATAMENTO DIRETO - sem criar variável intermediária
                if (response.code() == 401) {  // ← Vamos testar com parênteses
                    Log.e("ApiClient", "❌ Refresh token expirado - usuário precisa fazer login")
                    tokenManager.clear()
                } else {
                    Log.e("ApiClient", "❌ Falha ao renovar token: ${response.code()}")
                }

                return@runBlocking null

            } catch (e: Exception) {
                Log.e("ApiClient", "❌ Erro ao renovar token: ${e.message}")
                e.printStackTrace()
                return@runBlocking null
            }
        }
    }
}