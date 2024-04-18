# flutter_blue_classic

Flutter_blue_classic is a flutter plugin for communicating with bluetooth classic devices.
If you want to use an Bluetooth low energy (BLE) device, you might want to
consider [flutter_blue_plus](https://pub.dev/packages/flutter_blue_plus).

<!-- TOC -->
* [flutter_blue_classic](#flutterblueclassic)
  * [A note on iOS](#a-note-on-ios)
  * [Getting Started](#getting-started)
    * [minSdkVersion](#minsdkversion)
    * [Permissions](#permissions)
      * [Without location access](#without-location-access)
      * [With location access](#with-location-access)
  * [Reference](#reference)
    * [FlutterBlueClassic](#flutterblueclassic-1)
    * [BluetoothConnection](#bluetoothconnection)
  * [Acknowledgement](#acknowledgement)
<!-- TOC -->

## A note on iOS

This plugin is currently only compatible with Android. iOS does not provide a similar interface for
Bluetooth Classic like Android. On iOS your BL device must
be [MFi (Made for iPod/iPhone)](https://mfi.apple.com/en/faqs)
certified and you will have to use
the [External Accessory Framework](https://developer.apple.com/documentation/externalaccessory/).
Since this is out of the scope of this package, you will have to implement this yourself.

## Getting Started

### minSdkVersion

This package is only compatible with Android SDK 21 or higher. Please set your minSdkVersion in the
android/app/build.gradle accordingly.

### Permissions

As the Android permission requirements can differ from case to case, this package does not define
any
permissions in it's Manifest. It is therefore necessary to declare them yourself. For detailed
information, take a look at
the [Android Developer Documentation on Permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions).

Below are two common presets:

#### Without location access

In the **android/app/src/main/AndroidManifest.xml** add:

```xml
<!-- Permissions for Android 12 or above -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

    <!-- legacy for Android 11 or lower -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />

    <!-- legacy for Android 9 or lower -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" android:maxSdkVersion="28" />
```

#### With location access

In the **android/app/src/main/AndroidManifest.xml** add:

```xml
<!-- Permissions for Android 12 or above -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

    <!-- legacy for Android 11 or lower -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />

    <!-- legacy for Android 9 or lower -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" android:maxSdkVersion="28" />
```

Then pass the `accessFineLocation` parameter when initializing the plugin:

```dart

final blueClassic = FlutterBlueClassic(usesFineLocation: true);
```

## Reference

### FlutterBlueClassic
|                 | Description                                                        |
|:----------------|:-------------------------------------------------------------------|
| isSupported     | Checks whether the device supports Bluetooth                       |
| isEnabled       | Checks whether Bluetooth is enabled                                |
| adapterStateNow | Current state of the bluetooth adapter                             |
| adapterState    | Stream of on, off and intermediary states of the bluetooth adapter |
| bondedDevices   | Returns the list of bonded (paired) devices                        |
| startScan       | Starts a scan for Bluetooth devices                                |
| stopScan        | Stop an existing scan for Bluetooth devices                        |
| isScanningNow   | Checks whether the Bluetooth adapter is currently scanning.        |
| isScanning      | Stream whether the device is scanning for BL devices.              |
| scanResults     | Stream of found devices during scan                                |
| turnOn          | Requests to turns the bluetooth adapter on                         |
| bondDevice      | Requests to create a bond with a bluetooth device                  |
| connect         | Tries to connect with a bluetooth device                           |

### BluetoothConnection
|             | Description                                                                                        |
|:------------|:---------------------------------------------------------------------------------------------------|
| input       | The stream of data received from the connected BL device                                           |
| output      | A stream sink to send byte data to the connected BL device                                         |
| writeString | A helper to send utf-8 encoded strings to the remote device                                        |
| isConnected | Indicates whether the connection is still open.                                                    |
| dispose     | This should be called, when the Connection is no longer needed and will call `finish` (see below). |
| finish      | This will wait for any ongoing writes to be finished and gracefully close the connection.          |
| close       | This will immediately close the connection.                                                        |

## Acknowledgement
flutter_blue_classic is loosely based on flutter_bluetooth_serial.