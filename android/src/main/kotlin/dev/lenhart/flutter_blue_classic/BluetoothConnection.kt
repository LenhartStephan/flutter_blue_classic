package dev.lenhart.flutter_blue_classic

import android.Manifest
import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID


abstract class BluetoothConnection(
    private val bluetoothAdapter: BluetoothAdapter
) {

    companion object {
        val DEFAULT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var connectionThread: ConnectionThread? = null

    private fun isConnected(): Boolean {
        return connectionThread != null && !connectionThread!!.requestedClosing
    }

    @Throws(IOException::class)
    @TargetApi(31)
    @RequiresPermission( allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    fun connect(address: String?, uuid: UUID?) {
        if (isConnected()) {
            throw IOException("already connected")
        }
        val device = bluetoothAdapter.getRemoteDevice(address)
            ?: throw IOException("device not found")
        val socket = device.createRfcommSocketToServiceRecord(uuid)
            ?: throw IOException("socket connection not established")

        // Cancel discovery just to be sure
        bluetoothAdapter.cancelDiscovery()
        socket.connect()
        connectionThread = ConnectionThread(socket)
        connectionThread!!.start()
    }

    /// Connects to given device by hardware address (default UUID used)
    @TargetApi(31)
    @RequiresPermission( allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    @Throws(IOException::class)
    fun connect(address: String?) {
        connect(address, DEFAULT_UUID)
    }

    /// Disconnects current session (ignore if not connected)
    fun disconnect() {
        if (isConnected()) {
            connectionThread!!.cancel()
            connectionThread = null
        }
    }

    /// Writes to connected remote device
    @Throws(IOException::class)
    fun write(data: ByteArray?) {
        if (!isConnected()) {
            throw IOException("not connected")
        }
        connectionThread!!.write(data)
    }

    /// Callback for reading data.
    protected abstract fun onRead(data: ByteArray?)

    /// Callback for disconnection.
    protected abstract fun onDisconnected(byRemote: Boolean)


    inner class ConnectionThread internal constructor(private val socket: BluetoothSocket) :
        Thread() {

        private val input: InputStream?
        private val output: OutputStream?
        var requestedClosing = false

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null
            try {
                tmpIn = socket.inputStream
                tmpOut = socket.outputStream
            } catch (e: IOException) {
                e.printStackTrace()
            }
            input = tmpIn
            output = tmpOut
        }

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (!requestedClosing) {
                try {
                    bytes = input!!.read(buffer)
                    onRead(buffer.copyOf(bytes))
                } catch (e: IOException) {
                    break
                }
            }

            // Make sure output stream is closed
            if (output != null) {
                try {
                    output.close()
                } catch (_: Exception) {
                }
            }

            // Make sure input stream is closed
            if (input != null) {
                try {
                    input.close()
                } catch (_: Exception) {
                }
            }

            // Callback on disconnected, with information which side is closing
            onDisconnected(!requestedClosing)
            requestedClosing = true
        }

        fun write(bytes: ByteArray?) {
            try {
                output!!.write(bytes)
                output.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun cancel() {
            if (requestedClosing) {
                return
            }
            requestedClosing = true

            // Flush output buffers before closing
            try {
                output!!.flush()
            } catch (_: Exception) { }

            // Close the connection socket
            try {
                sleep(111)
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

}