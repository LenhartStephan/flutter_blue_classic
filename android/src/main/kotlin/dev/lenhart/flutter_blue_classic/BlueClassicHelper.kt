package dev.lenhart.flutter_blue_classic

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice

class BlueClassicHelper {
    companion object {
        const val NAMESPACE: String = "blue_classic"
        const val METHOD_CHANNEL_NAME: String = "$NAMESPACE/methods"
        const val ERROR_ADDRESS_INVALID : String= "addressInvalid"

        fun adapterStateString(state: Int): String {
            return when (state) {
                BluetoothAdapter.STATE_OFF -> "off"
                BluetoothAdapter.STATE_ON -> "on"
                BluetoothAdapter.STATE_TURNING_OFF -> "turningOff"
                BluetoothAdapter.STATE_TURNING_ON -> "turningOn"
                else -> "unknown"
            }
        }


        fun bondStateString(state: Int): String {
            return when (state) {
                BluetoothDevice.BOND_BONDING -> "bonding"
                BluetoothDevice.BOND_BONDED -> "bonded"
                BluetoothDevice.BOND_NONE -> "none"
                else -> "unknown"
            }
        }

        fun bluetoothDeviceToMap(device: BluetoothDevice): MutableMap<String, Any> {
            val entry: MutableMap<String, Any> = HashMap()
            entry["address"] = device.address
            entry["name"] = device.name ?: ""
            entry["bondState"] = bondStateString(device.bondState)
            return entry
        }
    }
}
