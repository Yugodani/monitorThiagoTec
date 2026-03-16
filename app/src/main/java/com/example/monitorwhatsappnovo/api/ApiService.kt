package com.monitorwhatsapp.api

import com.monitorwhatsapp.models.*
import retrofit2.Response
import retrofit2.http.*


interface ApiService {

    // ========== AUTENTICAÇÃO ==========

    @POST("token/")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("token/refresh/")  // ← ISSO ESTÁ CORRETO?
    suspend fun refreshToken(@Body refreshRequest: Map<String, String>): Response<Map<String, String>>

    @POST("api/auth/register/")
    suspend fun register(@Body request: RegisterRequest): Response<LoginResponse>

    // ========== DISPOSITIVOS ==========

    @POST("devices/devices/")
    suspend fun registerDevice(@Body device: DeviceRegistration): Response<DeviceRegistration>

    // ========== LIGAÇÕES ==========

    @POST("calls/calls/sync/bulk/")  // Gera /api/calls/calls/sync/bulk/
    suspend fun syncCalls(@Body body: SyncCallsRequest): Response<Map<String, Any>>

    // ========== MENSAGENS SMS ==========

    @POST("messages/messages/sync/bulk/")
    suspend fun syncMessages(@Body body: SyncMessagesRequest): Response<Map<String, Any>>

    // ========== WHATSAPP ==========

    @POST("whatsapp/sync/bulk/")
    suspend fun syncWhatsApp(@Body body: SyncWhatsAppRequest): Response<SyncResponse>

}