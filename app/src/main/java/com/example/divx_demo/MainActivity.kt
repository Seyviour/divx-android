package com.example.divx_demo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
//import android.support.v4.app.DialogFragment
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ComponentName
import android.content.DialogInterface
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.divx_demo.ble.ConnectionEventListener
import com.example.divx_demo.ble.ConnectionManager
import com.example.divx_demo.ble.toHexString
import com.example.divx_demo.databinding.ActivityMainBinding
import com.example.divx_demo.media.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue


private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val RUNTIME_PERMISSION_REQUEST_CODE = 2

private const val DIVX_MAIN = "DIVX_MAIN"
private const val XCODE_UUID = "0000ff01-0000-1000-8000-00805f9b34fb"

class MainActivity : AppCompatActivity() {


//    var myToaster = Toast(applicationContext)

    private lateinit var sessionToken: SessionToken
    private lateinit var controllerFuture: ListenableFuture<MediaController>;

    private val viewModel: DivxViewModel by viewModels()
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private val bluetoothManager: BluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }


    @SuppressLint("MissingPermission")
    fun checkConnectedDevices(){
        val connectedDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
        for (device in connectedDevices){
            Log.d("${DIVX_MAIN}-CONNECTED-DEVICES","${device.toString()}")

        }
    }


    var connectionListener = ConnectionEventListener().apply {
        onCharacteristicChanged = {device: BluetoothDevice, characteristic: BluetoothGattCharacteristic ->
            Log.d(DIVX_MAIN, "Executing Listener")
            Log.d(DIVX_MAIN, "${characteristic.value.toHexString()}")
            if (characteristic.uuid == UUID.fromString(XCODE_UUID) && device == viewModel.device.value){
                var  linkedResource: XResourceType? = viewModel.getHardMappedResource(characteristic.value.toHexString())
                if (linkedResource !=  null){
                    val navController = findNavController(R.id.nav_host_fragment_content_main)
                    when(linkedResource){
                        is XVideo -> {
                            navController.navigate(R.id.action_global_XVideoFragment,
                                XVideoFragment.makeArgBundle(linkedResource.xid, linkedResource.uri.toString()))
                        }

                        is XAudio -> {
                            navController.navigate(R.id.action_global_XVideoFragment,
                                XVideoFragment.makeArgBundle(linkedResource.xid, linkedResource.uri.toString()))
                        }

                        is XQuiz -> {}

                        is XWebpage -> {}

                        is XImage -> {}
                    }
                }
            }
        }
    }

//    private val bleScanner by lazy {
//        bluetoothAdapter.bluetoothLeScanner
//    }

    private val scanResults = mutableListOf<ScanResult>()

    fun acquirePlayer(){
        this?.let {context->
            Log.d("DIVX-$DIVX_MAIN", "FRAGMENT CONTEXT EXISTS")
            Log.d("DIVX", "PLAYBACK SERVICE IS RUNNING: ${PlaybackService.isRunning}")
            sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java) )
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture.addListener(
                {viewModel.player = controllerFuture.get() },
                MoreExecutors.directExecutor()
            )
        }

    }

    private fun startMediaService() {
        val startMediaServiceIntent = Intent(this, PlaybackService::class.java)
        this?.startService(startMediaServiceIntent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startMediaService()
        acquirePlayer()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        viewModel.bluetoothDevice.observe(this, Observer{bluetoothDevice ->
            bluetoothDevice?.let{
                ConnectionManager.registerListener(connectionListener)
            }

        })

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
    }

    override fun onResume() {
        Log.d(DIVX_MAIN, "PlaybackService Running: ${PlaybackService.isRunning}")
        Log.d("DIVX", "Calling onResume")
        super.onResume()
        checkConnectedDevices()
        Log.d(DIVX_MAIN, "Bluetooth Adapter State: ${bluetoothAdapter.isEnabled.toString()}")
        if (!bluetoothAdapter.isEnabled){
            promptEnableBluetooth()
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

    private fun promptEnableBluetooth() {
        var bluetoothPermitted = (this.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED)
        Log.d(DIVX_MAIN, "Bluetooth Permission Granted: ${bluetoothPermitted.toString()}")
        if (!bluetoothAdapter.isEnabled or  !bluetoothPermitted) {
            Log.d(DIVX_MAIN, "Asking user to enable bluetooth")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }


}