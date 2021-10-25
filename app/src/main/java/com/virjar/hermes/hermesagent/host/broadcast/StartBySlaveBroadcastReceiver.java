package com.virjar.hermes.hermesagent.host.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.virjar.hermes.hermesagent.host.service.FontService;

/**
 * Created by virjar on 2018/9/12.
 */

public class StartBySlaveBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent service = new Intent(context, FontService.class);
        context.startService(service);
    }
}
