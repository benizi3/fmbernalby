package com.virjar.hermes.hermesagent.plugin;

import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.Constant;
import com.virjar.hermes.hermesagent.hermes_api.ExternalWrapperAdapter;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;

/**
 * Created by virjar on 2018/12/3.
 * write error message to caller if wrapper startup failed,this always wrapper code error,so hermes framework can not use fail over strategy(restart and so on)
 *
 * @author virjar
 * @since 1.8
 */

public class BrokenWrapper extends ExternalWrapperAdapter {
    private Throwable throwable;

    BrokenWrapper(Throwable throwable) {
        this.throwable = throwable;
    }

    @Override
    public InvokeResult invoke(InvokeRequest invokeRequest) {
        return InvokeResult.failed(Constant.status_wrapper_broken, Constant.wrapperBrokenMessage + " ,detail message is:" + APICommonUtils.getStackTrack(throwable));
    }
}
