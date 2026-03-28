package com.rokid.cxrssdksamples.activities.gattServer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

private const val TAG = "GattServerVM"

/**
 * GATT Server 页面的 ViewModel。
 * 负责打开/关闭蓝牙 GATT Server 并同时开启/关闭 BLE 广播，使扫描端能发现本设备；
 * 通过 [gattStatus] 向 UI 暴露当前服务状态。
 */
class GattServerViewModel : ViewModel() {

    /**
     * GATT Server 开关状态。
     * false：服务已关闭；true：服务已打开。
     * 对外暴露为只读 [gattStatus]。
     */
    private val _gattStatus = MutableStateFlow(false)
    val gattStatus = _gattStatus.asStateFlow()

    /**
     * 当前已打开的 GATT Server 实例，未打开时为 null。
     * 在 [startGattServer] 中赋值，在 [stopGattServer] 与 [onCleared] 中关闭并置 null。
     */
    private var bluetoothGattServer: BluetoothGattServer? = null

    /**
     * BLE 广播器，用于对外广播 Service UUID，使扫描端能发现本设备。
     * 未开启广播时为 null。
     */
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

    /**
     * 当前用于 [BluetoothLeAdvertiser.startAdvertising] 的回调，停止广播时需传入同一实例。
     */
    private var advertiseCallback: AdvertiseCallback? = null

    /** 与 GATT Service 一致的 UUID，用于广播数据，便于扫描端按 UUID 发现设备。 */
    private val serviceUuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

    /**
     * GATT Server 回调。
     * 当通过 [BluetoothGattServer.addService] 添加的 Service 被系统成功注册时，
     * [onServiceAdded] 会回调，此时将 [_gattStatus] 设为 true 表示服务已就绪。
     */
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onServiceAdded: success, uuid=${service.uuid}")
                _gattStatus.value = true
            } else {
                Log.e(TAG, "onServiceAdded: failure, status=$status")
            }
        }
    }

    /**
     * BLE 广播回调。仅用于打 Log 与在 stop 时传入 [BluetoothLeAdvertiser.stopAdvertising]。
     */
    private val advertiseCallbackImpl = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "onStartSuccess: BLE advertising started, scan app can discover this device")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "onStartFailure: BLE advertising failed, errorCode=$errorCode")
        }
    }

    /**
     * 打开 GATT Server 并启动 BLE 广播。
     * 使用 [context] 获取 [BluetoothManager]，打开 Server 并添加 PRIMARY GATT Service；
     * 同时使用 [BluetoothLeAdvertiser] 广播该 Service UUID，这样扫描端（如 AndroidTestBLEScan）
     * 才能通过 BLE 扫描发现本设备及 serviceUuids。
     * 若当前已有 Server 实例则仅更新状态为已打开；若设备无蓝牙或 open 失败则直接返回不更新状态。
     *
     * @param context 调用前需已获得 BLUETOOTH_CONNECT、BLUETOOTH_ADVERTISE 权限
     */
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE])
    @SuppressLint("MissingPermission")
    fun startGattServer(context: Context) {
        Log.d(TAG, "startGattServer: begin")
        if (bluetoothGattServer != null) {
            Log.d(TAG, "startGattServer: already running, set status true")
            _gattStatus.value = true
            return
        }
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothManager == null) {
            Log.e(TAG, "startGattServer: BluetoothManager is null")
            return
        }
        if (bluetoothManager.adapter == null) {
            Log.e(TAG, "startGattServer: BluetoothAdapter is null")
            return
        }

        val server = bluetoothManager.openGattServer(context, gattServerCallback)
        if (server == null) {
            Log.e(TAG, "startGattServer: openGattServer returned null")
            return
        }
        bluetoothGattServer = server
        Log.d(TAG, "startGattServer: openGattServer ok")

        // 普通 Service
//        val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        // Secondary
        val service =
            BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_SECONDARY).apply {
                // General Discoverable
                addCharacteristic(
                    BluetoothGattCharacteristic(
                        serviceUuid,
                        BluetoothGattCharacteristic.PROPERTY_READ,
                        BluetoothGattCharacteristic.PERMISSION_READ
                    )
                )
                addCharacteristic(
                    BluetoothGattCharacteristic(
                        serviceUuid,
                        BluetoothGattCharacteristic.PROPERTY_WRITE,
                        BluetoothGattCharacteristic.PERMISSION_WRITE
                    )
                )
            }

        val added = server.addService(service)
        Log.d(TAG, "startGattServer: addService(uuid=$serviceUuid) returned $added")

        val advertiser = bluetoothManager.adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(
                TAG,
                "startGattServer: BluetoothLeAdvertiser is null, scan app may not discover this device"
            )
        } else {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .build()
            val data = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(serviceUuid))
                .setIncludeDeviceName(true)
                .build()
            advertiseCallback = advertiseCallbackImpl
            advertiser.startAdvertising(settings, data, advertiseCallbackImpl)
            bluetoothLeAdvertiser = advertiser
            Log.d(TAG, "startGattServer: startAdvertising with serviceUuid=$serviceUuid")
        }
    }

    /**
     * 关闭 BLE 广播并关闭 GATT Server。
     * 先停止广播（否则 stopAdvertising 可能报错），再 close Server，最后将 [_gattStatus] 设为 false。
     *
     * @param context 当前未使用，保留以与 [startGattServer] 接口一致
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @SuppressLint("MissingPermission")
    fun stopGattServer(context: Context) {
        Log.d(TAG, "stopGattServer: begin")
        val callback = advertiseCallback
        val advertiser = bluetoothLeAdvertiser
        if (advertiser != null && callback != null) {
            try {
                advertiser.stopAdvertising(callback)
                Log.d(TAG, "stopGattServer: stopAdvertising ok")
            } catch (e: Exception) {
                Log.w(TAG, "stopGattServer: stopAdvertising exception", e)
            }
            bluetoothLeAdvertiser = null
            advertiseCallback = null
        }
        bluetoothGattServer?.close()
        bluetoothGattServer = null
        _gattStatus.value = false
        Log.d(TAG, "stopGattServer: done, gattStatus=false")
    }

    /**
     * ViewModel 被销毁时清理广播与 GATT Server，避免泄漏。
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @SuppressLint("MissingPermission")
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared: cleaning up")
        val callback = advertiseCallback
        val advertiser = bluetoothLeAdvertiser
        if (advertiser != null && callback != null) {
            try {
                advertiser.stopAdvertising(callback)
            } catch (_: Exception) {
            }
            bluetoothLeAdvertiser = null
            advertiseCallback = null
        }
        bluetoothGattServer?.close()
        bluetoothGattServer = null
        _gattStatus.value = false
    }
}