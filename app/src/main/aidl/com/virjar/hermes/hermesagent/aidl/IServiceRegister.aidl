// IServiceRegister.aidl
package com.virjar.hermes.hermesagent.aidl;

import com.virjar.hermes.hermesagent.aidl.IHookAgentService;
// Declare any non-default types here with import statements

interface IServiceRegister {
    void registerHookAgent(IHookAgentService hookAgentService);
    void unRegisterHookAgent(IHookAgentService hookAgentService);
}
