package io.rebble.fossil

import android.bluetooth.BluetoothDevice
import java.nio.ByteBuffer

interface BlueIO {
    val isConnected: Boolean

    fun sendPacket(bytes: ByteArray)
    fun readStream(buffer: ByteBuffer, offset: Int, count: Int): Int
    fun targetPebble(device: BluetoothDevice): Boolean
    fun closePebble()
    fun getTarget(): BluetoothDevice?

    fun setOnConnectionChange(f: (Boolean) -> Unit)
}