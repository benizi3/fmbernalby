package com.virjar.hermes.hermesagent.host.manager;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.DeadObjectException;
import android.os.RemoteException;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.jaredrummler.android.processes.AndroidProcesses;
import com.jaredrummler.android.processes.models.AndroidAppProcess;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.virjar.hermes.hermesagent.hermes_api.aidl.AgentInfo;
import com.virjar.hermes.hermesagent.hermes_api.aidl.IHookAgentService;
import com.virjar.hermes.hermesagent.host.orm.ServiceModel;
import com.virjar.hermes.hermesagent.host.service.FontService;
import com.virjar.hermes.hermesagent.host.service.PingWatchTask;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.DelayQueue;

import javax.annotation.Nullable;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2018/8/24.<br>
 * server端，监控所有agent的状态，无法调通agent的话，尝试拉起agent
 */
@Slf4j
public class AgentWatchTask extends LoggerTimerTask {
    private ConcurrentMap<String, IHookAgentService> allRemoteHookService;
    private Set<String> allCallback;
    private Context context;
    private FontService fontService;

    public AgentWatchTask(FontService fontService, ConcurrentMap<String, IHookAgentService> allRemoteHookService, Set<String> allCallback, Context context) {
        this.fontService = fontService;
        this.allRemoteHookService = allRemoteHookService;
        this.context = context;
        this.allCallback = allCallback;
    }

    @Override
    public void doRun() {
        Set<String> needRestartApp;
        if (CommonUtils.isLocalTest()) {
            //本地测试模式，监控所有agent，死亡拉起
            needRestartApp = Sets.newConcurrentHashSet(allCallback);
            log.info("local test mode, watch all wrapper:{}", JSONObject.toJSONString(needRestartApp));
        } else {
            List<ServiceModel> serviceModels = SQLite.select().from(ServiceModel.class).queryList();
            needRestartApp =
                    Sets.newConcurrentHashSet(Iterables.transform(Iterables.filter(serviceModels, new Predicate<ServiceModel>() {
                        @Override
                        public boolean apply(@Nullable ServiceModel input) {
                            return input != null && input.getStatus() != Constant.serviceStatusUnInstall;
                        }
                    }), new Function<ServiceModel, String>() {
                        @Nullable
                        @Override
                        public String apply(ServiceModel input) {
                            return input.getAppPackage();
                        }
                    }));
            log.info("production mode,watch wrapper,form hermes admin configuration:{}", JSONObject.toJSONString(needRestartApp));

        }
        Set<String> onlineServices = Sets.newHashSet();
        for (Map.Entry<String, IHookAgentService> entry : allRemoteHookService.entrySet()) {
            AgentInfo agentInfo = handleAgentHeartBeat(entry.getKey(), entry.getValue());
            if (agentInfo != null) {
                log.info("the wrapper for app:{} is online,skip restart it", agentInfo.getPackageName());
                onlineServices.add(agentInfo.getPackageName());
                needRestartApp.remove(agentInfo.getPackageName());
            }
        }
        fontService.setOnlineServices(onlineServices);
        if (needRestartApp.size() == 0) {
            log.info("all wrapper online");
            return;
        }

        Set<String> needInstallApp = Sets.newCopyOnWriteArraySet(needRestartApp);
        PackageManager packageManager = context.getPackageManager();
        Set<String> runningProcess = runningProcess(context);

        for (String packageName : needInstallApp) {
            try {
                packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA);
                needInstallApp.remove(packageName);

                if (runningProcess.contains(packageName)) {
                    log.warn("app: {} 正常运行，但是agent没有正常注册,请检查xposed模块加载是否失败（日志中显示file not exist，在高版本Android中容易出现）", packageName);
                    continue;
                }

                if ("127.0.0.1".equalsIgnoreCase(CommonUtils.getLocalIp())) {
                    log.warn("手机未联网");
                    continue;
                }

                log.warn("start app：" + packageName);
                Intent launchIntentForPackage = packageManager.getLaunchIntentForPackage(packageName);
                context.startActivity(launchIntentForPackage);
            } catch (PackageManager.NameNotFoundException e) {
                //ignore
            }
        }

        log.info("some app is configured install on this device,but not presented on the system, we will install this,install list:{}", JSONObject.toJSONString(needInstallApp));
        for (String needInstall : needInstallApp) {
            TargetAppInstallTaskQueue.getInstance().install(needInstall, context);
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
