package com.virjar.hermes.hermesagent.hermes_api;

import android.content.Context;
import android.util.Log;

import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;

import org.joda.time.DateTime;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by virjar on 2018/9/13.
 */
public class APICommonUtils {
    private static AtomicLong fileSequence = new AtomicLong(1);

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

    private static String invokeLogTag = "hermes_IPC_Invoke";

    public static void requestLogI(InvokeRequest invokeRequest, String msg) {
        Log.i(invokeLogTag, buildMessageBody(invokeRequest, msg));
    }

    public static void requestLogW(InvokeRequest invokeRequest, String msg, Throwable throwable) {
        Log.w(invokeLogTag, buildMessageBody(invokeRequest, msg), throwable);
    }

    public static void requestLogW(InvokeRequest invokeRequest, String msg) {
        Log.w(invokeLogTag, buildMessageBody(invokeRequest, msg));
    }

    public static void requestLogE(InvokeRequest invokeRequest, String msg, Throwable throwable) {
        Log.e(invokeLogTag, buildMessageBody(invokeRequest, msg), throwable);
    }

    public static void requestLogE(InvokeRequest invokeRequest, String msg) {
        Log.e(invokeLogTag, buildMessageBody(invokeRequest, msg));
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

}
