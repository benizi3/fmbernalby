package com.virjar.hermes.hermesagent.host.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jaredrummler.android.processes.AndroidProcesses;
import com.jaredrummler.android.processes.models.AndroidAppProcess;
import com.virjar.hermes.hermesagent.BuildConfig;
import com.virjar.hermes.hermesagent.MainActivity;
import com.virjar.hermes.hermesagent.R;
import com.virjar.hermes.hermesagent.hermes_api.Constant;
import com.virjar.hermes.hermesagent.hermes_api.aidl.AgentInfo;
import com.virjar.hermes.hermesagent.hermes_api.aidl.DaemonBinder;
import com.virjar.hermes.hermesagent.hermes_api.aidl.IHookAgentService;
import com.virjar.hermes.hermesagent.hermes_api.aidl.IServiceRegister;
import com.virjar.hermes.hermesagent.host.http.HttpServer;
import com.virjar.hermes.hermesagent.host.manager.AgentWatchTask;
import com.virjar.hermes.hermesagent.host.manager.DynamicRateLimitManager;
import com.virjar.hermes.hermesagent.host.manager.LoggerTimerTask;
import com.virjar.hermes.hermesagent.host.manager.ReportTask;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.libsuperuser.Shell;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.DelayQueue;

import lombok.extern.slf4j.Slf4j;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;

/**
 * Created by virjar on 2018/8/22.<br>
 * 远程调用服务注册器，代码注入到远程apk之后，远程apk通过发现这个服务。注册自己的匿名binder，到这个容器里面来
 */
@Slf4j
public class FontService extends Service {
    private ConcurrentMap<String, IHookAgentService> allRemoteHookService = Maps.newConcurrentMap();
    public static RemoteCallbackList<IHookAgentService> mCallbacks = new RemoteCallbackList<>();
    public static Timer timer = null;
    private volatile long lastCheckTimerCheck = 0;
    private static final long aliveCheckDuration = 5000;
    private static final long timerCheckThreashHold = aliveCheckDuration * 4;
    private Set<String> onlineServices = null;

    private DaemonBinder daemonBinder = null;
    private ReportTask reportTask = null;


    public void setOnlineServices(Set<String> onlineServices) {
        this.onlineServices = onlineServices;
    }

    /**
     * 如果运行在小米系统上面的话，开放对应wrapper宿主的后台网络权限
     */
    private void makeSureMIUINetworkPermissionOnBackground(String packageName) {
        if (!Build.BRAND.equalsIgnoreCase("xiaomi")) {
            //非小米系统，不做该适配
            return;
        }
        log.info("grant network permission for miui system");
        Uri uri = Uri.parse(Constant.MIUIPowerKeeperContentProviderURI);
        //CREATE TABLE userTable (
        // _id INTEGER PRIMARY KEY AUTOINCREMENT,
        // userId INTEGER NOT NULL DEFAULT 0,
        // pkgName TEXT NOT NULL,
        // lastConfigured INTEGER,
        // bgControl TEXT NOT NULL DEFAULT 'miuiAuto',
        // bgLocation TEXT, bgDelayMin INTEGER,
        // UNIQUE (userId, pkgName) ON CONFLICT REPLACE );
        //query(uri, new String[]{"_id", "pkgName", "bgControl"}, "pkgName=?", new String[]{packageName}, null)
        try (Cursor cursor = getContentResolver().
                query(uri, null, "pkgName=?", new String[]{packageName}, null)) {
            if (cursor == null) {
                return;
            }
            while (cursor.moveToNext()) {
                Map<String, String> configData = Maps.newHashMap();
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    configData.put(cursor.getColumnName(i), cursor.getString(i));
                }
                String id = configData.get("_id");
                if (id == null) {
                    return;
                }
                String pkgName = configData.get("pkgName");
                if (!StringUtils.equalsIgnoreCase(packageName, pkgName)) {
                    continue;
                }
                boolean needUpdate = false;
                //高版本的配置选项，noRestrict为不限制后台行为
                String bgControl = configData.get("bgControl");
                if (bgControl != null && !StringUtils.equalsIgnoreCase("noRestrict", bgControl)) {
                    configData.put("bgControl", "noRestrict");
                    needUpdate = true;
                }

                //低版本的配置选项
                String miuiSuggest = configData.get("miuiSuggest");
                if (miuiSuggest != null && !StringUtils.equalsIgnoreCase(miuiSuggest, "disable")) {
                    //关闭小米推荐配置
                    configData.put("miuiSuggest", "disable");
                    needUpdate = true;
                }

                String bgData = configData.get("bgData");
                if (bgData != null && !StringUtils.equalsIgnoreCase(bgData, "enable")) {
                    //允许后台联网
                    configData.put("bgData", "enable");
                    needUpdate = true;
                }

                String bgLocation = configData.get("bgLocation");
                if (bgLocation != null && !StringUtils.equalsIgnoreCase(bgLocation, "enable")) {
                    //允许后台定位
                    configData.put("bgLocation", "enable");
                    needUpdate = true;
                }

                if (!needUpdate) {
                    continue;
                }

                ContentValues contentValues = new ContentValues();
                configData.remove("_id");

                for (Map.Entry<String, String> entry : configData.entrySet()) {
                    contentValues.put(entry.getKey(), entry.getValue());
                }
                getContentResolver().update(Uri.parse(Constant.MIUIPowerKeeperContentProviderURI + "/" + id),
                        contentValues, "_id=?", new String[]{id});
            }
        } catch (Exception e) {
            //这个异常，暂时忽略，如果失败，则需要手动去开启后台网络权限
            log.error("call miui system content provider failed", e);
        }
    }

    private IServiceRegister.Stub binder = new IServiceRegister.Stub() {
        @Override
        public void registerHookAgent(IHookAgentService hookAgentService) throws RemoteException {
            if (hookAgentService == null) {
                throw new RemoteException("service register, service implement can not be null");
            }
            AgentInfo agentInfo = hookAgentService.ping();
            if (agentInfo == null) {
                log.warn("service register,ping failed");
                return;
            }
            makeSureMIUINetworkPermissionOnBackground(agentInfo.getPackageName());
            log.info("service :{} register success", agentInfo.getPackageName());
            mCallbacks.register(hookAgentService);
            allRemoteHookService.putIfAbsent(agentInfo.getServiceAlis(), hookAgentService);
            if (reportTask != null) {
                //本地测试模式下，不存在reportTask
                reportTask.report();
            }
        }

        @Override
        public void unRegisterHookAgent(IHookAgentService hookAgentService) throws RemoteException {
            allRemoteHookService.remove(hookAgentService.ping().getServiceAlis());
            mCallbacks.unregister(hookAgentService);
        }

        @Override
        public List<String> onlineService() {
            return Lists.newArrayList(onlineAgentServices());
        }

        @Override
        public void notifyPingDuration(long duration) throws RemoteException {
            DynamicRateLimitManager.getInstance().recordPingDuration(duration);
        }

        @Override
        public void notifyPingFailed() throws RemoteException {
            DynamicRateLimitManager.getInstance().recordPingFailed();
        }

        @Override
        public double systemScore() throws RemoteException {
            return DynamicRateLimitManager.getInstance().getLimitScore();
        }
    };

    public IHookAgentService findHookAgent(String serviceName) {
        return allRemoteHookService.get(serviceName);
    }

    public void releaseDeadAgent(String serviceName) {
        allRemoteHookService.remove(serviceName);
    }

    public Set<String> onlineAgentServices() {
        if (onlineServices == null) {
            return allRemoteHookService.keySet();
        }
        return onlineServices;
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        startService();
        return binder;
    }


    @Override
    public void onDestroy() {
        allRemoteHookService.clear();
        mCallbacks.kill();
        HttpServer.getInstance().stopServer();
        stopForeground(true);

        Intent intent = new Intent(Constant.fontServiceDestroyAction);
        sendBroadcast(intent);
        super.onDestroy();
    }

    private void startService() {
        Notification.Builder builder = new Notification.Builder(this.getApplicationContext()); //获取一个Notification构造器
        Intent nfIntent = new Intent(this, MainActivity.class);
        // 设置PendingIntent
        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, FLAG_UPDATE_CURRENT))
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher)) // 设置下拉列表中的图标(大图标)
                .setContentTitle("HermesAgent") // 设置下拉列表里的标题
                .setSmallIcon(R.mipmap.ic_launcher) // 设置状态栏内的小图标
                .setContentText("群控系统") // 设置上下文内容
                .setWhen(System.currentTimeMillis()); // 设置该通知发生的时间
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(BuildConfig.APPLICATION_ID);
        }

        Notification notification = builder.build(); // 获取构建好的Notification
        notification.defaults = Notification.DEFAULT_SOUND; //设置为默认的声音
        startForeground(110, notification);// 开始前台服务

        log.info("start hermes font service");

        //确保HermesAgent后台联网正常
        makeSureMIUINetworkPermissionOnBackground(BuildConfig.APPLICATION_ID);

        //启动httpServer
        log.info("start http server...");
        HttpServer.getInstance().setFontService(this);
        HttpServer.getInstance().startServer(this);

        log.info("start daemon process..");
        startDaemonProcess();

        if (CommonUtils.xposedStartSuccess && lastCheckTimerCheck + timerCheckThreashHold < System.currentTimeMillis()) {
            if (lastCheckTimerCheck != 0) {
                log.info("timer 假死，重启timer");
            }
            restartTimer();
        }
    }

    private void startDaemonProcess() {
        Intent intent = new Intent(this, DaemonService.class);
        startService(intent);

        bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                daemonBinder = DaemonBinder.Stub.asInterface(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                daemonBinder = null;
                startDaemonProcess();
            }
        }, BIND_AUTO_CREATE);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startService();
        return START_STICKY;
    }

    private void restartTimer() {
        if (timer != null) {
            timer.cancel();
        }
        //之前的time可能死掉了
        timer = new Timer("FontServiceTimer", true);


        //半个小时，check一下adb的状态，守护adb进程
        timer.scheduleAtFixedRate(new LoggerTimerTask("adbCheck") {
            @Override
            public void doRun() {
                try {
                    CommonUtils.enableADBTCPProtocol(FontService.this);
                } catch (Exception e) {
                    log.error("enable adb remote exception", e);
                }
            }
        }, 10, 1000 * 60 * 30);


        //平均每半个小时重启所有的targetApp
        timer.scheduleAtFixedRate(new LoggerTimerTask("restartTargetApp") {
            @Override
            public void doRun() {
                for (Map.Entry<String, IHookAgentService> entry : allRemoteHookService.entrySet()) {
                    try {
                        log.info("杀死targetApp:{}", entry.getKey());
                        entry.getValue().killSelf();
                    } catch (RemoteException e) {
                        //ignore
                    }
                }
            }
        }, 30 * 60 * 1000 + new Random().nextLong() % (30 * 60 * 1000), 60 * 60 * 1000);

        //半天，重启一次手机系统，避免系统跑死
        timer.scheduleAtFixedRate(new LoggerTimerTask("rebootTask") {
            @Override
            public void doRun() {
                if (!CommonUtils.isSuAvailable()) {
                    log.warn("reboot command need root permission");
                    return;
                }
                Shell.SU.run("reboot");
            }
        }, 6 * 60 * 60 * 1000 + new Random().nextLong() % (6 * 60 * 60 * 1000), 12 * 60 * 60 * 100);


        //注册存活检测，如果timer线程存活，那么lastCheckTimerCheck将会刷新，如果长时间不刷新，证明timer已经挂了
        timer.scheduleAtFixedRate(new LoggerTimerTask("timerResponseCheck") {
            @Override
            public void doRun() {
                lastCheckTimerCheck = System.currentTimeMillis();
                log.info("record times last alive timestamp:{}", lastCheckTimerCheck);
            }
        }, aliveCheckDuration, aliveCheckDuration);
        lastCheckTimerCheck = System.currentTimeMillis();

        timer.scheduleAtFixedRate(new LoggerTimerTask("daemonProcessCheck") {
            @Override
            public void doRun() {
                DaemonBinder daemonBinderCopy = daemonBinder;
                if (daemonBinderCopy == null) {
                    startDaemonProcess();
                    return;
                }
                PingWatchTask pingWatchTask = new PingWatchTask(System.currentTimeMillis() + 1000 * 25, null);
                try {
                    //如果targetApp假死，那么这个调用将会阻塞，需要监控这个任务的执行时间，如果长时间ping没有响应，那么需要强杀targetApp
                    pingWatchTaskLinkedBlockingDeque.offer(pingWatchTask);
                    long start = System.currentTimeMillis();
                    daemonBinderCopy.ping();
                    DynamicRateLimitManager.getInstance().recordPingDuration(System.currentTimeMillis() - start);
                } catch (DeadObjectException deadObjectException) {
                    log.error("remote service dead,wait for re register");
                    daemonBinder = null;
                    startDaemonProcess();
                } catch (RemoteException e) {
                    log.error("failed to ping agent", e);
                } finally {
                    pingWatchTaskLinkedBlockingDeque.remove(pingWatchTask);
                    pingWatchTask.isDone = true;
                }

            }
        }, 5 * 60 * 1000, 5 * 60 * 1000);

        if (!CommonUtils.isLocalTest()) {
            //向服务器上报服务信息,正式版本才进行上报，测试版本上报可能使得线上服务打到测试apk上面来
            //重启之后，马上进行上报
            reportTask = new ReportTask(this, this);
            timer.scheduleAtFixedRate(reportTask,
                    1, 30000);
            //监控所有agent状态
            timer.scheduleAtFixedRate(new AgentWatchTask(this, allRemoteHookService, this), 1000, 30000);
        }

    }

    private static DelayQueue<PingWatchTask> pingWatchTaskLinkedBlockingDeque = new DelayQueue<>();


    static {
        Thread thread = new Thread("pingWatchTask") {
            @Override
            public void run() {
                while (true) {
                    try {
                        PingWatchTask poll = pingWatchTaskLinkedBlockingDeque.take();
                        if (poll.isDone) {
                            continue;
                        }
                        List<AndroidAppProcess> runningAppProcesses = AndroidProcesses.getRunningAppProcesses();
                        for (AndroidAppProcess androidAppProcess : runningAppProcesses) {
                            if (!StringUtils.equalsIgnoreCase(androidAppProcess.getPackageName(), BuildConfig.APPLICATION_ID)) {
                                continue;
                            }
                            if (StringUtils.containsIgnoreCase(androidAppProcess.name, ":daemon")) {
                                Shell.SU.run("kill -9 " + androidAppProcess.pid);
                                return;
                            }
                        }
                    } catch (InterruptedException e) {
                        return;
                    } catch (Exception e) {
                        log.error("handle ping task failed", e);
                    }
                }
            }
        };
        thread.setDaemon(false);
        thread.start();
    }
}
