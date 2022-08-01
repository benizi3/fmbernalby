// IServiceRegister.aidl
package com.virjar.hermes.hermesagent.hermes_api.aidl;

import com.virjar.hermes.hermesagent.hermes_api.aidl.IHookAgentService;
// Declare any non-default types here with import statements

interface IServiceRegister {
    void registerHookAgent(IHookAgentService hookAgentService);
    void unRegisterHookAgent(IHookAgentService hookAgentService);
    List<String> onlineService();
    void notifyPingDuration(long duration);
    void notifyPingFailed();
    double systemScore();
}
