package com.virjar.hermes.hermesagent.plugin;

import com.virjar.hermes.hermesagent.hermes_api.AgentCallback;
import com.virjar.hermes.hermesagent.hermes_api.EmbedWrapper;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2018/11/2.<br>
 * 外置wrapper包装
 */

public class ExternalWrapper implements EmbedWrapper {
    private AgentCallback delegate;
    private String targetPackageName;

    ExternalWrapper(AgentCallback delegate, String targetPackageName) {
        this.delegate = delegate;
        this.targetPackageName = targetPackageName;
    }

    @Override
    public String targetPackageName() {
        return targetPackageName;
    }

    @Override
    public boolean needHook(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        return delegate.needHook(loadPackageParam);
    }

    @Override
    public InvokeResult invoke(InvokeRequest invokeRequest) {
        return delegate.invoke(invokeRequest);
    }

    @Override
    public void onXposedHotLoad() {
        delegate.onXposedHotLoad();
    }

    String wrapperClassName() {
        return delegate.getClass().getName();
    }
}
