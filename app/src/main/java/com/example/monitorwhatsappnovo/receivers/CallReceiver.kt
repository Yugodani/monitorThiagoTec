package com.monitorwhatsapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.monitorwhatsapp.models.CallData
import java.text.SimpleDateFormat
import java.util.*

class CallReceiver : BroadcastReceiver() {

    private val TAG = "CallReceiver"
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var incomingNumber: String? = null
    private var callStartTime: Long = 0

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                incomingNumber = number
                lastState = TelephonyManager.CALL_STATE_RINGING
                Log.d(TAG, "Chamada recebida de: $number")
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (lastState != TelephonyManager.CALL_STATE_OFFHOOK) {
                    callStartTime = System.currentTimeMillis()
                    lastState = TelephonyManager.CALL_STATE_OFFHOOK
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (lastState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    val duration = (System.currentTimeMillis() - callStartTime) / 1000

                    Log.d(TAG, "Chamada finalizada: $duration segundos")
                    // Aqui você pode implementar o envio imediato para a API
                }
                lastState = TelephonyManager.CALL_STATE_IDLE
            }
        }
    }
}