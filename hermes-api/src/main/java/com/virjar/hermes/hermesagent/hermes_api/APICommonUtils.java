package com.virjar.hermes.hermesagent.hermes_api;

import android.content.Context;

import com.google.common.base.Charsets;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;

import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by virjar on 2018/9/13.
 */

public class APICommonUtils {

    private static AtomicLong fileSequence = new AtomicLong(1);

    public static File genTempFile(Context context) {
        File cacheDir = context.getCacheDir();
        File retFile = new File(cacheDir, Constant.exchangeFilePreffx + System.currentTimeMillis()
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

    @Deprecated
    public static void requestLogI(InvokeRequest invokeRequest, String msg) {
        WrapperLog.requestLogI(invokeRequest, msg);
    }

    @Deprecated
    public static void requestLogW(InvokeRequest invokeRequest, String msg, Throwable throwable) {
        WrapperLog.requestLogW(invokeRequest, msg, throwable);
    }

    @Deprecated
    public static void requestLogW(InvokeRequest invokeRequest, String msg) {
        WrapperLog.requestLogW(invokeRequest, msg);
    }

    @Deprecated
    public static void requestLogE(InvokeRequest invokeRequest, String msg, Throwable throwable) {
        WrapperLog.requestLogE(invokeRequest, msg, throwable);
    }

    @Deprecated
    public static void requestLogE(InvokeRequest invokeRequest, String msg) {
        WrapperLog.requestLogE(invokeRequest, msg);
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
            WrapperLog.getWrapperLogger().warn("query local ip failed", e);
        }


        if (lookUpIP != null) {
            return lookUpIP;
        }
        return ipV6Ip;
    }

    public static String translateSimpleExceptionMessage(Throwable exception) {
        String message = exception.getClass().getName();
        if (exception.getMessage() != null && exception.getMessage().trim().length() >= 0) {
            message += ":" + exception.getMessage().trim();
        }
        return message;
    }

    public static String getStackTrack(Throwable throwable) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(byteArrayOutputStream));
        throwable.printStackTrace(printWriter);
        printWriter.close();
        return byteArrayOutputStream.toString(Charsets.UTF_8);
    }
}
