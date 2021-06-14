package com.virjar.hermes.hermesagent.host.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import com.google.common.collect.Maps;
import com.virjar.hermes.hermesagent.aidl.IHookAgentService;
import com.virjar.hermes.hermesagent.aidl.IServiceRegister;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by virjar on 2018/8/22.<br>
 * 远程调用服务注册器，代码注入到远程apk之后，远程apk通过发现这个服务。注册自己的匿名binder，到这个容器里面来
 */

public class AIDLRegisterService extends Service {
    private ConcurrentMap<String, IHookAgentService> allRemoteHookService = Maps.newConcurrentMap();
    public static RemoteCallbackList<IHookAgentService> mCallbacks = new RemoteCallbackList<>();

    private IServiceRegister.Stub binder = new IServiceRegister.Stub() {
        @Override
        public void registerHookAgent(String key, IHookAgentService hookAgentService) throws RemoteException {
            if (key == null || hookAgentService == null) {
                throw new RemoteException("service register, service key and service implement can not be null");
            }
            mCallbacks.register(hookAgentService);
            allRemoteHookService.putIfAbsent(key, hookAgentService);
        }

        @Override
        public void unRegisterHookAgent(String key) throws RemoteException {
            // mCallbacks.unregister()
            // mCallbacks.
            //TODO
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
}
