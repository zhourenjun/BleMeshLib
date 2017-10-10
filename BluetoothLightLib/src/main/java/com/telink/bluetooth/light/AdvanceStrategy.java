package com.telink.bluetooth.light;

import android.os.Handler;

import com.telink.bluetooth.TelinkLog;

/**
 * 命令写入FIFO策略
 */
public abstract class AdvanceStrategy {

    public final static byte[] DEFAULT_SAMPLE_LIST = new byte[]{(byte) 0xD0, (byte) 0xD2, (byte) 0xE2};

    private final static AdvanceStrategy DEFAULT = new DefaultAdvanceStrategy();
    private static AdvanceStrategy definition;
    protected Callback mCallback;
    protected int sampleRate = 320;
    protected byte[] sampleOpcodes;

    private static final int COMMAND_DELAY = 320;

    public static AdvanceStrategy getDefault() {
        synchronized (AdvanceStrategy.class) {
            if (definition != null)
                return definition;
        }
        return DEFAULT;
    }

    public static void setDefault(AdvanceStrategy strategy) {
        synchronized (AdvanceStrategy.class) {
            if (strategy != null)
                definition = strategy;
        }
    }

    static public boolean isExists(byte opcode, byte[] opcodeList) {
        for (byte opc : opcodeList) {
            if ((opc & 0xFF) == (opcode & 0xFF))
                return true;
        }
        return false;
    }

    final public int getSampleRate() {
        return sampleRate;
    }

    /**
     * 设置采样率,单位毫秒
     *
     * @param sampleRate
     */
    final public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    /**
     * 回调接口,采样到的命令交由回调接口处理
     *
     * @param mCallback
     */
    public void setCallback(Callback mCallback) {
        this.mCallback = mCallback;
    }

    public byte[] getSampleOpcodes() {
        if (sampleOpcodes == null)
            return DEFAULT_SAMPLE_LIST;
        return sampleOpcodes;
    }

    /**
     * 设置采样的Opcode数组
     *
     * @param sampleOpcodes
     */
    public void setSampleOpcodes(byte[] sampleOpcodes) {
        this.sampleOpcodes = sampleOpcodes;
    }

    /**
     * 处理传进来的命令
     *
     * @param opcode     命令吗
     * @param address    目标地址
     * @param params     命令参数
     * @param delay      命令延时
     * @param tag        命令标签
     * @param noResponse 命令发送方式
     * @param immediate  是否立即写入底层FIFO
     * @return 命令是否成功写入
     */
    abstract public boolean postCommand(byte opcode, int address, byte[] params, int delay, Object tag, boolean noResponse, boolean immediate);

    /**
     * 启动,执行初始化
     */
    abstract public void onStart();

    /**
     * 停止，做一些清理工作
     */
    abstract public void onStop();

    public interface Callback {
        boolean onCommandSampled(byte opcode, int address, byte[] params, Object tag, int delay);
    }

    /**
     * 默认的命令发送策略
     */
    private static class DefaultAdvanceStrategy extends AdvanceStrategy {

        public final static String TAG = "AdvanceStrategy";

        private long lastSampleTime;

        // 上一个是否是采样指令
        private Handler commandSender;

        // 上一次发送指令时间
        private long lastCmdTime;

        private StrategyTask task;
//        private boolean isLastSampleCmd = false;

        private class StrategyTask implements Runnable {
            private byte opcode;
            private int address;
            private byte[] params;
            private int delay;
            private Object tag;

            public void setCommandArgs(byte opcode, int address, byte[] params, int delay, Object tag) {
                this.opcode = opcode;
                this.address = address;
                this.params = params;
                this.delay = delay;
                this.tag = tag;
            }

            @Override
            public void run() {
                TelinkLog.d(TAG, "Delay run Opcode : " + Integer.toHexString(opcode));
                lastSampleTime = System.currentTimeMillis();
                lastCmdTime = System.currentTimeMillis();
                DefaultAdvanceStrategy.this.mCallback.onCommandSampled(opcode, address, params, tag, delay);
            }
        }

        public DefaultAdvanceStrategy() {
            commandSender = new Handler();
            task = new StrategyTask();
        }


        @Override
        public void onStart() {
            this.lastSampleTime = 0;
        }

        @Override
        public void onStop() {
        }

        @Override
        public boolean postCommand(byte opcode, int address, byte[] params, int delay, Object tag, boolean noResponse, boolean immediate) {
            long currentTime = System.currentTimeMillis();
            // 是否直接发送指令
            boolean now = false;
            if (lastCmdTime == 0) {
                //第一个命令,直接写入FIFO
                now = true;
            } else if (immediate) {
                //立即发送的命令
                now = true;
            } else {
                if (isExists(opcode, this.getSampleOpcodes())) {
                    long interval = currentTime - this.lastSampleTime;
                    if (interval < 0) {
                        now = true;
                        lastSampleTime = currentTime;
                    } else if (interval >= this.getSampleRate()) {
                        now = true;
                        lastSampleTime = currentTime;
                    } else {
                        commandSender.removeCallbacks(task);
                        task.setCommandArgs(opcode, address, params, delay, tag);
                        commandSender.postDelayed(task, this.getSampleRate() - interval);
                    }
                } else {
                    now = true;
                }
            }

            if (now && this.mCallback != null) {
                TelinkLog.d(TAG, "Sample Opcode : " + Integer.toHexString(opcode) + " delay:" + delay);

                long period = currentTime - this.lastCmdTime;
                if (period > 0 && period < COMMAND_DELAY) {
                    if (delay < (COMMAND_DELAY - period))
                        delay = (int) (COMMAND_DELAY - period);
                }
                lastCmdTime = System.currentTimeMillis();
                //所有采样到的命令立即交给回调接口处理
                return this.mCallback.onCommandSampled(opcode, address, params, tag, delay);
            }
            TelinkLog.d(TAG, "Delay Opcode : " + Integer.toHexString(opcode));
            return false;
        }
    }
}
