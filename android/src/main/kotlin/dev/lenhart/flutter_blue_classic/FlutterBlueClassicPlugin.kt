package dev.lenhart.flutter_blue_classic

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.SparseArray
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat.getSystemService
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.util.UUID
import java.util.concurrent.Executors

/** FlutterBlueClassicPlugin */
class FlutterBlueClassicPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

    companion object {
        const val TAG: String = "FlutterBlueClassic"
    }

    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var methodChannel: MethodChannel

    private var activityPluginBinding: ActivityPluginBinding? = null
    private var binaryMessenger: BinaryMessenger? = null

    private lateinit var adapterStateChannel: EventChannel
    private lateinit var adapterStateReceiver: AdapterStateReceiver

    private lateinit var scanResultChannel: EventChannel
    private lateinit var scanResultReceiver: ScanResultReceiver

    private lateinit var discoveryStateChannel: EventChannel
    private lateinit var discoveryStateReceiver: DiscoveryStateReceiver

    private lateinit var permissionManager: PermissionManager
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val connections = SparseArray<BluetoothConnectionWrapper>(2)
    private var lastConnectionId = 0

    private var context: Application? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel =
            MethodChannel(
                flutterPluginBinding.binaryMessenger,
                BlueClassicHelper.METHOD_CHANNEL_NAME
            )
        methodChannel.setMethodCallHandler(this)
        binaryMessenger = flutterPluginBinding.binaryMessenger

        // Adapter State Stream
        adapterStateReceiver = AdapterStateReceiver()
        adapterStateChannel =
            EventChannel(flutterPluginBinding.binaryMessenger, AdapterStateReceiver.CHANNEL_NAME)
        adapterStateChannel.setStreamHandler(adapterStateReceiver)
        val filterAdapter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        flutterPluginBinding.applicationContext.registerReceiver(
            adapterStateReceiver.mBluetoothAdapterStateReceiver,
            filterAdapter
        )

        // Scan Result Stream
        scanResultReceiver = ScanResultReceiver()
        scanResultChannel =
            EventChannel(flutterPluginBinding.binaryMessenger, ScanResultReceiver.CHANNEL_NAME)
        scanResultChannel.setStreamHandler(scanResultReceiver)
        val filterScanResults = IntentFilter(BluetoothDevice.ACTION_FOUND)
        flutterPluginBinding.applicationContext.registerReceiver(
            scanResultReceiver.scanResultReceiver,
            filterScanResults
        )

        // Discovery State Stream
        discoveryStateReceiver = DiscoveryStateReceiver()
        discoveryStateChannel =
            EventChannel(flutterPluginBinding.binaryMessenger, DiscoveryStateReceiver.CHANNEL_NAME)
        discoveryStateChannel.setStreamHandler(discoveryStateReceiver)
        val filterDiscoveryState = IntentFilter()
        filterDiscoveryState.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filterDiscoveryState.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        flutterPluginBinding.applicationContext.registerReceiver(
            discoveryStateReceiver.discoveryStateReceiver,
            filterDiscoveryState
        )

        this.context = flutterPluginBinding.applicationContext as Application

        val bluetoothManager =
            getSystemService(flutterPluginBinding.applicationContext, BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter

    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityPluginBinding = binding
        permissionManager = PermissionManager(context!!.applicationContext, binding.activity)
        binding.addRequestPermissionsResultListener(permissionManager)
    }

    override fun onDetachedFromActivity() {
        activityPluginBinding?.removeRequestPermissionsResultListener(permissionManager)
        activityPluginBinding = null
        permissionManager.setActivity(null)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "isSupported" -> result.success(checkBluetoothSupport())
            "isEnabled" -> result.success(isBluetoothEnabled())
            "turnOn" -> turnOn(result)
            "getAdapterState" -> result.success(getAdapterState())
            "bondedDevices" -> getBondedDevices(result)
            "startScan" -> startScan(result, call.argument<Boolean>("usesFineLocation") ?: false)
            "stopScan" -> stopScan(result)
            "isScanningNow" -> isScanningNow(result)
            "bondDevice" -> bondDevice(result, call.argument<String>("address") ?: "")
            "connect" -> connect(
                result,
                call.argument<String>("address") ?: "",
                call.argument<String>("uuid")
            )

            "write" -> {
                val id = call.argument<Int>("id")
                val bytes = call.argument<ByteArray>("bytes")
                if (id != null && bytes != null) {
                    write(result, id, bytes)
                } else {
                    result.error(BlueClassicHelper.ERROR_ARGUMENT_MISSING, "Not all required arguments were specified", null)
                }
            }

            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        adapterStateChannel.setStreamHandler(null)
        binaryMessenger = null

        binding.applicationContext.unregisterReceiver(adapterStateReceiver.mBluetoothAdapterStateReceiver)
        binding.applicationContext.unregisterReceiver(scanResultReceiver.scanResultReceiver)
        binding.applicationContext.unregisterReceiver(discoveryStateReceiver.discoveryStateReceiver)
    }


    /**
     * Checks if the device supports Bluetooth.
     *
     * @return True if Bluetooth is supported, false otherwise.
     */
    private fun checkBluetoothSupport(): Boolean {
        return context?.packageManager?.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) == true
    }

    /**
     * Checks if Bluetooth is enabled on the device.
     *
     * @return true if Bluetooth is enabled, false otherwise.
     */
    private fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Turns on Bluetooth if it's not already enabled.
     *
     * @param result The [Result] object to return the result of the operation.
     */
    private fun turnOn(result: Result) {
        if (isBluetoothEnabled()) {
            result.success(null)
            return
        }
        val permissions = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionManager.ensurePermissions(permissions.toTypedArray()) { success: Boolean, deniedPermissions: List<String>? ->
            run {
                try {
                    if (success) {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        startActivityForResult(
                            activityPluginBinding!!.activity,
                            enableBtIntent,
                            PermissionManager.REQUEST_ENABLE_BT,
                            null
                        )
                        result.success(null)
                        return@run
                    }
                } catch (_: Exception) {
                }
                result.error(
                    BlueClassicHelper.ERROR_PERMISSION_DENIED,
                    String.format(
                        "Required permission(s) %s denied",
                        deniedPermissions?.joinToString() ?: ""
                    ), null
                )

            }
        }
    }

    /**
     * Retrieves the current state of the Bluetooth adapter as a string.
     */
    private fun getAdapterState(): String {
        return bluetoothAdapter?.state?.let { BlueClassicHelper.adapterStateString(it) }
            ?: "unavailable"
    }

    /**
     * Retrieves a list of bonded (paired) Bluetooth devices.
     *
     * @param result The [Result] object to return the scanning status.
     *               Returns the list of bonded devices.
     */
    @SuppressLint("MissingPermission")
    private fun getBondedDevices(result: Result) {
        val permissions = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionManager.ensurePermissions(permissions.toTypedArray()) { success: Boolean, deniedPermissions: List<String>? ->
            run {
                if (success) {
                    val devices: List<MutableMap<String, Any?>>? =
                        bluetoothAdapter?.bondedDevices?.filter { it.type != BluetoothDevice.DEVICE_TYPE_LE }
                            ?.map {
                                BlueClassicHelper.bluetoothDeviceToMap(it)
                            }

                    result.success(devices)
                } else {
                    result.error(
                        BlueClassicHelper.ERROR_PERMISSION_DENIED,
                        String.format(
                            "Required permission(s) %s denied",
                            deniedPermissions?.joinToString() ?: ""
                        ), null
                    )
                }
            }
        }
    }

    /**
     * Starts a Bluetooth discovery process.
     *
     * @param result The [Result] object to return the scanning status.
     *               Returns true if discovery started, false otherwise.
     * @param usesFineLocation A boolean indicating whether ACCESS_FINE_LOCATION permission
     *                         is required for the scan. This is typically true if the scan
     *                         needs to derive physical location from Bluetooth beacons.
     */
    @SuppressLint("MissingPermission")
    private fun startScan(result: Result, usesFineLocation: Boolean) {
        val permissions = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (usesFineLocation) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        permissionManager.ensurePermissions(permissions.toTypedArray()) { success: Boolean, deniedPermissions: List<String>? ->
            run {
                if (success) {
                    val discoveryStartState = bluetoothAdapter?.startDiscovery()
                    result.success(discoveryStartState)
                } else {
                    result.error(
                        BlueClassicHelper.ERROR_PERMISSION_DENIED,
                        String.format(
                            "Required permission(s) %s denied",
                            deniedPermissions?.joinToString() ?: ""
                        ), null
                    )
                }
            }
        }
    }

    /**
     * Stops an ongoing Bluetooth device discovery.
     *
     * @param result The [Result] object to send the outcome of the operation.
     *               Returns true if discovery is stopped, false otherwise.
     */
    @SuppressLint("MissingPermission")
    private fun stopScan(result: Result) {
        val permissions = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        permissionManager.ensurePermissions(permissions.toTypedArray()) { success: Boolean, deniedPermissions: List<String>? ->
            run {
                if (success) {
                    result.success(bluetoothAdapter?.cancelDiscovery())
                } else {
                    result.error(
                        BlueClassicHelper.ERROR_PERMISSION_DENIED,
                        String.format(
                            "Required permission(s) %s denied",
                            deniedPermissions?.joinToString() ?: ""
                        ), null
                    )
                }
            }
        }
    }

    /**
     * Checks if the Bluetooth adapter is currently discovering devices.
     *
     * @param result The [Result] object to return the scanning status.
     *               Returns true if discovering, false otherwise.
     */
    @SuppressLint("MissingPermission")
    private fun isScanningNow(result: Result) {
        val permissions = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        permissionManager.ensurePermissions(permissions.toTypedArray()) { success: Boolean, deniedPermissions: List<String>? ->
            run {
                if (success) {
                    result.success(bluetoothAdapter?.isDiscovering ?: false)
                } else {
                    result.error(
                        BlueClassicHelper.ERROR_PERMISSION_DENIED,
                        String.format(
                            "Required permission(s) %s denied",
                            deniedPermissions?.joinToString() ?: ""
                        ), null
                    )
                }
            }
        }
    }

    /**
     * Attempts to bond (pair) with a Bluetooth device.
     *
     * @param result The [Result] object to return the outcome of the bonding attempt.
     *               Returns true if bonding is initiated, false otherwise.
     * @param address The MAC address of the Bluetooth device to bond with.
     */
    @SuppressLint("MissingPermission")
    private fun bondDevice(result: Result, address: String) {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            result.error(
                BlueClassicHelper.ERROR_ADDRESS_INVALID,
                "The bluetooth address $address is invalid",
                null
            )
            return
        }

        val permissions = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        permissionManager.ensurePermissions(permissions.toTypedArray()) { success: Boolean, deniedPermissions: List<String>? ->
            run {
                if (success) {
                    val device = bluetoothAdapter?.getRemoteDevice(address)
                    result.success(device?.createBond() == true)
                } else {
                    result.error(
                        BlueClassicHelper.ERROR_PERMISSION_DENIED,
                        String.format(
                            "Required permission(s) %s denied",
                            deniedPermissions?.joinToString() ?: ""
                        ), null
                    )
                }
            }
        }
    }


    /**
     * Connects to a Bluetooth device.
     *
     * This function attempts to establish a connection with a Bluetooth device specified by its MAC address.
     * If a UUID is provided, it attempts to parse it. An invalid UUID will result in an error.
     * A new connection ID is generated, and a [BluetoothConnectionWrapper] is created and stored.
     *
     * @param result The [Result] object to report success or failure of the connection attempt.
     *               Returns the id of the new connection.
     * @param address The MAC address of the Bluetooth device to connect to.
     * @param uuid An optional UUID string for the service to connect to. If null, a default or pre-configured UUID might be used by the underlying connection mechanism.
     */
    @SuppressLint("MissingPermission")
    private fun connect(result: Result, address: String, uuid: String?) {
        var permissionSuccess = true
        val permissions = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        permissionManager.ensurePermissions(permissions.toTypedArray()) { success: Boolean, deniedPermissions: List<String>? ->
            if (!success) {
                result.error(
                    BlueClassicHelper.ERROR_PERMISSION_DENIED,
                    String.format(
                        "Required permission(s) %s denied",
                        deniedPermissions?.joinToString() ?: ""
                    ), null
                )
            }
            permissionSuccess = success
        }

        if (!permissionSuccess) return

        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            result.error(
                BlueClassicHelper.ERROR_ADDRESS_INVALID,
                "The bluetooth address $address is invalid",
                null
            )
            return
        }

        val connectUuid = try {
            uuid?.let { UUID.fromString(it) }
        } catch (_: Exception) {
            result.error(
                BlueClassicHelper.ERROR_UUID_INVALID,
                "$uuid is not a valid UUID.",
                null
            )
            return
        }

        val id = ++lastConnectionId
        val connection = BluetoothConnectionWrapper(id, bluetoothAdapter!!)
        connections.put(id, connection)
        Log.d(
            TAG,
            "Connecting to $address (id: $id)"
        )

        Thread {
            try {
                connection.connect(address, connectUuid)
                activityPluginBinding!!.activity.runOnUiThread {
                    result.success(id)
                }
            } catch (ex: java.lang.Exception) {
                activityPluginBinding!!.activity.runOnUiThread {
                    result.error(
                        BlueClassicHelper.ERROR_COULD_NOT_CONNECT,
                        ex.message, null
                    )
                }
                connections.remove(id)
            }
        }.also { it.start() }
    }

    /**
     * Writes [bytes] to the connection identified by [id].
     *
     * @param result The [Result] object to report result of the operation.
     *
     * @param id The id of the Bluetooth connection.
     * @param bytes The [ByteArray] to be written to the connection.
     */
    private fun write(result: Result, id: Int, bytes: ByteArray) {
        val connection: BluetoothConnection =
        try {
            connections.get(id) ?: throw Exception("Connection with id $id does not exist.")
        } catch (_: Exception) {
            activityPluginBinding!!.activity.runOnUiThread {
                result.error(
                    BlueClassicHelper.ERROR_CONNECTION_INVALID,
                    "The connection with id $id does not exist.",
                    null
                )
            }
            return
        }
            Thread {
                try {
                connection.write(bytes)
                activityPluginBinding!!.activity.runOnUiThread {
                    result.success(null)
                }
            } catch (e: Exception) {
            Log.e(TAG, "Error during write. Connection might have closed.", e)
            activityPluginBinding!!.activity.runOnUiThread {
                result.error(
                    BlueClassicHelper.ERROR_WRITE_FAILED,
                    "Error during write occurred. Connection might have closed.",
                    null
                )
            }
        }
            }.also { it.start() }
    }

    // ------ INNER CLASS ------

    private inner class BluetoothConnectionWrapper(private val id: Int, adapter: BluetoothAdapter) :
        BluetoothConnection(adapter), EventChannel.StreamHandler {
        private var readSink: EventSink? = null
        private var readChannel: EventChannel = EventChannel(
            binaryMessenger,
            BlueClassicHelper.NAMESPACE + "/connection/" + id
        )

        init {
            readChannel.setStreamHandler(this)
        }

        /**
         * Callback for reading data.
         * This function is called when data is received from the connected Bluetooth device.
         * This method sends the received data to flutter.
         *
         * @param data The byte array containing the received data.
         */
        override fun onRead(data: ByteArray) {
            activityPluginBinding?.activity?.runOnUiThread {
                readSink?.success(data)
            }
        }

        /**
         * Callback for disconnection.
         *
         * This method is called when the Bluetooth connection is terminated.
         *
         * @param byRemote `true` if the disconnection was initiated by the remote device,
         *                 `false` if it was initiated locally (e.g., by calling [disconnect]).
         */
        override fun onDisconnected(byRemote: Boolean) {
            activityPluginBinding?.activity?.runOnUiThread {
                if (byRemote) {
                    Log.d(TAG, "onDisconnected by remote (id: $id)")
                    readSink?.endOfStream()
                    readSink = null
                } else {
                    Log.d(TAG, "onDisconnected by local (id: $id)")
                }
            }
        }

        /**
         * Called when the event channel is first listened to.
         * It receives the [readSink] for sending received data to flutter.
         *
         * @param obj Arbitrary arguments for the stream, can be null.
         * @param eventSink The [EventSink] to which events are sent.
         */
        override fun onListen(obj: Any?, eventSink: EventSink) {
            readSink = eventSink
        }

        /**
         * Called when the connection is closed from the Flutter side.
         *
         * It disconnects the Bluetooth connection, sets the stream handler to null,
         * removes the connection from the `connections` map, and logs the disconnection.
         */
        override fun onCancel(obj: Any?) {
            this.disconnect()

            Executors.newSingleThreadExecutor().execute {
                Handler(Looper.getMainLooper()).post {
                    readChannel.setStreamHandler(null)
                    connections.remove(id)
                    Log.d(
                        TAG,
                        "Disconnected (id: $id)"
                    )
                }
            }
        }
    }
}
