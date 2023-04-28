package com.example.divx_demo

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.Player
import androidx.media3.datasource.RawResourceDataSource
import com.example.divx_demo.ble.ConnectionEventListener
import com.example.divx_demo.ble.ConnectionManager
import com.example.divx_demo.ble.toHexString
import com.example.divx_demo.ble.toXCode
import java.util.*
import kotlin.collections.HashMap

val _hardCodedResources: HashMap<String, XResourceType> = hashMapOf(
    "ABCDBCDA" to XVideo("ABCDBCDA", RawResourceDataSource.buildRawResourceUri(R.raw.newton)),
    "ABCDBCD" to XQuiz("ABCD"),
    "ABCDBCDBDA" to XQuiz("ABCDBCDBDA"),
    "ABCDBCDCDA" to XQuiz("ABCDBCDCDA"),
    "ABCDBCABDA" to XQuiz("ABCDBCABDA")
)

const val TAG_VIEW_MODEL = "VIEWMODEL"

class DivxViewModel: ViewModel() {

    val X_CODE_CHAR_UUID = "0000ff01-0000-1000-8000-00805f9b34fb"
    val DIVX_SERVICE_UUID = "000000ff-0000-1000-8000-00805f9b34fb"

    private val isConnected = MutableLiveData<Boolean>()
    val connectionState: LiveData<Boolean> get() = isConnected

    fun setConnectionState(connected: Boolean) {
        isConnected.postValue(connected)
    }

    fun getHardMappedResource(xid: String): XResourceType?{
        if (xid in hardCodedResources )
            return hardCodedResources[xid]
        return null
    }

    fun charToXCode(characteristic: String?): String{
        characteristic?.let{
            val firstPeriod = it.indexOf(".")
            if (firstPeriod > 0)
                return characteristic.substring(0,firstPeriod)
        }
        return ""
    }


    private val xCodeNotificationsEnabledMLD = MutableLiveData<Boolean>(false)
    val xCodeNotificationsEnabled: LiveData<Boolean> get() = xCodeNotificationsEnabledMLD

    fun setXCodeNotificationState(state: Boolean){
        xCodeNotificationsEnabledMLD.postValue(state)
    }

    var vmGatt:BluetoothGatt? = null

    var currXCodeMLD: MutableLiveData<String> = MutableLiveData<String>("")
    val currXCode: LiveData<String> get() = currXCodeMLD
    fun setCurrXCode(xCode: String){ currXCodeMLD.postValue(xCode)}

    var player: Player? = null;

    val hardCodedResources: HashMap<String, XResourceType> = _hardCodedResources

    var bluetoothDevice: MutableLiveData<BluetoothDevice> = MutableLiveData<BluetoothDevice>()
    val vmDevice : LiveData<BluetoothDevice> get() = bluetoothDevice

    fun setBluetoothDevice(device: BluetoothDevice){
        bluetoothDevice.postValue(device)
    }

    companion object{
        const val X_CODE_CHAR_UUID = "0000ff01-0000-1000-8000-00805f9b34fb"
        const val DIVX_SERVICE_UUID = "000000ff-0000-1000-8000-00805f9b34fb"
    }

    var vwConnectionListener = ConnectionEventListener().apply{

        onCharacteristicChanged = {device, characteristic ->
            Log.d(TAG_VIEW_MODEL, "onCharacteristicChanged")
            if (characteristic.uuid == UUID.fromString(X_CODE_CHAR_UUID) && device==vmDevice.value){
                setCurrXCode(characteristic.value.toXCode())
            }
        }

        onConnectionSetupComplete = {gatt ->
            vmGatt = gatt
            setConnectionState(true)
            bluetoothDevice.value?.let{it_device->
                var characteristic = ConnectionManager.getCharacteristic(it_device, UUID.fromString(X_CODE_CHAR_UUID))
                characteristic?.let{it_characteristic->
                    ConnectionManager.enableNotifications(it_device, it_characteristic)
                }
            }
        }

        onDisconnect = {device ->
            if (device == vmDevice.value)
                setConnectionState(false)
        }

        onNotificationsEnabled = {device, characteristic ->
            if (device == vmDevice.value && characteristic.uuid.toString() == X_CODE_CHAR_UUID)
                setXCodeNotificationState(true)
        }

        onNotificationsDisabled = {device, characteristic ->
            if (device == vmDevice.value && characteristic.uuid.toString() == X_CODE_CHAR_UUID)
                setXCodeNotificationState(false)
        }

    }

    fun registerListener(){
        ConnectionManager.registerListener(vwConnectionListener)
    }

    fun unRegisterListener(){
        ConnectionManager.registerListener(vwConnectionListener)
    }


}