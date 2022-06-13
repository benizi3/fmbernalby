package com.virjar.hermes.hermesagent.hermes_api;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by virjar on 2018/9/13.
 */

public class APICommonUtils {
    private static final Logger log;
    private static AtomicLong fileSequence = new AtomicLong(1);

    static {
        if (Process.myPid() < Process.FIRST_APPLICATION_UID) {
            Log.w("weijia", "you a use slf4j for system app, some bad things maybe occur,so i will redirect to logcat");
            log = new LogCatLogger();
        } else {
            log = LoggerFactory.getLogger(Constant.hermesWrapperLogTag);
        }
    }

    public static File genTempFile(Context context) {
        File cacheDir = context.getCacheDir();
        File retFile = new File(cacheDir, "hermes_exchange_" + System.currentTimeMillis()
                + "_" + fileSequence.incrementAndGet()
                + "_" + Thread.currentThread().getId());
        try {
            if (!retFile.createNewFile()) {
                throw new IllegalStateException("failed to create temp file :" + retFile.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        makeFileRW(retFile);
        return retFile;
    }

    public static void makeFileRW(File file) {
        try {
            int returnCode = Runtime.getRuntime().exec("chmod 666 " + file.getAbsolutePath()).waitFor();
            if (returnCode != 0) {
                throw new IllegalStateException("failed to change temp file mode");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void requestLogI(InvokeRequest invokeRequest, String msg) {
        log.info(buildMessageBody(invokeRequest, msg));
    }

    public static void requestLogW(InvokeRequest invokeRequest, String msg, Throwable throwable) {
        log.warn(buildMessageBody(invokeRequest, msg), throwable);
    }

    public static void requestLogW(InvokeRequest invokeRequest, String msg) {
        log.warn(buildMessageBody(invokeRequest, msg));
    }

    public static void requestLogE(InvokeRequest invokeRequest, String msg, Throwable throwable) {
        log.error(buildMessageBody(invokeRequest, msg), throwable);
    }

    public static void requestLogE(InvokeRequest invokeRequest, String msg) {
        log.error(buildMessageBody(invokeRequest, msg));
    }

    private static String buildMessageBody(InvokeRequest invokeRequest, String msg) {
        return invokeRequest.getRequestID() + " " + DateTime.now().toString("yyyy-MM-dd hh:mm:ss") + " " + msg;
    }

    public static String safeToString(Object input) {
        if (input == null) {
            return null;
        }
        return input.toString();
    }

    public static final String HERMES_EXTERNAL_WRAPPER_FLAG_KEY = "hermes_target_package";

    /**
     * 获取本机IP
     */
    public static String getLocalIp() {
        String ipV6Ip = null;
        String lookUpIP = null;
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                if (intf.getName() != null && intf.getName().equalsIgnoreCase("usbnet")) {
                    continue;
                }
                for (Enumeration<InetAddress> ipAddr = intf.getInetAddresses(); ipAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = ipAddr.nextElement();
                    if (inetAddress.isLoopbackAddress()) {
                        lookUpIP = inetAddress.getHostAddress();
                        continue;
                    }
                    if (inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    } else {
                        ipV6Ip = inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("query local ip failed", e);
        }


        if (lookUpIP != null) {
            return lookUpIP;
        }
        return ipV6Ip;
    }

    public static String translateSimpleExceptionMessage(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().length() == 0) {
            message = exception.getClass().getName();
        }
        return message;
    }
}
