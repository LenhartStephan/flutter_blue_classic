import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flutter_blue_classic.dart';
import 'flutter_blue_classic_platform_interface.dart';
import 'model/bluetooth_connection.dart';
import 'model/bluetooth_device.dart';

/// An implementation of [FlutterBlueClassicPlatform] that uses method channels.
class MethodChannelFlutterBlueClassic extends FlutterBlueClassicPlatform {
  static const String namespace = "blue_classic";

  /// The method channel used to interact with the native platform
  @visibleForTesting
  final MethodChannel methodChannel = const MethodChannel("$namespace/methods");

  /// The event channel used to get updates for the adapter state
  @visibleForTesting
  final EventChannel adapterStateEventChannel =
      const EventChannel("$namespace/adapterState");

  /// The event channel used to get updates for new discovered devices
  @visibleForTesting
  final EventChannel scanStateEventChannel =
      const EventChannel("$namespace/discoveryState");

  /// The event channel used to get updates for new discovered devices
  @visibleForTesting
  final EventChannel scanResultEventChannel =
      const EventChannel("$namespace/scanResults");

  @override
  Future<bool> isSupported() async =>
      await methodChannel.invokeMethod<bool>("isSupported") ?? false;

  @override
  Future<bool> isEnabled() async =>
      await methodChannel.invokeMethod<bool>("isEnabled") ?? false;

  @override
  Future<BluetoothAdapterState> adapterStateNow() async {
    String? state = await methodChannel.invokeMethod<String>("getAdapterState");
    return BluetoothAdapterState.values.firstWhere((e) => e.name == state,
        orElse: () => BluetoothAdapterState.unknown);
  }

  @override
  Stream<BluetoothAdapterState> adapterState() {
    return adapterStateEventChannel.receiveBroadcastStream().map((event) =>
        BluetoothAdapterState.values.firstWhere((e) => e.name == event,
            orElse: () => BluetoothAdapterState.unknown));
  }

  @override
  Future<List<BluetoothDevice>?> bondedDevices() async {
    return Platform.isAndroid
        ? (await methodChannel.invokeMethod<List>("bondedDevices") ?? [])
            .map((e) => BluetoothDevice.fromMap(e))
            .toList()
        : null;
  }

  @override
  Stream<bool> isScanning() {
    return scanStateEventChannel
        .receiveBroadcastStream()
        .map((event) => event == true ? true : false);
  }

  @override
  Future<bool> isScanningNow() async =>
      await methodChannel.invokeMethod<bool>("isScanningNow") ?? false;

  @override
  Stream<BluetoothDevice> scanResults() {
    return scanResultEventChannel
        .receiveBroadcastStream()
        .map((event) => BluetoothDevice.fromMap(event));
  }

  @override
  void turnOn() {
    if (Platform.isAndroid) methodChannel.invokeMethod("turnOn");
  }

  @override
  void startScan(bool usesFineLocation) {
    methodChannel
        .invokeMethod("startScan", {"usesFineLocation": usesFineLocation});
  }

  @override
  void stopScan() {
    methodChannel.invokeMethod("stopScan");
  }

  @override
  Future<bool> bondDevice(String address) async => Platform.isAndroid
      ? await methodChannel
              .invokeMethod<bool>("bondDevice", {"address": address}) ??
          false
      : false;

  @override
  Future<BluetoothConnection?> connect(String address) async {
    int? id =
        await methodChannel.invokeMethod<int>("connect", {"address": address});
    return id != null
        ? BluetoothConnection.fromConnectionId(id, address)
        : null;
  }

  @override
  Future<void> write(int id, Uint8List data) {
    return methodChannel.invokeMethod<void>("write", {"id": id, "bytes": data});
  }
}
