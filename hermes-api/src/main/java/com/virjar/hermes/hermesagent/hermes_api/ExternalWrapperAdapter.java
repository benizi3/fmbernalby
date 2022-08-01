package com.virjar.hermes.hermesagent.hermes_api;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

public abstract class ExternalWrapperAdapter implements AgentCallback {
    @Override
    public boolean needHook(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        return loadPackageParam.processName.equalsIgnoreCase(loadPackageParam.packageName);
    }


    @Override
    public void onXposedHotLoad() {
        //do nothing
    }
}
