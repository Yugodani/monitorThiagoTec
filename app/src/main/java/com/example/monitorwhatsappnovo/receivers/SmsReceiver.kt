package com.monitorwhatsapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.monitorwhatsapp.models.MessageData
import java.text.SimpleDateFormat
import java.util.*

class SmsReceiver : BroadcastReceiver() {

    private val TAG = "SmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            messages.forEach { sms ->
                val message = MessageData(
                    phone_number = sms.displayOriginatingAddress,
                    contact_name = null,
                    direction = "received",
                    content = sms.messageBody,
                    message_date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                        .format(Date()),
                    is_read = false
                )

                Log.d(TAG, "SMS recebido: ${sms.messageBody}")
                // O envio será feito pelo MonitoringService na próxima coleta periódica
            }
        }
    }
}