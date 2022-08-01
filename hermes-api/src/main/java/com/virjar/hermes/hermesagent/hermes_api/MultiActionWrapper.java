package com.virjar.hermes.hermesagent.hermes_api;

import android.support.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;
import com.virjar.xposed_extention.SharedObject;

import java.util.Map;

/**
 * 通用的wrapper基类，支持多种action路由功能，屏蔽回调函数中一些为了功能完整性定制的但是实际上很少使用的接口。
 * 提供异步返回场景的封装（Android中大量存在异步回调，导致无法直接拿到返回值，需要通过信号通知的方式）。
 */
public class MultiActionWrapper extends ExternalWrapperAdapter {
    private static final String action = "action";
    private static final String actionList = "_actionList";

    /**
     * 存在一个默认的action，为了兼容一些老接口，由于接口刚刚开发的时候，只有一个api。那个时候没有使用多action机制。后来多action机制上线之后，不能影响老的接口
     *
     * @return action名字，默认为Null
     */
    @Nullable
    protected String defaultAction() {
        return null;
    }

    /**
     * 在异步场景，等待时间，我们不能无限制的等待异步回调。默认6.5s，为啥6.5。我拍脑门
     *
     * @return 等待时间
     */
    protected long waitTimeout() {
        return 6500L;
    }

    /**
     * action是否忽略大小写，默认忽略
     *
     * @return 是否忽略大小写
     */
    protected boolean actionCaseIgnore() {
        return true;
    }


    @Override
    public final InvokeResult invoke(InvokeRequest invokeRequest) {
        String action = invokeRequest.getString(MultiActionWrapper.action);
        if (action == null || action.trim().isEmpty()) {
            action = defaultAction();
        }
        if (action == null || action.trim().isEmpty()) {
            return InvokeResult.failed("the param:{" + MultiActionWrapper.action + "} not presented");
        }
        action = actionCaseIgnore() ? action.toLowerCase() : action;
        ActionRequestHandler requestHandler = requestHandlerMap.get(action);
        if (requestHandler == null) {
            if (actionList.equalsIgnoreCase(action)) {
                return InvokeResult.success(requestHandlerMap.keySet(), SharedObject.context);
            }
            return InvokeResult.failed("unknown action: " + action);
        }
        Object handlerResult = requestHandler.handleRequest(invokeRequest);

        if (handlerResult instanceof AsyncResult.AsyncResultBuilder) {
            handlerResult = ((AsyncResult.AsyncResultBuilder) handlerResult).build();
        }

        InvokeResult result = tryWrap(invokeRequest, handlerResult);
        if (result != null) {
            return result;
        }

        AsyncResult asyncResult = (AsyncResult) handlerResult;
        asyncResult.waitCallback(waitTimeout());
        if (!asyncResult.isCallbackCalled()) {
            return InvokeResult.failed("Request TimeOut");
        }
        return tryWrap(invokeRequest, asyncResult.finalResult());
    }

    private InvokeResult tryWrap(InvokeRequest invokeRequest, Object handlerResult) {
        if (handlerResult == null) {
            return InvokeResult.failed("handler return null, wrapper logic error");
        }
        if (handlerResult instanceof InvokeResult) {
            return (InvokeResult) handlerResult;
        }
        if (handlerResult instanceof Throwable) {
            WrapperLog.requestLogE(invokeRequest, "handler throw an exception", (Throwable) handlerResult);
            return InvokeResult.failed(APICommonUtils.translateSimpleExceptionMessage((Throwable) handlerResult));
        }

        if (handlerResult instanceof InvokeResultPromise) {
            return InvokeResult.success(handlerResult, SharedObject.context);
        }

        if (!(handlerResult instanceof AsyncResult)) {
            return InvokeResult.success(handlerResult, SharedObject.context);
        }
        return null;
    }


    public void registryHandler(ActionRequestHandler requestHandler) {
        registryHandler(MultiActionWrapperFactory.resolveAction(requestHandler), requestHandler);
    }

    public void registryHandler(String action, ActionRequestHandler requestHandler) {
        Preconditions.checkNotNull(action);
        action = actionCaseIgnore() ? action.toLowerCase() : action;
        if (requestHandlerMap.containsKey(action) && !requestHandlerMap.get(action).equals(requestHandler)) {
            throw new IllegalStateException("the request handler: " + requestHandler + " for action:" + action + "  registered already!!");
        }
        requestHandlerMap.put(action, requestHandler);
    }

    private Map<String, ActionRequestHandler> requestHandlerMap = Maps.newHashMap();
}
