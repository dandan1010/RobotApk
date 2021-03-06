package com.example.robot.task;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.dcm360.controller.gs.GSRobotController;
import com.dcm360.controller.gs.controller.GsController;
import com.dcm360.controller.gs.controller.bean.PositionListBean;
import com.dcm360.controller.gs.controller.bean.RecordStatusBean;
import com.dcm360.controller.gs.controller.bean.RecordingBean;
import com.dcm360.controller.gs.controller.bean.charge_bean.ModifyRobotParam;
import com.dcm360.controller.gs.controller.bean.data_bean.RobotDeviceStatus;
import com.dcm360.controller.gs.controller.bean.data_bean.RobotPositions;
import com.dcm360.controller.gs.controller.bean.data_bean.VersionBean;
import com.dcm360.controller.gs.controller.bean.map_bean.RobotMap;
import com.dcm360.controller.gs.controller.bean.map_bean.RobotPosition;
import com.dcm360.controller.gs.controller.bean.paths_bean.RobotTaskQueue;
import com.dcm360.controller.gs.controller.bean.paths_bean.RobotTaskQueueList;
import com.dcm360.controller.gs.controller.bean.paths_bean.UpdataVirtualObstacleBean;
import com.dcm360.controller.gs.controller.bean.paths_bean.VirtualObstacleBean;
import com.dcm360.controller.gs.controller.bean.system_bean.UltrasonicPhitBean;
import com.dcm360.controller.robot_interface.bean.Status;
import com.dcm360.controller.robot_interface.status.RobotStatus;
import com.dcm360.controller.utils.WebSocketUtil;
import com.example.robot.R;
import com.example.robot.bean.PointStateBean;
import com.example.robot.bean.SaveTaskBean;
import com.example.robot.bean.TaskBean;
import com.example.robot.content.BaseEvent;
import com.example.robot.service.NavigationService;
import com.example.robot.service.SocketServices;
import com.example.robot.sqlite.SqLiteOpenHelperUtils;
import com.example.robot.utils.AlarmUtils;
import com.example.robot.utils.AssestFile;
import com.example.robot.content.Content;
import com.example.robot.utils.EventBusMessage;
import com.example.robot.controller.RobotManagerController;
import com.example.robot.utils.GsonUtils;
import com.example.robot.utils.SharedPrefUtil;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.WebSocket;

public class TaskManager {
    private static final String TAG = "TaskManager";
    private static TaskManager mTaskManager;
    private MyThread myThread;
    private boolean scanningFlag = false;
    public ArrayList<TaskBean> mTaskArrayList = new ArrayList<>();
    private boolean navigateSuccess = false;
    private RobotPosition mRobotPosition = null;
    private Context mContext;
    private RobotTaskQueue robotTaskQueue;
    private List<String> pois;
    private RobotTaskQueueList mRobotTaskQueueList = null;
    private RobotPositions mRobotPositions = null;
    private SqLiteOpenHelperUtils sqLiteOpenHelperUtils;
    private GsonUtils gsonUtils;
    public static PointStateBean pointStateBean;
    private AlarmUtils mAlarmUtils;
    private static WebSocket webSocket;
    private int timerCount = 0;
    private boolean isSendType = false;
    private boolean isAddInitialize = false;
    private AssestFile mAssestFile;
    private int currentTaskArea = 0;
    private long taskCount = 0, taskTime = 0, area = 0;
    private boolean isChecked = false;
    private long recordingTime = System.currentTimeMillis();


    private TaskManager(Context mContext) {
        this.mContext = mContext;
        sqLiteOpenHelperUtils = new SqLiteOpenHelperUtils(mContext);
        mAlarmUtils = new AlarmUtils(mContext);
        mAssestFile = new AssestFile(mContext);
    }

    public static TaskManager getInstances(Context mContext) {
        if (mTaskManager == null) {
            synchronized (TaskManager.class) {
                if (mTaskManager == null)
                    mTaskManager = new TaskManager(mContext);
            }
        }

        return mTaskManager;
    }

    /**
     * 通过地图名字获取地图图片
     */
    public void getMapPic(String mapName) {
        if (TextUtils.isEmpty(mapName)) {
            Log.d(TAG, "mapName is null");
            return;
        }

        if (RobotManagerController.getInstance() == null) {
            Log.d(TAG, "RobotManagerController is null");
            return;
        }
        if (RobotManagerController.getInstance().getRobotController() == null) {
            Log.d(TAG, "getRobotController is null ");
            return;
        }
        RobotManagerController.getInstance().getRobotController().getMapPicture(mapName, new RobotStatus<byte[]>() {
            @Override
            public void success(byte[] bytes) {
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.get_mapIcon) + "successed"));
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.SENDMAPICON, bytes));
            }

            @Override
            public void error(Throwable error) {

                String msg = "get map Png failed :" + error.getMessage();
                Log.d(TAG, msg);
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.get_mapIcon) + error.getMessage()));
            }
        });
    }

    /**
     * 获取机器人的位置
     */
    public void getPositions(String mapName) {

        GsController.INSTANCE.getPositions(mapName, new RobotStatus<RobotPosition>() {
            @Override
            public void success(RobotPosition robotPosition) {
                if (robotPosition != null) {
                    mRobotPosition = robotPosition;
                    if (null != robotPosition.getGridPosition()) {
                        EventBus.getDefault().post(new EventBusMessage(BaseEvent.SENDGPSPOSITION, robotPosition));
                    }

                } else {
                    Log.d(TAG, "robotPosition == NULL");
                }
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "gps error :" + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.get_robotPosition) + error.getMessage()));
            }
        });
    }

    /**
     * 获取地图列表
     */
    @SuppressLint("CheckResult")
    public void loadMapList() {
        GsController.INSTANCE.getGsControllerService().getMapList()
                .filter(robotMap -> robotMap != null && robotMap.getData() != null && robotMap.getData().size() > 0)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RobotMap>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(RobotMap robotMap) {
                        EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.get_mapList) + "successed"));

                        EventBus.getDefault().post(new EventBusMessage(BaseEvent.SENDMAPNAME, robotMap));

                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d(TAG, "get map list is error ： " + e.getMessage());
                        EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.get_mapList) + e.getMessage()));
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    /**
     * 移动到导航点
     */
    public void navigate_Position(String mapName, String positionName) {
        RobotManagerController.getInstance().getRobotController().navigate_Position(mapName, positionName, new RobotStatus<Status>() {

            @Override
            public void success(Status status) {
                Log.d(TAG, "navigate " + mapName + " to " + positionName + ",     " + status.getMsg());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.navigate_position) + positionName + "successed"));
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "navigate " + mapName + " to " + positionName + " : " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.navigate_position) + error.getMessage()));
            }
        });

    }

    /**
     * 开始扫描地图
     */
    public void start_scan_map(String map_name) {

        GsController.INSTANCE.startScanMap(map_name, 0, new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                scanningFlag = true;
                if (myThread == null) {
                    myThread = new MyThread(map_name);
                    myThread.start();
                }
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "start scan map failed :  " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.start_scanMap) + error.getMessage()));
            }
        });

    }

    /**
     * 扩展扫描地图
     */
    public void start_develop_map(String map_name) {
        GsController.INSTANCE.startScanMap(map_name, 1, new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "develop map  :  " + status.toString() + " mapName ：" + map_name);
                if ("START_SCAN_MAP_FAILED".equals(status.getErrorCode())) {
                    EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.develop_map) + status.getMsg()));
                } else {
                    scanningFlag = true;
                    if (myThread == null) {
                        Log.d(TAG, "start develop thread ");
                        myThread = new MyThread(map_name);
                        myThread.start();
                    }
                }

            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "develop map  failed :  " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.develop_map) + error.getMessage()));
            }
        });

    }

    /**
     * 获取实时扫地图图片png
     */
    public void scanMapPng() {
        RobotManagerController.getInstance().getRobotController().scanMapPng(new RobotStatus<byte[]>() {
            @Override
            public void success(byte[] bytes) {
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.SENDMAPICON, bytes));
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "scanMapPng failed :  " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.get_scanMap) + error.getMessage()));
            }
        });
    }

    /**
     * 取消扫描地图——保存
     */
    public void stopScanMap() {
        Content.InitializePositionName = "End";
        isAddInitialize = false;
        GsController.INSTANCE.stopScanMap(new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.stop_scanSaveMap) + status.getMsg()));
                scanningFlag = false;
                if (myThread != null) {
                    myThread = null;
                }
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "stopScanMap failed :  " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.stop_scanSaveMap) + error.getMessage()));
            }
        });
    }

    /**
     * 取消扫描地图——不保存
     */

    public void cancleScanMap() {
        GsController.INSTANCE.cancelScanMap(new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "cancleScanMap ：" + status.getMsg());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.stop_scanMap) + status.getMsg()));
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "cancleScanMap failed ：" + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.stop_scanMap) + error.getMessage()));

            }
        });
    }

    /**
     * 删除地图
     */
    public void deleteMap(String map_name) {
        GsController.INSTANCE.deleteMap(map_name, new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "deleteMap :  " + status.getMsg());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.delete_map) + status.getMsg()));
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "deleteMap failed :  " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.delete_map) + error.getMessage()));
            }
        });
    }

    public void renameMapName(String oldMap, String newMap) {
        GsController.INSTANCE.renameMap(oldMap, newMap, new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "renameMapName :  " + status);
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.rename_map) + status.getMsg()));
                Cursor cursor = sqLiteOpenHelperUtils.searchAllPointTask();
                while (cursor.moveToNext()) {
                    String mapTaskName = cursor.getString(cursor.getColumnIndex(Content.dbPointTaskName));
                    Log.d(TAG, "renameMapName-pointTaskCursor :  " + mapTaskName);
                    if (mapTaskName.startsWith(oldMap)) {
                        sqLiteOpenHelperUtils.updatePointTask(Content.dbPointTaskName, mapTaskName,
                                newMap + "," + mapTaskName.split(",")[1]);
                    }
                }
                Cursor searchAllAlarmTask = sqLiteOpenHelperUtils.searchAllAlarmTask();
                while (searchAllAlarmTask.moveToNext()) {
                    String mapTaskName = searchAllAlarmTask.getString(searchAllAlarmTask.getColumnIndex(Content.dbAlarmMapTaskName));
                    Log.d(TAG, "renameMapName-alarmCursor :  " + mapTaskName);
                    if (mapTaskName.startsWith(oldMap)) {
                        sqLiteOpenHelperUtils.updateAlarmTask(mapTaskName, Content.dbAlarmMapTaskName,
                                newMap + "," + mapTaskName.split(",")[1]);
                    }
                }
                Cursor searchAllAlarmTask1 = sqLiteOpenHelperUtils.searchAllAlarmTask();
                while (searchAllAlarmTask1.moveToNext()) {
                    String mapTaskName = searchAllAlarmTask1.getString(searchAllAlarmTask1.getColumnIndex(Content.dbAlarmMapTaskName));
                    Log.d(TAG, "get all alarm task key :  " + mapTaskName);
                }
                sqLiteOpenHelperUtils.close();

            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "renameMapName failed :  " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.delete_map) + error.getMessage()));
            }
        });
    }

    class MyThread extends Thread {

        private String newMapName;

        public MyThread(String newMapName) {
            this.newMapName = newMapName;
        }

        @Override
        public void run() {
            super.run();
            while (scanningFlag) {
                scanMapPng();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 保存任务队列
     */
    public void save_taskQueue(String mapName, String taskName, List<SaveTaskBean> list) {
        ArrayList<TaskBean> taskPositionMsg = getTaskPositionMsg(mapName, taskName, list);

        robotTaskQueue = exeTaskPoi(mapName, taskName, taskPositionMsg);

        GsController.INSTANCE.saveTaskQueue(robotTaskQueue, new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "saveTaskQueue : " + status);
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.save_task) + status.getMsg()));
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "saveTaskQueue failed : " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.save_task) + error.getMessage()));
            }
        });

    }

    private ArrayList<TaskBean> getTaskPositionMsg(String mapName, String taskName, List<SaveTaskBean> list) {
        ArrayList<TaskBean> arrayList = new ArrayList<>();
        if (mRobotPositions != null) {
            for (int j = 0; j < list.size(); j++) {
                for (int i = 0; i < mRobotPositions.getData().size(); i++) {
                    Log.d(TAG, "list mRobotPositions name : " + mRobotPositions.getData().get(i).getName() + ",   " + list.get(j).getPositionName());
                    if (list.get(j).getPositionName().equals(mRobotPositions.getData().get(i).getName())) {
                        TaskBean taskBean = new TaskBean(mRobotPositions.getData().get(i).getName(),
                                list.get(j).getTime(),
                                mRobotPositions.getData().get(i).getGridX(),
                                mRobotPositions.getData().get(i).getGridY());
                        arrayList.add(taskBean);
                        sqLiteOpenHelperUtils.savePointTask(mapName + "," + taskName,
                                mRobotPositions.getData().get(i).getName(),
                                "" + list.get(j).getTime(),
                                "" + mRobotPositions.getData().get(i).getGridX(),
                                "" + mRobotPositions.getData().get(i).getGridY());
                    }
                }
                sqLiteOpenHelperUtils.close();
            }
        }
        return arrayList;
    }

    public RobotTaskQueue exeTaskPoi(String mapName, String taskName, ArrayList<TaskBean> mTaskArrayList) {
        pois = new ArrayList<>();
        for (int i = 0; i < mTaskArrayList.size(); i++) {
            pois.add(mTaskArrayList.get(i).getName());
        }
        //pois.add(Content.CHARGING_POINT);

        RobotTaskQueue taskQueue = new RobotTaskQueue();
        taskQueue.setName(taskName);
        taskQueue.setMap_name(mapName);
        taskQueue.setLoop(false);

        if (pois != null && pois.size() > 0) {
            List<RobotTaskQueue.TasksBean> tasksBeans = new ArrayList<>();
            for (int i = 0; i < pois.size(); i++) {
                RobotTaskQueue.TasksBean bean = new RobotTaskQueue.TasksBean();
                RobotTaskQueue.TasksBean.StartParamBean paramBean = new RobotTaskQueue.TasksBean.StartParamBean();
                paramBean.setMap_name(mapName);
                paramBean.setPosition_name(pois.get(i));
                bean.setName("NavigationTask");
                bean.setStart_param(paramBean);
                tasksBeans.add(bean);
            }
            taskQueue.setTasks(tasksBeans);
        }
        return taskQueue;
    }

    /**
     * 开始执行任务队列
     */
    public void startTaskQueue(String mapName, String taskName, int taskIndex) {
        //robotStatus();
        Content.isLastTask = false;
        Content.taskIsFinish = false;
        Content.taskIndex = taskIndex;
        Log.d(TAG, "START TASK index ： " + taskIndex);
        Content.pir_timeCount = 20;
        getTaskPositionMsg(mapName, taskName);
//        robotTaskQueue = exeTaskPoi(mapName, taskName, mTaskArrayList);

        if (mTaskArrayList.size() == 0) {
            Content.taskName = null;
            Log.d(TAG, "taskList is null");
            return;
        }
//        if (robotTaskQueue == null) {
//            Content.taskName = null;
//            Log.d(TAG, "robotTaskQueue is null");
//
//            return;
//        }
//        Log.d(TAG, "任务请求：" + robotTaskQueue.toString());
//        GsController.INSTANCE.startTaskQueue(robotTaskQueue, new RobotStatus<Status>() {
//            @Override
//            public void success(Status status) {
//                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.start_task) + status.getMsg()));
//                if ("successed".equals(status.getMsg())) {
//                    Content.robotState = 3;
//                    Content.time = 300;
//                    Content.taskState = 1;
//                    Content.taskIndex = 0;
//                    Content.startTime = System.currentTimeMillis();
//                    Log.d("zdzd", "开始时间：" + Content.startTime);
//                    if (Content.Working_mode == 1) {
//                        EventBus.getDefault().post(new EventBusMessage(10005, -1));
//                    }
//                } else {
//                    Content.taskName = null;
//                    Log.d(TAG, "11111任务：" + status.getMsg());
//                    EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.start_task) + status.getMsg()));
//                }
//            }
//
//            @Override
//            public void error(Throwable error) {
//                Content.taskName = null;
//                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.start_task) + error.getMessage()));
//            }
//        });
        Content.startTime = System.currentTimeMillis();
        currentTaskArea = 0;
        sqLiteOpenHelperUtils.saveTaskHistory(Content.mapName, Content.taskName,
                "-1",
                "" + mAlarmUtils.getTimeYear(Content.startTime),
                SocketServices.battery + "%",
                SocketServices.battery + "%",
                "" + Content.taskIndex);
        sqLiteOpenHelperUtils.close();
        EventBus.getDefault().post(new EventBusMessage(10038, pointStateBean));
        handler.sendEmptyMessageDelayed(1001, 0);

    }

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == 1001) {
                Log.d(TAG, "start task  taskIsFinish： " + Content.taskIsFinish + ",  taskIndex: " + Content.taskIndex + " , mTaskArrayList " + mTaskArrayList.size()
                        + ",   isCharging: " + Content.isCharging + ",   Content.EMERGENCY : " + Content.EMERGENCY + ",   Content.isHightTemp : " + Content.isHightTemp);
                if (!Content.taskIsFinish && !Content.EMERGENCY && !Content.isHightTemp) {
                    isSendType = false;
                    if (Content.taskIndex < mTaskArrayList.size()) {
                        recordingTime = System.currentTimeMillis();
                        recording(Content.startRecordingOpName);
                        if (Content.Working_mode == 1) {
                            EventBus.getDefault().post(new EventBusMessage(BaseEvent.STARTLIGHT, -1));
                        }
                        navigate_Position(Content.mapName, mTaskArrayList.get(Content.taskIndex).getName());
                        pointStateBean.getList().get(Content.taskIndex).setPointState(mContext.getResources().getString(R.string.ongoing));
                        EventBus.getDefault().post(new EventBusMessage(BaseEvent.ROBOT_TASK_STATE, pointStateBean));
                        Content.taskIsFinish = true;
                        Content.taskState = 1;
                        handler.removeMessages(1001);
                        handler.removeMessages(1002);
                        handler.removeMessages(1003);
                        handler.sendEmptyMessageDelayed(1002, 1000);
                        handler.sendEmptyMessageDelayed(1001, 1000);
                    } else {
                        Log.d(TAG, "remove message ");
                        recording(Content.endRecordingOpName);
                        handler.sendEmptyMessageDelayed(2001, 3000);
                        handler.removeMessages(1001);
                        handler.removeMessages(1002);
                        handler.removeMessages(1003);
                        Content.Sum_Time = 0;
                        SocketServices.myHandler.removeMessages(9);
                        sqLiteOpenHelperUtils.updateHistory(Content.dbTime,
                                "" + ((System.currentTimeMillis() - Content.startTime) / 1000 / 60),
                                mAlarmUtils.getTimeYear(Content.startTime),
                                SocketServices.battery + "%");
                        sqLiteOpenHelperUtils.saveTaskState(Content.mapName,
                                Content.taskName,
                                pointStateBean.toString().replace("'", ""),
                                mAlarmUtils.getTimeYear(Content.startTime));
                        //所有任务
                        Cursor cursor = sqLiteOpenHelperUtils.searchTaskTotalCount();
                        taskCount = 0;
                        taskTime = 0;
                        area = 0;
                        while (cursor.moveToNext()) {
                            taskCount = Long.parseLong(cursor.getString(cursor.getColumnIndex(Content.dbTaskTotalCount)));
                            taskTime = Long.parseLong(cursor.getString(cursor.getColumnIndex(Content.dbTimeTotalCount)));
                            area = Long.parseLong(cursor.getString(cursor.getColumnIndex(Content.dbAreaTotalCount)));
                        }
                        sqLiteOpenHelperUtils.reset_Db(Content.dbTotalCount);
                        sqLiteOpenHelperUtils.saveTaskTotalCount((taskCount + 1) + "", (taskTime + (System.currentTimeMillis() - Content.startTime)) + "", (area + currentTaskArea) + "");
                        //当月任务
                        Cursor currentCursor = sqLiteOpenHelperUtils.searchTaskCurrentCount(mAlarmUtils.getTimeMonth(System.currentTimeMillis()));
                        taskCount = 0;
                        taskTime = 0;
                        area = 0;
                        while (currentCursor.moveToNext()) {
                            taskCount = Long.parseLong(currentCursor.getString(currentCursor.getColumnIndex(Content.dbTaskCurrentCount)));
                            taskTime = Long.parseLong(currentCursor.getString(currentCursor.getColumnIndex(Content.dbTimeCurrentCount)));
                            area = Long.parseLong(currentCursor.getString(currentCursor.getColumnIndex(Content.dbAreaCurrentCount)));
                        }
                        sqLiteOpenHelperUtils.reset_Db(Content.dbCurrentCount);
                        sqLiteOpenHelperUtils.saveTaskCurrentCount((taskCount + 1) + "", (taskTime + (System.currentTimeMillis() - Content.startTime)) + "", (area + currentTaskArea) + "", mAlarmUtils.getTimeMonth(System.currentTimeMillis()));

                        sqLiteOpenHelperUtils.close();
                        currentTaskArea = 0;
                        Content.taskState = 0;
                        Content.robotState = 1;
                        Content.taskIndex = -1;
                        Content.taskIsFinish = false;
                        Content.time = 4000;
                        Content.taskName = null;
                        mTaskArrayList.clear();
                        SocketServices.checkLztekLamp.setUvcMode(1);
                        if (Content.Working_mode == 1) {
                            SocketServices.stopDemoMode();
                        }
                    }
                } else {
                    handler.sendEmptyMessageDelayed(1001, 1000);
                }
            } else if (msg.what == 1002) {
                Log.d(TAG, "timer-1002 速度 : " + Content.speed);
                if (Content.speed == 0 && !SocketServices.toLightControlBtn) {
                    handler.sendEmptyMessageDelayed(1003, 1 * 60 * 1000);
                } else {
                    handler.removeMessages(1003);
                }
                handler.removeMessages(1002);
                handler.sendEmptyMessageDelayed(1002, 1000);
            } else if (msg.what == 1003) {
                Log.d(TAG, "timer-1003 速度 : " + Content.speed + ",   mapName : " + Content.mapName);
                if (Content.taskIndex < mTaskArrayList.size()) {
                    pointStateBean.getList().get(Content.taskIndex).setPointState("UNREACHED");
                    EventBus.getDefault().post(new EventBusMessage(BaseEvent.ROBOT_TASK_STATE, pointStateBean));
                    if (Content.taskIndex == mTaskArrayList.size() - 1 && Content.isCharging) {
                        Content.taskIsFinish = false;
                        Content.taskIndex++;
                        sqLiteOpenHelperUtils.updateTaskIndex(Content.dbTaskIndex,
                                "" + Content.taskIndex,
                                "" + mAlarmUtils.getTimeYear(Content.startTime));
                        sqLiteOpenHelperUtils.close();
                    } else {
                        isSendType = true;
                        Content.is_initialize_finished = 0;
                        NavigationService.initGlobal(Content.mapName);
                        handler.removeMessages(1004);
                        handler.sendEmptyMessageDelayed(1004, 1000);
                    }

                } else if (!Content.isCharging) {
                    Content.taskIsFinish = false;
                    navigate_Position(Content.mapName, Content.CHARGING_POINT);
                    pointStateBean.getList().get(Content.taskIndex).setPointState(mContext.getResources().getString(R.string.ongoing));
                    EventBus.getDefault().post(new EventBusMessage(BaseEvent.ROBOT_TASK_STATE, pointStateBean));
                }
                handler.removeMessages(1003);
            } else if (msg.what == 1004) {
                Log.d(TAG, "timer-1004 速度 : " + Content.is_initialize_finished);
                if (Content.is_initialize_finished == 0) {
                    handler.sendEmptyMessageDelayed(1004, 1000);
                } else {
                    Content.taskIsFinish = false;
                    Content.taskIndex++;
                    sqLiteOpenHelperUtils.updateTaskIndex(Content.dbTaskIndex,
                            "" + Content.taskIndex,
                            "" + mAlarmUtils.getTimeYear(Content.startTime));
                    sqLiteOpenHelperUtils.close();
                    handler.removeMessages(1004);
                }
            } else if (msg.what == 1005) {
                NavigationService.move(0.2f, 0.0f);
                handler.sendEmptyMessageDelayed(1005, 10);
            } else if (msg.what == 1006) {
                handler.removeMessages(1005);
                handler.removeMessages(1006);
                PositionListBean positionListBean = new PositionListBean();
                positionListBean.setName(Content.InitializePositionName);
                positionListBean.setGridX((int) mRobotPosition.getGridPosition().getX());
                positionListBean.setGridY((int) mRobotPosition.getGridPosition().getY());
                positionListBean.setAngle(mRobotPosition.getAngle());
                positionListBean.setType(0);
                positionListBean.setMapName(Content.mapName);
                add_Position(positionListBean);
            } else if (msg.what == 1007) {
                isChecked = false;
                if (mAssestFile.getFileCount() == 3) {
                    mAssestFile.deepFile((String) msg.obj);
                    reboot();
                } else if (mAssestFile.getFileCount() < 3) {
                    mAssestFile.deepFile((String) msg.obj);
                } else {
                    EventBus.getDefault().post(new EventBusMessage(BaseEvent.Robot_Error, Content.Robot_Error));
                }
            } else if (msg.what == 2001) {
                getBag(mAlarmUtils.getTimeYear(recordingTime).replace(" ", "_") + "_0.bag");
            } else if (msg.what == 2002) {
                deleteBag(mAlarmUtils.getTimeYear(recordingTime).replace(" ", "_") + "_0.bag");
            }
        }
    };

    public void reboot() {
        RobotManagerController.getInstance().getRobotController().reboot(new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "REBOOT ----" + status.getMsg());
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "REBOOT ----" + error.getMessage());
            }
        });
    }

    public ArrayList<TaskBean> getTaskPositionMsg(String mapName, String taskName) {
        mTaskArrayList.clear();
        pointStateBean = new PointStateBean();
        pointStateBean.setMapName(mapName);
        pointStateBean.setTaskName(taskName);
        List<PointStateBean.PointState> pointStates = new ArrayList<>();
        Cursor cursor = sqLiteOpenHelperUtils.searchPointTask(Content.dbPointTaskName, mapName + "," + taskName);
        Log.d(TAG, "get task msg ：" + cursor.getCount() + "   ,mapName : " + mapName + " , taskName : " + taskName);
        while (cursor.moveToNext()) {
            TaskBean taskBean = new TaskBean(cursor.getString(cursor.getColumnIndex(Content.dbPointName)),
                    Integer.parseInt(cursor.getString(cursor.getColumnIndex(Content.dbSpinnerTime))),
                    Integer.parseInt(cursor.getString(cursor.getColumnIndex(Content.dbPointX))),
                    Integer.parseInt(cursor.getString(cursor.getColumnIndex(Content.dbPointY))));
            mTaskArrayList.add(taskBean);
            PointStateBean.PointState pointState = new PointStateBean.PointState();
            pointState.setPointName(cursor.getString(cursor.getColumnIndex(Content.dbPointName)));
            pointState.setPointState(mContext.getResources().getString(R.string.not_work));
            String[] spinnerItem = mContext.getResources().getStringArray(R.array.spinner_time);
            pointState.setTimeCount(Integer.parseInt(spinnerItem[Integer.parseInt(cursor.getString(cursor.getColumnIndex(Content.dbSpinnerTime)))]) * 60 + "");
            pointStates.add(pointState);
            Content.Sum_Time = Content.Sum_Time
                    + (Integer.parseInt(spinnerItem[Integer.parseInt(cursor.getString(cursor.getColumnIndex(Content.dbSpinnerTime)))]) * 60)
                    + 60;
            Log.d("zdzd : ", "时间 ： " + spinnerItem[Integer.parseInt(cursor.getString(cursor.getColumnIndex(Content.dbSpinnerTime)))] + ",   " + Content.Sum_Time);
        }
        String point_Name = "";
        if (Content.have_charging_mode) {
            point_Name = Content.CHARGING_POINT;
        } else {
            point_Name = Content.InitializePositionName;
        }
        TaskBean taskBean = new TaskBean(point_Name,
                0,
                0,
                0);
        PointStateBean.PointState pointState = new PointStateBean.PointState();
        pointState.setPointName(point_Name);
        pointState.setPointState(mContext.getResources().getString(R.string.not_work));
        pointState.setTimeCount("0");
        pointStates.add(pointState);
        mTaskArrayList.add(taskBean);
        pointStateBean.setList(pointStates);
        sqLiteOpenHelperUtils.close();
        EventBus.getDefault().post(new EventBusMessage(BaseEvent.ROBOT_TASK_STATE, pointStateBean));
        return mTaskArrayList;
    }

    /**
     * 暂停任务
     */
    public void pauseTaskQueue() {
        GsController.INSTANCE.pauseTaskQueue(new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "pauseTaskQueue " + status.getMsg());
                Content.taskState = 2;
                //myHandler.removeCallbacks(runnable);
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "pauseTaskQueue failed " + error.getMessage());
            }
        });
    }

    /**
     * 恢复任务
     */
    public void resumeTaskQueue() {
        GsController.INSTANCE.resumeTaskQueue(new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "resumeTaskQueue" + status.getMsg());
                Content.taskState = 1;
                Content.robotState = 3;
                Content.time = 300;
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "resumeTaskQueue failed " + error.getMessage());
            }
        });
    }

    /**
     * 结束任务
     */
    public void stopTaskQueue(String mapName) {
        Content.taskState = 0;
        Content.taskIndex = -1;
        Content.is_initialize_finished = 2;
        Content.taskIsFinish = false;
        isSendType = false;
        handler.removeMessages(1001);
        handler.removeMessages(1002);
        handler.removeMessages(1003);
        NavigationService.stopInitialize();
        Log.d(TAG, "stopTaskQueue taskName : " + Content.taskName + " , charging : " + Content.isCharging
                + "time：" + (System.currentTimeMillis() - Content.startTime) / 1000 / 60 + ",  mapName : " + mapName);
        if (!Content.isCharging) {
            Content.IS_STOP_TASK = true;
            if (Content.have_charging_mode) {
                navigate_Position(mapName, Content.CHARGING_POINT);
            } else {
                navigate_Position(mapName, Content.InitializePositionName);
            }
        }
        sqLiteOpenHelperUtils.updateHistory(Content.dbTime,
                "" + ((System.currentTimeMillis() - Content.startTime) / 1000 / 60),
                mAlarmUtils.getTimeYear(Content.startTime),
                SocketServices.battery + "%");
        sqLiteOpenHelperUtils.saveTaskState(Content.mapName,
                Content.taskName,
                "" + pointStateBean.toString().replace("'", ""),
                mAlarmUtils.getTimeYear(Content.startTime));
        //更新总任务个数统计
        Cursor totalCursor = sqLiteOpenHelperUtils.searchTaskTotalCount();
        taskCount = 0;
        taskTime = 0;
        area = 0;
        while (totalCursor.moveToNext()) {
            taskCount = Long.parseLong(totalCursor.getString(totalCursor.getColumnIndex(Content.dbTaskTotalCount)));
            taskTime = Long.parseLong(totalCursor.getString(totalCursor.getColumnIndex(Content.dbTimeTotalCount)));
            area = Long.parseLong(totalCursor.getString(totalCursor.getColumnIndex(Content.dbAreaTotalCount)));
        }
        sqLiteOpenHelperUtils.reset_Db(Content.dbTotalCount);
        sqLiteOpenHelperUtils.saveTaskTotalCount((taskCount + 1) + "", (taskTime + (System.currentTimeMillis() - Content.startTime)) + "", (area + currentTaskArea) + "");
        //当月任务
        Cursor currentCursor = sqLiteOpenHelperUtils.searchTaskCurrentCount(mAlarmUtils.getTimeMonth(System.currentTimeMillis()));
        taskCount = 0;
        taskTime = 0;
        area = 0;
        while (currentCursor.moveToNext()) {
            taskCount = Long.parseLong(currentCursor.getString(currentCursor.getColumnIndex(Content.dbTaskCurrentCount)));
            taskTime = Long.parseLong(currentCursor.getString(currentCursor.getColumnIndex(Content.dbTimeCurrentCount)));
            area = Long.parseLong(currentCursor.getString(currentCursor.getColumnIndex(Content.dbAreaCurrentCount)));
        }
        sqLiteOpenHelperUtils.reset_Db(Content.dbCurrentCount);
        sqLiteOpenHelperUtils.saveTaskCurrentCount((taskCount + 1) + "", (taskTime + (System.currentTimeMillis() - Content.startTime)) + "", (area + currentTaskArea) + "", mAlarmUtils.getTimeMonth(System.currentTimeMillis()));

        sqLiteOpenHelperUtils.close();
        currentTaskArea = 0;
        Content.taskName = null;
        mTaskArrayList.clear();
        Content.startTime = System.currentTimeMillis();
        if (Content.Working_mode == 1) {
            EventBus.getDefault().post(new EventBusMessage(BaseEvent.STOPLIGHT, -1));
        }
        EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.stop_task) + "successed"));
    }

    /**
     * 删除任务队列
     */
    public void deleteTaskQueue(String mapName, String task_name) {
        GsController.INSTANCE.deleteTaskQueue(mapName, task_name, new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "deleteTaskQueue : " + status.getMsg());
                sqLiteOpenHelperUtils.deleteAlarmTask(mapName + "," + task_name);
                sqLiteOpenHelperUtils.deletePointTask(Content.dbPointTaskName, mapName + "," + task_name);
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.delete_task) + status.getMsg()));
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "deleteTaskQueue failed : " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.delete_task) + error.getMessage()));
            }
        });
    }

    /**
     * 获取任务队列
     */
    public void getTaskQueues(String map_name) {
        GsController.INSTANCE.taskQueues(map_name, new RobotStatus<RobotTaskQueueList>() {
            @Override
            public void success(RobotTaskQueueList robotTaskQueueList) {
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.get_taskList) + " successed"));
                Log.d(TAG, "getTaskQueues success" + robotTaskQueueList.getData().size());
                mRobotTaskQueueList = robotTaskQueueList;
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.STOPLIGHT, robotTaskQueueList));
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.SENDTASKQUEUE, robotTaskQueueList));
            }

            @Override
            public void error(Throwable error) {
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.get_taskList) + error.getMessage()));
            }
        });

    }

    /**
     * 添加点
     */
    public void add_Position(PositionListBean positionListBean) {
        GsController.INSTANCE.add_Position(positionListBean, new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "addPosition success : " + positionListBean.getName() + ",    " + Content.isCharging);
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.add_position) + status.getMsg()));
                if (Content.isCharging && !isAddInitialize) {
                    isAddInitialize = true;
                    handler.sendEmptyMessageDelayed(1005, 0);
                    handler.sendEmptyMessageDelayed(1006, 2000);
                }
                getPosition(Content.mapName);
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "addPosition failed : " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.add_position) + error.getMessage()));
            }
        });
    }

    /**
     * 删除点
     */
    public void deletePosition(String mapName, String positionName) {
        GsController.INSTANCE.deletePosition(mapName, positionName, new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "deletePosition success");
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.delete_position) + status.getMsg()));
                sqLiteOpenHelperUtils.deletePointTask(Content.dbPointName, positionName);
                getPosition(Content.mapName);
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "deletePosition fail : " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.delete_position) + error.getMessage()));

            }
        });
    }

    /**
     * 重命名点
     */
    public void renamePosition(String mapName, String originName, String newName) {
        GsController.INSTANCE.renamePosition(mapName, originName, newName, new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "renamePosition success");
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.rename_position) + status.getMsg()));
                sqLiteOpenHelperUtils.updatePointTask(Content.dbPointName, originName, newName);
                sqLiteOpenHelperUtils.close();
                getPosition(Content.mapName);
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "renamePosition failed : " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.rename_position) + error.getMessage()));

            }
        });
    }

    /**
     * 地图点数据，导航点列表
     */
    public void getPosition(String mapName) {
        GsController.INSTANCE.getMapPositions(mapName, new RobotStatus<RobotPositions>() {
            @Override
            public void success(RobotPositions robotPositions) {
                Log.d(TAG, "getPosition success : " + robotPositions.getData().size());
                mRobotPositions = robotPositions;
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.SENDPOINTPOSITION, robotPositions));
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.get_mapPositionList) + "successed"));
                for (int i = 0; i < robotPositions.getData().size(); i++) {
                    Log.d(TAG, "getPosition Initialize : " + Content.InitializePositionName + ",   点名字：" + robotPositions.getData().get(i).getName());
                    if (Content.CHARGING_POINT.equals(robotPositions.getData().get(i).getName())) {
                        Content.InitializePositionName = "Initialize";
                        break;
                    } else {
                        Content.InitializePositionName = "End";
                    }
                }

            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "renamePosition failed : " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.get_mapPositionList) + error.getMessage()));
            }
        });


    }

    /**
     * 取消导航
     */
    public void cancel_navigate() {
        GsController.INSTANCE.cancelNavigate(new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "cancel_navigate " + status.getMsg());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.stop_navigate) + status.getMsg()));
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "cancel_navigate failed :" + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.stop_navigate) + error.getMessage()));
            }
        });
    }

    /**
     * 使用地图
     */
    public void use_map(String map_name) {
        if (!TextUtils.isEmpty(map_name)) {
            SharedPrefUtil.getInstance(mContext).setSharedPrefMapName(Content.MAP_NAME, map_name);
        }
        Content.is_initialize_finished = 0;
        Log.d(TAG, "use_map： " + map_name);
        Content.noChargingCount = 5;
        RobotManagerController.getInstance().getRobotController().use_map(map_name, new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "use_map success : " + Content.taskIndex);
                if (Content.taskIndex == -1) {
                    NavigationService.initGlobal(Content.mapName);
                } else if (Content.isCharging) {
                    NavigationService.initialize_directly(Content.mapName);
                } else {
                    NavigationService.initialize(Content.mapName, Content.InitializePositionName);
                }
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.use_map) + status.getMsg()));
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "use_map failed " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.use_map) + error.getMessage()));
            }
        });
    }

    //获取虚拟墙
    public void getVirtual_obstacles(String mapName) {
        RobotManagerController.getInstance().getRobotController().getRecordStatus(new RobotStatus<RecordStatusBean>() {
            @Override
            public void success(RecordStatusBean status) {
                Log.d(TAG, "lines : getRecordStatus " + status.getMsg());

                RobotManagerController.getInstance().getRobotController().getVirtualObstacleData(mapName, new RobotStatus<VirtualObstacleBean>() {
                    @Override
                    public void success(VirtualObstacleBean virtualObstacleBean) {
                        EventBus.getDefault().post(new EventBusMessage(BaseEvent.SEND_VIRTUAL, virtualObstacleBean));
                        EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.virtual_obstacle) + "successed"));

                    }

                    @Override
                    public void error(Throwable error) {
                        Log.d(TAG, "lines : virtualObstacleBean error : " + error.getMessage());
                        EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.virtual_obstacle) + error.getMessage()));
                    }
                });
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "lines : getRecordStatus error " + error.getMessage());
            }
        });


    }

    //添加虚拟墙
    public void update_virtual_obstacles(UpdataVirtualObstacleBean updataVirtualObstacleBean, String mapName, String obstacle_name) {
        Log.d(TAG, "update_virtual111 " + updataVirtualObstacleBean.toString());
        RobotManagerController.getInstance().getRobotController().updateVirtualObstacleData(updataVirtualObstacleBean, mapName, obstacle_name, new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "update_virtual " + status.getMsg());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.add_virtual_obstacle) + status.getMsg()));
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "update_virtual " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.add_virtual_obstacle) + error.getMessage()));
            }
        });
    }

    //跑路径速度
    public void setSpeedLevel(int level) {
        RobotManagerController.getInstance().getRobotController().setSpeedLevel(String.valueOf(level), new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "setSpeedLevel " + status.getMsg());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.set_speed_level) + status.getMsg()));
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "setSpeedLevel failed " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.set_speed_level) + error.getMessage()));

            }
        });
    }

    //导航速度
    public void setnavigationSpeedLevel(int level) {
        RobotManagerController.getInstance().getRobotController().setnavigationSpeedLevel(String.valueOf(level), new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "setnavigationSpeedLevel " + status.getMsg());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.set_speed_level) + status.getMsg()));
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "setnavigationSpeedLevel " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.set_speed_level) + error.getMessage()));

            }
        });
    }

    /**
     * 获取设备信息
     */
    public void deviceStatus() {
        GsController.INSTANCE.deviceStatus(new RobotStatus<RobotDeviceStatus>() {
            @Override
            public void success(RobotDeviceStatus robotDeviceStatus) {
                Log.d(TAG, "deviceStatus： ： " + robotDeviceStatus.getData());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.DEVICES_STATUS, robotDeviceStatus));
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "deviceStatus failed :  " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.device_status) + error.getMessage()));
            }
        });
    }

    /**
     * 获取版本信息
     */
    public void deviceRobotVersion() {
        GsController.INSTANCE.deviceRobotVersion(new RobotStatus<VersionBean>() {
            @Override
            public void success(VersionBean versionBean) {
                Log.d(TAG, "VersionBean： ： " + versionBean.getData().getVersion());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.ROBOTVERSIONCODE, versionBean.getData().getVersion()));
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "VersionBean failed :  " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.device_status) + error.getMessage()));
            }
        });
    }

    private void navigation() {
        try {
            NavigationService.disposables.add(WebSocketUtil.getWebSocket(Content.ROBOROT_INF_TWO + "/gs-robot/notice/navigation_status")
                    .subscribe(data -> {
                        Log.d("zdzd111 : ", "NavigationStatus : " + data);
                        if (TextUtils.isEmpty(data)) {
                            return;
                        }
                        JSONObject jsonObject = new JSONObject(data);
                        String noticeType = jsonObject.getString("noticeType");
                        navigationStatus(noticeType);
                    }, throwable -> {
                        Log.d(TAG, "NavigationStatus throw ：" + throwable.getMessage());
                        navigation();
                    }));
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "NavigationStatus Exception ：" + e.getMessage());
        }

    }

    private void notice_status() {
        try {
            NavigationService.disposables.add(WebSocketUtil.getWebSocket(Content.ROBOROT_INF_TWO + "/gs-robot/notice/status")
                    .subscribe(data -> {
                        Log.d("zdzd222 : ", "Navigationnotice : " + data);
                        if (TextUtils.isEmpty(data)) {
                            return;
                        }
                        JSONObject jsonObject = new JSONObject(data);
                        int statusCode = jsonObject.getInt("statusCode");
                        switch (statusCode) {
                            case 701:
                                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, jsonObject.getString("statusMsg")));
                                break;
                            case 401:
                                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, jsonObject.getString("statusMsg")));
                                break;
                            case 702:
                                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, jsonObject.getString("statusMsg")));
                                break;
                            case 1006:
                                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, jsonObject.getString("statusMsg")));
                                break;
                        }
                    }, throwable -> {
                        Log.d(TAG, "Navigationnotice throw ：" + throwable.getMessage());
                        getRobotHealthy();
                    }));
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "Navigationnotice Exception ：" + e.getMessage());
        }
    }

    private void system_health_status() {
        try {
            NavigationService.disposables.add(WebSocketUtil.getWebSocket(Content.ROBOROT_INF_TWO + "/gs-robot/notice/system_health_status")
                    .subscribe(data -> {

                        if (TextUtils.isEmpty(data)) {
                            return;
                        }
                        EventBus.getDefault().post(new EventBusMessage(BaseEvent.ROBOT_HEALTHY, data));
                        if (!isChecked) {
                            String[] strings = data.split(",");
                            for (int i = 0; i < strings.length; i++) {
                                if (!strings[i].contains("cannotRotate") && !strings[i].contains("localizationLost")) {
                                    if (strings[i].endsWith("false")) {
                                        Message message = handler.obtainMessage();
                                        message.what = 1007;
                                        message.obj = data;
                                        handler.sendMessageDelayed(message, 5 * 60 * 1000);
                                        isChecked = true;
                                        break;
                                    }
                                }
                                if (i == strings.length - 1) {
                                    handler.removeMessages(1007);
                                    mAssestFile.deleteErrorCode();
                                }
                            }
                        }

                    }, throwable -> {
                        Log.d(TAG, "system_health_status ：" + throwable.getMessage());
                        system_health_status();
                    }));
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "system_health_status Exception ：" + e.getMessage());
        }
    }


    public void getRobotHealthy() {
        system_health_status();
        navigation();
        notice_status();
    }

    public void navigationStatus(String type) {
        Log.d(TAG, "navigationStatus ： " + type + " , isSendType : " + isSendType);
        if ("REACHED".equals(type) && Content.robotState != 6 && !isSendType) {//已经到达目的地
            Log.d(TAG, "REACHED");
            recording(Content.endRecordingOpName);
            isSendType = true;
            handler.removeMessages(1002);
            handler.removeMessages(1003);
            pointStateBean.getList().get(Content.taskIndex).setPointState(mContext.getResources().getString(R.string.done));
            EventBus.getDefault().post(new EventBusMessage(BaseEvent.ROBOT_TASK_STATE, pointStateBean));
            Log.d(TAG, "Content.taskIndex" + Content.taskIndex + " ,   " + mTaskArrayList.size());
            if (Content.taskIndex < mTaskArrayList.size() - 1) {
                EventBus.getDefault().post(new EventBusMessage(1007, mTaskArrayList.get(Content.taskIndex).getDisinfectTime()));
                Content.robotState = 5;
                Content.time = 1000;
            } else {
                Content.taskIsFinish = false;
            }
            currentTaskArea = currentTaskArea + 9;
            Content.taskIndex++;
            sqLiteOpenHelperUtils.updateTaskIndex(Content.dbTaskIndex,
                    "" + Content.taskIndex,
                    "" + mAlarmUtils.getTimeYear(Content.startTime));
            handler.sendEmptyMessageDelayed(2002, 3000);
        } else if (type.equals("UNREACHABLE") && Content.robotState != 6 && !isSendType) {
            recording(Content.endRecordingOpName);
            isSendType = true;
            handler.removeMessages(1002);
            handler.removeMessages(1003);
            pointStateBean.getList().get(Content.taskIndex).setPointState(mContext.getResources().getString(R.string.done));
            EventBus.getDefault().post(new EventBusMessage(BaseEvent.ROBOT_TASK_STATE, pointStateBean));
            Content.taskIndex++;
            Content.taskIsFinish = false;
            Log.d(TAG, "UNREACHABLE");
            sqLiteOpenHelperUtils.updateTaskIndex(Content.dbTaskIndex,
                    "" + Content.taskIndex,
                    "" + mAlarmUtils.getTimeYear(Content.startTime));
            handler.sendEmptyMessageDelayed(2001, 3000);
        } else if (type.equals("GOAL_NOT_SAFE") && Content.robotState != 6 && !isSendType) {
            recording(Content.endRecordingOpName);
            isSendType = true;
            handler.removeMessages(1002);
            handler.removeMessages(1003);
            pointStateBean.getList().get(Content.taskIndex).setPointState(mContext.getResources().getString(R.string.done));
            EventBus.getDefault().post(new EventBusMessage(BaseEvent.ROBOT_TASK_STATE, pointStateBean));
            Content.taskIndex++;
            Content.taskIsFinish = false;
            Log.d(TAG, "GOAL_NOT_SAFE");
            sqLiteOpenHelperUtils.updateTaskIndex(Content.dbTaskIndex,
                    "" + Content.taskIndex,
                    "" + mAlarmUtils.getTimeYear(Content.startTime));
            handler.sendEmptyMessageDelayed(2001, 3000);
        } else if (type.equals("UNREACHED") && Content.robotState != 6 && !isSendType) {
            recording(Content.endRecordingOpName);
            isSendType = true;
            handler.removeMessages(1002);
            handler.removeMessages(1003);
            pointStateBean.getList().get(Content.taskIndex).setPointState(mContext.getResources().getString(R.string.done));
            EventBus.getDefault().post(new EventBusMessage(BaseEvent.ROBOT_TASK_STATE, pointStateBean));
            Content.taskIsFinish = false;
            Content.taskIndex++;
            Log.d(TAG, "UNREACHED");
            sqLiteOpenHelperUtils.updateTaskIndex(Content.dbTaskIndex,
                    "" + Content.taskIndex,
                    "" + mAlarmUtils.getTimeYear(Content.startTime));
            handler.sendEmptyMessageDelayed(2001, 3000);
        } else if (type.equals("HEADING") || type.equals("PLANNING")) {

        }
        sqLiteOpenHelperUtils.close();
    }

    public void recording(String op_name) {
        RecordingBean recordingBean = new RecordingBean();
        recordingBean.setNode_name("record-bag");
        recordingBean.setOp_name(op_name);
        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.clear();
        if (Content.startRecordingOpName.equals(op_name)) {
            arrayList.add("/root/GAUSSIAN_RUNTIME_DIR//bag/" + mAlarmUtils.getTimeYear(recordingTime).replace(" ", "_") + ".bag");
        }
        recordingBean.setArgs(arrayList);
        GsController.INSTANCE.recording(recordingBean, new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "recording-- op_name : " + op_name + ",  status : " + status.getMsg());
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "recording error-- op_name : " + op_name + ",  status : " + error.getMessage());
            }
        });
    }

    public void getBag(String bagName) {
        RobotManagerController.getInstance().getRobotController().getBag(bagName, new RobotStatus<byte[]>() {
            @Override
            public void success(byte[] bytes) {
                Log.d(TAG, "下载bag文件 ： " + bagName + ",   " + bytes.length);
                mAssestFile.writeBagFiles(bytes);
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "下载bag文件 error ： " + bagName + ",   " + error.getMessage());
            }
        });
    }

    public void deleteBag(String bagName) {
        GsController.INSTANCE.deleteBag("bag/" + bagName, new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "删除bag文件 : " + bagName + ",   " + status.getMsg());
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "删除bag文件失败 : " + bagName + ",   " + error.getMessage());
            }
        });
    }

    public void robot_reset() {
        GsController.INSTANCE.reset_robot(new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "reset_robot : " + status.getMsg());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.reset_robot) + " : " + status.getMsg()));
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "reset_robot failed : " + error.getMessage());
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.reset_robot) + " : " + error.getMessage()));
            }
        });

    }

    public void getUltrasonicPhit() {
        GsController.INSTANCE.getUltrasonicPhit(new RobotStatus<UltrasonicPhitBean>() {
            @Override
            public void success(UltrasonicPhitBean ultrasonicPhitBean) {
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.SEND_ULTRASONIC, ultrasonicPhitBean));
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.get_ultrasonic_phit) + "成功"));

            }

            @Override
            public void error(Throwable error) {
                EventBus.getDefault().post(new EventBusMessage(BaseEvent.REQUEST_MSG, mContext.getResources().getString(R.string.get_ultrasonic_phit) + error.getMessage()));
            }
        });
    }

    public void modifyRobotParam(double value) {
        ModifyRobotParam.RobotParam robotParam = new ModifyRobotParam.RobotParam();
        robotParam.setNamespace("/strategy/charger_base/backward_dis");
        robotParam.setType("double");
        robotParam.setValue(value + "");
        ModifyRobotParam.RobotParam[] params = new ModifyRobotParam.RobotParam[]{robotParam};

        GsController.INSTANCE.modifyRobotParam(params, new RobotStatus<Status>() {
            @Override
            public void success(Status status) {
                Log.d(TAG, "设置回充电桩的距离 ： " + status + ",  value : " + value);
            }

            @Override
            public void error(Throwable error) {
                Log.d(TAG, "设置回充电桩的距离error ： " + error.getMessage() + ",  value : " + value);
            }
        });
    }
}
