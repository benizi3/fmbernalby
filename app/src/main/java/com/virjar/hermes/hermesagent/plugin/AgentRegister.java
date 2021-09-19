package com.virjar.hermes.hermesagent.plugin;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.virjar.hermes.hermesagent.aidl.AgentInfo;
import com.virjar.hermes.hermesagent.aidl.IHookAgentService;
import com.virjar.hermes.hermesagent.aidl.IServiceRegister;
import com.virjar.hermes.hermesagent.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.aidl.InvokeResult;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;

/**
 * Created by virjar on 2018/8/23.
 */

public class AgentRegister {
    private static final String TAG = "agent_register";

    public static void registerToServer(final AgentCallback agentCallback, final Context application) {

        final IHookAgentService iHookAgentService = new IHookAgentService.Stub() {
            @Override
            public AgentInfo ping() throws RemoteException {
                return new AgentInfo(application.getPackageName(), application.getPackageName());
            }

            @Override
            public InvokeResult invoke(InvokeRequest param) throws RemoteException {
                try {
                    return agentCallback.invoke(param);
                } catch (Exception e) {
                    Log.e(TAG, "invoke callback failed", e);
                    return InvokeResult.failed(CommonUtils.translateSimpleExceptionMessage(e));
                }
            }
        };

        ServiceConnection mServiceConnection = new ServiceConnection() {
            private IServiceRegister mService = null;

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (mService != null) {
                    return;
                }
                mService = IServiceRegister.Stub.asInterface(service);
                try {
                    // mService.registerCallback("weishi", mCallback);
                    mService.registerHookAgent(iHookAgentService);
                    Log.i(TAG, "register callback" + iHookAgentService.ping().getPackageName());
                } catch (RemoteException e) {
                    Log.e(TAG, "register callback error", e);
                }

            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                //这个调不得，再看看
//                if (mService == null) {
//                    return;
//                }
//                try {
//                    mService.unRegisterHookAgent(iHookAgentService);
//                    mService = null;
//                    Log.i(TAG, " unregister callback" + iHookAgentService.ping().getPackageName());
//                } catch (RemoteException e) {
//                    Log.e(TAG, " unregister callback error", e);
//                }
            }
        };


        Intent intent = new Intent();
        intent.setAction(Constant.serviceRegisterAction);
        intent.setPackage(Constant.packageName);
        application.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

}
