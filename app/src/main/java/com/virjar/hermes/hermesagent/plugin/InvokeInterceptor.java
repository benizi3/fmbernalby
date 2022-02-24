package com.virjar.hermes.hermesagent.plugin;

import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;

/**
 * Created by virjar on 2018/9/27.
 */

public interface InvokeInterceptor {
    InvokeResult intercept(InvokeRequest invokeRequest);

    void setup();
}
