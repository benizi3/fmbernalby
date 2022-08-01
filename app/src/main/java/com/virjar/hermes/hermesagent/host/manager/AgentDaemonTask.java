package com.virjar.hermes.hermesagent.host.manager;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.virjar.hermes.hermesagent.BuildConfig;
import com.virjar.hermes.hermesagent.hermes_api.EmbedWrapper;
import com.virjar.hermes.hermesagent.plugin.AgentRegister;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.Constant;

import org.apache.commons.lang3.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2018/8/24.<br>
 * 保持和server的心跳，发现server挂掉之后，拉起server
 */

@Slf4j
public class AgentDaemonTask extends LoggerTimerTask {
    private Context context;
    private EmbedWrapper agentCallback;
    private int retryTimes = 0;

    public AgentDaemonTask(Context context, EmbedWrapper agentCallback) {
        this.context = context;
        this.agentCallback = agentCallback;
    }

    @Override
    public void doRun() {
        log.info("begin agent daemon task");
        long start = System.currentTimeMillis();
        String pingResponse = CommonUtils.pingServer(agentCallback.targetPackageName());
        if (StringUtils.equalsIgnoreCase(pingResponse, Constant.rebind)) {
            AgentRegister.notifyPingDuration(System.currentTimeMillis() - start);
            log.info("server register binder lost ,rebind");
            AgentRegister.registerToServer(agentCallback, context);
            return;
        }

        if (StringUtils.equalsIgnoreCase(pingResponse, "true")) {
            AgentRegister.notifyPingDuration(System.currentTimeMillis() - start);
            retryTimes = 0;
            log.info("ping success");
            return;
        }
        AgentRegister.notifyPingFailed();

        pingResponse = CommonUtils.pingServer(agentCallback.targetPackageName());
        if (StringUtils.equalsIgnoreCase(pingResponse, Constant.unknown)) {
            log.info("HermesAgent dead, restart it，retryTimes：" + retryTimes);
            if (retryTimes > 5) {
                //如果广播方案被禁止，那么尝试显示的启动Hermes进程
                log.info("start hermesAgent launcher activity");
                PackageManager packageManager = context.getPackageManager();
                Intent launchIntentForPackage = packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID);
                context.startActivity(launchIntentForPackage);
            } else {
                retryTimes++;
                log.info("send start hermesAgent font service broadcast");
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
