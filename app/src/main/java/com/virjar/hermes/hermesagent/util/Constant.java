package com.virjar.hermes.hermesagent.util;

import android.annotation.SuppressLint;
import android.os.Build;

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


    String serverBaseURL = "http://hermesadmin.virjar.com:5597";
    String reportPath = "/device/report";
    String getConfigPath = "/device/deviceConfig";
    String downloadPath = "/targetApp/download";

    String invokePackage = "invoke_package";

    int status_service_not_available = -2;
    String serviceNotAvailableMessage = "service not available";
    int status_need_invoke_package_param = -3;
    String needInvokePackageParamMessage = "the param {" + invokePackage + "} not present";
    int status_rate_limited = -4;
    String rateLimitedMessage = "rate limited";
    String rebind = "rebind";
    String restartServer = "restartServer";
    String unknown = "unknown";

    int serviceStatusOnline = 0;
    int serviceStatusOffline = 1;
    int serviceStatusInstalling = 3;
    int serviceStatusUnInstall = 4;

    //http的server，使用NIO模式，单线程事件驱动，请注意不要在server逻辑里面执行耗时任务
    String httpServerLooperThreadName = "httpServerLooper";
    @SuppressLint("SdCardPath")
    String BASE_DIR = Build.VERSION.SDK_INT >= 24
            ? "/data/user_de/0/" + packageName + "/"
            : "/data/data/" + packageName + "/";
    String HERMES_WRAPPER_DIR = Constant.BASE_DIR + "hermesModules/";

    //adb 远程接口，运行在4555端口,默认端口为5555，但是貌似有其他配置会和5555冲突，引起device offline，所以这里避开冲突
    int ADBD_PORT = 4555;
}
