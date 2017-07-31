

day 5.22 update
1.修复 发送指令后更改系统时间至更早，会导致指令发送不出去 问题：
    在AdvanceStrategy 采样方法中添加 if (interval < 0) interval = 0  即当前时间取值小于之前指令发送时间，则置为零。
2.采样策略中移除默认320ms delay，保证指令的准时发送。
3.Demo修改：
    3.1 开关灯时，以online_status状态为准；
    3.2 调节亮度时，范围改为5-100；

    
day 5.19 update
针对firmware在Android N（7.0）版本有较大兼容性问题,Android这边主要是添加UI提醒
1.LightController类修改：
    1.1 添加 N 版本判断和连接重试方法，以及相应变量， 具体可以参考命名和相关注释 // android N check
    1.2 在内部类LightEvent中添加 常量 CONNECT_FAILURE_N 用于标识错误类型
2.LightAdapter类修改：
    1.1 添加常量 STATUS_ERROR_N，标识N错误，调用setStatus(STATUS_ERROR_N)，可讲错误事件分发； 可参考DeviceScanningActivity中使用；

3.Demo修改：
    1.DeviceScanningActivity 添加 STATUS_ERROR_N处理；

4.其它修改：
    1.MeshOTA功能添加（测试中）；
    2.UI改动；



day 4.26 update
1.自动连接参数中，添加自动连接的MAC，设置可连接指定mac设备；

day 4.19 update
1.LeBluetooth类中添加对Android M（6.0）支持，具体为：在M以上版本会判断Location是否开启,未开启会回调onScanFail(int errorCode), 前端可根据
    在调用扫描接口时，建议添加监听 MeshEvent.ERROR,并判断errorCode,若是Location问题,可以通过Intent跳转设置页面打开Location开关.

2.采样策略类优化 AdvanceStrategy.DefaultAdvanceStrategy：除了第一条指令和特别声明的立即发送的指令外，其它控制指令都会默认delay 320ms；
    同时对特定指令采样 默认320ms的采样间隔。




day 4.10 update
经过反复测试可以确认在Android 7.0版本会出现如下情况：30s内有5次开关蓝牙扫描动作，第6次会开启动作会不生效。这个是Android蓝牙底层控制的，上层无法通过接口参数更改。
据此在SDK的LightAdapter类中添加扫描延时机制：
    1.一个扫描周期的最少时间为10s;
    2.10s蓝牙关闭操作会延时到10s执行，期间有重新开启扫描则取消Timer;
    3.低版本设备保留之前的扫描机制.
其它更新：
1.删除LightService类中的sendCommand 接口，仅保留一个sendCommandNoResponse;
2.在自动连接模式下，添加扫描设备队列，连接重试机制，重试一次，失败后会从队列中获取;
3.删除不用的delete接口;
4.LightAdapter类中去掉处理扫描结果的子线程;
5.Demo中加灯页面 DeviceScanningActivity 去掉扫描开启延时.
