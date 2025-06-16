## 0.0.6
* **[Fix]** When calling close, the connection was not properly closed. #12

## 0.0.5
* **[Fix]** App could crash, when trying to write while the connection is/will be closed. #10

## 0.0.4
* Added option to specify a uuid during `connect`
* Upgraded Java and Gradle Versions

## 0.0.3
* **[Fix]** When connecting to a non-bonded device: The Android bond dialog would trigger an app not responding #2

## 0.0.2
* Added RSSI value, alias name and device type added to BluetoothDevice
* Scan results und bonded devices are now filtered correctly to only return BL Classic devices
* Example app updated: Tap on a scan result to connect to it and send and receive messages.

## 0.0.1

* Initial release: Scan for and connect to BL Classic devices.
