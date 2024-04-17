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
import java.util.concurrent.Executors

/** FlutterBlueClassicPlugin */
class FlutterBlueClassicPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {

  companion object {
    const val TAG: String = "FlutterBlueClassic"
  }

  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var methodChannel : MethodChannel

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
      "connect" -> connect(result, call.argument<String>("address") ?: "")
      "write" -> {
        val id = call.argument<Int>("id")
        val bytes = call.argument<ByteArray>("bytes")
        if (id != null && bytes != null) {
          write(result, id, bytes)
        } else {
          result.error("argumentError", "Not all required arguments were specified", null)
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




  private fun checkBluetoothSupport(): Boolean {
    return context?.packageManager?.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) ?: false
  }

  private fun isBluetoothEnabled(): Boolean {
    return bluetoothAdapter?.isEnabled ?: false
  }

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
          PermissionManager.ERROR_PERMISSION_DENIED,
          String.format(
            "Required permission(s) %s denied",
            deniedPermissions?.joinToString() ?: ""
          ), null
        )

      }
    }
  }

  private fun getAdapterState(): String {
    return bluetoothAdapter?.state?.let { BlueClassicHelper.adapterStateString(it) }
      ?: "unavailable"
  }

  @SuppressLint("MissingPermission")
  private fun getBondedDevices(result: Result) {
    val permissions = ArrayList<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    permissionManager.ensurePermissions(permissions.toTypedArray()) { success: Boolean, deniedPermissions: List<String>? ->
      run {
        if (success) {
          val devices: List<MutableMap<String, Any>>? =
            bluetoothAdapter?.bondedDevices?.map {
              BlueClassicHelper.bluetoothDeviceToMap(it)
            }

          result.success(devices)
        } else {
          result.error(
            PermissionManager.ERROR_PERMISSION_DENIED,
            String.format(
              "Required permission(s) %s denied",
              deniedPermissions?.joinToString() ?: ""
            ), null
          )
        }
      }
    }
  }

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
            PermissionManager.ERROR_PERMISSION_DENIED,
            String.format(
              "Required permission(s) %s denied",
              deniedPermissions?.joinToString() ?: ""
            ), null
          )
        }
      }
    }
  }

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
            PermissionManager.ERROR_PERMISSION_DENIED,
            String.format(
              "Required permission(s) %s denied",
              deniedPermissions?.joinToString() ?: ""
            ), null
          )
        }
      }
    }
  }

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
            PermissionManager.ERROR_PERMISSION_DENIED,
            String.format(
              "Required permission(s) %s denied",
              deniedPermissions?.joinToString() ?: ""
            ), null
          )
        }
      }
    }
  }

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
          result.success(device?.createBond() ?: false)
        } else {
          result.error(
            PermissionManager.ERROR_PERMISSION_DENIED,
            String.format(
              "Required permission(s) %s denied",
              deniedPermissions?.joinToString() ?: ""
            ), null
          )
        }
      }
    }
  }


  @SuppressLint("MissingPermission")
  private fun connect(result: Result, address: String) {
    var permissionSuccess = true
    val permissions = ArrayList<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
      permissions.add(Manifest.permission.BLUETOOTH_SCAN)
    }

    permissionManager.ensurePermissions(permissions.toTypedArray()){ success: Boolean, deniedPermissions: List<String>? ->
      if(!success){
        result.error(
          PermissionManager.ERROR_PERMISSION_DENIED,
          String.format(
            "Required permission(s) %s denied",
            deniedPermissions?.joinToString() ?: ""
          ), null
        )
      }
      permissionSuccess = success
    }

    if(!permissionSuccess) return

    if (!BluetoothAdapter.checkBluetoothAddress(address)) {
      result.error(
        BlueClassicHelper.ERROR_ADDRESS_INVALID,
        "The bluetooth address $address is invalid",
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

    Executors.newSingleThreadExecutor().execute {
      Handler(Looper.getMainLooper()).post {
        try {
          connection.connect(address)
          activityPluginBinding!!.activity.runOnUiThread {
            result.success(id)
          }
        } catch (ex: java.lang.Exception) {
          activityPluginBinding!!.activity.runOnUiThread {
            result.error(
              "connect_error",
              ex.message, null
            )
          }
          connections.remove(id)
        }
      }
    }
  }

  private fun write(result: Result, id: Int, bytes: ByteArray) {
    val connection: BluetoothConnection = connections[id]
    Executors.newSingleThreadExecutor().execute {
      Handler(Looper.getMainLooper()).post {
        connection.write(bytes)
        activityPluginBinding!!.activity.runOnUiThread {
          result.success(null)
        }
      }
    }

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

    override fun onRead(data: ByteArray?) {
      activityPluginBinding?.activity?.runOnUiThread {
        if (readSink != null) {
          readSink?.success(data)
        }
      }
    }

    override fun onDisconnected(byRemote: Boolean) {
      activityPluginBinding?.activity?.runOnUiThread {
        if (byRemote) {
          Log.d(
            TAG,
            "onDisconnected by remote (id: $id)"
          )
          if (readSink != null) {
            readSink?.endOfStream()
            readSink = null
          }
        } else {
          Log.d(
            TAG,
            "onDisconnected by local (id: $id)"
          )
        }
      }
    }

    override fun onListen(obj: Any?, eventSink: EventSink) {
      readSink = eventSink
    }

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
