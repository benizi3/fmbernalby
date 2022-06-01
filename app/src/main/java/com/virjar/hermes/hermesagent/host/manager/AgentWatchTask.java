package com.virjar.hermes.hermesagent.host.manager;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.DeadObjectException;
import android.os.RemoteException;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jaredrummler.android.processes.AndroidProcesses;
import com.jaredrummler.android.processes.models.AndroidAppProcess;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.aidl.AgentInfo;
import com.virjar.hermes.hermesagent.hermes_api.aidl.IHookAgentService;
import com.virjar.hermes.hermesagent.host.orm.ServiceModel;
import com.virjar.hermes.hermesagent.host.orm.ServiceModel_Table;
import com.virjar.hermes.hermesagent.host.service.FontService;
import com.virjar.hermes.hermesagent.host.service.PingWatchTask;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.SUShell;

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

        for (ServiceModel testInstallApp : needInstallApps) {
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(testInstallApp.getTargetAppPackage(), PackageManager.GET_META_DATA);
                needInstallApps.remove(testInstallApp);

                if (testInstallApp.getTargetAppVersionCode() != packageInfo.versionCode) {
                    log.info("target:{} app versionCode update, uninstall it", testInstallApp.getTargetAppPackage());
                    needUnInstallApps.add(testInstallApp);
                    continue;
                }

                if (runningProcess.contains(testInstallApp.getTargetAppPackage())) {
                    needCheckWrapperApps.add(testInstallApp);
                    continue;
                }

                if ("127.0.0.1".equalsIgnoreCase(APICommonUtils.getLocalIp())) {
                    log.warn("手机未联网");
                    continue;
                }
                log.warn("start app：" + testInstallApp);
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
            SUShell.run("pm uninstall " + needReInstall.getTargetAppPackage());
            InstallTaskQueue.getInstance().installTargetApk(needReInstall, context);
        }

        for (ServiceModel needInstallWrapper : needCheckWrapperApps) {
            InstallTaskQueue.getInstance().installWrapper(needInstallWrapper, context);
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
