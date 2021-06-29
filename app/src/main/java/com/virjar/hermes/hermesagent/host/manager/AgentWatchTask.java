package com.virjar.hermes.hermesagent.host.manager;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.util.Log;

import com.google.common.collect.Sets;
import com.virjar.hermes.hermesagent.aidl.AgentInfo;
import com.virjar.hermes.hermesagent.aidl.IHookAgentService;
import com.virjar.hermes.hermesagent.host.service.FontService;

import java.util.Collections;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by virjar on 2018/8/24.<br>
 * server端，监控所有agent的状态，无法调通agent的话，尝试拉起agent
 */

public class AgentWatchTask extends TimerTask {
    private String TAG = "agent_watch_task";
    private ConcurrentMap<String, IHookAgentService> allRemoteHookService;
    private Set<String> monitorSets = null;
    private Context context;
    private FontService fontService;

    public AgentWatchTask(FontService fontService, ConcurrentMap<String, IHookAgentService> allRemoteHookService, Set<String> monitorSets, Context context) {
        this.fontService = fontService;
        this.allRemoteHookService = allRemoteHookService;
        this.monitorSets = monitorSets;
        this.context = context;
    }

    @Override
    public void run() {
        Set<String> needRestartApp = Sets.newConcurrentHashSet(monitorSets);
        Set<String> onlineServices = Sets.newHashSet();
        for (IHookAgentService entry : allRemoteHookService.values()) {
            AgentInfo agentInfo = handleAgentHeartBeat(entry);
            if (agentInfo != null) {
                onlineServices.add(agentInfo.getPackageName());
                needRestartApp.remove(agentInfo.getPackageName());
            }
        }
        fontService.setOnlineServices(onlineServices);

        Set<String> needInstallApp = Sets.newCopyOnWriteArraySet(needRestartApp);
        PackageManager packageManager = context.getPackageManager();
        Set<String> runningProcess = runningProcess(context);

        for (String packageName : needInstallApp) {
            try {
                packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA);
                needInstallApp.remove(packageName);

                if (runningProcess.contains(packageName)) {
                    Log.w(TAG, "app :" + packageName + " 正常运行，但是agent没有正常注册");
                    continue;
                }

                Log.i(TAG, "启动app：" + packageName);
                Intent launchIntentForPackage = packageManager.getLaunchIntentForPackage(packageName);
                context.startActivity(launchIntentForPackage);
            } catch (PackageManager.NameNotFoundException e) {
                //ignore
            }
        }

        //TODO 文件下载

    }

    private Set<String> runningProcess(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return Collections.emptySet();
        }
        Set<String> ret = Sets.newHashSet();
        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : am.getRunningAppProcesses()) {
            ret.add(runningAppProcessInfo.processName);
        }
        return ret;
    }

    private AgentInfo handleAgentHeartBeat(IHookAgentService hookAgentService) {
        try {
            return hookAgentService.ping();
        } catch (RemoteException e) {
            Log.e(TAG, "failed to ping agent", e);
            return null;
        }
    }
}
