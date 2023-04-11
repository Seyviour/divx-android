package com.example.divx_demo.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import java.util.UUID

sealed class BleOperationType {
    abstract val device: BluetoothDevice
}

data class Connect(override val device: BluetoothDevice, val context: Context): BleOperationType()


data class Disconnect(override val device: BluetoothDevice): BleOperationType()

data class CharacteristicWrite(
    override val device: BluetoothDevice,
    val characteristicUUID: UUID,
    val writeType: Int,
    val payload: ByteArray
): BleOperationType() {
    override fun equals(other: Any?): Boolean{
        if (this == other) return true
        if (javaClass != other?.javaClass) return false

        other as CharacteristicWrite

        if (device != other.device) return false
        if (characteristicUUID != other.characteristicUUID) return false
        if (writeType != other.writeType) return false
        if (!payload.contentEquals((other.payload))) return false

        return true
    }

    override fun hashCode(): Int {
        var result = device.hashCode()
        result = 31 * result + characteristicUUID.hashCode()
        result = 31 * result + writeType
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

data class CharacteristicRead(
    override val device: BluetoothDevice,
    val characteristicUUID: UUID
) : BleOperationType()

data class DescriptorWrite(
    override val device: BluetoothDevice,
    val descriptorUUID: UUID,
    val payload: ByteArray
): BleOperationType() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DescriptorWrite

        if (device != other.device) return false
        if (descriptorUUID != other.descriptorUUID) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = device.hashCode()
        result = 31 * result + descriptorUUID.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

data class DescriptorRead(
    override val device: BluetoothDevice,
    val descriptorUUID: UUID,
) : BleOperationType()

data class EnableNotifications(
    override val device: BluetoothDevice,
    val characteristicUUID: UUID
): BleOperationType()

data class DisableNotifications(
    override val device: BluetoothDevice,
    val characteristicUUID: UUID
): BleOperationType()

data class MtuRequest(
    override val device: BluetoothDevice,
    val mtu: Int
) : BleOperationType()