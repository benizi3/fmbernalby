package com.virjar.hermes.hermesagent.hermes_api;

import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;

/**
 * handle处理抽象，针对于不同action的路由处理
 */
public interface ActionRequestHandler {
    Object handleRequest(InvokeRequest invokeRequest);
}
