package com.virjar.hermes.hermesagent.host.broadcast;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.virjar.hermes.hermesagent.host.service.FontService;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2018/8/23.
 */
@Slf4j
public class StartOnBootBroadcastReceiver extends BroadcastReceiver {
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @Override
    public void onReceive(Context context, Intent intent) {
        log.info("receive start service broadcast");
        Intent service = new Intent(context, FontService.class);
        context.startService(service);


        // clearAbortBroadcast();
    }
}
