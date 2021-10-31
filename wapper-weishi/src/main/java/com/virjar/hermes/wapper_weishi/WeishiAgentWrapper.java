package com.virjar.hermes.wapper_weishi;

import com.virjar.hermes.hermesagent.hermes_api.AgentCallback;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2018/9/13.
 */

public class WeishiAgentWrapper implements AgentCallback {
    @Override
    public String targetPackageName() {
        return null;
    }

    @Override
    public InvokeResult invoke(InvokeRequest invokeRequest) {
        return null;
    }

    @Override
    public void onXposedHotLoad() {

    }

    @Override
    public boolean needHook(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        return false;
    }
}
