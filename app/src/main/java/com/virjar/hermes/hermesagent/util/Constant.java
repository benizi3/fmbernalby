package com.virjar.hermes.hermesagent.util;

/**
 * Created by virjar on 2018/8/23.
 */

public interface Constant {
    int httpServerPort = 5597;
    String httpServerPingPath = "/ping";
    String startAppPath = "/startApp";
    String jsonContentType = "application/json; charset=utf-8";

    String nativeLibName = "native-lib";

    String packageName = "com.virjar.hermes.hermesagent";
    String fontServiceDestroyAction = "com.virjar.hermes.hermesagent.fontServiceDestroy";
    int status_ok = 0;
    int status_failed = -1;

    String xposedHotloadEntry = "com.virjar.hermes.hermesagent.plugin.HotLoadPackageEntry";
    String appHookSupperPackage = "com.virjar.hermes.hermesagent.hookagent";
    String serviceRegisterAction = "com.virjar.hermes.hermesagent.aidl.IServiceRegister";
}
