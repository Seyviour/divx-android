package com.example.divx_demo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.fragment.app.activityViewModels
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Util
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.divx_demo.ble.ConnectionManager
import com.example.divx_demo.ble.isReadable
import com.example.divx_demo.databinding.FragmentFirstBinding
import com.example.divx_demo.media.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
private const val xCodeUUID = "0000ff01-0000-1000-8000-00805f9b34fb"
private const val TAG_SCAN_FRAGMENT = "DIVX_SCAN_FRAGMENT"

private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val RUNTIME_PERMISSION_REQUEST_CODE = 2

private const val DIVX_SERVICE_UUID = "A"
private const val XCODE_CHAR_UUID = "B"

@SuppressLint("MissingPermission")
class FirstFragment : Fragment() {

    private val viewModel:DivxViewModel by activityViewModels()


    private val bluetoothAdapter: BluetoothAdapter by lazy{
        val bluetoothManager = activity!!.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()


    private var isScanning = false


    private val scanResultAdapter: ScanResultAdapter by lazy {


        ScanResultAdapter(scanResults) {result ->
            if (isScanning){
                stopBleScan()
            }
            with(result.device){
                Log.w("ScanResultAdapter", "Connecting to $address")
//                connectGatt(context, false, gattCallback)
                ConnectionManager.connect(this, activity!!)
                viewModel.setBluetoothDevice(this)
                var divxCharacteristic = ConnectionManager.getCharacteristic(this, UUID.fromString(xCodeUUID))
                Log.d(TAG_SCAN_FRAGMENT, "${divxCharacteristic?.uuid ?: "null"}")

                divxCharacteristic?.let{
                    Log.d("DIVX-NOTIFY", "ENABLING NOTIFICATIONS ON ${divxCharacteristic.uuid}")
                    ConnectionManager.enableNotifications(this, divxCharacteristic)
                }

            }
        }
    }

    private lateinit var bluetoothGatt: BluetoothGatt

    fun ByteArray.toHexString(): String = joinToString(separator ="", prefix = "0x") {String.format("%02X", it)}

    private fun readXCode() {
        val divxServiceUUID = UUID.fromString(DIVX_SERVICE_UUID)
        val xCodeCharUUID = UUID.fromString(XCODE_CHAR_UUID)
        val currXcodeChar = bluetoothGatt.getService(divxServiceUUID)?.getCharacteristic(xCodeCharUUID)
        if (currXcodeChar?.isReadable() == true){
            bluetoothGatt.readCharacteristic(currXcodeChar)
        }
    }






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

    private fun Context.hasRequiredRuntimePermissions(): Boolean {

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

    var hasplayed = false


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonScan.setOnClickListener {
//            play()
            if (isScanning) {
                stopBleScan()

            } else {
                startBleScan()
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }


    override fun onStart() {
        if (viewModel.player == null){
            (activity as MainActivity)!!.acquirePlayer()
        }
        super.onStart()

    }

    override fun onStop() {
        super.onStop()
//        MediaController.releaseFuture(controllerFuture)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}