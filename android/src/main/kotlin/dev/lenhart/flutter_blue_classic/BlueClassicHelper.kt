package dev.lenhart.flutter_blue_classic

import android.Manifest
import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission

class BlueClassicHelper {
    companion object {
        const val NAMESPACE: String = "blue_classic"
        const val METHOD_CHANNEL_NAME: String = "$NAMESPACE/methods"
        const val ERROR_ADDRESS_INVALID: String = "addressInvalid"

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

        fun deviceTypeString(type: Int): String {
            return when (type) {
                BluetoothDevice.DEVICE_TYPE_LE -> "le"
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "classic"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "dual"
                else -> "unknown"
            }
        }

        @TargetApi(34)
        @RequiresPermission(value = Manifest.permission.BLUETOOTH_CONNECT)
        fun bluetoothDeviceToMap(
            device: BluetoothDevice,
            rssi: Short? = null
        ): MutableMap<String, Any?> {
            val entry: MutableMap<String, Any?> = HashMap()
            entry["address"] = device.address
            entry["name"] = device.name
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                entry["alias"] = device.alias
            }
            entry["bondState"] = bondStateString(device.bondState)
            entry["deviceType"] = deviceTypeString(device.type)
            entry["rssi"] = rssi
            return entry
        }
    }
}
