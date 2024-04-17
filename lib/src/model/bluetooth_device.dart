import '../flutter_blue_classic.dart';

class BluetoothDevice {
  final String address;

  /// Broadcasted friendly name of the device.
  final String? name;

  /// Bonding state of the device.
  final BluetoothBondState bondState;

  BluetoothDevice({required this.address, this.name = "", this.bondState = BluetoothBondState.none});
  factory BluetoothDevice.fromMap(Map map) => BluetoothDevice(
      name: map["name"],
      address: map["address"]!,
      bondState: BluetoothBondState.values
          .firstWhere((e) => e.name == map["bondState"], orElse: () => BluetoothBondState.none));

  @override
  operator ==(Object other) {
    return other is BluetoothDevice && other.address == address;
  }

  @override
  int get hashCode => address.hashCode;
}
