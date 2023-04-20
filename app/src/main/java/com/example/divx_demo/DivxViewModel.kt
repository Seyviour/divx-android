package com.example.divx_demo

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.Player
import androidx.media3.datasource.RawResourceDataSource

val _hardCodedResources: HashMap<String, XResourceType> = hashMapOf(
    "ABCDBCD" to XQuiz("ABCD"),
    "ABCDBCDBDA" to XQuiz("ABCDBCDBDA"),
    "ABCDBCDCDA" to XQuiz("ABCDBCDCDA"),
    "ABCDBCABDA" to XQuiz("ABCDBCABDA"),
    "0x00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E" to XVideo("0x00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E", RawResourceDataSource.buildRawResourceUri(R.raw.newton))
)


class DivxViewModel: ViewModel() {

    private val isConnected = MutableLiveData<Boolean>()
    val connectionState: LiveData<Boolean> get() = isConnected

    fun setConnectionState(connected: Boolean) {
        isConnected.value = connected
    }

    fun getHardMappedResource(xid: String): XResourceType?{
        if (xid in hardCodedResources )
            return hardCodedResources[xid]
        return null
    }

    var player: Player? = null;

    val hardCodedResources: HashMap<String, XResourceType> = _hardCodedResources

    var bluetoothDevice: MutableLiveData<BluetoothDevice> = MutableLiveData<BluetoothDevice>()
    val device : LiveData<BluetoothDevice> get() = bluetoothDevice

    fun setBluetoothDevice(device: BluetoothDevice){
        bluetoothDevice.value = device
    }

    companion object{
        const val X_CODE_CHAR_UUID = "0000ff01-0000-1000-8000-00805f9b34fb"
        const val DIVX_SERVICE_UUID = "000000ff-0000-1000-8000-00805f9b34fb"
    }
}