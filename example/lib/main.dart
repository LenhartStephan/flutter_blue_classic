import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_blue_classic/flutter_blue_classic.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _flutterBlueClassicPlugin = FlutterBlueClassic();

  BluetoothAdapterState _adapterState = BluetoothAdapterState.unknown;
  StreamSubscription? _adapterStateSubscription;

  final Set<BluetoothDevice> _scanResults = {};
  StreamSubscription? _scanSubscription;

  bool _isScanning = false;
  StreamSubscription? _scanningStateSubscription;

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  Future<void> initPlatformState() async {
    BluetoothAdapterState adapterState = _adapterState;

    try {
      adapterState = await _flutterBlueClassicPlugin.adapterStateNow;
      _adapterStateSubscription = _flutterBlueClassicPlugin.adapterState.listen((current) {
        if (mounted) setState(() => _adapterState = current);
      });
      _scanSubscription = _flutterBlueClassicPlugin.scanResults.listen((device) {
        if (mounted) setState(() => _scanResults.add(device));
      });
      _scanningStateSubscription = _flutterBlueClassicPlugin.isScanning.listen((isScanning) {
        if (mounted) setState(() => _isScanning = isScanning);
      });
    } catch (e) {
      if (kDebugMode) print(e);
    }

    if (!mounted) return;

    setState(() {
      _adapterState = adapterState;
    });
  }

  @override
  void dispose() {
    _adapterStateSubscription?.cancel();
    _scanSubscription?.cancel();
    _scanningStateSubscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    List<BluetoothDevice> scanResults = _scanResults.toList();

    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('FlutterBluePlus example app'),
        ),
        body: ListView(
          children: [
            ListTile(
              title: const Text("Bluetooth Adapter state"),
              subtitle: const Text("Tap to enable"),
              trailing: Text(_adapterState.name),
              leading: const Icon(Icons.settings_bluetooth),
              onTap: () => _flutterBlueClassicPlugin.turnOn(),
            ),
            const Divider(),
            if (scanResults.isEmpty)
              const Center(child: Text("No devices found yet"))
            else
              for (var result in scanResults)
                ListTile(
                  title: Text("${result.name ?? "???"} (${result.address})"),
                )
          ],
        ),
        floatingActionButton: FloatingActionButton.extended(
          onPressed: () {
            if (_isScanning) {
              _flutterBlueClassicPlugin.stopScan();
            } else {
              _flutterBlueClassicPlugin.startScan();
            }
          },
          label: Text(_isScanning ? "Scanning..." : "Start device scan"),
          icon: Icon(_isScanning ? Icons.bluetooth_searching : Icons.bluetooth),
        ),
      ),
    );
  }
}
