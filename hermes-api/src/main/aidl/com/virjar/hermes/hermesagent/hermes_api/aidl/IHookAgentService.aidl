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

    /**
    * IPC调用目标服务
    **/
    InvokeResult invoke(inout InvokeRequest param);

    /**
    * master如果更新代码，那么能通知slave重启自身
    **/
    void killSelf();

    /**
    * master处理完成slave发过来的数据，通知slave删除ICP中的临时文件，
    **/
    void clean(String filePath);
}
