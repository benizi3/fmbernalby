package com.virjar.hermes.hermesagent.util;

/**
 * Created by virjar on 2018/8/23.
 */

public interface Constant {
    int httpServerPort = 5597;
    String httpServerPingPath = "/ping";
    String startAppPath = "/startApp";
    String invokePath = "/invoke";
    String aliveServicePath = "/aliveService";
    String restartDevicePath = "/restartDevice";
    String executeShellCommandPath = "/executeCommand";
    String jsonContentType = "application/json; charset=utf-8";

    String nativeLibName = "native-lib";

    String packageName = "com.virjar.hermes.hermesagent";
    String fontServiceDestroyAction = "com.virjar.hermes.hermesagent.fontServiceDestroy";
    int status_ok = 0;
    int status_failed = -1;

    String xposedHotloadEntry = "com.virjar.hermes.hermesagent.plugin.HotLoadPackageEntry";
    String appHookSupperPackage = "com.virjar.hermes.hermesagent.hookagent";
    String serviceRegisterAction = "com.virjar.hermes.hermesagent.aidl.IServiceRegister";


    String serverBaseURL = "http://hermas.virjar.com:5598";
    String reportPath = "/report";
    String downloadPath = "/downloadAPK";

    String invokePackage = "invoke_package";

    int status_service_not_available = -2;
    String serviceNotAvailableMessage = "service not available";
    int status_need_invoke_package_param = -3;
    String needInvokePackageParamMessage = "the param {" + invokePackage + "} not present";
    int status_rate_limited = -4;
    String rateLimitedMessage = "rate limited";
}
