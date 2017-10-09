package com.telink.light

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.support.annotation.RequiresApi
import com.telink.util.BleLog
import java.util.*

/**
 * 蓝牙扫描接口
 */
class LeBluetooth private constructor() {
    @Volatile private var mStarted = false
    @Volatile private var mScanning = false

    private lateinit var mLeScanCallback: BluetoothAdapter.LeScanCallback
    private lateinit var mScanner: BluetoothLeScanner
    private lateinit var mScanCallback: ScanCallback
    private lateinit var mCallback: LeScanCallback
    private var mAdapter: BluetoothAdapter? = null
    private lateinit var mContext: Context


    /**
     * 是否正在扫描
     */
    val isScanning: Boolean
        get() = synchronized(this) {
            return mScanning
        }
    val isSupportLollipop = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP


    /**
     * 蓝牙是否打开
     */
    val isEnabled = mAdapter != null && mAdapter!!.isEnabled

    /**
     * 设置回调函数
     */
    fun setLeScanCallback(callback: LeScanCallback) {
        this.mCallback = callback

        if (mCallback == null) return

        if (isSupportLollipop) {
            mScanCallback = @RequiresApi(Build.VERSION_CODES.LOLLIPOP) object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (isSupportLollipop) {
                        var scanRecord: ByteArray? = null

                        if (result.scanRecord != null)
                            scanRecord = result.scanRecord.bytes
                        if (mCallback != null)
                            mCallback.onLeScan(result.device, result.rssi, scanRecord)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    if (errorCode != ScanCallback.SCAN_FAILED_ALREADY_STARTED)
                        if (mCallback != null)
                            mCallback.onScanFail(Companion.SCAN_FAILED_FEATURE_UNSUPPORTED)
                }
            }
        } else {
            mLeScanCallback = BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
                if (mCallback != null)
                    mCallback.onLeScan(device, rssi, scanRecord)
            }
        }
    }

    /**
     * 开始扫描
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Synchronized
    fun startScan(serviceUUIDs: Array<UUID>?): Boolean {
        synchronized(this) {
            if (mScanning || mStarted)
                return true
        }
        BleLog.w("LeBluetooth#StartScan")
        if (!isEnabled)
            return false
        synchronized(this) {
            mStarted = true
            scan(serviceUUIDs)
        }
        return true
    }


    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun scan(serviceUUIDs: Array<UUID>?) {

        if (isSupportLollipop) {
            mScanner = mAdapter!!.bluetoothLeScanner
            if (mScanner == null) {
                synchronized(this) {
                    mScanning = false
                }
                if (mCallback != null)
                    mCallback.onScanFail(SCAN_FAILED_FEATURE_UNSUPPORTED)
            } else {
                mScanner.startScan(mScanCallback)
                synchronized(this) {
                    mScanning = true
                }
                mCallback.onStartedScan()
            }

        } else {
            if (!mAdapter!!.startLeScan(serviceUUIDs, mLeScanCallback)) {
                synchronized(this) {
                    mScanning = false
                }
                if (mCallback != null)
                    mCallback.onScanFail(SCAN_FAILED_FEATURE_UNSUPPORTED)
            } else {
                synchronized(this) {
                    mScanning = true
                }
                mCallback.onStartedScan()
            }
        }
    }

    /**
     * 停止扫描
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Synchronized
    fun stopScan() {
        BleLog.w("LeBluetooth#stopScan")
        synchronized(this) {
            if (!mScanning)
                return
        }

        try {
            if (isSupportLollipop) {
                if (mScanner != null)
                    mScanner.stopScan(mScanCallback)
            } else {
                if (mAdapter != null)
                    mAdapter!!.stopLeScan(mLeScanCallback)
            }
        } catch (e: Exception) {
            BleLog.d("蓝牙停止异常")
        }
        synchronized(this) {
            mStarted = false
            mScanning = false
        }
        if (mCallback != null)
            mCallback.onStoppedScan()
    }

    /**
     * 是否支持BLE
     */
    fun isSupport(context: Context): Boolean {
        this.mContext = context
        return getAdapter(context) != null
    }

    fun enable() = if (mAdapter!!.isEnabled) true else mAdapter!!.enable()

    private fun getAdapter(context: Context): BluetoothAdapter? {
        synchronized(this) {
            if (mAdapter == null) {
                val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                mAdapter = manager.adapter
            }
        }
        return mAdapter
    }

    interface LeScanCallback {
        fun onLeScan(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray?)

        fun onScanFail(errorCode: Int)

        fun onStartedScan()

        fun onStoppedScan()
    }

    companion object {
        val SCAN_FAILED_FEATURE_UNSUPPORTED = 4
        private var mThis: LeBluetooth? = null

        val instance: LeBluetooth?
            get() {
                synchronized(LeBluetooth::class.java) {
                    if (mThis == null) {
                        mThis = LeBluetooth()
                    }
                }
                return mThis
            }
    }
}
