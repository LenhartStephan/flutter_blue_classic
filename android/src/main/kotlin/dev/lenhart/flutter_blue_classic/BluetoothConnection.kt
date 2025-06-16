package dev.lenhart.flutter_blue_classic

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.util.Log
import androidx.annotation.RequiresPermission
import dev.lenhart.flutter_blue_classic.FlutterBlueClassicPlugin.Companion.TAG
import java.io.IOException
import java.util.UUID


abstract class BluetoothConnection(
    private val bluetoothAdapter: BluetoothAdapter
) {

    companion object {
        private val DEFAULT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var connectionThread: ConnectionThread? = null

    /**
     * Checks if a Bluetooth connection is currently active.
     *
     * @return `true` if a connection is active and not in the process of closing, `false` otherwise.
     */
    private fun isConnected(): Boolean {
        val thread = connectionThread
        return thread != null && !thread.requestedClosing && thread.isAlive && !thread.isInterrupted
    }

    /**
     * Connects to a given bluetooth address.
     *
     * @param address The bluetooth address to connect to.
     * @param uuid The UUID of the service to connect to. If null, the default UUID will be used.
     * @throws IOException if the connection fails, or if the device is already connected.
     * @throws SecurityException if the required bluetooth permissions are not granted.
     */
    @Throws(IOException::class)
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    fun connect(address: String?, uuid: UUID?) {
        if (isConnected()) {
            throw IOException("already connected")
        }
        val device = bluetoothAdapter.getRemoteDevice(address)
            ?: throw IOException("device not found")
        val socket = device.createRfcommSocketToServiceRecord(uuid ?: DEFAULT_UUID)
            ?: throw IOException("socket connection not established")

        // Cancel discovery just to be sure
        bluetoothAdapter.cancelDiscovery()
        socket.connect()
        connectionThread = ConnectionThread(socket).also { it.start() }
    }

    /**
     * Disconnects the current Bluetooth connection.
     *
     * This function checks if a connection is currently active. If it is,
     * it cancels the ongoing connection thread and sets it to null, effectively
     * closing the connection. If no connection is active, this function
     * does nothing.
     */
    fun disconnect() {
        if (isConnected()) {
            connectionThread?.cancel()
            connectionThread = null
        }
    }

    /**
     * Writes data to the connected Bluetooth device.
     *
     * This function attempts to send the provided byte array [data] to the remote device
     * through the active Bluetooth connection.
     *
     * @param data The [ByteArray] containing the data to be written.
     * @throws IOException if the device is not connected, or if an error occurs during the write operation.
     */
    @Throws(IOException::class)
    fun write(data: ByteArray) {
        if (!isConnected()) {
            throw IOException("not connected")
        }
        connectionThread!!.write(data)
    }

    /**
     * Callback for reading data.
     * This function is called when data is received from the connected Bluetooth device.
     * This method should handle the incoming data.
     *
     * @param data The byte array containing the received data.
     */
    protected abstract fun onRead(data: ByteArray)

    /**
     * Callback for disconnection.
     *
     * This method is called when the Bluetooth connection is terminated.
     *
     * @param byRemote `true` if the disconnection was initiated by the remote device,
     *                 `false` if it was initiated locally (e.g., by calling [disconnect]).
     */
    protected abstract fun onDisconnected(byRemote: Boolean)

    private inner class ConnectionThread(private val socket: BluetoothSocket) :
        Thread() {

        /**
         * A flag indicating whether a request to close the connection has been made.
         * This is used to signal the connection thread to terminate its operations gracefully.
         * It is marked as `@Volatile` to ensure that changes made by one thread are immediately
         * visible to other threads.
         */
        @Volatile
        var requestedClosing = false

        /**
         * This method is the main execution loop for the connection thread.
         * It continuously reads data from the input stream and calls the `onRead` callback
         * with the received data.
         * If an error occurs during reading or the connection is closed, the loop terminates.
         * Finally, it calls the `onDisconnected` callback and ensures resources are closed.
         */
        override fun run() {
            try {
                socket.inputStream.use { inputStream ->
                    socket.outputStream.use { _ ->
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while (!requestedClosing && !isInterrupted) {
                            try {
                                bytesRead = inputStream.read(buffer)
                                if (bytesRead < 0) { // End of stream reached
                                    Log.d(TAG, "Remote send EOF. Closing connection.")
                                    break
                                }
                                onRead(buffer.copyOf(bytesRead))
                            } catch (_: IOException) {
                                break
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                onDisconnected(!requestedClosing)
                requestedClosing = true
                closeResources()
            }
        }

        /**
         * Writes the given [bytes] to the output stream.
         *
         * This function will attempt to write the provided byte array to the connected Bluetooth socket's
         * output stream. If the connection is closing, this will log a warning and do nothing.
         *
         * @param bytes The [ByteArray] to be written to the output stream.
         */
        fun write(bytes: ByteArray) {
            if (requestedClosing || isInterrupted || !socket.isConnected) {
                Log.w(TAG, "Attempted to write while closing or disconnected.")
                return
            }
            try {
                socket.outputStream.write(bytes)
                socket.outputStream.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Error during write in ConnectionThread", e)
            }
        }

        /**
         * Cancels the connection.
         *
         * If the connection is already closing, this method does nothing.
         *
         * This method should be called when the connection is no longer needed.
         */
        fun cancel() {
            if (requestedClosing) {
                return
            }
            requestedClosing = true
            interrupt()
            closeResources()
        }


        /**
         * Closes the resources associated with this connection thread.
         *
         * This method attempts to flush the output stream and then close the socket.
         * Any exceptions encountered during these operations are caught and logged,
         * but they do not prevent the subsequent cleanup steps.
         */
        private fun closeResources() {
            try { // Flush output buffers before closing
                socket.outputStream?.flush()
            } catch (_: Exception) { /* Ignored */ }

            try {
                socket.close()
                Log.d(TAG, "Bluetooth socket closed.")
            } catch (e: IOException) {
                Log.e(TAG, "Error closing socket in closeResources", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unknown error during closeResources", e)
            }
        }
    }

}