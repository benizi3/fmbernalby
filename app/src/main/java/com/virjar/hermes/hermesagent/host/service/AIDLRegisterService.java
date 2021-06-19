package com.virjar.hermes.hermesagent.host.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.common.collect.Maps;
import com.virjar.hermes.hermesagent.aidl.AgentInfo;
import com.virjar.hermes.hermesagent.aidl.IHookAgentService;
import com.virjar.hermes.hermesagent.aidl.IServiceRegister;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by virjar on 2018/8/22.<br>
 * 远程调用服务注册器，代码注入到远程apk之后，远程apk通过发现这个服务。注册自己的匿名binder，到这个容器里面来
 */

public class AIDLRegisterService extends Service {
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
}
