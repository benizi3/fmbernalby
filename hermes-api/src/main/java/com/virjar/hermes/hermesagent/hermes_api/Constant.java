package com.virjar.hermes.hermesagent.hermes_api;

import android.annotation.SuppressLint;
import android.os.Build;


/**
 * Created by virjar on 2018/8/23.
 * <br>通用的常量定义，TODO 需要整理，不是所有都是hermes-api的常量
 */
public interface Constant {
    int httpServerPort = 5597;
    String httpServerPingPath = "/ping";
    String startAppPath = "/startApp";
    String invokePath = "/invoke";
    String aliveServicePath = "/aliveService";
    String restartDevicePath = "/restartDevice";
    String executeShellCommandPath = "/executeCommand";
    String reloadService = "/reloadService";
    String restartAdbD = "/restartAdbD";
    String jsonContentType = "application/json; charset=utf-8";
    String plainTextContentType = "text/plain; charset=utf-8";

    String nativeLibName = "native-lib";

    String fontServiceDestroyAction = "com.virjar.hermes.hermesagent.fontServiceDestroy";
    int status_ok = 0;
    int status_failed = -1;

    String xposedHotloadEntry = "com.virjar.hermes.hermesagent.plugin.HotLoadPackageEntry";
    String appHookSupperPackage = "com.virjar.hermes.hermesagent.hookagent";
    String serviceRegisterAction = "com.virjar.hermes.hermesagent.aidl.IServiceRegister";


    //String serverHost = "hermesadmin.virjar.com";
    //10.8.126.249为测试服务器地址，暂时使用这个
    //String serverHost = "10.8.126.249";
    int serverHttpPort = 5597;
    int serverNettyPort = 5598;

    String serverBaseURL = "https://content.bytedance.net/hermes";
    String reportPath = "/device/report";
    String getConfigPath = "/device/deviceConfig";
    String downloadPath = "/targetApp/download";

    String invokePackage = "invoke_package";

    String invokeSessionID = "invoke_session_id";

    String invokeRequestID = "invoke_request_id";

    int status_service_not_available = -2;
    String serviceNotAvailableMessage = "service not available";
    int status_need_invoke_package_param = -3;
    String needInvokePackageParamMessage = "the param {" + invokePackage + "} not present";
    int status_rate_limited = -4;
    String rateLimitedMessage = "rate limited";
    String rebind = "rebind";

    String unknown = "unknown";


    //http的server，使用NIO模式，单线程事件驱动，请注意不要在server逻辑里面执行耗时任务
    String httpServerLooperThreadName = "httpServerLooper";


    //adb 远程接口，运行在4555端口,默认端口为5555，但是貌似有其他配置会和5555冲突，引起device offline，所以这里避开冲突
    int ADBD_PORT = 4555;

    String xposedInstallerPackage = "de.robv.android.xposed.installer";

    @SuppressLint("SdCardPath")
    String XPOSED_BASE_DIR = Build.VERSION.SDK_INT >= 24
            ? "/data/user_de/0/" + xposedInstallerPackage + "/"
            : "/data/data/" + xposedInstallerPackage + "/";

    int WEBSOCKET_PORT = 19999;

    // 最大协议包长度
    int MAX_FRAME_LENGTH = 1024 * 10;

    // 10k
    int MAX_AGGREGATED_CONTENT_LENGTH = 65536;

    int HEAD_LENGTH = 4;

    String xposedBridgeApkFileName = "xposedbridge-release.apk";

    String MIUIPowerKeeperContentProviderURI = "content://com.miui.powerkeeper.configure/userTable";
    String MIUIStartActivityRuleListProviderURI = "content://com.lbe.security.miui.permmgr/StartActivityRuleList";
    String AGENT_INTERCEPTOR_PACKAGE = "com.virjar.hermes.hermesagent.plugin.interceptor";
    String HERMES_SETTING_INVOKE = "__hermes_inner_invoke";
    String HERMES_IGNORE_INTERCEPTOR_FILTER = "__hermes_ignore_interceptor_filter";

    String hermesWrapperLogTag = "hermes_IPC_Invoke";
}
