package com.virjar.hermes.hermesagent.host.manager;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.DeadObjectException;
import android.os.RemoteException;
import android.util.Log;

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
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nullable;

/**
 * Created by virjar on 2018/8/24.<br>
 * server端，监控所有agent的状态，无法调通agent的话，尝试拉起agent
 */

public class AgentWatchTask extends TimerTask {
    private String TAG = "agent_watch_task";
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
    public void run() {
        Set<String> needRestartApp;
        if (CommonUtils.isLocalTest()) {
            //本地测试模式，监控所有agent，死亡拉起
            needRestartApp = Sets.newConcurrentHashSet(allCallback);
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

        }
        Set<String> onlineServices = Sets.newHashSet();
        for (Map.Entry<String, IHookAgentService> entry : allRemoteHookService.entrySet()) {
            AgentInfo agentInfo = handleAgentHeartBeat(entry.getKey(), entry.getValue());
            if (agentInfo != null) {
                onlineServices.add(agentInfo.getPackageName());
                needRestartApp.remove(agentInfo.getPackageName());
            }
        }
        fontService.setOnlineServices(onlineServices);
        if (needRestartApp.size() == 0) {
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
                    Log.w(TAG, "app :" + packageName + " 正常运行，但是agent没有正常注册,请检查xposed模块加载是否失败（日志中显示file not exist，在高版本Android中容易出现）");
                    continue;
                }

                Log.i(TAG, "启动app：" + packageName);
                Intent launchIntentForPackage = packageManager.getLaunchIntentForPackage(packageName);
                context.startActivity(launchIntentForPackage);
            } catch (PackageManager.NameNotFoundException e) {
                //ignore
            }
        }

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
        try {
            return hookAgentService.ping();
        } catch (DeadObjectException deadObjectException) {
            Log.e(TAG, "remote service dead,wait for re register");
            fontService.releaseDeadAgent(targetPackageName);
        } catch (RemoteException e) {
            Log.e(TAG, "failed to ping agent", e);
        }
        return null;
    }
}
