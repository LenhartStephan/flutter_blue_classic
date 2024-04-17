import 'flutter_blue_classic_platform_interface.dart';
import 'model/bluetooth_connection.dart';
import 'model/bluetooth_device.dart';

class FlutterBlueClassic {
  final _instance = FlutterBlueClassicPlatform.instance;

  /// Indicates whether your app is using the fine location feature on android. For iOS apps this can be ignored.
  ///
  /// Note that you have to either define the ACCESS_FINE_LOCATION permission and set this [_usesFineLocation] to true or the
  /// usesPermissionFlags="neverForLocation" on the relevant <uses-permission> manifest tag and set this to false
  final bool usesFineLocation;

  FlutterBlueClassic({this.usesFineLocation = false});

  /// Checks whether the device supports Bluetooth
  Future<bool> get isSupported => _instance.isSupported();

  /// Checks whether Bluetooth is enabled on the device
  Future<bool> get isEnabled => _instance.isEnabled();

  /// Returns the current adapter state
  Future<BluetoothAdapterState> get adapterStateNow => _instance.adapterStateNow();

  /// Returns an event stream for all adapter state changes
  Stream<BluetoothAdapterState> get adapterState => _instance.adapterState();

  /// On Android this returns the list of bonded devices.
  /// On iOS/macOS this will always return null
  Future<List<BluetoothDevice>?> get bondedDevices => _instance.bondedDevices();

  /// This will attempt to start scanning for Bluetooth devices.
  void startScan() => _instance.startScan(usesFineLocation);

  /// This will attempt to stop scanning for Bluetooth devices.
  void stopScan() => _instance.stopScan();

  /// Returns an event stream about whether the device is currently scanning for Bluetooth devices
  Stream<bool> get isScanning => _instance.isScanning();

  /// Checks whether the device is currently scanning for bluetooth devices
  Future<bool> get isScanningNow => _instance.isScanningNow();

  /// Returns an event stream for every bluetooth device found during scan
  Stream<BluetoothDevice> get scanResults => _instance.scanResults();

  /// On Android this will attempt to enable Bluetooth. On iOS this is a no-op.
  void turnOn() => _instance.turnOn();

  /// On Android this creates a bond to the device with the given address.
  /// On iOS/macOS this is a no-op and will always return false
  ///
  /// Returns whether the bond was successfully created.
  Future<bool> bondDevice(String address) => _instance.bondDevice(address);

  /// Creates a connection to the device with the given address.
  Future<BluetoothConnection?> connect(String address) => _instance.connect(address);
}

/// State of the Bluetooth adapter
enum BluetoothAdapterState { unknown, unavailable, unauthorized, turningOn, on, turningOff, off }

/// Bonding state on a device
enum BluetoothBondState { none, bonding, bonded }
