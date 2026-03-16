package com.monitorwhatsapp.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.util.Log
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.monitorwhatsapp.api.ApiClient
import com.monitorwhatsapp.models.WhatsAppMessage
import com.monitorwhatsapp.models.SyncWhatsAppRequest
import com.monitorwhatsapp.utils.TokenManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class WhatsAppAccessibilityService : AccessibilityService() {

    private val TAG = "WhatsAppAccess"
    private lateinit var tokenManager: TokenManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)
        Log.d(TAG, "✅ Serviço de acessibilidade CRIADO")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ Serviço de acessibilidade CONECTADO")

        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        info.notificationTimeout = 100
        serviceInfo = info

        Log.d(TAG, "✅ Serviço configurado: ${info.eventTypes}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Log.d(TAG, "📱 Evento recebido: tipo=${event.eventType}, pacote=${event.packageName}")

        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            Log.d(TAG, "📱 Notificação detectada!")

            if (event.packageName == "com.whatsapp") {
                Log.d(TAG, "📱 É do WhatsApp!")
                captureNotification(event)
            } else {
                Log.d(TAG, "📱 Ignorando pacote: ${event.packageName}")
            }
        }
    }

    private fun captureNotification(event: AccessibilityEvent) {
        try {
            // Extrair texto da notificação
            val text = if (event.text.isNotEmpty()) {
                event.text.joinToString(" ")
            } else {
                ""
            }

            Log.d(TAG, "📱 Texto bruto: $text")

            var title = "Desconhecido"
            var content = text
            var chatId = ""
            var phoneNumber = ""
            var messagesList = mutableListOf<Pair<String, String>>() // Lista de (remetente, mensagem)

            // Tentar obter o Notification do evento
            val parcelable = event.parcelableData
            if (parcelable is Notification) {
                val extras = parcelable.extras

                // Título da notificação (normalmente é o nome do contato)
                title = extras.getString(Notification.EXTRA_TITLE) ?: "Desconhecido"

                // Tentar obter as mensagens individuais do estilo "MessagingStyle"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                    if (messages != null) {
                        for (msg in messages) {
                            try {
                                // Para cada mensagem no estilo do WhatsApp
                                val sender = msg::class.java.getMethod("getSenderName").invoke(msg) as? String ?: title
                                val messageText = msg::class.java.getMethod("getText").invoke(msg) as? String ?: ""

                                if (messageText.isNotEmpty()) {
                                    messagesList.add(Pair(sender, messageText))
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Erro ao extrair mensagem: ${e.message}")
                            }
                        }
                    }
                }

                // Se não conseguiu extrair mensagens individuais, usa o texto padrão
                if (messagesList.isEmpty()) {
                    content = extras.getString(Notification.EXTRA_TEXT) ?: text

                    if (content.contains("Novas mensagens:") || content.contains("new messages")) {
                        Log.d(TAG, "📱 Notificação de múltiplas mensagens - não é possível extrair conteúdo individual")
                        messagesList.add(Pair(title, "📱 ${content}"))
                    } else {
                        messagesList.add(Pair(title, content))
                    }
                }

                // Tentar extrair chat_id
                chatId = extras.getString("android.intent.extra.CHAT_ID") ?: ""
                if (chatId.isEmpty()) {
                    chatId = title.replace(" ", "_").replace("[^a-zA-Z0-9_]".toRegex(), "")
                }

                phoneNumber = extras.getString("android.intent.extra.PHONE_NUMBER") ?: ""

                Log.d(TAG, "📱 Notification - Título: $title")
                Log.d(TAG, "📱 Notification - Chat ID: $chatId")
                Log.d(TAG, "📱 Notification - Telefone: $phoneNumber")
                Log.d(TAG, "📱 Mensagens extraídas: ${messagesList.size}")
            }

            // Se ainda não temos número, tentar extrair do título
            if (phoneNumber.isEmpty()) {
                phoneNumber = extractPhoneNumber(title)
            }

            val contactName = title.replace("WhatsApp", "").replace(":", "").trim()
            val timestamp = System.currentTimeMillis()

            Log.d(TAG, "📱 Contato: $contactName")
            Log.d(TAG, "📱 Telefone: $phoneNumber")
            Log.d(TAG, "📱 Chat ID gerado: $chatId")

            // Criar uma mensagem para cada item da lista
            var messageCount = 0
            for ((sender, msgContent) in messagesList) {
                messageCount++

                val message = WhatsAppMessage(
                    phone_number = phoneNumber,
                    contact_name = sender,
                    content = msgContent,
                    message_date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(timestamp + messageCount * 1000)), // Incrementa timestamp para não duplicar
                    direction = "received",
                    is_read = false,
                    is_deleted = false,
                    chat_id = chatId
                )

                Log.d(TAG, "📱 Mensagem $messageCount: ${msgContent.take(50)}")

                serviceScope.launch {
                    sendWhatsAppMessage(message)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao capturar notificação: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun extractPhoneNumber(title: String): String {
        // Tentar extrair número se estiver no formato
        // Exemplos: "+55 11 99999-9999", "(11) 99999-9999", "11999999999"
        val regex = "\\+?[0-9]{10,13}".toRegex()
        val match = regex.find(title)
        return match?.value ?: ""
    }

    private suspend fun sendWhatsAppMessage(message: WhatsAppMessage) {
        val deviceId = tokenManager.getDeviceId()
        if (deviceId == null) {
            Log.e(TAG, "❌ Device ID não encontrado")
            return
        }

        Log.d(TAG, "📤 Enviando mensagem para API...")
        Log.d(TAG, "📤 Device ID: $deviceId")
        Log.d(TAG, "📤 Mensagem: ${message.content}")

        try {
            val apiService = ApiClient.create(tokenManager)

            // Criar a requisição com a classe específica
            val request = SyncWhatsAppRequest(
                device_id = deviceId,
                messages = listOf(message)
            )

            val response = apiService.syncWhatsApp(request)

            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "✅ Mensagem WhatsApp enviada com sucesso!")
                Log.d(TAG, "✅ Resposta: created=${body?.created}, total=${body?.total}")
            } else {
                Log.e(TAG, "❌ Erro HTTP: ${response.code()}")
                Log.e(TAG, "❌ Erro: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exceção: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "⚠️ Serviço interrompido")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "👋 Serviço destruído")
    }
}