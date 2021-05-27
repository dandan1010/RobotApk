package com.example.robot.uvclamp;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.example.robot.R;
import com.example.robot.content.BaseEvent;
import com.example.robot.service.NavigationService;
import com.example.robot.service.SocketServices;
import com.example.robot.task.TaskManager;
import com.example.robot.utils.EventBusMessage;
import com.example.robot.content.Content;
import com.example.robot.utils.SharedPrefUtil;
import com.lztek.toolkit.AddrInfo;
import com.lztek.toolkit.Lztek;
import com.lztek.toolkit.SerialPort;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class CheckLztekLamp {

    private static final String TAG = "CheckLztekLamp";

    private static final int ON = 0;
    private static final int OFF = 1;
    private Context mContext;
    private Lztek mLztek;
    private SerialPort serialPort;
    private SerialPort batterySerialPort;
    private int mUVCLightStatus = OFF;
    private int mLEDStatus = OFF;
    private int mSensorStatus = OFF;
    private SerialPort speedSerialPort;
    private SerialPort tempSerialPort;
    private ArrayList<String> setArrayList = new ArrayList<>();
    private int flowId = 0;
    private int lowbatteryCount = 0;
    private int fullbatteryCount = 0;
    private int factoryRes = 0;
    private int openBattery = 0;

    /**
     * 248:充电
     * 249:前边sensor
     * 218:右边sensor
     * 250:uvc
     * 230:uvc灯
     * 228:uvc灯
     * 229:uvc灯
     * 251:led灯
     */
    private int[] port = new int[]{218, 248, 249, 250, 230, 228, 229, 251};
    private MoreSerialPortThread moreSerialPortThread;
    public boolean threadFlag = false;

    public CheckLztekLamp(Context mContext) {
        this.mContext = mContext;
        mLztek = Lztek.create(mContext);
    }

    private void setEnable() {
        for (int i = 0; i < port.length; i++) {
            mLztek.gpioEnable(port[i]);
        }
    }

    public void initUvcMode() {
        mLztek.setGpioOutputMode(port[1]);
        mLztek.setGpioValue(port[1], 1);
        for (int i = 3; i < 7; i++) {
            mLztek.setGpioOutputMode(port[i]);
            mLztek.setGpioValue(port[i], 1);
        }
    }

    public void setUvcMode(int flag) {
        if (Content.Working_mode == 2) {
            if (mUVCLightStatus != flag) {
                mUVCLightStatus = flag;
                for (int i = 3; i < 7; i++) {
                    mLztek.setGpioValue(port[i], flag);
                    try {
                        if (flag == 0 && SocketServices.battery < 40) {
                            if (getGpioSensorState()) {
                                for (int k = 3; k < 7; k++) {
                                    mLztek.setGpioValue(port[i], 1);
                                }
                            } else {
                                Thread.sleep(2000);
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void setUvcModeForDemo(int flag) {
        if (Content.Working_mode == 1) {
            if (mUVCLightStatus != flag) {
                mUVCLightStatus = flag;
                for (int i = 3; i < 7; i++) {
                    mLztek.setGpioValue(port[i], flag);
                    try {
                        if (flag == 0 && SocketServices.battery < 40) {
                            if (getGpioSensorState()) {
                                for (int k = 3; k < 7; k++) {
                                    mLztek.setGpioValue(port[i], 1);
                                }
                            } else {
                                Thread.sleep(2000);
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void setMode() {
        for (int i = 0; i < port.length; i++) {
            mLztek.setGpioInputMode(port[i]);
        }
    }

    public void setChargingGpio(int portInt) {
        mLztek.setGpioValue(port[1], portInt);
    }

    public boolean getChargingGpio() {

        Log.d("zdzd : ", "gpio 218 值: " + mLztek.getGpioValue(port[0]));
        if (Content.charging_gpio != mLztek.getGpioValue(port[0])) {
            Content.is_first_charging = true;
        } else {
            Content.is_first_charging = false;
        }
        Content.charging_gpio = mLztek.getGpioValue(port[0]);
        return mLztek.getGpioValue(port[0]) == 0 ? true : false;
    }

    public boolean getGpioSensorState() {

//        boolean k1 = mLztek.getGpioValue(port[0]) == 0 ? true : false;
//        boolean k2 = mLztek.getGpioValue(port[1]) == 0 ? true : false;
        boolean k3 = mLztek.getGpioValue(port[2]) == 0 ? true : false;
        Log.d("getGpioSensorState : ", "" + k3);
        if (k3) {
            Content.pir_timeCount = 0;
            return true;
        } else {
            Content.pir_timeCount++;
            if (Content.pir_timeCount >= 20) {
                return false;
            }
        }
        return true;
    }

    public void startCheckSensorAtTime() {
        setEnable();
        setMode();
    }

    public void startLedLamp() {
        mLztek.setGpioValue(port[7], 0);
        threadFlag = true;
        openSerialPort();
    }

    public void stopLedLamp() {
        mLztek.setGpioValue(port[7], 1);
        threadFlag = false;
    }

    public void startUvc1Lamp() {
        if (Content.Working_mode == 2) {
            mLztek.setGpioValue(port[5], 0);
        }
    }

    public void stopUvc1Lamp() {
        mLztek.setGpioValue(port[5], 1);
    }

    public void startUvc2Lamp() {
        if (Content.Working_mode == 2) {
            mLztek.setGpioValue(port[6], 0);
        }
    }

    public void stopUvc2Lamp() {
        mLztek.setGpioValue(port[6], 1);
    }

    public void startUvc3Lamp() {
        if (Content.Working_mode == 2) {
            mLztek.setGpioValue(port[4], 0);
        }
    }

    public void stopUvc3Lamp() {
        mLztek.setGpioValue(port[4], 1);
    }

    public void startUvc4Lamp() {
        if (Content.Working_mode == 2) {
            mLztek.setGpioValue(port[3], 0);
        }
    }

    public void stopUvc4Lamp() {
        mLztek.setGpioValue(port[3], 1);
    }

    public boolean getEthEnable() {
        return mLztek.getEthEnable();
    }

    public void openEth() {
        mLztek.setEthEnable(true);
    }

    public void setEthAddress() {
        mLztek.setEthIpAddress(Content.ip, Content.mask, Content.gateway, Content.dns);
    }

    public AddrInfo getAddrInfo() {
        return mLztek.getEthAddrInfo();
    }


    /**
     * 设置系统时间
     */
    public void setSystemTime(long milliseconds) {
        mLztek.setSystemTime(milliseconds);
    }

    /**
     * 请求led灯光
     */
    private void openSerialPort() {//控制Led灯
        File file = new File("/sys/class/rtc/rtc1");
        Log.d("zdzd 555", "文件 ： " + file.exists());
        if (file.exists()) {
            serialPort = mLztek.openSerialPort("/dev/ttyS3", 115200, 8, 0, 1, 0);
        } else {
            serialPort = mLztek.openSerialPort("/dev/ttyS1", 115200, 8, 0, 1, 0);
        }

        if (serialPort == null) {
            Log.d(TAG, "serialPort is null");
            return;
        }

        if (moreSerialPortThread == null) {
            Log.d(TAG, "myThread is null");
            moreSerialPortThread = new MoreSerialPortThread();
        }
        moreSerialPortThread.start();
    }

    /**
     * led线程
     */
    class MoreSerialPortThread extends Thread {
        int index = 0;
        String[] lightArray;
        OutputStream outputStream;

        @Override
        public void run() {
            super.run();
            while (threadFlag) {
                outputStream = serialPort.getOutputStream();
                try {
                    Log.d(TAG, "led robotstatus : " + Content.robotState);
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
                        case 4:
                            lightArray = mContext.getResources().getStringArray(R.array.charge_light);
                            break;
                        case 5:
                            lightArray = mContext.getResources().getStringArray(R.array.warning_light);
                            break;
                        case 6:
                            lightArray = mContext.getResources().getStringArray(R.array.low_power_light);
                            break;
                        default:
                            break;
                    }
                    if (index < lightArray.length - 1) {

                    } else {
                        index = 0;
                    }
                    byte[] colorLight = hexBytes(lightArray[index]);
                    if (null != colorLight) {
                        outputStream.write(colorLight);
                    }
                    if (index < lightArray.length - 1) {
                        index++;
                    } else {
                        index = 0;
                    }
                    Thread.sleep(Content.time);
                    if (Content.robotState == 1 || Content.robotState == 5 || Content.robotState == 6) {
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
     */
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
     */
    public void openBatteryPort() {//获取电池
        if (batterySerialPort == null) {
            Log.d(TAG, "BatteryPort is null");
            return;
        }
        handler.removeCallbacks(runnable);
        handler.postDelayed(runnable, 0);
    }

    public void restartBatteryPort() {
        openBattery++;
        if (openBattery > 10) {
            handler.removeCallbacks(runnable);
            handler.removeCallbacks(batteryFactoryRunnable);
            handler.removeCallbacks(batteryRunnable);
            batterySerialPort.close();
            readBatteryFactory();
        }
    }

    /**
     * 间隔1秒写入请求一次电池数据
     */
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "batteryRunnable : " + openBattery);
            restartBatteryPort();
            OutputStream outputStream = null;
            byte[] battery = hexBytes("DDA50300FFFD77");
            try {
                if (null != battery) {
                    outputStream = batterySerialPort.getOutputStream();
                    outputStream.write(battery);
                }
                handler.postDelayed(batteryRunnable, 0);
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
            handler.postDelayed(this::run, 1 * 1000);
        }
    };

    Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == 1001) {
                NavigationService.move(0.2f, 0.0f);
                handler.sendEmptyMessageDelayed(1001, 10);
            } else if (msg.what == 1002) {
                handler.removeMessages(1001);
                handler.removeMessages(1002);
            } else if (msg.what == 1003) {
                if (Content.is_initialize_finished == 1) {
                    TaskManager.getInstances(mContext).navigate_Position((String) msg.obj, Content.CHARGING_POINT);
                } else if (Content.is_initialize_finished == 0) {
                    Message message = handler.obtainMessage();
                    message.obj = msg.obj;
                    message.what = 1003;
                    handler.sendMessageDelayed(message, 1 * 1000);
                } else if (Content.is_initialize_finished == 2) {
                    NavigationService.initialize((String) msg.obj, Content.CHARGING_POINT);
                    Message message = handler.obtainMessage();
                    message.obj = msg.obj;
                    message.what = 1003;
                    handler.sendMessageDelayed(message, 1 * 1000);
                }
            }
        }
    };

    /**
     * 读取电池数据
     */
    Runnable batteryRunnable = new Runnable() {
        @Override
        public void run() {
            if (null == batterySerialPort) {
                return;
            }
            java.io.InputStream input = batterySerialPort.getInputStream();
            try {
                byte[] buffer = new byte[1024];
                int len = input.read(buffer);
                if (len > 0) {
                    byte[] data = java.util.Arrays.copyOfRange(buffer, 0, len);
                    EditText editText = new EditText(mContext);
                    for (byte b : data) {
                        byte h = (byte) (0x0F & (b >> 4));
                        byte l = (byte) (0x0F & b);
                        editText.append("" + (char) (h > 9 ? 'A' + (h - 10) : '0' + h));
                        editText.append("" + (char) (l > 9 ? 'A' + (l - 10) : '0' + l));
                    }
                    String two = editText.getText().toString().substring(8, 12);
                    Content.chargerVoltage = Integer.parseInt(two, 16) / 1000;
                    Log.d(TAG, "读取数据 ： " + editText.getText().toString() + " ,two : " + editText.getText().toString().substring(12, 14));
                    openBattery = 0;
                    EventBus.getDefault().post(new EventBusMessage(10033, data));
                    String msg = "放电";
                    Log.d("zdzd555:", "taskName : " + Content.taskName
                            + ", battery : " + SocketServices.battery
                            + ",leave : " + Content.is_leave_charging
                            + " , mapName : " + SharedPrefUtil.getInstance(mContext).getSharedPrefMapName(Content.MAP_NAME));
                    getChargingGpio();
                    /*if (Content.taskName == null) {
                        //充电桩充电 && 不在执行任务
                        // 当前正在充电 && 电量100 && 电流小于200---->离开充电桩
                        if (Content.charging_gpio == 0 && Content.isCharging
                                && SocketServices.battery >= 99) {
                            fullbatteryCount++;
                            if (fullbatteryCount >= 5) {
                                setLeaveChargingLimit();
                                handler.sendEmptyMessageDelayed(1001, 10 * 1000);
                                handler.sendEmptyMessageDelayed(1002, 12 * 1000);
                                Log.d("zdzd555 : ", "离开充电桩 ： " + Content.mapName);
                                Content.is_leave_charging = true;
                                fullbatteryCount = 0;
                            }

                        }
                        // 不在充电 && 没有离开充电桩 && 电量小于90% ---->回到充电桩
                        else if (Content.charging_gpio == 1 && !Content.isCharging
                                && SocketServices.battery < 90 && Content.is_leave_charging) {
                            lowbatteryCount++;
                            if (lowbatteryCount >= 5) {
                                String mapName = SharedPrefUtil.getInstance(mContext).getSharedPrefMapName(Content.MAP_NAME);
                                Log.d("zdzd555 : ", "导航到充点电 ： " + mapName);
                                NavigationService.initialize(mapName, Content.CHARGING_POINT);
                                Message message = handler.obtainMessage();
                                message.obj = mapName;
                                message.what = 1003;
                                handler.sendMessageDelayed(message, 3 * 1000);
                                lowbatteryCount = 0;
                                Content.is_leave_charging = false;
                            }

                        } else {
                            fullbatteryCount = 0;
                            lowbatteryCount = 0;
                        }
                    }*/
                    Log.d("zdzd555:", "Content.is_first_charging : " + Content.is_first_charging + ", charging : " + Content.isCharging
                            + ",  battery : " + SocketServices.battery + ", Content.chargingState : " + Content.chargingState);
                    //读gpio
                    if (editText.getText().toString().substring(12, 14).startsWith("F")) {
                        Content.noChargingCount++;
                    } else {
                        Content.noChargingCount = 0;
                    }

                    if (!Content.isCharging && Content.charging_gpio == 0 && Content.chargingState != 2) {
                        setChargingGpio(0);
                        Content.chargingState = 2;
                    }

                    Log.d("zdzd555 : ", "noChargingCount : " + Content.noChargingCount);
                    if (!editText.getText().toString().substring(12, 14).startsWith("F")) {
                        Content.robotState = 4;
                        Content.time = 200;
                        msg = "充电";
                        Content.isCharging = true;
                        Content.chargingState = 2;
                        EventBus.getDefault().post(new EventBusMessage(10000, msg));
                    } else if (Content.taskName != null || Content.noChargingCount >= 40) {
                        Content.noChargingCount = 0;
                        msg = "放电";
                        if (Content.robotState == 4) {
                            Content.robotState = 1;
                            Content.time = 4000;
                        }
                        //关闭gpio口
                        Log.d("zdzd555:", "关闭gpio口 : Content.chargingState = " + Content.chargingState + "charging = " + Content.isCharging);
                        if (Content.chargingState == 2 && Content.isCharging) {
                            setChargingGpio(1);
                            Content.chargingState = 0;
                            Log.d("zdzd555:", "关闭gpio口 : ");
                        }
                        Content.isCharging = false;
                        EventBus.getDefault().post(new EventBusMessage(10000, msg));
                    }
                }
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
    };

    /**
     * 请求速度和温度
     */
    public void getSpeed() {
        speedSerialPort = mLztek.openSerialPort(Content.limiting_path, 115200, 8, 0, 1, 0);
        if (speedSerialPort == null) {
            Log.d(TAG, "Speed is null");
            return;
        }
        handler.postDelayed(speedA, 0);
    }

    Runnable speedA = new Runnable() {
        @Override
        public void run() {
            OutputStream outputStream = null;
            byte[] speed = hexBytes(mContext.getString(R.string.temp_request).replace(" ", ""));
            try {
                if (null != speed) {
                    outputStream = speedSerialPort.getOutputStream();
                    outputStream.write(speed);
                    outputStream.flush();
                }
                java.io.InputStream input = speedSerialPort.getInputStream();
                try {
                    byte[] buffer = new byte[1024];
                    int len = input.read(buffer);
                    byte[] data = java.util.Arrays.copyOfRange(buffer, 0, len);
                    EditText editText = new EditText(mContext);
                    for (byte b : data) {
                        byte h = (byte) (0x0F & (b >> 4));
                        byte l = (byte) (0x0F & b);
                        editText.append("" + (char) (h > 9 ? 'A' + (h - 10) : '0' + h));
                        editText.append("" + (char) (l > 9 ? 'A' + (l - 10) : '0' + l));
                    }
                    Log.d(TAG, "getSpeedAndTemp11 : " + editText.getText().toString());
                    setLimiting(editText.getText().toString().substring(12, 16), editText.getText().toString().substring(16, 20));
                } catch (Exception e) {
                    e.printStackTrace();
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
            handler.postDelayed(this::run, 5000);
        }
    };

    private void setLimiting(String substring, String substring1) {
        setArrayList.clear();
        int aTemp = Integer.parseInt(substring, 16);
        int bTemp = Integer.parseInt(substring1, 16);
        Log.d("setLimiting ", "Content.limitint_init_flag : " + Content.limitint_init_flag
                + ",  Content.isLimiting_flag: " + Content.isLimiting_flag
                + ",   Content.limiting_flag : " + Content.charging_limiting_flag
                + ", temp :" + aTemp + "----" + bTemp);
        if (Content.limitint_init_flag == 0) {
            setArrayList.add(mContext.getString(R.string.a_password1));
            setArrayList.add(mContext.getString(R.string.a_flow_20A));
            setArrayList.add(mContext.getString(R.string.a_password2));

            setArrayList.add(mContext.getString(R.string.b_password1));
            setArrayList.add(mContext.getString(R.string.b_flow_20A));
            setArrayList.add(mContext.getString(R.string.b_password2));
            Content.limitint_init_flag = 1;
            Content.isLimiting_flag = 0;
            Content.isHightTemp = false;
            setSpeed();
        }
        if (Content.isLimiting_flag != 1 && (aTemp >= 58 || bTemp >= 58)) {//电流设置为0A
            Content.isHightTemp = true;
            Content.taskIsFinish = false;
            SocketServices.toLightControlBtn = false;
            TaskManager.getInstances(mContext).cancel_navigate();
            setArrayList.add(mContext.getString(R.string.a_password1));
            setArrayList.add(mContext.getString(R.string.a_flow_0A));
            setArrayList.add(mContext.getString(R.string.a_password2));

            setArrayList.add(mContext.getString(R.string.b_password1));
            setArrayList.add(mContext.getString(R.string.b_flow_0A));
            setArrayList.add(mContext.getString(R.string.b_password2));
            Content.isLimiting_flag = 1;
            setSpeed();
        } else if (Content.isLimiting_flag != 2 && (aTemp >= 55 || bTemp >= 55) && aTemp < 58 && bTemp < 58) {//电流设置为7A
            Content.isHightTemp = true;
            Content.taskIsFinish = false;
            SocketServices.toLightControlBtn = false;
            TaskManager.getInstances(mContext).cancel_navigate();
            setArrayList.add(mContext.getString(R.string.a_password1));
            setArrayList.add(mContext.getString(R.string.a_flow_7A));
            setArrayList.add(mContext.getString(R.string.a_password2));

            setArrayList.add(mContext.getString(R.string.b_password1));
            setArrayList.add(mContext.getString(R.string.b_flow_7A));
            setArrayList.add(mContext.getString(R.string.b_password2));
            Content.isLimiting_flag = 2;
            setSpeed();
        } else {
            if (Content.charging_gpio == 0) {
                if (Content.charging_limiting_flag == 0) {//设置为5A
                    Content.isHightTemp = false;
                    setArrayList.add(mContext.getString(R.string.a_password1));
                    setArrayList.add(mContext.getString(R.string.a_flow_5A));
                    setArrayList.add(mContext.getString(R.string.a_password2));

                    setArrayList.add(mContext.getString(R.string.b_password1));
                    setArrayList.add(mContext.getString(R.string.b_flow_5A));
                    setArrayList.add(mContext.getString(R.string.b_password2));
                    Content.charging_limiting_flag = 1;
                    setSpeed();
                } else {
                    Log.d("setLimiting ", "already limit current for charging!");
                }
            } else {
                if (Content.charging_limiting_flag == 1 && aTemp < 55 && bTemp < 55) {//设置为20A
                    setArrayList.add(mContext.getString(R.string.a_password1));
                    setArrayList.add(mContext.getString(R.string.a_flow_20A));
                    setArrayList.add(mContext.getString(R.string.a_password2));

                    setArrayList.add(mContext.getString(R.string.b_password1));
                    setArrayList.add(mContext.getString(R.string.b_flow_20A));
                    setArrayList.add(mContext.getString(R.string.b_password2));
                    Content.isHightTemp = false;
                    Content.charging_limiting_flag = 0;
                    setSpeed();
                } else {
                    if (Content.charging_limiting_flag == 1)
                        Log.d("setLimiting ", "Content.limiting_flag=" + Content.charging_limiting_flag
                                + ",aTemp=" + aTemp
                                + ",bTemp=" + bTemp);
                }

                if (Content.isLimiting_flag != 0 && aTemp <= 35 && bTemp <= 35) {//电流设置为20A
                    setArrayList.add(mContext.getString(R.string.a_flow_20A));
                    setArrayList.add(mContext.getString(R.string.a_password2));

                    setArrayList.add(mContext.getString(R.string.b_password1));
                    setArrayList.add(mContext.getString(R.string.b_flow_20A));
                    setArrayList.add(mContext.getString(R.string.b_password2));
                    Content.isHightTemp = false;
                    Content.isLimiting_flag = 0;
                    setSpeed();
                }
            }
        }
    }

    public void setLeaveChargingLimit() {
        setArrayList.clear();
        setArrayList.add(mContext.getString(R.string.a_flow_20A));
        setArrayList.add(mContext.getString(R.string.a_password2));

        setArrayList.add(mContext.getString(R.string.b_password1));
        setArrayList.add(mContext.getString(R.string.b_flow_20A));
        setArrayList.add(mContext.getString(R.string.b_password2));
        Content.isLimiting_flag = 0;
        setSpeed();
    }

    public void setSpeed() {
        speedSerialPort = mLztek.openSerialPort(Content.limiting_path, 115200, 8, 0, 1, 0);
        if (speedSerialPort == null) {
            Log.d(TAG, "Speed is null");
            return;
        }
        handler.postDelayed(setSpeedA, 0);
    }

    Runnable setSpeedA = new Runnable() {
        @Override
        public void run() {
            for (int i = 0; i < setArrayList.size(); i++) {
                OutputStream outputStream = null;
                byte[] speed = hexBytes(setArrayList.get(i));
                try {
                    if (null != speed) {
                        outputStream = speedSerialPort.getOutputStream();
                        outputStream.write(speed);
                        outputStream.flush();
                    }
                    java.io.InputStream input = speedSerialPort.getInputStream();
                    try {
                        byte[] buffer = new byte[1024];
                        int len = input.read(buffer);
                        byte[] data = java.util.Arrays.copyOfRange(buffer, 0, len);
                        EditText editText = new EditText(mContext);
                        for (byte b : data) {
                            byte h = (byte) (0x0F & (b >> 4));
                            byte l = (byte) (0x0F & b);
                            editText.append("" + (char) (h > 9 ? 'A' + (h - 10) : '0' + h));
                            editText.append("" + (char) (l > 9 ? 'A' + (l - 10) : '0' + l));
                        }
                        Log.d(TAG, "getSpeedAndTemp22 : " + editText.getText().toString());
                        Thread.sleep(100);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    public void readBatteryFactory() {
        File file = new File("/sys/class/rtc/rtc1");
        Log.d("zdzd 555", "文件 ： " + file.exists());
        if (file.exists()) {
            batterySerialPort = mLztek.openSerialPort("/dev/ttyS1", 9600, 8, 0, 1, 0);
        } else {
            batterySerialPort = mLztek.openSerialPort("/dev/ttyS0", 9600, 8, 0, 1, 0);
        }
        if (batterySerialPort == null) {
            Log.d(TAG, "BatteryPort is null");
            return;
        }
        factoryRes = R.string.open_factory;
        handler.postDelayed(batteryFactoryRunnable, 0);
    }

    Runnable batteryFactoryRunnable = new Runnable() {
        @Override
        public void run() {
            OutputStream outputStream = null;
            byte[] openFactory = hexBytes(mContext.getString(factoryRes).replace(" ", ""));
            try {
                if (null != openFactory) {
                    outputStream = batterySerialPort.getOutputStream();
                    outputStream.write(openFactory);
                    outputStream.flush();
                }
                handler.postDelayed(readFactoryresult, 100);
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
    };

    Runnable readFactoryresult = new Runnable() {
        @Override
        public void run() {
            java.io.InputStream input = batterySerialPort.getInputStream();
            try {
                byte[] buffer = new byte[1024];
                int len = input.read(buffer);
                byte[] data = java.util.Arrays.copyOfRange(buffer, 0, len);
                EditText editText = new EditText(mContext);
                for (byte b : data) {
                    byte h = (byte) (0x0F & (b >> 4));
                    byte l = (byte) (0x0F & b);
                    editText.append("" + (char) (h > 9 ? 'A' + (h - 10) : '0' + h));
                    editText.append("" + (char) (l > 9 ? 'A' + (l - 10) : '0' + l));
                }
                Log.d(TAG, "Factory : " + mContext.getString(factoryRes) + "------" + editText.getText().toString());
                if ("DD 00 00 00 00 00 77".replace(" ", "").equals(editText.getText().toString())) {
                    factoryRes = R.string.read_battery_an;
                    handler.postDelayed(batteryFactoryRunnable, 0);
                } else if ("DD 28 00 02 07 D0 FF 27 77".replace(" ", "").equals(editText.getText().toString())) {
                    factoryRes = R.string.write_25a_battery;
                    handler.postDelayed(batteryFactoryRunnable, 0);
                } else if ("DD 28 00 00 00 00 77".replace(" ", "").equals(editText.getText().toString())) {
                    factoryRes = R.string.close_factory;
                    handler.postDelayed(batteryFactoryRunnable, 0);
                } else if ("DD 28 00 02 09 C4 FF 31 77".replace(" ", "").equals(editText.getText().toString())) {
                    factoryRes = R.string.close_factory;
                    handler.postDelayed(batteryFactoryRunnable, 0);
                } else if (!TextUtils.isEmpty(editText.getText().toString())) {
                    handler.removeCallbacks(batteryFactoryRunnable);
                    openBatteryPort();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    };

    //TEST
    public String testGpioSensorState() {
        //boolean k1 = mLztek.getGpioValue(port[0]) == 0 ? true : false;
        //boolean k2 = mLztek.getGpioValue(port[1]) == 1 ? true : false;
        boolean k3 = mLztek.getGpioValue(port[2]) == 0 ? true : false;
        return "" + k3;
    }

    public void test_uvc_start1() {
        mLztek.setGpioValue(port[5], 0);
    }

    public void test_uvc_start2() {
        mLztek.setGpioValue(port[6], 0);
    }

    public void test_uvc_start3() {
        mLztek.setGpioValue(port[4], 0);
    }

    public void test_uvc_start4() {
        mLztek.setGpioValue(port[3], 0);
    }

    public void test_uvc_startAll() {
        test_uvc(0);
    }

    public void test_uvc_stopAll() {
        test_uvc(1);
    }

    private void test_uvc(int flag) {
        for (int i = 3; i < 7; i++) {
            mLztek.setGpioValue(port[i], flag);
//            try {
//                if (flag == 0) {
//                    Thread.sleep(2000);
//                }
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
        }
    }

    public void test_uvc_stop1() {
        stopUvc1Lamp();
    }

    public void test_uvc_stop2() {
        stopUvc2Lamp();
    }

    public void test_uvc_stop3() {
        stopUvc3Lamp();
    }

    public void test_uvc_stop4() {
        stopUvc4Lamp();
    }

    public int getUVCStatus() {
        return mUVCLightStatus;
    }
}
