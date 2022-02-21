package com.virjar.hermes.hermesagent.plugin;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.virjar.hermes.hermesagent.BuildConfig;
import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.AgentCallback;
import com.virjar.hermes.hermesagent.hermes_api.aidl.AgentInfo;
import com.virjar.hermes.hermesagent.hermes_api.aidl.IHookAgentService;
import com.virjar.hermes.hermesagent.hermes_api.aidl.IServiceRegister;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;

import java.io.File;

import eu.chainfire.libsuperuser.Shell;

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
                    APICommonUtils.requestLogI(param, "handle IPC invoke request");
                    InvokeResult result = agentCallback.invoke(param);
                    if (result == null) {
                        APICommonUtils.requestLogW(param, "agent return null");
                    } else {
                        APICommonUtils.requestLogI(param, "IPC invoke result: " + result.getTheData(false));
                    }
                    return result;
                } catch (Exception e) {
                    APICommonUtils.requestLogW(param, "invoke callback failed", e);
                    return InvokeResult.failed(CommonUtils.translateSimpleExceptionMessage(e));
                }
            }

            @Override
            public void killSelf() throws RemoteException {
                new Thread() {
                    @Override
                    public void run() {
                        Process.killProcess(Process.myPid());
                        System.exit(0);
                    }
                }.start();
            }

            @Override
            public void clean(String filePath) throws RemoteException {
                File file = new File(filePath);
                if (!file.exists()) {
                    return;
                }

                if (!file.delete()) {
                    Shell.SH.run("rm -f " + file.getAbsolutePath());
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
        intent.setPackage(BuildConfig.APPLICATION_ID);
        application.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

}
