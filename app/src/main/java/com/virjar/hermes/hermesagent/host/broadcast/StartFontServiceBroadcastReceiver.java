package com.virjar.hermes.hermesagent.host.broadcast;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.virjar.hermes.hermesagent.host.service.FontService;

/**
 * Created by virjar on 2018/8/23.
 */

public class StartFontServiceBroadcastReceiver extends BroadcastReceiver {
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("StartFontService", "receive start service broadcast");
        Intent service = new Intent(context, FontService.class);
        context.startService(service);

       // clearAbortBroadcast();
    }
}
