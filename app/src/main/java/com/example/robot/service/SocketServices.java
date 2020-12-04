package com.example.robot.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dcm360.controller.gs.controller.bean.PositionListBean;
import com.dcm360.controller.gs.controller.bean.data_bean.RobotDeviceStatus;
import com.dcm360.controller.gs.controller.bean.data_bean.RobotPositions;
import com.dcm360.controller.gs.controller.bean.map_bean.RobotMap;
import com.dcm360.controller.gs.controller.bean.map_bean.RobotPosition;
import com.dcm360.controller.gs.controller.bean.paths_bean.RobotTaskQueueList;
import com.dcm360.controller.gs.controller.bean.paths_bean.UpdataVirtualObstacleBean;
import com.dcm360.controller.gs.controller.bean.paths_bean.VirtualObstacleBean;
import com.example.robot.R;
import com.example.robot.bean.PointStateBean;
import com.example.robot.bean.SaveTaskBean;
import com.example.robot.sqlite.SqLiteOpenHelperUtils;
import com.example.robot.task.TaskManager;
import com.example.robot.utils.AlarmUtils;
import com.example.robot.utils.Content;
import com.example.robot.utils.EventBusMessage;
import com.example.robot.utils.GsonUtils;
import com.example.robot.utils.SharedPrefUtil;
import com.example.robot.utils.TimeUtils;
import com.example.robot.utils.VirtualBeanUtils;
import com.example.robot.uvclamp.CheckLztekLamp;
import com.example.robot.uvclamp.UvcWarning;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class SocketServices extends Service {

    private static final String TAG = "SocketServices";
    private NavigationService navigationService;
    private Intent intentService;
    private Context mContext;
    private ImageView robot_Position;
    private UvcWarning uvcWarning;
    private CheckLztekLamp checkLztekLamp;
    private GsonUtils gsonUtils;
    private String[] spinner;
    private boolean toLightControlBtn = false;
    private boolean completeFlag = false;
    private int ledtime = 0;
    private Long workTime;
    private String tvText;
    private TimeUtils mTimeUtils;
    public static MyHandler myHandler;
    private long pauseTime = 0;
    private byte battery = 0;
    private boolean isTaskFlag = false;
    private float x;
    private float y;
    private double angle;
    private String spinnerString;
    private boolean isDevelop = false;
    private boolean threadDestory = false;
    private VirtualBeanUtils mVirtualBeanUtils;
    private SqLiteOpenHelperUtils mSqLiteOpenHelperUtils;
    private AlarmUtils mAlarmUtils;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //handler.sendEmptyMessage(1);
        Log.d("zdzd --- ", "server oncreate");
        EventBus.getDefault().register(this);
        mContext = this;
        spinner = mContext.getResources().getStringArray(R.array.spinner_time);
        handler.sendEmptyMessage(1);

        robot_Position = new ImageView(mContext);

        initView();
    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == 1) {
                Log.d("c : ", "getEthEnable " + checkLztekLamp.getEthEnable());
                if (checkLztekLamp.getEthEnable()) {
                    navigationService = new NavigationService();
                    intentService = new Intent(getApplicationContext(), NavigationService.class);
                    startService(intentService);
                    threadDestory = true;
                    handler.sendEmptyMessageDelayed(2, 30 * 1000);

                } else {
                    handler.sendEmptyMessageDelayed(1, 1000);
                }
            } else if (msg.what == 2) {

            }
        }
    };

    private void initView() {
        uvcWarning = new UvcWarning(mContext);
        checkLztekLamp = new CheckLztekLamp(mContext);

        gsonUtils = new GsonUtils();
        myHandler = new MyHandler(SocketServices.this);
        checkLztekLamp.openBatteryPort();
        if (!checkLztekLamp.getEthEnable()) {
            Log.d(TAG, "网络设置失败");
        } else {
            Log.d(TAG, "网络设置成功");
        }
        mTimeUtils = new TimeUtils(this);
        mVirtualBeanUtils = new VirtualBeanUtils(this);
        mSqLiteOpenHelperUtils = new SqLiteOpenHelperUtils(this);
        mAlarmUtils = new AlarmUtils(this);

        checkLztekLamp = new CheckLztekLamp(this);
        Content.robotState = 1;
        Content.time = 4000;
        checkLztekLamp.startCheckSensorAtTime();
        checkLztekLamp.startLedLamp();
        checkLztekLamp.openEth();
        checkLztekLamp.setEthAddress();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("zdzd --- ", "server onDestroy");
        EventBus.getDefault().unregister(this);
        try {
            Content.server.stop();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        threadDestory = false;
        stopService(intentService);
    }

    public void onCheckedChanged(int index) {
        if (toLightControlBtn) {
            completeFlag = false;
            ledtime = 0;
            uvcWarning.startWarning();
            Content.robotState = 5;
            Content.time = 1000;
            String spinnerItem = (String) spinner[index];
            workTime = System.currentTimeMillis() +
                    Long.parseLong(spinnerItem.substring(0, spinnerItem.length() - 2)) * 60 * 1000 + 10 * 1000;
            Log.d(TAG, "onCheckedChanged：workTime : " + workTime);
        } else {
            ledtime = 0;
            Content.robotState = 1;
            Content.time = 4000;
            uvcWarning.stopWarning();
            checkLztekLamp.stopUvc1Lamp();
            checkLztekLamp.stopUvc2Lamp();
            checkLztekLamp.stopUvc3Lamp();
            tvText = mTimeUtils.calculateDays(System.currentTimeMillis());
            gsonUtils.setTvTime(tvText);
            if (Content.server != null) {
                Content.server.broadcast(gsonUtils.putTVTime(Content.TV_TIME));
            }
        }
        myHandler.sendEmptyMessageDelayed(1, 0);
    }

    public class MyHandler extends Handler {//防止内存泄漏
        //持有弱引用MainActivity,GC回收时会被回收掉.
        private final WeakReference<SocketServices> mAct;

        private MyHandler(SocketServices socketServices) {
            mAct = new WeakReference<SocketServices>(socketServices);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 1:
                    Log.d(TAG, "case 1  " + workTime);
                    startLoopDetection();
                    break;
                case 2:
                    Log.d(TAG, "case 2  " + completeFlag);

                    if (!completeFlag) {
                        startUvcDetection();
                        tvText = mTimeUtils.calculateDays(workTime);
                        gsonUtils.setTvTime(tvText);
                        if (Content.server != null) {
                            Content.server.broadcast(gsonUtils.putTVTime(Content.TV_TIME));
                        }

                    } else {
                        gsonUtils.setTvTime("消毒完成");
                        if (Content.server != null) {
                            Content.server.broadcast(gsonUtils.putTVTime(Content.TV_TIME));
                        }
                        toLightControlBtn = false;
                        onCheckedChanged(0);
                        checkLztekLamp.stopUvc1Lamp();
                        checkLztekLamp.stopUvc2Lamp();
                        checkLztekLamp.stopUvc3Lamp();
                        Content.robotState = 1;
                        Content.time = 4000;
                        completeFlag = false;
                        Log.d(TAG, "恢复任务" + Content.taskState);
                        if (Content.taskState == 2) {
                            TaskManager.getInstances(mContext).resumeTaskQueue();
                        }
                    }
                    break;
                case 3:
                    Log.d(TAG, "case 3  " + workTime);
                    startUvcDetection();
                    break;
                case 4:
                    Log.d(TAG, "case 4  " + workTime);
                    gsonUtils.setTvTime("电量回充,消毒未完成");
                    if (Content.server != null) {
                        Content.server.broadcast(gsonUtils.putTVTime(Content.TV_TIME));
                    }
                    checkLztekLamp.stopUvc1Lamp();
                    checkLztekLamp.stopUvc2Lamp();
                    checkLztekLamp.stopUvc3Lamp();

                    Content.robotState = 6;
                    Content.time = 4000;
                    if (Content.taskState == 1) {
                        Content.taskState = 3;
                        TaskManager.getInstances(mContext).pauseTaskQueue();
                    }
                    TaskManager.getInstances(mContext).navigate_Position(Content.mapName, "Charing");
                    break;
                case 5:
                    break;

                default:
                    break;
            }

        }
    }

    /**
     * 10秒的sensor检查
     */
    private void startLoopDetection() {
        Log.d(TAG, "startLoopDetection: " + (10 - ledtime) + "秒");
        tvText = (10 - ledtime) + "秒";
        gsonUtils.setTvTime(tvText);
        if (Content.server != null) {
            Content.server.broadcast(gsonUtils.putTVTime(Content.TV_TIME));
        }
        if (battery < Content.battery) {//是否到达回冲电量
            myHandler.sendEmptyMessageDelayed(4, 1000);
        } else {
            ledtime++;
            if (!toLightControlBtn) {
                ledtime = 0;
                return;
            }
            if (ledtime <= 10) {
                Log.d(TAG, "正在警告");
                if (checkLztekLamp.getGpioSensorState()) {
                    //有人靠近
                    Log.v(TAG, "10秒重置");
                    ledtime = 0;
                }
            } else {
                Log.d(TAG, "警告结束，关闭警告和led，开启uvc灯");
                ledtime = 0;
                uvcWarning.stopWarning();
                Content.robotState = 5;
                Content.time = 1000;
                startUvcDetection();
                return;
            }
            myHandler.sendEmptyMessageDelayed(1, 1000);
        }
    }

    /**
     * 开启uvc灯
     */
    private void startUvcDetection() {
        Log.d(TAG, "startUvcDetection" + battery);
        if (!toLightControlBtn) {
            return;
        }
        if (battery < Content.battery) {//是否到达回冲电量
            myHandler.sendEmptyMessageDelayed(4, 1000);
        } else if (!checkLztekLamp.getGpioSensorState() && !isTaskFlag) {
            Log.d(TAG, "startUvcDetection" + "关led灯,开uvc灯");
            if (pauseTime != 0) {
                workTime = workTime + System.currentTimeMillis() - pauseTime;
            }
            pauseTime = 0;
            uvcWarning.stopWarning();
            Content.robotState = 5;
            Content.time = 1000;
            checkLztekLamp.setUvcMode();
            checkLztekLamp.startUvc1Lamp();
            checkLztekLamp.startUvc2Lamp();
            checkLztekLamp.startUvc3Lamp();
            myHandler.sendEmptyMessageDelayed(2, 1000);
        } else {
            if (pauseTime == 0) {
                pauseTime = System.currentTimeMillis();
            }
            Log.d(TAG, "startUvcDetection" + "开led灯,关uvc灯");
            uvcWarning.startWarning();
            Content.robotState = 5;
            Content.time = 1000;
            checkLztekLamp.stopUvc1Lamp();
            checkLztekLamp.stopUvc2Lamp();
            checkLztekLamp.stopUvc3Lamp();
            myHandler.sendEmptyMessageDelayed(3, 1000);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMsg(EventBusMessage messageEvent) {
        onBaseEventMessage(messageEvent);
    }


    protected void onBaseEventMessage(EventBusMessage messageEvent) {
        //phone 发送的命令
        if (messageEvent.getState() == 10000) {//callback信息的返回
            if (Content.server != null) {
                gsonUtils.setCallback((String) messageEvent.getT());
                Content.server.broadcast(gsonUtils.putCallBackMsg(Content.REQUEST_MSG));
            }
        } else if (messageEvent.getState() == 10001) {//后退
            handler1.postDelayed(runnable1, 10);
            Content.time = 300;
            Content.robotState = 3;
        } else if (messageEvent.getState() == 10002) {//前进
            handler2.postDelayed(runnable2, 10);
            Content.time = 300;
            Content.robotState = 3;
        } else if (messageEvent.getState() == 10003) {//左转
            handler3.postDelayed(runnable3, 10);
            Content.time = 300;
            Content.robotState = 3;
        } else if (messageEvent.getState() == 10004) {//右转
            handler4.postDelayed(runnable4, 10);
            Content.time = 300;
            Content.robotState = 3;
        } else if (messageEvent.getState() == 10005) {//开始消毒检测
            int index = (int) messageEvent.getT();
            toLightControlBtn = true;
            onCheckedChanged(index);
        } else if (messageEvent.getState() == 10006) {//停止消毒检测
            toLightControlBtn = false;
            onCheckedChanged(0);
        } else if (messageEvent.getState() == 10007) {//停前
            handler2.removeCallbacks(runnable2);
            Content.robotState = 1;
            Content.time = 4000;
        } else if (messageEvent.getState() == 10008) {//停退
            handler1.removeCallbacks(runnable1);
            Content.robotState = 1;
            Content.time = 4000;
        } else if (messageEvent.getState() == 10009) {//停左
            handler3.removeCallbacks(runnable3);
            Content.robotState = 1;
            Content.time = 4000;
        } else if (messageEvent.getState() == 10010) {//停右
            handler4.removeCallbacks(runnable4);
            Content.robotState = 1;
            Content.time = 4000;
        } else if (messageEvent.getState() == 10011) {//地图列表
            TaskManager.getInstances(mContext).loadMapList();
        } else if (messageEvent.getState() == 10012) {//地图列表获取后发送
            RobotMap robotMap = (RobotMap) messageEvent.getT();
            if (Content.server != null) {
                Content.server.broadcast(gsonUtils.putMapListMessage(Content.SENDMAPNAME, robotMap));
            }
        } else if (messageEvent.getState() == 10013) {//存储任务队列
            String messageEventT = (String) messageEvent.getT();
            JSONObject jsonObject = null;
            try {
                jsonObject = new JSONObject(messageEventT);
                String taskName = jsonObject.getString(Content.TASK_NAME);
                List<SaveTaskBean> points = new ArrayList<>();
                for (int i = 0; i < jsonObject.getJSONArray(Content.SAVETASKQUEUE).length(); i++) {
                    SaveTaskBean saveTaskBean = new SaveTaskBean();
                    JSONObject jsonObject1 = (JSONObject) jsonObject.getJSONArray(Content.SAVETASKQUEUE).get(i);
                    saveTaskBean.setPositionName(jsonObject1.getString(Content.POINT_NAME));
                    saveTaskBean.setTime(jsonObject1.getInt(Content.SPINNERTIME));
                    points.add(saveTaskBean);
                }
                TaskManager.getInstances(mContext).save_taskQueue(Content.mapName, taskName, points);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (messageEvent.getState() == 10014) {//删除任务队列
            String taskName = (String) messageEvent.getT();
            TaskManager.getInstances(mContext).deleteTaskQueue(Content.mapName, taskName);
            mSqLiteOpenHelperUtils.deleteAlarmTask(Content.mapName+","+taskName);
        } else if (messageEvent.getState() == 10015) {//获取任务列表
            TaskManager.getInstances(mContext).getTaskQueues(Content.mapName);
        } else if (messageEvent.getState() == 10016) {//返回任务列表
            RobotTaskQueueList robotTaskQueueList = (RobotTaskQueueList) messageEvent.getT();
            List<String> list = new ArrayList<>();
            for (int i = 0; i < robotTaskQueueList.getData().size(); i++) {
                list.add(robotTaskQueueList.getData().get(i).getName());
            }
            gsonUtils.setData(list);
            if (Content.server != null) {
                Content.server.broadcast(gsonUtils.putJsonMessage(Content.SENDTASKQUEUE));
            }
        } else if (messageEvent.getState() == 10017) {//返回地图点数据
            RobotPositions robotPositions = (RobotPositions) messageEvent.getT();
            gsonUtils.setmRobotPositions(robotPositions);
            if (Content.server != null) {
                Content.server.broadcast(gsonUtils.putJsonMessage(Content.SENDPOINTPOSITION));
            }
        } else if (messageEvent.getState() == 10019) {//请求地图图片
            TaskManager.getInstances(mContext).getMapPic((String) messageEvent.getT());
        } else if (messageEvent.getState() == 10020) {//返回地图图片
            byte[] bytes = (byte[]) messageEvent.getT();
            if (Content.server != null) {
                Content.server.broadcast(bytes);
            }
        } else if (messageEvent.getState() == 10021) {//添加点
            String s = (String) messageEvent.getT();
            try {
                JSONObject jsonObject = new JSONObject(s);
                PositionListBean positionListBean = new PositionListBean();
                positionListBean.setName(jsonObject.getString(Content.POINT_NAME));
                positionListBean.setGridX((int) x);
                positionListBean.setGridY((int) y);
                positionListBean.setAngle(angle);
                positionListBean.setType(2);
                positionListBean.setMapName(Content.mapName);
                TaskManager.getInstances(mContext).add_Position(positionListBean);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (messageEvent.getState() == 10022) {//开始任务
            TaskManager.getInstances(mContext).startTaskQueue(Content.mapName, Content.taskName);
            selectAlarmSqlite(Content.mapName, Content.taskName);
        } else if (messageEvent.getState() == 10023) {//停止任务
            TaskManager.getInstances(mContext).stopTaskQueue(Content.mapName);
            toLightControlBtn = false;
            onCheckedChanged(0);
        } else if (messageEvent.getState() == 10024) {//返回机器人位置
            RobotPosition robotPosition = (RobotPosition) messageEvent.getT();
            x = (float) robotPosition.getGridPosition().getX();
            y = (float) robotPosition.getGridPosition().getY();
            gsonUtils.setX((double) robotPosition.getGridPosition().getX());
            gsonUtils.setY((double) robotPosition.getGridPosition().getY());
            gsonUtils.setGridHeight((int) robotPosition.getMapInfo().getGridHeight());
            gsonUtils.setGridWidth((int) robotPosition.getMapInfo().getGridWidth());
            gsonUtils.setOriginX((double) robotPosition.getMapInfo().getOriginX());
            gsonUtils.setOriginY((double) robotPosition.getMapInfo().getOriginY());
            gsonUtils.setResolution((double) robotPosition.getMapInfo().getResolution());
            gsonUtils.setAngle((double) robotPosition.getAngle());
            if (Content.server != null) {
                Content.server.broadcast(gsonUtils.putRobotPosition(Content.SENDGPSPOSITION));
            }
        } else if (messageEvent.getState() == 10025) {//开始扫描地图
            TaskManager.getInstances(mContext).start_scan_map((String) messageEvent.getT());
        } else if (messageEvent.getState() == 10026) {//选定地图
            TaskManager.getInstances(mContext).getMapPic(Content.mapName);
            TaskManager.getInstances(mContext).use_map(Content.mapName);
            myHandler.removeCallbacks(runnablePosition);
            myHandler.postDelayed(runnablePosition, 1000);
        } else if (messageEvent.getState() == 10027) {//转圈初始化结果
            Log.d("zdzd ", "初始化结果： " + (String) messageEvent.getT() + ",     isDevelop :" + isDevelop);
            if ("successed".equals((String) messageEvent.getT()) && isDevelop) {
                handlerInitialize.postDelayed(runnableInitialize, 1000);
            }
            if (Content.server != null) {
                Content.server.broadcast((String) messageEvent.getT());
            }
        } else if (messageEvent.getState() == 10028) {//请求地图点列表
            TaskManager.getInstances(mContext).getPosition((String) messageEvent.getT());
        } else if (messageEvent.getState() == 10029) {//取消扫描地图并保存
            TaskManager.getInstances(mContext).stopScanMap();
            isDevelop = false;
        } else if (messageEvent.getState() == 10030) {//拓展地图
            isDevelop = true;
            NavigationService.initialize(Content.mapName);
        } else if (messageEvent.getState() == 10031) {//删除地图
            TaskManager.getInstances(mContext).deleteMap((String) messageEvent.getT());
        } else if (messageEvent.getState() == 10032) {//删除点
            TaskManager.getInstances(mContext).deletePosition(Content.mapName, (String) messageEvent.getT());
        } else if (messageEvent.getState() == 10033) {//电池电量
            byte[] bytes = (byte[]) messageEvent.getT();
            battery = bytes[23];
            if (Content.server != null) {
                gsonUtils.setBattery(battery + "%");
                Content.server.broadcast(gsonUtils.putBattery(Content.BATTERY_DATA));
            }
        } else if (messageEvent.getState() == 10034) {
            Log.d("zdzd :", "是否完成初始化" + (String) messageEvent.getT());
            if ("true".equals((String) messageEvent.getT())) {
                handlerInitialize.removeCallbacks(runnableInitialize);
                EventBus.getDefault().post(new EventBusMessage(10000, mContext.getResources().getString(R.string.finish_initialize)));
                TaskManager.getInstances(mContext).start_develop_map(Content.mapName);
            } else {
                handler1.postDelayed(runnableInitialize, 1000);
                EventBus.getDefault().post(new EventBusMessage(10000, mContext.getResources().getString(R.string.is_initialize)));
            }
        } else if (messageEvent.getState() == 10035) {
            Log.d("zdzd :", "是否完成初始化error: " + (String) messageEvent.getT());
            EventBus.getDefault().post(new EventBusMessage(10000, mContext.getResources().getString(R.string.fail_initialize)) + (String) messageEvent.getT());
            handlerInitialize.removeCallbacks(runnableInitialize);
        } else if (messageEvent.getState() == 10036) {//取消扫描不保存地图
            TaskManager.getInstances(mContext).cancleScanMap();
        } else if (messageEvent.getState() == 10037) {//系统健康
            if (Content.server != null) {
                Log.d("socket_healthy: ", (String) messageEvent.getT());
                gsonUtils.setHealthyMsg((String) messageEvent.getT());
                Content.server.broadcast(gsonUtils.putSocketHealthyMsg(Content.ROBOT_HEALTHY));
            }
        } else if (messageEvent.getState() == 10038) {//任务状态
            if (Content.server != null) {
                gsonUtils.setTaskState((PointStateBean) messageEvent.getT());
                Content.server.broadcast(gsonUtils.putSocketTaskMsg(Content.ROBOT_TASK_STATE));
            }
        } else if (messageEvent.getState() == 10039) {//任务历史
            if (Content.server != null) {
                Content.server.broadcast(gsonUtils.putSocketTaskHistory(Content.ROBOT_TASK_HISTORY, mContext));
            }
        } else if (messageEvent.getState() == 10041) {//获取虚拟强数据
            TaskManager.getInstances(mContext).getVirtual_obstacles((String) messageEvent.getT());
        } else if (messageEvent.getState() == 10042) {//返回虚拟强数据
            VirtualObstacleBean virtualObstacleBean = (VirtualObstacleBean) messageEvent.getT();
            gsonUtils.setVirtualObstacleBean(virtualObstacleBean);
            if (Content.server != null) {
                Content.server.broadcast(gsonUtils.putVirtualObstacle(Content.SEND_VIRTUAL));
            }
        } else if (messageEvent.getState() == 10043) {//更新虚拟强
            mVirtualBeanUtils.updateVirtual(4, Content.mapName, Content.carpets, null);
            mVirtualBeanUtils.updateVirtual(6, Content.mapName, Content.decelerations, null);
            mVirtualBeanUtils.updateVirtual(1, Content.mapName, Content.slopes, null);
            mVirtualBeanUtils.updateVirtual(5, Content.mapName, Content.displays, null);
            String message = (String) messageEvent.getT();
            //最外边「」
            List<List<UpdataVirtualObstacleBean.ObstaclesEntity.PolylinesEntity>> polylinesEntities = new ArrayList<>();
            //每个「」
            List<UpdataVirtualObstacleBean.ObstaclesEntity.PolylinesEntity> polylinesEntitiesList = new ArrayList<>();
            try {
                JSONObject jsonObject = new JSONObject(message);
                JSONArray jsonArray = jsonObject.getJSONArray(Content.UPDATA_VIRTUAL);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONArray jsarray = jsonArray.getJSONArray(i);
                    for (int j = 0; j < jsarray.length(); j++) {
                        JSONObject js = jsarray.getJSONObject(j);
                        UpdataVirtualObstacleBean.ObstaclesEntity.PolylinesEntity polylinesEntity = new UpdataVirtualObstacleBean.ObstaclesEntity.PolylinesEntity();
                        polylinesEntity.setX(js.getDouble(Content.VIRTUAL_X));
                        polylinesEntity.setY(js.getDouble(Content.VIRTUAL_Y));
                        polylinesEntitiesList.add(polylinesEntity);
                    }
                    polylinesEntities.add(polylinesEntitiesList);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mVirtualBeanUtils.updateVirtual(0, Content.mapName, Content.obstacles, polylinesEntities);
        } else if (messageEvent.getState() == 10044) {//定时任务
            String message = (String) messageEvent.getT();
            try {
                JSONObject jsonObject = new JSONObject(message);
                JSONArray jsonArray = jsonObject.getJSONArray(Content.dbAlarmCycle);
                if (jsonObject.getBoolean(Content.TASK_TYPE)) {
                    for (int i = 0; i < jsonArray.length(); i++) {
                        mSqLiteOpenHelperUtils.saveAlarmTask(
                                Content.mapName + "," + jsonObject.getString(Content.dbAlarmMapTaskName),
//                                jsonObject.getString(Content.dbAlarmTime),
                                (System.currentTimeMillis() + 2 * 60 * 1000) + "",
                                jsonArray.getString(i),
                                "false");
                    }
                    mSqLiteOpenHelperUtils.close();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (messageEvent.getState() == 10045) {//重命名地图
            String message = (String) messageEvent.getT();
            try {
                JSONObject jsonObject = new JSONObject(message);
                TaskManager.getInstances(mContext).renameMapName(jsonObject.getString(Content.OLD_MAP_NAME),
                        jsonObject.getString(Content.NEW_MAP_NAME));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (messageEvent.getState() == 10046) {//设置导航速度
            int level = (int) messageEvent.getT();
            TaskManager.getInstances(mContext).setSpeedLevel(level);
        } else if (messageEvent.getState() == 10047) {//重命名点
            String message = (String) messageEvent.getT();
            try {
                JSONObject jsonObject = new JSONObject(message);
                TaskManager.getInstances(mContext).renamePosition(jsonObject.getString(Content.MAP_NAME),
                        jsonObject.getString(Content.OLD_POSITION_NAME),
                        jsonObject.getString(Content.NEW_POSITION_NAME));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (messageEvent.getState() == 10048) {//获取设备信息
            TaskManager.getInstances(mContext).deviceStatus();
        } else if (messageEvent.getState() == 10049) {//返回设备信息
            RobotDeviceStatus robotDeviceStatus = (RobotDeviceStatus) messageEvent.getT();
            if (Content.server != null) {
                gsonUtils.setLevel(robotDeviceStatus.getData().getNavigationSpeedLevel());
                Content.server.broadcast(gsonUtils.putVirtualObstacle(Content.SEND_SPEED_LEVEL));
            }
        } else if (messageEvent.getState() == 10050) {//添加充电点
            PositionListBean positionListBean = new PositionListBean();
            positionListBean.setName(Content.CHARING_POINT);
            positionListBean.setGridX((int) x);
            positionListBean.setGridY((int) y);
            positionListBean.setAngle(angle);
            positionListBean.setType(1);
            positionListBean.setMapName(Content.mapName);
            TaskManager.getInstances(mContext).add_Position(positionListBean);
        } else if (messageEvent.getState() == 10051) {//编辑任务
            String taskName = (String) messageEvent.getT();
            TaskManager.getInstances(mContext).getTaskPositionMsg(Content.mapName, taskName);
            if (Content.server != null) {
                String taskMsg = SharedPrefUtil.getInstance(mContext, Content.mapName).getPositionMsg(taskName);
                gsonUtils.setEditTask(taskMsg);
                Content.server.broadcast(gsonUtils.putJsonMessage(Content.EDITTASKQUEUE));
            }
        }

//test request
        else if (messageEvent.getState() == 20001) {
            checkLztekLamp.setUvcMode();
            checkLztekLamp.startUvc1Lamp();
            checkLztekLamp.startUvc2Lamp();
            checkLztekLamp.startUvc3Lamp();
        } else if (messageEvent.getState() == 20002) {
            checkLztekLamp.stopUvc1Lamp();
            checkLztekLamp.stopUvc2Lamp();
            checkLztekLamp.stopUvc3Lamp();
        } else if (messageEvent.getState() == 20003) {
            checkLztekLamp.startLedLamp();
        } else if (messageEvent.getState() == 20004) {
            checkLztekLamp.stopLedLamp();
        } else if (messageEvent.getState() == 20005) {
            String s = checkLztekLamp.testGpioSensorState();
            Log.d("zdzd : ", "sensor返回值： " + s);
            gsonUtils.setTestCallBack(s);
            if (Content.server != null) {
                Content.server.broadcast(gsonUtils.putTestSensorCallBack(Content.TEST_SENSOR_CALLBACK));
            }
        } else if (messageEvent.getState() == 20006) {
            uvcWarning.startWarning();
        } else if (messageEvent.getState() == 20007) {
            uvcWarning.stopWarning();
        }
    }

    private void selectAlarmSqlite(String mapName, String taskName) {
        Cursor aTrue = mSqLiteOpenHelperUtils.searchAlarmTask("false");
        while (aTrue.moveToNext()) {
            Log.d("Cursor aTrue", aTrue.getString(aTrue.getColumnIndex(Content.dbAlarmMapTaskName)) + ",   " + aTrue.getString(aTrue.getColumnIndex(Content.dbAlarmIsRun)));
        }

        mSqLiteOpenHelperUtils.updateAlarmTask(mapName+","+taskName, "true");
        Cursor aTrue111 = mSqLiteOpenHelperUtils.searchAlarmTask("true");
        while (aTrue111.moveToNext()) {
            Log.d("Cursor aTrue1111", aTrue111.getString(aTrue111.getColumnIndex(Content.dbAlarmMapTaskName)) + ",   " + aTrue111.getString(aTrue111.getColumnIndex(Content.dbAlarmIsRun)));
        }
    }


    private Runnable runnablePosition = new Runnable() {
        @Override
        public void run() {
            TaskManager.getInstances(mContext).getPositions(Content.mapName);
            myHandler.postDelayed(this, 1000);
        }
    };

    //初始化结果
    Handler handlerInitialize = new Handler();
    Runnable runnableInitialize = new Runnable() {
        @Override
        public synchronized void run() {
            NavigationService.is_initialize_finished();
        }
    };

    //持续移动 下
    Handler handler1 = new Handler();
    Runnable runnable1 = new Runnable() {
        @Override
        public synchronized void run() {
            NavigationService.move(-0.2f, 0.0f);
            handler1.postDelayed(runnable1, 10);
        }
    };

    //持续移动 上
    Handler handler2 = new Handler();
    Runnable runnable2 = new Runnable() {
        @Override
        public synchronized void run() {
            NavigationService.move(0.2f, 0.0f);
            handler2.postDelayed(runnable2, 10);
        }
    };

    //持续移动 左
    Handler handler3 = new Handler();
    Runnable runnable3 = new Runnable() {
        @Override
        public synchronized void run() {
            NavigationService.move(0.0f, 0.2f);
            handler3.postDelayed(runnable3, 10);
        }
    };

    //持续移动 右
    Handler handler4 = new Handler();
    Runnable runnable4 = new Runnable() {
        @Override
        public synchronized void run() {
            NavigationService.move(0.0f, -0.2f);
            handler4.postDelayed(runnable4, 10);
        }
    };
}
