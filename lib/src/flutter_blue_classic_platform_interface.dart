import 'dart:typed_data';

import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'flutter_blue_classic.dart';
import 'flutter_blue_classic_method_channel.dart';
import 'model/bluetooth_connection.dart';
import 'model/bluetooth_device.dart';

abstract class FlutterBlueClassicPlatform extends PlatformInterface {
  /// Constructs a BlueClassicPlatform.
  FlutterBlueClassicPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterBlueClassicPlatform _instance =
      MethodChannelFlutterBlueClassic();

  /// The default instance of [BlueClassicPlatform] to use.
  ///
  /// Defaults to [MethodChannelBlueClassic].
  static FlutterBlueClassicPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [BlueClassicPlatform] when
  /// they register themselves.
  static set instance(FlutterBlueClassicPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Checks whether the device supports Bluetooth
  Future<bool> isSupported() {
    throw UnimplementedError('isSupported() has not been implemented.');
  }

  /// Checks whether Bluetooth is enabled on the device
  Future<bool> isEnabled() {
    throw UnimplementedError('isEnabled() has not been implemented.');
  }

  /// Returns the current adapter state
  Future<BluetoothAdapterState> adapterStateNow() {
    throw UnimplementedError('adapterStateNow() has not been implemented.');
  }

  /// Returns an event stream for the scan state
  Stream<bool> isScanning() {
    throw UnimplementedError('isScanning() has not been implemented.');
  }

  /// Returns whether the device is currently scanning
  Future<bool> isScanningNow() {
    throw UnimplementedError('isScanningNow() has not been implemented.');
  }

  /// Returns an event stream for every bluetooth device found during scan
  Stream<BluetoothDevice> scanResults() {
    throw UnimplementedError('scanResults() has not been implemented.');
  }

  /// Returns an event stream for all adapter state changes
  Stream<BluetoothAdapterState> adapterState() {
    throw UnimplementedError('adapterState() has not been implemented.');
  }

  /// Returns the list of bonded devices
  Future<List<BluetoothDevice>?> bondedDevices() {
    throw UnimplementedError('bondedDevices() has not been implemented.');
  }

  /// Turns bluetooth on
  void turnOn() {
    throw UnimplementedError('turnOn() has not been implemented.');
  }

  /// Starts scanning for bluetooth devices
  void startScan(bool usesFineLocation) {
    throw UnimplementedError('startScan() has not been implemented.');
  }

  /// Stops scanning for bluetooth devices
  void stopScan() {
    throw UnimplementedError('stopScan() has not been implemented.');
  }

  /// Creates a bond to the device with the given address.
  ///
  /// Returns whether the bond was successfully created.
  Future<bool> bondDevice(String address) {
    throw UnimplementedError('bondDevice() has not been implemented.');
  }

  /// Creates a connection to the device with the given address.
  Future<BluetoothConnection?> connect(String address) {
    throw UnimplementedError('connect() has not been implemented.');
  }

  /// Writes [data] to a given connection [id]
  Future<void> write(int id, Uint8List data) {
    throw UnimplementedError('write() has not been implemented.');
  }
}
