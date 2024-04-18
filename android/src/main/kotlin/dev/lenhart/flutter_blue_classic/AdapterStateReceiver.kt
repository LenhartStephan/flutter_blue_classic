package dev.lenhart.flutter_blue_classic

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.flutter.plugin.common.EventChannel

class AdapterStateReceiver : EventChannel.StreamHandler {
    companion object {
        const val CHANNEL_NAME: String = "${BlueClassicHelper.NAMESPACE}/adapterState"
    }

    private var adapterStateEventSink: EventChannel.EventSink? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        adapterStateEventSink = events
    }

    override fun onCancel(arguments: Any?) {
        adapterStateEventSink = null
    }

    val mBluetoothAdapterStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == null || BluetoothAdapter.ACTION_STATE_CHANGED != action) {
                return
            }
            val adapterState =
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)

            adapterStateEventSink.let {
                it?.success(
                    BlueClassicHelper.adapterStateString(
                        adapterState
                    )
                )
            }
        }
    }
}