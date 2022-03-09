package com.virjar.hermes.hermesagent.plugin.interceptor;

import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;
import com.virjar.hermes.hermesagent.plugin.InvokeInterceptor;

/**
 * Created by virjar on 2018/9/28.<br>
 * 修改app重启时间间隔，默认app在半个小时重启一次，由master控制，如果你的app在挂载插件后，很快就会处于不好的状态，那么可以通过这个修改
 */
@SuppressWarnings("unused")
public class RestartIntervalInterceptor implements InvokeInterceptor {
    @Override
    public InvokeResult intercept(InvokeRequest invokeRequest) {
        return null;
    }

    @Override
    public void setup() {

    }
}
