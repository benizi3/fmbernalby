package com.virjar.hermes.hermesagent.host.manager;

import android.content.Context;
import android.content.Intent;

import com.virjar.hermes.hermesagent.host.service.FontService;
import com.virjar.hermes.hermesagent.plugin.AgentCallback;
import com.virjar.hermes.hermesagent.plugin.AgentRegister;
import com.virjar.hermes.hermesagent.util.CommonUtils;

import java.util.TimerTask;

/**
 * Created by virjar on 2018/8/24.
 */

public class AgentDaemonTask extends TimerTask {
    private Context context;
    private AgentCallback agentCallback;

    public AgentDaemonTask(Context context, AgentCallback agentCallback) {
        this.context = context;
        this.agentCallback = agentCallback;
    }

    @Override
    public void run() {
        if (CommonUtils.pingServer()) {
            return;
        }

        if (CommonUtils.pingServer()) {
            return;
        }

        Intent service = new Intent(context, FontService.class);
        context.startService(service);

        AgentRegister.registerToServer(agentCallback, context);
    }
}
