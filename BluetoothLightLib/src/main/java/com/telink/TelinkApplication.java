package com.telink;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;

import com.telink.bluetooth.TelinkLog;
import com.telink.bluetooth.event.DeviceEvent;
import com.telink.bluetooth.event.LeScanEvent;
import com.telink.bluetooth.event.MeshEvent;
import com.telink.bluetooth.event.NotificationEvent;
import com.telink.bluetooth.event.ServiceEvent;
import com.telink.bluetooth.light.DeviceInfo;
import com.telink.bluetooth.light.GetAlarmNotificationParser;
import com.telink.bluetooth.light.GetGroupNotificationParser;
import com.telink.bluetooth.light.GetSceneNotificationParser;
import com.telink.bluetooth.light.GetTimeNotificationParser;
import com.telink.bluetooth.light.LightAdapter;
import com.telink.bluetooth.light.LightService;
import com.telink.bluetooth.light.NotificationInfo;
import com.telink.bluetooth.light.NotificationParser;
import com.telink.bluetooth.light.OnlineStatusNotificationParser;
import com.telink.util.Event;
import com.telink.util.EventBus;
import com.telink.util.EventListener;
import com.telink.util.Strings;

public class TelinkApplication extends Application {

    private static TelinkApplication mThis;

    protected final EventBus<String> mEventBus = new EventBus<>();
    protected Context mContext;
    protected boolean serviceStarted;
    protected boolean serviceConnected;
    protected final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            serviceConnected(name, service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceDisconnected(name);
        }
    };

    protected DeviceInfo mCurrentConnect;
    private BroadcastReceiver mLightReceiver;

    public static TelinkApplication getInstance() {
        if (mThis == null)
            mThis = new TelinkApplication();
        return mThis;
    }

    /**
     * 当前连接的设备
     */
    public DeviceInfo getConnectDevice() {
        return mCurrentConnect;
    }

    @Override
    public void onCreate() {
        mThis = this;
        mContext = this;
        super.onCreate();
        TelinkLog.d("TelinkApplication Created.");
    }

    public void doInit() {
        doInit(this);
    }

    public void doInit(Context context) {
        doInit(context, null);
    }

    /**
     * 执行初始化,APP启动时调用
     */
    public void doInit(Context context, Class<? extends LightService> clazz) {
        mContext = context;
        LocalBroadcastManager.getInstance(mContext).registerReceiver(makeLightReceiver(), makeLightFilter());
        if (clazz != null)
            startLightService(clazz);
        registerNotificationParser(OnlineStatusNotificationParser.create());
        registerNotificationParser(GetGroupNotificationParser.create());
        registerNotificationParser(GetAlarmNotificationParser.create());
        registerNotificationParser(GetSceneNotificationParser.create());
        registerNotificationParser(GetTimeNotificationParser.create());
    }

    /**
     * 销毁,当退出APP时调用此方法
     */
    public void doDestroy() {
        stopLightService();
        if (mLightReceiver != null)
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mLightReceiver);
        removeEventListeners();
        mCurrentConnect = null;
        serviceStarted = false;
        serviceConnected = false;
    }

    protected BroadcastReceiver makeLightReceiver() {
        if (mLightReceiver == null)
            mLightReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {

                    switch (intent.getAction()) {
                        case LightService.ACTION_NOTIFICATION:
                            onNotify(intent);
                            break;
                        case LightService.ACTION_STATUS_CHANGED:
                            onStatusChanged(intent);
                            break;
                        case LightService.ACTION_UPDATE_MESH_COMPLETED:
                            onUpdateMeshCompleted();
                            break;
                        case LightService.ACTION_OFFLINE:
                            onMeshOffline();
                            break;
                        case LightService.ACTION_LE_SCAN:
                            onLeScan(intent);
                            break;
                        case LightService.ACTION_LE_SCAN_TIMEOUT:
                            onLeScanTimeout();
                            break;
                        case LightService.ACTION_SCAN_COMPLETED:
                            onLeScanCompleted();
                            break;
                        case LightService.ACTION_ERROR:
                            onError(intent);
                            break;
                    }
                }
            };

        return mLightReceiver;
    }

    protected IntentFilter makeLightFilter() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(LightService.ACTION_LE_SCAN);
        filter.addAction(LightService.ACTION_SCAN_COMPLETED);
        filter.addAction(LightService.ACTION_LE_SCAN_TIMEOUT);
        filter.addAction(LightService.ACTION_NOTIFICATION);
        filter.addAction(LightService.ACTION_STATUS_CHANGED);
        filter.addAction(LightService.ACTION_UPDATE_MESH_COMPLETED);
        filter.addAction(LightService.ACTION_OFFLINE);
        filter.addAction(LightService.ACTION_ERROR);
        return filter;
    }

    /**
     * 添加一个事件监听器
     */
    public void addEventListener(String eventType, EventListener<String> listener) {
        mEventBus.addEventListener(eventType, listener);
    }

    /**
     * 移除事件监听器
     */
    public void removeEventListener(EventListener<String> listener) {
        mEventBus.removeEventListener(listener);
    }

    /**
     * 从事件监听器中移除指定的事件
     */
    public void removeEventListener(String eventType, EventListener<String> listener) {
        mEventBus.removeEventListener(eventType, listener);
    }

    /**
     * 移除所有的事件监听器
     */
    public void removeEventListeners() {
        mEventBus.removeEventListeners();
    }

    /**
     * 分发事件
     */
    public void dispatchEvent(Event<String> event) {
        mEventBus.dispatchEvent(event);
    }


    /**
     * 启动LightService
     */
    public void startLightService(Class<? extends LightService> clazz) {
        if (serviceStarted || serviceConnected)
            return;
        serviceStarted = true;
        Intent service = new Intent(mContext, clazz);
        bindService(service, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * 停止LightService
     */
    public void stopLightService() {
        if (!serviceStarted)
            return;
        serviceStarted = false;
        if (serviceConnected) {
            unbindService(mServiceConnection);
        }
    }

    protected void serviceConnected(ComponentName name, IBinder service) {
        TelinkLog.d("service connected --> " + name.getShortClassName());
        serviceConnected = true;
        dispatchEvent(ServiceEvent.newInstance(this, ServiceEvent.SERVICE_CONNECTED, service));
    }

    protected void serviceDisconnected(ComponentName name) {
        TelinkLog.d("service disconnected --> " + name.getShortClassName());
        serviceConnected = false;
        dispatchEvent(ServiceEvent.newInstance(this, ServiceEvent.SERVICE_DISCONNECTED, null));
    }


    protected void onLeScan(Intent intent) {
        DeviceInfo deviceInfo = intent.getParcelableExtra(LightService.EXTRA_DEVICE);
        dispatchEvent(LeScanEvent.newInstance(this, LeScanEvent.LE_SCAN, deviceInfo));
    }

    protected void onLeScanCompleted() {
        dispatchEvent(LeScanEvent.newInstance(this, LeScanEvent.LE_SCAN_COMPLETED, null));
    }

    protected void onLeScanTimeout() {
        dispatchEvent(LeScanEvent.newInstance(this, LeScanEvent.LE_SCAN_TIMEOUT, null));
    }

    protected void onStatusChanged(Intent intent) {

        DeviceInfo deviceInfo = intent.getParcelableExtra(LightService.EXTRA_DEVICE);

        if (deviceInfo.status == LightAdapter.STATUS_LOGIN) {
            mCurrentConnect = deviceInfo;
            dispatchEvent(DeviceEvent.newInstance(this, DeviceEvent.CURRENT_CONNECT_CHANGED, deviceInfo));
        }else if (deviceInfo.status == LightAdapter.STATUS_LOGOUT){
            mCurrentConnect = null;
        }

        dispatchEvent(DeviceEvent.newInstance(this, DeviceEvent.STATUS_CHANGED, deviceInfo));
    }

    protected void onUpdateMeshCompleted() {
        dispatchEvent(MeshEvent.newInstance(this, MeshEvent.UPDATE_COMPLETED, -1));
    }

    protected void onMeshOffline() {
        dispatchEvent(MeshEvent.newInstance(this, MeshEvent.OFFLINE, -1));
    }

    protected void onError(Intent intent) {
        int errorCode = intent.getIntExtra(LightService.EXTRA_ERROR_CODE, -1);
        dispatchEvent(MeshEvent.newInstance(this, MeshEvent.ERROR, errorCode));
    }

    protected void onNotify(Intent intent) {
        NotificationInfo notifyInfo = intent.getParcelableExtra(LightService.EXTRA_NOTIFY);
        onNotify(notifyInfo);
    }

    protected void onNotify(NotificationInfo notifyInfo) {
        int opcode = notifyInfo.opcode;
        String eventType = NotificationEvent.getEventType((byte) opcode);
        if (Strings.isEmpty(eventType))
            return;
        TelinkLog.e("postCommand event : " + eventType + "--" + opcode);
        NotificationEvent event = NotificationEvent.newInstance(this, eventType, notifyInfo);
        event.setThreadMode(Event.ThreadMode.Background);
        dispatchEvent(event);
    }


    /**
     * 注册通知解析器
     */
    protected void registerNotificationParser(NotificationParser parser) {
        NotificationParser.register(parser);
    }
}
