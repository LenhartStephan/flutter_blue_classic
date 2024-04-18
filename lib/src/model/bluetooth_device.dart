import '../flutter_blue_classic.dart';

class BluetoothDevice {
  /// The Bluetooth adress of the remote device.
  final String address;

  /// The Broadcasted friendly name of the device.
  final String? name;

  /// The Friendly name of the device or, if set the locally modifiable alias name.
  ///
  /// Note: The alias is not available for Android device below API level 30
  final String? alias;

  /// The RSSI (signal strength) value of the remote device as reported by the Bluetooth hardware.
  final int? rssi;

  /// Indicates the device type.
  final BluetoothDeviceType type;

  /// Bonding state of the device.
  final BluetoothBondState bondState;

  BluetoothDevice._(
      {required this.address,
      this.name = "",
      this.alias,
      this.type = BluetoothDeviceType.unknown,
      this.rssi,
      this.bondState = BluetoothBondState.none});
  factory BluetoothDevice.fromMap(Map map) => BluetoothDevice._(
      name: map["name"],
      alias: map["alias"],
      address: map["address"]!,
      type: BluetoothDeviceType.values.firstWhere(
          (e) => e.name == map["deviceType"],
          orElse: () => BluetoothDeviceType.unknown),
      rssi: map["rssi"],
      bondState: BluetoothBondState.values.firstWhere(
          (e) => e.name == map["bondState"],
          orElse: () => BluetoothBondState.none));

  @override
  operator ==(Object other) {
    return other is BluetoothDevice && other.address == address;
  }

  @override
  int get hashCode => address.hashCode;
}

enum BluetoothDeviceType { classic, dual, unknown }
