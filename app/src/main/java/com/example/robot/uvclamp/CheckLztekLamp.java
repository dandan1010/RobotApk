package com.example.robot.uvclamp;

import android.content.Context;
import android.util.Log;
import android.widget.EditText;

import com.example.robot.R;
import com.example.robot.utils.EventBusMessage;
import com.example.robot.utils.Content;
import com.lztek.toolkit.Lztek;
import com.lztek.toolkit.SerialPort;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.io.OutputStream;

public class CheckLztekLamp {

    private static final String TAG = "CheckLztekLamp";
    private Context mContext;
    private Lztek mLztek;
    private SerialPort serialPort;
    private SerialPort batterySerialPort;
    /**
     * 218:
     * 248:左边sensor
     * 249:
     * 250:右边sensor
     * 251:
     * 228:
     * 229:uvc灯
     * 230:led灯
     * */
    private int[] port = new int[]{218, 248, 249, 250, 251, 228, 229, 230};
    private MoreSerialPortThread moreSerialPortThread;
    private BatteryPortThread batteryPortThread;
    public boolean threadFlag = false;
    private boolean batteryThread = false;


    public CheckLztekLamp(Context mContext) {
        this.mContext = mContext;
        mLztek = Lztek.create(mContext);
    }

    private void setEnable() {
        for (int i = 0; i < port.length; i++) {
            mLztek.gpioEnable(port[i]);
        }
    }

    public void setUvcMode(){
        for (int i = 4; i < 7; i++) {
            mLztek.setGpioOutputMode(port[i]);
        }
    }

    private void setMode() {
        for (int i = 0; i < port.length; i++) {
            mLztek.setGpioInputMode(port[i]);
        }
    }

    public boolean getGpioSensorState() {

//        boolean k1 = mLztek.getGpioValue(port[0]) == 1 ? true : false;
//        boolean k2 = mLztek.getGpioValue(port[1]) == 1 ? true : false;
//        boolean k3 = mLztek.getGpioValue(port[2]) == 1 ? true : false;
//        boolean k4 = mLztek.getGpioValue(port[3]) == 1 ? true : false;
//
//        if (k1 && k2 && k3 && k4) {
//            return true;
//        } else {
//            return false;
//        }

        return false;
    }

    public void startCheckSensorAtTime() {
        setEnable();
        setMode();
    }

    public void startLedLamp() {
        mLztek.setGpioValue(port[7], 0);
        Log.v("zdzd", "startLedLamp");
        threadFlag = true;
        openSerialPort();
    }

    public void stopLedLamp() {
        mLztek.setGpioValue(port[7], 1);
        threadFlag = false;
        Log.v("zdzd", "stopLedLamp");
    }

    public void startUvc1Lamp() {
        mLztek.setGpioValue(port[5], 0);
    }

    public void stopUvc1Lamp() {
        mLztek.setGpioValue(port[5], 1);
    }

    public boolean getUvc1Lamp() {
        int gpioValue = mLztek.getGpioValue(port[5]);
        return gpioValue == 1 ? true : false;
    }

    public void startUvc2Lamp() {
        mLztek.setGpioValue(port[6], 0);
    }

    public void stopUvc2Lamp() {
        mLztek.setGpioValue(port[6], 1);
    }

    public void startUvc3Lamp() {
        mLztek.setGpioValue(port[4], 0);
    }

    public void stopUvc3Lamp() {
        mLztek.setGpioValue(port[4], 1);
    }

    /**
     * 请求led灯光
     * */
    private void openSerialPort() {//控制Led灯
        serialPort = mLztek.openSerialPort("/dev/ttyS1", 115200, 8, 0, 1, 0);
        if (serialPort == null) {
            Log.d(TAG, "serialPort is null");
            return;
        }

        Log.d("ZDZD :", "robotstate : " + Content.robotState + "  thread = " + threadFlag);
        if (moreSerialPortThread == null) {
            Log.d(TAG, "myThread is null");
            moreSerialPortThread = new MoreSerialPortThread();
        }
        moreSerialPortThread.start();
    }
    /**
     * led线程
     * */
    class MoreSerialPortThread extends Thread {
        int index = 0;
        String[] lightArray;
        OutputStream outputStream;

        @Override
        public void run() {
            super.run();
            Log.d("zdzd : " , "thread = " + threadFlag);
            while (threadFlag) {
                outputStream = serialPort.getOutputStream();
                try {
                    switch (Content.robotState) {
                        case 0:
                            break;
                        case 1:
                            index = 0;
                            lightArray = mContext.getResources().getStringArray(R.array.static_light);
                            break;
                        case 2:
                            break;
                        case 3:
                            lightArray = mContext.getResources().getStringArray(R.array.move_light);
                            break;
                        case 4:lightArray = mContext.getResources().getStringArray(R.array.charge_light);
                            break;
                        case 5:
                            lightArray = mContext.getResources().getStringArray(R.array.warning_light);
                            break;
                        case 6:
                            lightArray = mContext.getResources().getStringArray(R.array.low_power_light);
                            break;
                        default:break;
                    }
                    if (index < lightArray.length - 1) {

                    } else {
                        index = 0;
                    }
                    byte[] colorLight = hexBytes(lightArray[index]);
                    if (null != colorLight) {
                        outputStream.write(colorLight);
                        Log.d(TAG, "serialPort write success index = " + index + "  , state = "+ Content.robotState);
                    }
                    if (index < lightArray.length - 1) {
                        index++;
                    } else {
                        index = 0;
                    }
                    Thread.sleep(Content.time);
                    if (Content.robotState == 1 || Content.robotState == 5 || Content.robotState == 6){
                        outputStream.write(hexBytes(mContext.getResources().getString(R.string.no_color_light)));
                        Thread.sleep(1000);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "#ERROR# : [COM]Write Faild: " + e.getMessage(), e);
                } finally {
                    if (outputStream != null) {
                        try {
                            outputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    /**
     * 转换16进制
     * */
    private byte[] hexBytes(String hexString) {
        int length;
        byte h;
        byte l;
        byte[] byteArray;
        hexString = hexString.replaceAll(" ", "");
        length = null != hexString ? hexString.length() : 0;
        length = (length - (length % 2)) / 2;
        if (length < 1) {
            return null;
        }

        byteArray = new byte[length];
        for (int i = 0; i < length; i++) {
            h = (byte) hexString.charAt(i * 2);
            l = (byte) hexString.charAt(i * 2 + 1);

            l = (byte) ('0' <= l && l <= '9' ? l - '0' :
                    'A' <= l && l <= 'F' ? (l - 'A') + 10 :
                            'a' <= l && l <= 'f' ? (l - 'a') + 10 : 0);
            h = (byte) ('0' <= h && h <= '9' ? h - '0' :
                    'A' <= h && h <= 'F' ? (h - 'A') + 10 :
                            'a' <= h && h <= 'f' ? (h - 'a') + 10 : 3);
            byteArray[i] = (byte) (0x0FF & ((h << 4) | l));
        }
        return byteArray;
    }

    /**
     * 请求电池数据
     * */
    public void openBatteryPort() {//获取电池
        batterySerialPort = mLztek.openSerialPort("/dev/ttyS0", 9600, 8, 0, 1, 0);
        if (batterySerialPort == null) {
            Log.d(TAG, "BatteryPort is null");
            return;
        }
        new Thread() {
            @Override
            public void run() {
                super.run();
                while (true) {
                    batteryThread = true;
                    if (batteryPortThread == null) {
                        batteryPortThread = new BatteryPortThread();
                        batteryPortThread.start();
                    }
                    readBattery();
                    try {
                        Thread.sleep(60 * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();


    }
    /**
     * 间隔1分钟写入请求一次电池数据
     * */
    class BatteryPortThread extends Thread {
        @Override
        public void run() {
            super.run();
            OutputStream outputStream = null;
            int index = 0;
            byte[] battery = hexBytes("DDA50300FFFD77");
            try {
                while (index < 2) {
                    if (null != battery) {
                        outputStream = batterySerialPort.getOutputStream();
                        outputStream.write(battery);
                        index++;
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Log.d(TAG, "batteryPort write success");
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 读取电池数据
     * */
    private void readBattery() {
        if (null == batterySerialPort) {
            return;
        }
        new Thread() {
            @Override
            public void run() {
                super.run();
                while (batteryThread) {
                    java.io.InputStream input = batterySerialPort.getInputStream();
                    try {
                        byte[] buffer = new byte[1024];
                        int len = input.read(buffer);
                        Log.d(TAG, "读取数据len ： " + len);
                        if (len > 0) {
                            byte[] data = java.util.Arrays.copyOfRange(buffer, 0, len);
                            EditText editText = new EditText(mContext);
                            for (byte b : data) {
                                byte h = (byte) (0x0F & (b >> 4));
                                byte l = (byte) (0x0F & b);
                                editText.append("" + (char) (h > 9 ? 'A' + (h - 10) : '0' + h));
                                editText.append("" + (char) (l > 9 ? 'A' + (l - 10) : '0' + l));
                            }
                            Log.d(TAG, "读取数据 ： " + editText.getText().toString());
                            EventBus.getDefault().post(new EventBusMessage(1004, data));
                            batteryThread = false;
                            batteryPortThread = null;
                        }
                        Thread.sleep(2000);
                    } catch (Exception e) {
                        android.util.Log.d(TAG, "[COM]Read Faild: " + e.getMessage(), e);
                    } finally {
                        if (null != input) {
                            try {
                                input.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }.start();
    }

}