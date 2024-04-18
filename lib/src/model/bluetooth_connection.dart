import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../flutter_blue_classic_method_channel.dart';
import '../flutter_blue_classic_platform_interface.dart';

/// Represents an ongoing Bluetooth connection to a remote device.
class BluetoothConnection {
  BluetoothConnection.fromConnectionId(this._id, this.address)
      : _readChannel = EventChannel(
            "${MethodChannelFlutterBlueClassic.namespace}/connection/$_id") {
    _readStreamController = StreamController<Uint8List>();

    _readStreamSubscription =
        _readChannel.receiveBroadcastStream().cast<Uint8List>().listen(
              _readStreamController.add,
              onError: _readStreamController.addError,
              onDone: close,
            );

    input = _readStreamController.stream;
    output = BluetoothStreamSink(_id);
  }

  /// This ID identifies the real BluetoothConenction object on platform side code.
  final int _id;

  /// The Bluetooth-Adress of the remote device.
  final String address;

  final EventChannel _readChannel;
  late StreamSubscription<Uint8List> _readStreamSubscription;
  late StreamController<Uint8List> _readStreamController;

  /// Stream sink used to read from the remote Bluetooth device
  ///
  /// `.onDone` can be used to detect when remote device closes the connection.
  ///
  /// You should use some encoding to receive string in your `.listen` callback, for example `ascii.decode(data)` or `utf8.encode(data)`.
  Stream<Uint8List>? input;

  /// Stream sink used to write to the remote Bluetooth device
  ///
  /// If you want to send strings, consider using your own encoding (e.g. ascii) or use the helper [writeString]
  late BluetoothStreamSink output;

  /// This is a helper function, for send text to the output. It will encode your supplied [text] in utf8.
  ///
  /// For byte data use [output.add(data)].
  void writeString(String text) => output.add(utf8.encode(text));

  /// Specifies whether the connection is currently open.
  bool get isConnected => output.isConnected;

  /// Should be called to make sure the connection is closed and resources are freed (sockets/channels).
  void dispose() => finish();

  /// Closes the connection immediately and does not wait for any ongoing writes
  Future<void> close() {
    return Future.wait([
      output.close(),
      _readStreamSubscription.cancel(),
      (!_readStreamController.isClosed)
          ? _readStreamController.close()
          : Future.value()
    ], eagerError: true);
  }

  /// Closes the connection gracefully and waits for ongoing writes to be finished
  Future<void> finish() async {
    await output.allSent;
    close();
  }
}

/// Helper class for sending data.
class BluetoothStreamSink
    implements EventSink<Uint8List>, StreamConsumer<Uint8List> {
  final int _id;

  final _instance = FlutterBlueClassicPlatform.instance;

  /// Specifies whether the connection is still open.
  ///
  /// Sending data when this is false will throw an error
  bool isConnected = true;

  /// Chain of features, the variable represents last of the futures.
  Future<void> _chainedFutures = Future.value();

  BluetoothStreamSink(this._id);

  /// Adds raw bytes to the output sink.
  ///
  /// The data is sent almost immediately, but if you want to be sure,
  /// there is `this.allSent` that provides future which completes when
  /// all added data are sent.
  ///
  /// You should use some encoding to send string, for example `ascii.encode('Hello!')` or `utf8.encode('Cześć!)`.
  ///
  /// Might throw `StateError("Not connected!")` if not connected.
  @override
  void add(Uint8List data) {
    if (!isConnected) {
      throw StateError("Not connected!");
    }

    _chainedFutures = _chainedFutures.then((_) async {
      if (!isConnected) {
        throw StateError("Not connected!");
      }
      await _instance.write(_id, data);
    }).catchError((e) {
      if (kDebugMode) print(e);
      close();
    });
  }

  /// Unsupported - this output sink cannot pass errors to platform code.
  @override
  void addError(Object error, [StackTrace? stackTrace]) {
    throw UnsupportedError(
        "BluetoothConnection output (response) sink cannot receive errors!");
  }

  @override
  Future addStream(Stream<Uint8List> stream) => Future(() async {
        final completer = Completer();
        stream.listen(add).onDone(completer.complete);
        await completer.future;
        await _chainedFutures;
      });

  @override
  Future close() {
    isConnected = false;
    return Future.value();
  }

  /// Returns a future which is completed when the sink sent all added data,
  /// instead of only if the sink got closed.
  ///
  /// Might fail with an error in case if something occurred while sending the data.
  /// Typical error could be `StateError("Not connected!")` which could happen
  /// if disconnected in middle of sending (queued) data.
  Future get allSent => Future(() async {
        // Simple `await` can't get job done here, because the `_chainedFutures` member
        // in one access time provides last Future, then `await`ing for it allows the library
        // user to add more futures on top of the waited-out Future.
        Future lastFuture;
        do {
          lastFuture = _chainedFutures;
          await lastFuture;
        } while (lastFuture != _chainedFutures);

        _chainedFutures = Future.value();
      });
}
