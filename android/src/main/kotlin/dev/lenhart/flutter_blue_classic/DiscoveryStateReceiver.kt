package dev.lenhart.flutter_blue_classic

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.flutter.plugin.common.EventChannel

class DiscoveryStateReceiver : EventChannel.StreamHandler {
    companion object {
        const val CHANNEL_NAME: String = "${BlueClassicHelper.NAMESPACE}/discoveryState"
    }

    private var adapterStateEventSink: EventChannel.EventSink? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        adapterStateEventSink = events
    }

    override fun onCancel(arguments: Any?) {
        adapterStateEventSink = null
    }

    val discoveryStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            adapterStateEventSink.let { it?.success(action == BluetoothAdapter.ACTION_DISCOVERY_STARTED) }
        }
    }
}