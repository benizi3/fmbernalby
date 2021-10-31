package com.virjar.hermes.hermesagent.hermes_api;

import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2018/9/13.<br>
 * wrapper接口规范
 */

public interface AgentCallback {
    /**
     * 该回调，依赖某个app，一个回调函数，只能允许hook一个app，避免功能错乱
     *
     * @return app包名
     */
    String targetPackageName();

    /**
     * 即使知道目标package，也可能该包名存在多个子进程，仍然需要对进程信息进行判定，最终决定那个进程注入代码
     *
     * @param loadPackageParam xposed的入口参数
     * @return 是否需要在这里注入
     */
    boolean needHook(XC_LoadPackage.LoadPackageParam loadPackageParam);

    /**
     * 同步rpc调用，根据具体的参数，返回指定结果
     *
     * @param invokeRequest 请求封装
     * @return 响应封装
     */
    InvokeResult invoke(InvokeRequest invokeRequest);


    /**
     * 当xposed启动的时候，执行的钩子挂载逻辑，在这里可以将钩子挂载到目标apk任意代码上，并在invoke的时候得到调用结果
     */
    void onXposedHotLoad();
}
