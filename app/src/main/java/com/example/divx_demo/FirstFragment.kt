package com.example.divx_demo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.divx_demo.databinding.FragmentFirstBinding
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */

private const val TAG_SCAN_FRAGMENT = "DIVX_SCAN_FRAGMENT"

private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val RUNTIME_PERMISSION_REQUEST_CODE = 2

private const val DIVX_SERVICE_UUID = "A"
private const val XCODE_CHAR_UUID = "B"

@SuppressLint("MissingPermission")
class FirstFragment : Fragment() {

    private val scanResultAdapter: ScanResultAdapter by lazy {
        ScanResultAdapter(scanResults) {result ->
            if (isScanning){
                stopBleScan()
            }
            with(result.device){
                Log.w("ScanResultAdapter", "Connecting to $address")
                connectGatt(context, false, gattCallback)
            }
        }
    }

    private val operationQueue = ConcurrentLinkedQueue<BleOperationType>()
    private var pendingOperation: FirstFragment.BleOperationType? = null


    private lateinit var bluetoothGatt: BluetoothGatt

    private val gattCallback = object: BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS){
                if (newState == BluetoothProfile.STATE_CONNECTED){
                    Log.w(TAG_SCAN_FRAGMENT, "Successfully connected to $deviceAddress")
                    bluetoothGatt = gatt
                    Handler(Looper.getMainLooper()).post{
                        bluetoothGatt?.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_CONNECTED){
                    Log.w(TAG_SCAN_FRAGMENT, "Successfully disconnected from $deviceAddress")
                    gatt.close()
                }
            }else {
                Log.w("BluetoothGattCallback", "Error $status encountered for $deviceAddress ! Disconnection...")
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            with (gatt){
                Log.w("BluetoothGattCallback", "Discovered ${services.size} services for ${device.address}")
                printGattTable()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.w(TAG_SCAN_FRAGMENT, "ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            with (characteristic) {
                when(status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Read characteristic $uuid:\n${value.toHexString()}")
                        }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Read not permitted for $uuid")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status")
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)

            with(characteristic){
                Log.i("BluetoothGattCallback", "Characteristic $uuid changed | value: ${value.toHexString()}")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            with(characteristic) {
                when(status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Wrote to characteristic $uuid | value: ${value.toHexString()}")
                    }
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        Log.e("BluetoothGattCallback", "Write exceeded connection ATT MTU!")
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Write not permitted for $uuid!")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic write failed for $uuid, error:$status")
                    }
                }
            }
        }
    }

    fun listenToBondStateChanges(context: Context){
        context.applicationContext.registerReceiver(broadcastReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }

    private val broadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            with(intent) {
                if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED){
                    val device = getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val previousBondSate = getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
                    val bondState = getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)

                    val bondTransition = "${previousBondSate.toBondStateDescription()} to " + bondState.toBondStateDescription()
                    Log.w("Bond state changed", "${device?.address} bond state changed | $bondTransition")
                }
            }
        }

        private fun Int.toBondStateDescription() = when(this) {
            BluetoothDevice.BOND_BONDED -> "BONDED"
            BluetoothDevice.BOND_BONDING -> "BONDING"
            BluetoothDevice.BOND_NONE -> "NOT BONDED"
            else -> "ERROR: $this"
        }
    }


    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray){
        val writeType = when{
            characteristic.isWritable() ->
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> error("Characteristic ${characteristic.uuid} cannot be written to")
        }

        bluetoothGatt?.let {gatt ->
            characteristic.writeType = writeType
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        } ?: error("Not connected to a BLE device!")
    }

    fun ByteArray.toHexString(): String = joinToString(separator ="", prefix = "0x") {String.format("%02X", it)}

    private fun readXCode() {
        val divxServiceUUID = UUID.fromString(DIVX_SERVICE_UUID)
        val xCodeCharUUID = UUID.fromString(XCODE_CHAR_UUID)
        val currXcodeChar = bluetoothGatt.getService(divxServiceUUID)?.getCharacteristic(xCodeCharUUID)
        if (currXcodeChar?.isReadable() == true){
            bluetoothGatt.readCharacteristic(currXcodeChar)
        }
    }


    fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray){
        bluetoothGatt?.let {gatt ->
            descriptor.value = payload
            gatt.writeDescriptor(descriptor)
        } ?: error("Not connected to a BLE device!")
    }

    fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val ccdUUID = UUID.fromString(CCC_DESCRIPTOR_UUID)
        val payload = when{
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> {Log.e("ConnectionManager", "${characteristic.uuid} doesn't support notifications/indications")
                    return
            }
        }

        characteristic.getDescriptor(ccdUUID)?.let { cccDescriptor ->
            if (bluetoothGatt?.setCharacteristicNotification(characteristic, true) == false){
                Log.e("ConnectionManager", "setCharacteristicNotification failed for ${characteristic.uuid}")
                return
            }
            writeDescriptor(cccDescriptor, payload)
        } ?: Log.e("ConnectionManager", "${characteristic.uuid} doesn't contain the CCC descriptor!")
    }

    fun disableNotifications(characteristic: BluetoothGattCharacteristic){
        if (!characteristic.isNotifiable() && !characteristic.isIndicatable()) {
            Log.e("ConnectionManager", "${characteristic.uuid} doesn't support indications/notifications")
            return
        }
        val cccdUUID = UUID.fromString(CCC_DESCRIPTOR_UUID)
        characteristic.getDescriptor(cccdUUID)?.let{ cccDescriptor ->
            if (bluetoothGatt?.setCharacteristicNotification(characteristic, false) == false){
                Log.e("ConnectionManager", "Disable notification failed for ${characteristic.uuid}")
            }
            writeDescriptor(cccDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        } ?: Log.e("ConnectionManager", "${characteristic.uuid} doesn't contain the CCC descriptor!")
    }



    private val bluetoothAdapter: BluetoothAdapter by lazy{
        val bluetoothManager = activity!!.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private var isScanning = false


    @SuppressLint("MissingPermission")
    fun stopBleScan(){
        bleScanner.stopScan(scanCallback)
        isScanning = false
        binding.buttonScan.text = "Start Scanning"
    }

    private var _binding: FragmentFirstBinding? = null

    private val scanResults = mutableListOf<ScanResult>()

    private val scanCallback = object: ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val indexQuery = scanResults.indexOfFirst {
                it.device.address == result?.device?.address
            }
            if (result!=null && indexQuery != -1 && result != scanResults[indexQuery]){
                scanResults[indexQuery] = result
                scanResultAdapter.notifyItemChanged(indexQuery)
            } else {
                with (result?.device){
//                    Log.i("ScanCallback", "Found BLE device, Name: ${name ?: "Unnamed"}, address: $address")
                }
                scanResults.add(result!!)
                scanResultAdapter.notifyItemInserted(scanResults.size - 1)
            }

        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("ScanCallBack", "onScanFailed: code $errorCode")
        }

    }

    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun Context.hasRequiredRuntimePermissions(): Boolean {

        var retval = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                    hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        Log.d(TAG_SCAN_FRAGMENT, "PERMISSIONS CHECK: ${retval.toString()} ")
        return retval
    }

    private fun requestLocationPermission() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(activity as Context)
        builder.setMessage("Starting from Android 6, the system requires apps to be granted location access in order to scan for BLE devices")
            .setTitle("Location Permission Required")
            .setPositiveButton("okay"){ _, _ ->
                ActivityCompat.requestPermissions(
                    activity!!,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    RUNTIME_PERMISSION_REQUEST_CODE
                )
            }.show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            RUNTIME_PERMISSION_REQUEST_CODE -> {
                val containsPermanentDenial = permissions.zip(grantResults.toTypedArray()).any {
                    it.second == PackageManager.PERMISSION_DENIED && !ActivityCompat.shouldShowRequestPermissionRationale(activity!!,it.first)
                }
                val containsDenial = grantResults.any{
                    it == PackageManager.PERMISSION_DENIED
                }
                val allGranted = grantResults.all{it == PackageManager.PERMISSION_GRANTED}

                when {
                    containsPermanentDenial -> {
                        TODO("HANDLE PERMANENT DENIAL")
                    }
                    containsDenial -> {
                        requestRelevantRuntimePermissions()
                    }
                    allGranted && (activity!! as Context).hasRequiredRuntimePermissions() -> {
                        startBleScan()
                    }
                    else -> {
                        // Unexpected scenario
                        activity!!.recreate()
                    }
                }
            }
        }
    }

    private fun requestBluetoothPermissions() {
        Log.d(TAG_SCAN_FRAGMENT, "Requesting Bluetooth Permissions")
        val builder: AlertDialog.Builder = AlertDialog.Builder(activity as Context)
        builder.setMessage("Starting from Android 12, the system requires apps to be granted bluetooth access in order to scan for and connect to BLE devices.")
            .setTitle("Bluetooth Permissions Required")
            .setPositiveButton("okay"){_, _ ->
                ActivityCompat.requestPermissions(
                    activity!!,

                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ),
                    RUNTIME_PERMISSION_REQUEST_CODE
                )
            }.show()
    }

    private fun requestRelevantRuntimePermissions(){
        if (activity!!.hasRequiredRuntimePermissions()) {
            return
        }
        Log.d(TAG_SCAN_FRAGMENT, "REQUESTING RUNTIME PERMISSIONS")
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                requestLocationPermission()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                requestBluetoothPermissions()
            }
        }
    }




    @SuppressLint("MissingPermission")
    private fun startBleScan(): MutableList<ScanResult>{
        scanResults.clear()
        Log.d(TAG_SCAN_FRAGMENT, "Attempting to start BLE scan")
        if (!activity!!.hasRequiredRuntimePermissions()){
            requestBluetoothPermissions()
        } else {
            setupRecyclerView()
            bleScanner.startScan(null, scanSettings, scanCallback)
            Log.d(TAG_SCAN_FRAGMENT, "REQUIRED PERMISSIONS AVAILABLE")
            isScanning = true
            binding.buttonScan.text = "Stop Scanning"
        }
        return scanResults
    }

    private fun setupRecyclerView() {
        binding.scanResultsRecyclerView.apply {
            adapter = scanResultAdapter
            layoutManager = LinearLayoutManager(
                context,
                RecyclerView.VERTICAL,
                false
            )
        }
    }

    private fun promptEnableBluetooth() {
        var bluetoothPermitted = (activity!!.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED)
        Log.d(TAG_SCAN_FRAGMENT, "Bluetooth Permission Granted: ${bluetoothPermitted.toString()}")
        if (!bluetoothAdapter.isEnabled or  !bluetoothPermitted) {
            Log.d(TAG_SCAN_FRAGMENT, "Asking user to enable bluetooth")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK) {
                    promptEnableBluetooth()
                }
            }
        }
    }


    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonScan.setOnClickListener {

            if (isScanning) {
                stopBleScan()

            } else {
                startBleScan()

            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    sealed class BleOperationType {
        abstract val device: BluetoothDevice
    }

    data class Connect(override val device: BluetoothDevice, val context: Context): BleOperationType()

    data class CharacteristicRead(override val device: BluetoothDevice, val characteristicUUID: UUID): BleOperationType()

    data class Disconnect(override val device: BluetoothDevice, val context: Context): BleOperationType()

    data class CharacteristicWrite(override val device: BluetoothDevice, val characteristicUUID: UUID): BleOperationType()

    @Synchronized
    private fun enqueueOperation(operation: FirstFragment.BleOperationType) {
        operationQueue.add(operation)
        if (pendingOperation == null) {
            doNextOperation()
        }
    }

    @Synchronized
    private fun doNextOperation() {
        if (pendingOperation != null) {
            Log.e("ConnectionManager", "doNextOperatoin() called when an operation is pending! Aborting.")
            return
        }

        val operation = operationQueue.poll() ?: run {
            Log.v("OperationQueue""Operation Queue empty, returning")
            return
        }

        pendingOperation = operation

        when (operation) {
            is FirstFragment.Connect -> Log.i(TAG_SCAN_FRAGMENT, "Connect")
            is Disconnect -> Log.i(TAG_SCAN_FRAGMENT, "Disconnect")
            is CharacteristicWrite -> Log.i(TAG_SCAN_FRAGMENT, "Write")
            is CharacteristicRead -> Log.i(TAG_SCAN_FRAGMENT, "Read")
        }
    }

    @Synchronized
    private fun signalEndOfOperation() {
        Log.d("ConnectionManager", "end of $pendingOperation")
        pendingOperation = null
        if (operationQueue.isNotEmpty()){
            doNextOperation()
        }
    }


}