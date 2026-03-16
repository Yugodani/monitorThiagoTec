package com.monitorwhatsapp.models

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.gson.annotations.SerializedName
import java.util.UUID

data class DeviceRegistration(
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("device_name")
    val deviceName: String,
    @SerializedName("device_model")
    val deviceModel: String,
    val manufacturer: String,
    @SerializedName("os_type")
    val osType: String = "android",
    @SerializedName("os_version")
    val osVersion: String,
    @SerializedName("app_version")
    val appVersion: String = "1.0.0",
    @SerializedName("phone_number")
    val phoneNumber: String = "",
    val imei: String = ""
) {
    companion object {
        fun fromContext(context: Context): DeviceRegistration {
            val deviceId = getDeviceId(context)

            return DeviceRegistration(
                deviceId = deviceId,
                deviceName = Build.MODEL,
                deviceModel = Build.MODEL,
                manufacturer = Build.MANUFACTURER,
                osVersion = Build.VERSION.RELEASE,
                phoneNumber = "",
                imei = ""
            )
        }

        private fun getDeviceId(context: Context): String {
            return try {
                val androidId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") {
                    androidId
                } else {
                    UUID.randomUUID().toString()
                }
            } catch (e: Exception) {
                UUID.randomUUID().toString()
            }
        }
    }
}