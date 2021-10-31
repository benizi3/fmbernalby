// IHookAgentService.aidl
package com.virjar.hermes.hermesagent.hermes_api.aidl;

// Declare any non-default types here with import statements
import com.virjar.hermes.hermesagent.hermes_api.aidl.AgentInfo;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;

interface IHookAgentService {

    /**
    * 获取服务信息，同时提供链路连通性探测的能力
    */
    AgentInfo ping();

    InvokeResult invoke(inout InvokeRequest param);
}
