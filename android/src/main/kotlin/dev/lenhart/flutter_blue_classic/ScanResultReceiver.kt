package dev.lenhart.flutter_blue_classic

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.flutter.plugin.common.EventChannel

class ScanResultReceiver : EventChannel.StreamHandler {

    companion object {
        const val CHANNEL_NAME: String = "${BlueClassicHelper.NAMESPACE}/scanResults"
    }

    private var adapterStateEventSink: EventChannel.EventSink? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        adapterStateEventSink = events
    }

    override fun onCancel(arguments: Any?) {
        adapterStateEventSink = null
    }

    val scanResultReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val device: BluetoothDevice? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                } else {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)

            if (device != null && device.type != BluetoothDevice.DEVICE_TYPE_LE) {
                adapterStateEventSink.let {
                    it?.success(
                        BlueClassicHelper.bluetoothDeviceToMap(
                            device, rssi
                        )
                    )
                }
            }
        }
    }
}