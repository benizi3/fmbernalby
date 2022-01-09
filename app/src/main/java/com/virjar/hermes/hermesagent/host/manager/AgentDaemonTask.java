package com.virjar.hermes.hermesagent.host.manager;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import com.virjar.hermes.hermesagent.BuildConfig;
import com.virjar.hermes.hermesagent.hermes_api.AgentCallback;
import com.virjar.hermes.hermesagent.plugin.AgentRegister;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;

import org.apache.commons.lang3.StringUtils;

import java.util.TimerTask;

/**
 * Created by virjar on 2018/8/24.<br>
 * 保持和server的心跳，发现server挂掉之后，拉起server
 */

public class AgentDaemonTask extends TimerTask {
    private static final String TAG = "AgentDaemonTask";
    private Context context;
    private AgentCallback agentCallback;
    private int retryTimes = 0;

    public AgentDaemonTask(Context context, AgentCallback agentCallback) {
        this.context = context;
        this.agentCallback = agentCallback;
    }

    @Override
    public void run() {
        String pingResponse = CommonUtils.pingServer(agentCallback.targetPackageName());
        if (StringUtils.equalsIgnoreCase(pingResponse, Constant.rebind)) {
            Log.i(TAG, "rebind service");
            AgentRegister.registerToServer(agentCallback, context);
            return;
        }

        if (StringUtils.equalsIgnoreCase(pingResponse, "true")) {
            retryTimes = 0;
            return;
        }

        pingResponse = CommonUtils.pingServer(agentCallback.targetPackageName());
        if (StringUtils.equalsIgnoreCase(pingResponse, Constant.unknown)) {
            Log.i(TAG, "HermesAgent dead, restart it，retryTimes：" + retryTimes);
            if (retryTimes > 5) {
                //如果广播方案被禁止，那么尝试显示的启动Hermes进程
                PackageManager packageManager = context.getPackageManager();
                Intent launchIntentForPackage = packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID);
                context.startActivity(launchIntentForPackage);
            } else {
                retryTimes++;
                Intent broadcast = new Intent();
                broadcast.setPackage(BuildConfig.APPLICATION_ID);
                broadcast.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                broadcast.setAction("com.virjar.hermes.hermesagent.fontServiceDestroy");
                //这里不能直接start，只能发广播的方式
                //请注意，这里需要放开自启动限制，否则广播可能被系统拦截
                context.sendBroadcast(broadcast);
            }
        }

    }
}
