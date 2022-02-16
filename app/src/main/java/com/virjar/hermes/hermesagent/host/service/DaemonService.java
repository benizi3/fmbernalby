package com.virjar.hermes.hermesagent.host.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.virjar.hermes.hermesagent.hermes_api.aidl.DaemonBinder;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.SUShell;

import org.apache.commons.lang3.StringUtils;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by virjar on 2018/9/25.<br>
 * daemon进程，守护hermes主进程，由于slave进程没有root权限，无法在面临hermes agent成为僵尸进程的情况下强杀hermes agent，
 * 所以单启这个进程，该进程啥事都不做，需要保证逻辑很轻量级，避免该进程也被打成僵尸进程了
 */

public class DaemonService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return daemonBinder.asBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (timer != null) {
            timer.cancel();
        }
        timer = new Timer("daemon-service");
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!CommonUtils.xposedStartSuccess) {
                    return;
                }
                if (StringUtils.equalsIgnoreCase(CommonUtils.pingServer(null), "true")) {
                    return;
                }
                Log.i("weijia", "ping hermes http server failed,retry..");
                CommonUtils.sleep(2000);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (StringUtils.equalsIgnoreCase(CommonUtils.pingServer(null), "true")) {
                    return;
                }
                Log.i("weijia", "ping hermes http server failed,retry..");
                CommonUtils.sleep(2000);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (StringUtils.equalsIgnoreCase(CommonUtils.pingServer(null), "true")) {
                    return;
                }
                Log.i("weijia", "ping hermes http server failed,reboot devices");

                SUShell.run("reboot");
            }
        }, 2000, 2 * 60 * 1000);
        return START_STICKY;
    }

    private Timer timer = null;

    private DaemonBinder daemonBinder = new DaemonBinder.Stub() {
        @Override
        public boolean ping() throws RemoteException {
            return true;
        }
    };
}
