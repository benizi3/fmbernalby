package com.virjar.hermes.hermesagent.host.manager;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.DeadObjectException;
import android.os.RemoteException;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jaredrummler.android.processes.AndroidProcesses;
import com.jaredrummler.android.processes.models.AndroidAppProcess;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.virjar.hermes.hermesagent.BuildConfig;
import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.Constant;
import com.virjar.hermes.hermesagent.hermes_api.aidl.AgentInfo;
import com.virjar.hermes.hermesagent.hermes_api.aidl.IHookAgentService;
import com.virjar.hermes.hermesagent.host.orm.ServiceModel;
import com.virjar.hermes.hermesagent.host.orm.ServiceModel_Table;
import com.virjar.hermes.hermesagent.host.service.FontService;
import com.virjar.hermes.hermesagent.host.service.PingWatchTask;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.SUShell;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.DelayQueue;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2018/8/24.<br>
 * server端，监控所有agent的状态，无法调通agent的话，尝试拉起agent
 */
@Slf4j
public class AgentWatchTask extends LoggerTimerTask {
    private ConcurrentMap<String, IHookAgentService> allRemoteHookService;
    private Context context;
    private FontService fontService;

    public AgentWatchTask(FontService fontService, ConcurrentMap<String, IHookAgentService> allRemoteHookService, Context context) {
        this.fontService = fontService;
        this.allRemoteHookService = allRemoteHookService;
        this.context = context;
    }

    private void makeSureStartOtherAppPermissionOnMIUISecurityCenter(String packageName) {
        if (!Build.BRAND.equalsIgnoreCase("xiaomi")) {
            //非小米系统，不做该适配
            return;
        }
        log.info("grant start other app for miui system");
        Uri uri = Uri.parse(Constant.MIUIStartActivityRuleListProviderURI);
        //CREATE TABLE StartActivityRuleList(_id INTEGER PRIMARY KEY AUTOINCREMENT, callerPkgName TEXT, calleePkgName TEXT, userSettings TINYINT);
        try (Cursor cursor = context.getContentResolver().
                query(uri, null, "callerPkgName=? and calleePkgName=?", new String[]{BuildConfig.APPLICATION_ID, packageName}, null)) {
            if (cursor == null) {
                log.warn("query StartActivityRuleList,get cursor failed");
                //not happened
                return;
            }
            if (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndex("_id"));
                int userSettings = cursor.getInt(cursor.getColumnIndex("userSettings"));
                if (userSettings != 0) {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put("userSettings", 0);
                    context.getContentResolver().update(uri,
                            contentValues, "_id=?", new String[]{String.valueOf(id)});
                    return;
                }
                log.info("start target app:{} permission setting already", packageName);
            } else {
                log.info("set start target app permission for package:{}", packageName);
                ContentValues contentValues = new ContentValues();
                contentValues.put("callerPkgName", BuildConfig.APPLICATION_ID);
                contentValues.put("calleePkgName", packageName);
                contentValues.put("userSettings", 0);
                context.getContentResolver().insert(uri, contentValues);
            }
        } catch (Exception e) {
            //这个异常，暂时忽略，如果失败，则需要手动去开启后台网络权限
            log.error("call miui system content provider failed", e);
        }
    }

    @Override
    public void doRun() {
        List<ServiceModel> needRestartApp = SQLite.select().from(ServiceModel.class).where(ServiceModel_Table.status.is(true)).queryList();
        log.info("production mode,watch wrapper,form hermes admin configuration:{}", JSONObject.toJSONString(needRestartApp));
        Map<String, ServiceModel> watchServiceMap = Maps.newHashMap();
        for (ServiceModel serviceModel : needRestartApp) {
            watchServiceMap.put(serviceModel.getTargetAppPackage(), serviceModel);
        }

        Set<String> onlineServices = Sets.newHashSet();
        for (Map.Entry<String, IHookAgentService> entry : allRemoteHookService.entrySet()) {
            AgentInfo agentInfo = handleAgentHeartBeat(entry.getKey(), entry.getValue());
            if (agentInfo != null) {
                log.info("the wrapper for app:{} is online,skip restart it", agentInfo.getPackageName());
                onlineServices.add(agentInfo.getPackageName());
                needRestartApp.remove(watchServiceMap.get(agentInfo.getPackageName()));
            }
        }
        fontService.setOnlineServices(onlineServices);
        if (needRestartApp.size() == 0) {
            log.info("all wrapper online");
            return;
        }


        Set<ServiceModel> needInstallApps = Sets.newHashSet(needRestartApp);
        Set<ServiceModel> needUnInstallApps = Sets.newHashSet();
        Set<ServiceModel> needCheckWrapperApps = Sets.newHashSet();

        PackageManager packageManager = context.getPackageManager();
        Set<String> runningProcess = runningProcess(context);

        Iterator<ServiceModel> iterator = needInstallApps.iterator();
        while (iterator.hasNext()) {
            ServiceModel testInstallApp = iterator.next();
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(testInstallApp.getTargetAppPackage(), PackageManager.GET_META_DATA);
                iterator.remove();

                if (testInstallApp.getTargetAppVersionCode() != packageInfo.versionCode) {
                    log.info("target:{} app versionCode update, uninstall it", testInstallApp.getTargetAppPackage());
                    needUnInstallApps.add(testInstallApp);
                    continue;
                }

                if (runningProcess.contains(testInstallApp.getTargetAppPackage())) {
                    log.info("target app:{} running ,but not register service, check if wrapper installed", testInstallApp.getTargetAppPackage());
                    needCheckWrapperApps.add(testInstallApp);
                    continue;
                }

                if ("127.0.0.1".equalsIgnoreCase(APICommonUtils.getLocalIp())) {
                    log.warn("手机未联网");
                    continue;
                }
                makeSureStartOtherAppPermissionOnMIUISecurityCenter(testInstallApp.getTargetAppPackage());
                log.warn("start app：" + testInstallApp.getTargetAppPackage());
                Intent launchIntentForPackage = packageManager.getLaunchIntentForPackage(testInstallApp.getTargetAppPackage());
                context.startActivity(launchIntentForPackage);
            } catch (PackageManager.NameNotFoundException e) {
                //ignore
            }
        }


        for (ServiceModel needInstall : needInstallApps) {
            InstallTaskQueue.getInstance().installTargetApk(needInstall, context);
        }

        for (ServiceModel needReInstall : needUnInstallApps) {
            log.info("uninstall service:{}", needReInstall.getTargetAppPackage());
            SUShell.run("pm uninstall " + needReInstall.getTargetAppPackage());
            InstallTaskQueue.getInstance().installTargetApk(needReInstall, context);
        }

        for (ServiceModel needInstallWrapper : needCheckWrapperApps) {
            if (!InstallTaskQueue.getInstance().installWrapper(needInstallWrapper, context)) {
                log.info("this wrapper install already, restart app again ");
                CommonUtils.killService(needInstallWrapper.getTargetAppPackage());
            }
        }

    }

    private Set<String> runningProcess(Context context) {
//        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
//        if (am == null) {
//            return Collections.emptySet();
//        }
//        Set<String> ret = Sets.newHashSet();
//        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : am.getRunningAppProcesses()) {
//            ret.add(runningAppProcessInfo.processName);
//        }
//        return ret;
        //高版本api中，Android api对该权限收紧，不允许直接获取其他app的运行状态
        // Get a list of running apps
        List<AndroidAppProcess> processes = AndroidProcesses.getRunningAppProcesses();
        Set<String> ret = Sets.newHashSet();
        for (AndroidAppProcess process : processes) {
            // Get some information about the process
            //Log.i("weijia", process.name);
            //ret.add(process.getPackageName());
            //只关心前台进程，所以这里放全称
            ret.add(process.name);
        }
        return ret;
    }

    private AgentInfo handleAgentHeartBeat(String targetPackageName, IHookAgentService hookAgentService) {
        //ping应该很快，如果25s都不能返回，那么肯定是假死了
        PingWatchTask pingWatchTask = new PingWatchTask(System.currentTimeMillis() + 1000 * 25, targetPackageName);
        try {
            //如果targetApp假死，那么这个调用将会阻塞，需要监控这个任务的执行时间，如果长时间ping没有响应，那么需要强杀targetApp
            pingWatchTaskLinkedBlockingDeque.offer(pingWatchTask);
            return hookAgentService.ping();
        } catch (DeadObjectException deadObjectException) {
            log.error("remote service dead,wait for re register");
            fontService.releaseDeadAgent(targetPackageName);
        } catch (RemoteException e) {
            log.error("failed to ping agent", e);
        } finally {
            pingWatchTaskLinkedBlockingDeque.remove(pingWatchTask);
            pingWatchTask.isDone = true;
        }
        return null;
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
                        log.info("the package:{} is zombie,now kill it", poll.targetPackageName);
                        CommonUtils.killService(poll.targetPackageName);
                    } catch (InterruptedException e) {
                        log.info("ping task waite task interrupted,stop loop", e);
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
