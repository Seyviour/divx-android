package com.example.divx_demo.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.divx_demo.FirstFragment
import java.lang.ref.WeakReference
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sign

private const val TAG_CONN_MANAGER = "CONNECTION MANAGER"
private const val GATT_MIN_MTU_SIZE = 23
private const val GATT_MAX_MTU_SIZE = 517

class ConnectionManager {

    private var listeners: MutableSet<WeakReference<ConnectionEventListener>> = mutableSetOf()

    private val deviceGattMap = ConcurrentHashMap<BluetoothDevice, BluetoothGatt>()

    private val operationQueue = ConcurrentLinkedQueue<BleOperationType>()

    private var pendingOperation: BleOperationType? = null


    fun servicesOnDevice(device: BluetoothDevice): List<BluetoothGattService>? = deviceGattMap[device]?.services

    fun listenToBondStateChanges(context: Context){
        context.applicationContext.registerReceiver(
            broadcastReciver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )
    }


    fun registerListener(listener: ConnectionEventListener) {
        if (listeners.map { it.get() } .contains(listener)) {return}
        listeners.add(WeakReference(listener))
        listeners = listeners.filter {it.get() != null}.toMutableSet()
        Log.d("ConnectionManager", "Added listener $listener, {listeners.size} listeners total")
    }

    fun unregisterListener(listener: ConnectionEventListener){
        var toRemove: WeakReference<ConnectionEventListener>? = null
        listeners.forEach{
            if (it.get() == listener) {
                toRemove = it
            }
        }
        toRemove?.let {
            listeners.remove(it)
            Log.d("ConnectionManager","Removed listener ${it.get()}, ${listeners.size} listeners total")
        }
    }

    fun connect(device: BluetoothDevice, context: Context){
        if (device.isConnected()) {
            Log.e("ConnectionManager.Connect", "Already connected to ${device.address}!")
        } else {
            enqueueOperation(Connect(device, context.applicationContext))
        }
    }

    fun teardownConnection(device: BluetoothDevice){
        if (device.isConnected()){
            enqueueOperation(Disconnect(device))
        } else {
            Log.e("ConnectionManager.TeardownConnection", "Not connected to ${device.address}, cannot teardown connection!")
        }
    }

    fun readCharacteristic(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic){
        if (device.isConnected() && characteristic.isReadable()) {
            enqueueOperation(CharacteristicRead(device, characteristic.uuid))
        } else if (!characteristic.isReadable()) {
            Log.e("ConnectionManager.ReadCharacteristic", "Attempt to read ${characteristic.uuid} that isn't readable!")
        } else if (!device.isConnected()){
            Log.e("ConnectionManager.ReadCharacteristic", "Not connected to ${device.address}, cannot perform characteristic read")
        }
    }

    fun writeCharacteristic(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray
    ) {
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

            else -> {
                Log.e("ConnectionManager.WriteCharacteristic","Characteristic ${characteristic.uuid} cannot be written to")
                return
            }
        }

        if (device.isConnected()) {
            enqueueOperation(CharacteristicWrite(device, characteristic.uuid, writeType, payload))
        } else {
            Log.e("ConnectionManager.WriteCharacteristic", "Not Connected to ${device.address}")
        }
    }

    fun readDescriptor(device: BluetoothDevice, descriptor: BluetoothGattDescriptor){
        if (device.isConnected() && descriptor.isReadable()) {
            enqueueOperation(DescriptorRead(device, descriptor.uuid))
        } else if (!descriptor.isReadable()) {
            Log.e("ConnectionManager.ReadDescriptor", "Attempt to read ${descriptor.uuid} that isn't readable!")
        } else if (!device.isConnected()){
            Log.e("ConnectionManager.ReadDescriptor", "Not connected to ${device.address}, cannot perform Descriptor read")
        }
    }

    fun writeDescriptor(
        device: BluetoothDevice,
        descriptor: BluetoothGattDescriptor,
        payload: ByteArray
    ) {
        if (device.isConnected() && (descriptor.isWritable() || descriptor.isCccd())) {
            enqueueOperation(DescriptorWrite(device, descriptor.uuid, payload))
        } else if (!device.isConnected()) {
            Log.e("ConnectionManager.WriteDescriptor", "Cannot write to descriptor (unconnected device) ")
        } else if (!descriptor.isWritable() && !descriptor.isCccd()){
            Log.e("ConnectionManager.WriteDescriptor", "Descriptor ${descriptor.uuid} may not be written to")
        }
    }

    fun enableNotifications(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic){
        if (device.isConnected() &&
            (characteristic.isIndicatable()) || characteristic.isNotifiable()
        ){
            enqueueOperation(EnableNotifications(device, characteristic.uuid))
        } else if (!device.isConnected()) {
            Log.e("ConnectionManager.EnableNotifications","Not connected to ${device.address}, cannot enable notifications")
        } else if (!characteristic.isIndicatable() && !characteristic.isNotifiable()) {
            Log.e("ConnectionManager.EnableNotifications","characteristic ${characteristic.uuid} doesn't support notifications/indications")
        }
    }

    fun disableNotifications(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic) {
        if (device.isConnected() && (characteristic.isIndicatable() || characteristic.isNotifiable())){
            enqueueOperation(DisableNotifications(device, characteristic.uuid))
        } else if (!device.isConnected()){
            Log.e("ConnectionManager.DisableNotifications", "Not connected to ${device.address}, cannot disable notifications")
        } else if (!characteristic.isIndicatable() && !characteristic.isNotifiable()) {
            Log.e("ConnectionManager.DisableNotifications", "characteristic ${characteristic.uuid} does not support notifications or indications")
        }
    }

    fun requestMtu(device: BluetoothDevice, mtu: Int) {
        if (device.isConnected()) {
            enqueueOperation(MtuRequest(device, mtu.coerceIn(GATT_MIN_MTU_SIZE, GATT_MAX_MTU_SIZE)))
        } else {
            Log.e(
                "ConnectionManagaer.RequestMtu",
                "Not connected to ${device.address}, cannot request MTU update!"
            )
        }
    }

    @Synchronized
    private fun enqueueOperation(operation: BleOperationType){
        operationQueue.add(operation)
        if (pendingOperation == null){
            doNextOperation()
        }
    }

    @Synchronized
    private fun signalEndOfOperation() {
        Log.d("QueueOps", "End of $pendingOperation")
        pendingOperation = null
        if (operationQueue.isNotEmpty()){
            doNextOperation()
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun doNextOperation() {
        if (pendingOperation != null) {
            Log.e("QueueOps.DoNextOperation", "doNextOperation() called while an operation is pending")
            return
        }

        // the receiver in 'run' here does not seem to serve any purpose
        val operation = operationQueue.poll() ?: run {
            Log.v("QueueOps->DoNextOperation", "Operation queue empty, returning")
            return
        }

        pendingOperation = operation

        if (operation is Connect) {
            with (operation) {
                Log.w("ConnectionManager.DoNextOperation", "Connecting to ${device.address}")
                device.connectGatt(context, false, callback)
                return
            }
        }

        val gatt = deviceGattMap[operation.device]
            ?: this@ConnectionManager.run {
                Log.e("ConnectionManager.doNextOperation", "Not connected to ${operation.device.address}! Aborting $operation operation.")
                signalEndOfOperation()
                return
            }

        when (operation) {
            is Disconnect -> with(operation) {
                Log.i("$TAG_CONN_MANAGER.doNextOperation", "Disconnecting frome ${device.address}")
                gatt.close()
                deviceGattMap.remove(device)
                listeners.forEach {it.get()?.onDisconnect?.invoke(device)}
                signalEndOfOperation()
            }

            is CharacteristicWrite -> with(operation) {
                gatt.findCharacteristic(characteristicUUID)?.let {characteristic ->
                    characteristic.writeType = writeType
                    characteristic.value = payload
                    gatt.writeCharacteristic(characteristic)
                } ?: this@ConnectionManager.run {
                    Log.e("$TAG_CONN_MANAGER.doNextOperation", "Cannot find characteristic ${characteristicUUID}UUID to write to ")
                    signalEndOfOperation()
                }
            }

            is CharacteristicRead -> with(operation) {
                gatt.findCharacteristic(characteristicUUID)?.let { characteristic ->
                    gatt.readCharacteristic(characteristic)
                } ?: this@ConnectionManager.run {
                    Log.e(TAG_CONN_MANAGER, "Cannot find $characteristicUUID to read from")
                    signalEndOfOperation()
                }
            }

            is DescriptorWrite -> with(operation) {
                gatt.findDescriptor(descriptorUUID)?.let {descriptor ->
                    descriptor.value = payload
                    gatt.writeDescriptor(descriptor)
                } ?: this@ConnectionManager.run{
                    Log.d(TAG_CONN_MANAGER, "Cannot find $descriptorUUID to write to")
                    signalEndOfOperation()
                }
            }

            is DescriptorRead -> with(operation) {
                gatt.findDescriptor(descriptorUUID)?.let {descriptor->
                    gatt.readDescriptor(descriptor)
                } ?: this@ConnectionManager.run {
                    Log.d(TAG_CONN_MANAGER, "Cannot find $descriptorUUID to read from")
                    signalEndOfOperation()
                }
            }

            is EnableNotifications -> with(operation){
                gatt.findCharacteristic(characteristicUUID)?.let {characteristic ->
                    val ccdUUID = UUID.fromString(CCC_DESCRIPTOR_UUID)
                    val payload = when {
                        characteristic.isIndicatable() ->
                            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        characteristic.isNotifiable() ->
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        else ->
                            error("${characteristic.uuid} doesn't support notifications/indications")
                    }

                    characteristic.getDescriptor(ccdUUID)?.let{ cccDescriptor->
                        if (!gatt.setCharacteristicNotification(characteristic, true)) {
                            Log.e(TAG_CONN_MANAGER, "setCharacteristicNotification failed for ${characteristic.uuid}")
                            signalEndOfOperation()
                            return
                        }

                        cccDescriptor.value = payload
                        gatt.writeDescriptor(cccDescriptor)
                    }?: this@ConnectionManager.run {
                        Log.e(TAG_CONN_MANAGER, "${characteristic.uuid} does not contain the CCC descriptor")
                        signalEndOfOperation()
                    }
                } ?: this@ConnectionManager.run{
                    Log.e(TAG_CONN_MANAGER, "cannot find $characteristicUUID to enable notifications.")
                    signalEndOfOperation()
                }
            }

            is DisableNotifications -> with(operation) {
                gatt.findCharacteristic(characteristicUUID)?.let {characteristic ->
                    val cccdUUID = UUID.fromString(CCC_DESCRIPTOR_UUID)
                    characteristic.getDescriptor(cccdUUID)?.let {cccDescriptor ->
                        if (!gatt.setCharacteristicNotification(characteristic, false)){
                            Log.e(TAG_CONN_MANAGER, "setCharacteristicNotification failed for ${characteristic.uuid}")
                            signalEndOfOperation()
                            return
                        }

                        cccDescriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(cccDescriptor)
                    }?: this@ConnectionManager.run {
                        Log.e(TAG_CONN_MANAGER, "characteristic ${characteristic.uuid} does not contain the CCC descriptor")
                        signalEndOfOperation()
                    }
                }?: this@ConnectionManager.run {
                    Log.e(TAG_CONN_MANAGER, "Cannot find $characteristicUUID! to disable notifications on")
                    signalEndOfOperation()
                }
            }

            is MtuRequest -> with(operation) {
                gatt.requestMtu(mtu)
            }

            else -> return
        }
    }


    private val callback = object: BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            val deviceAddress = gatt.device.address

            if(status == BluetoothGatt.GATT_SUCCESS){
                if (newState == BluetoothProfile.STATE_CONNECTED){
                    Log.d(TAG_CONN_MANAGER, "onConnectionStateChange: connected to $deviceAddress")
                    deviceGattMap[gatt.device] = gatt
                    Handler(Looper.getMainLooper()).post{
                        gatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG_CONN_MANAGER, "onConnectionStateChange: disconnected from $deviceAddress")
                    teardownConnection(gatt.device)
                }
            } else {
                Log.e(TAG_CONN_MANAGER, "onConnectionStateChange: status $status encountered for $deviceAddress!")
                if (pendingOperation is Connect) {
                    signalEndOfOperation()
                }
                teardownConnection(gatt.device)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                if (status == BluetoothGatt.GATT_SUCCESS){
                    Log.i(TAG_CONN_MANAGER, "Discovered ${services.size} services for ${device.address}")
                    printGattTable()
                    requestMtu(device, GATT_MAX_MTU_SIZE)
                    listeners.forEach{ it.get()?.onConnectionSetupComplete?.invoke(this)}
                } else {
                    Log.e(TAG_CONN_MANAGER, "Service discovery failed due to status $status")
                    teardownConnection(gatt.device)
                }
            }

            if (pendingOperation is Connect) {
                signalEndOfOperation()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG_CONN_MANAGER, "ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
            listeners.forEach{it.get()?.onMtuChanged?.invoke(gatt.device, mtu)}

            if (pendingOperation is MtuRequest) {
                signalEndOfOperation()
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(TAG_CONN_MANAGER, "Read characteristic $uuid | value: ${value.toHexString()}")
                        listeners.forEach{it.get()?.onCharacteristicRead?.invoke(gatt.device, this)}
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.d(TAG_CONN_MANAGER, "Read not permitted for $uuid!")
                    }
                    else -> {
                        Log.d(TAG_CONN_MANAGER, "Characteristic read failed for $uuid, error: $status")
                    }
                }
            }

            if (pendingOperation is CharacteristicRead) {
                signalEndOfOperation()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic){
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(TAG_CONN_MANAGER, "Wrote to characteristic $uuid | value: ${value.toHexString()}")
                        listeners.forEach{it.get()?.onCharacteristicWrite?.invoke(gatt.device, this)}
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e(TAG_CONN_MANAGER, "Write not permitted for $uuid!")
                    }
                    else -> {
                        Log.e(TAG_CONN_MANAGER, "Characteristic write failed for $uuid, error: $status")
                    }
                }

            }

            if(pendingOperation is CharacteristicWrite){
                signalEndOfOperation()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
           with (characteristic){
               Log.i(TAG_CONN_MANAGER, "Characteristic $uuid changed | value: ${value.toHexString()}")
               listeners.forEach{it.get()?.onCharacteristicChanged?.invoke(gatt.device, this)}
           }
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
//            super.onDescriptorRead(gatt, descriptor, status, value)
            with(descriptor) {
                when(status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(TAG_CONN_MANAGER, "Read descriptor $uuid | value: ${value.toHexString()}")
                        listeners.forEach {it.get()?.onDescriptorRead?.invoke(gatt.device, this)}
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e(TAG_CONN_MANAGER, "Read not permitted for $uuid!")
                    }
                    else -> {
                        Log.e(TAG_CONN_MANAGER, "Descriptor read failed for $uuid, error: $status")
                    }
                }
            }
            if(pendingOperation is DescriptorRead){
                signalEndOfOperation()
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            with(descriptor) {
                when(status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(TAG_CONN_MANAGER, "Wrote to desciptor $uuid | value ${value.toHexString()}")

                        if (isCccd()){
                            onCccdWrite(gatt, value, characteristic)
                        }
                        else {
                            listeners.forEach{it.get()?.onDescriptorWrite?.invoke(gatt.device, this)}
                        }
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e(TAG_CONN_MANAGER, "Write not permitted for descriptor $uuid!")
                    }
                    else -> {
                        Log.e(TAG_CONN_MANAGER, "Descriptor write failed for $uuid, error: $status")
                    }
                }
            }

            if(descriptor.isCccd() && (pendingOperation is EnableNotifications || pendingOperation is DisableNotifications)){
                signalEndOfOperation()
            } else if (!descriptor.isCccd() && pendingOperation is DescriptorWrite){
                signalEndOfOperation()
            }
        }

        private fun onCccdWrite(
            gatt: BluetoothGatt,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic
        ){
            val charUUID = characteristic.uuid

            val notificationsEnabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) or
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)

            val notificationsDisabled = value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)

            when {
                notificationsEnabled -> {
                    Log.w(TAG_CONN_MANAGER, "Notifications or indications enabled on $charUUID")
                    listeners.forEach {
                        it.get()?.onNotificationsEnabled?.invoke(gatt.device, characteristic)
                    }
                }

                notificationsDisabled -> {
                    Log.w(TAG_CONN_MANAGER, "Notifications or indications disabled on $charUUID")
                    listeners.forEach {
                        it.get()?.onNotificationsDisabled?.invoke(gatt.device, characteristic)
                    }
                }

                else -> {
                    Log.e(TAG_CONN_MANAGER, "UNEXPECTED VALUE ${value.toHexString()} on CCCD of $charUUID")
                }
            }
        }
    }

    private val broadcastReciver = object : BroadcastReceiver(){
        override fun onReceive(context: Context, intent: Intent) {
            with(intent){
                if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED){
                    val device = getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val previousBondState = getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
                    val bondState = getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    val bondTransition = "${previousBondState.toBondStateDescription()} to ${bondState.toBondStateDescription()}"
                    Log.w(TAG_CONN_MANAGER, "${device?.address} bond state changed | $bondTransition")
                }
            }
        }

        private fun Int.toBondStateDescription(): String = when(this){
            BluetoothDevice.BOND_BONDED -> "BONDED"
            BluetoothDevice.BOND_BONDING -> "BONDING"
            BluetoothDevice.BOND_NONE -> "NOT BONDED"
            else -> "ERROR: $this"
        }
    }

    private fun BluetoothDevice.isConnected() = deviceGattMap.containsKey(this)

}