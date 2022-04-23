package com.virjar.hermes.hermesagent.host.manager;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;

import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.virjar.hermes.hermesagent.hermes_api.CommonRes;
import com.virjar.hermes.hermesagent.util.CommonUtils;

/**
 * Created by virjar on 2018/8/23.
 */

public class StartAppTask extends AsyncTask<Void, Void, Void> {
    private String targetApp;
    @SuppressLint("StaticFieldLeak")
    private Context context;
    private AsyncHttpServerResponse response;

    public StartAppTask(String targetApp, Context context, AsyncHttpServerResponse response) {
        this.targetApp = targetApp;
        this.context = context;
        this.response = response;
    }


    @Override
    protected void onCancelled() {
        CommonUtils.sendJSON(response, CommonRes.failed("start error"));
    }

    @Override
    protected Void doInBackground(Void... voids) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            CommonUtils.sendJSON(response, CommonRes.failed("failed to load ActivityManager"));
            return null;
        }
        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : am.getRunningAppProcesses()) {
            if (runningAppProcessInfo.processName.equalsIgnoreCase(targetApp)) {
                CommonUtils.sendJSON(response, CommonRes.success("app already running"));
                return null;
            }
        }

        PackageManager packageManager = context.getPackageManager();
        try {
            packageManager.getPackageInfo(targetApp, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            CommonUtils.sendJSON(response, CommonRes.failed("can not find target app:" + targetApp));
            return null;
        }
        Intent launchIntentForPackage = packageManager.getLaunchIntentForPackage(targetApp);
        context.startActivity(launchIntentForPackage);
        CommonUtils.sendJSON(response, CommonRes.success("app " + targetApp + " start success"));
        return null;
    }
}