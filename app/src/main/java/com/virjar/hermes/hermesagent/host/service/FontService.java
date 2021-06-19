package com.virjar.hermes.hermesagent.host.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.common.collect.Maps;
import com.virjar.hermes.hermesagent.MainActivity;
import com.virjar.hermes.hermesagent.R;
import com.virjar.hermes.hermesagent.aidl.AgentInfo;
import com.virjar.hermes.hermesagent.aidl.IHookAgentService;
import com.virjar.hermes.hermesagent.aidl.IServiceRegister;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;

/**
 * Created by virjar on 2018/8/22.<br>
 * 远程调用服务注册器，代码注入到远程apk之后，远程apk通过发现这个服务。注册自己的匿名binder，到这个容器里面来
 */

public class FontService extends Service {
    private static final String TAG = "AIDLRegisterService";
    private ConcurrentMap<String, IHookAgentService> allRemoteHookService = Maps.newConcurrentMap();
    public static RemoteCallbackList<IHookAgentService> mCallbacks = new RemoteCallbackList<>();

    private IServiceRegister.Stub binder = new IServiceRegister.Stub() {
        @Override
        public void registerHookAgent(IHookAgentService hookAgentService) throws RemoteException {
            if (hookAgentService == null) {
                throw new RemoteException("service register, service implement can not be null");
            }
            AgentInfo agentInfo = hookAgentService.ping();
            if (agentInfo == null) {
                Log.w(TAG, "service register,ping failed");
                return;
            }
            mCallbacks.register(hookAgentService);
            allRemoteHookService.putIfAbsent(agentInfo.getServiceAlis(), hookAgentService);
        }

        @Override
        public void unRegisterHookAgent(IHookAgentService hookAgentService) throws RemoteException {
            mCallbacks.unregister(hookAgentService);
            allRemoteHookService.remove(hookAgentService.ping().getServiceAlis());
        }
    };

    public IHookAgentService findHookAgent(String serviceName) {
        return allRemoteHookService.get(serviceName);
    }


    public Collection<String> onlineAgentServices() {
        return allRemoteHookService.keySet();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        allRemoteHookService.clear();
        mCallbacks.kill();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification.Builder builder = new Notification.Builder(this.getApplicationContext()); //获取一个Notification构造器
        Intent nfIntent = new Intent(this, MainActivity.class);
        // 设置PendingIntent
        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, FLAG_UPDATE_CURRENT))
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher)) // 设置下拉列表中的图标(大图标)
                .setContentTitle("HermasAgent") // 设置下拉列表里的标题
                .setSmallIcon(R.mipmap.ic_launcher) // 设置状态栏内的小图标
                .setContentText("群控系统") // 设置上下文内容
                .setWhen(System.currentTimeMillis()); // 设置该通知发生的时间

        Notification notification = builder.build(); // 获取构建好的Notification
        notification.defaults = Notification.DEFAULT_SOUND; //设置为默认的声音
        startForeground(110, notification);// 开始前台服务

        return START_STICKY;
    }
}
