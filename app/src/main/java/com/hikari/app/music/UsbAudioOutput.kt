package com.hikari.app.music

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/** USB audio devices currently attached, for the Music settings status row. */
object UsbAudioOutput {

    data class Device(val name: String, val type: Int)

    fun devices(context: Context): List<Device> = runCatching {
        val manager = context.getSystemService(AudioManager::class.java) ?: return emptyList()
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }
            .map {
                Device(
                    name = it.productName?.toString()?.takeIf { name -> name.isNotBlank() }
                        ?: "USB audio device",
                    type = it.type,
                )
            }
            .distinctBy { it.name }
    }.getOrDefault(emptyList())
}
