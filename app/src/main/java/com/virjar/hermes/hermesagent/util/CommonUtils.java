package com.virjar.hermes.hermesagent.util;

import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Charsets;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.virjar.hermes.hermesagent.bean.CommonRes;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

import se.vidstige.jadb.JadbConnection;
import se.vidstige.jadb.JadbDevice;

/**
 * Created by virjar on 2018/8/22.
 */

public class CommonUtils {
    private static String TAG = "common_util";

    public static File genTempFile() {
        return null;
    }


    /**
     * 获取本机IP
     */
    public static String getLocalIp() {
        String ipV6Ip = null;
        String lookUpIP = null;
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> ipAddr = intf.getInetAddresses(); ipAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = ipAddr.nextElement();
                    if (inetAddress.isLoopbackAddress()) {
                        lookUpIP = inetAddress.getHostAddress();
                        continue;
                    }
                    if (inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                    ipV6Ip = inetAddress.getHostAddress();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "query local ip failed", e);
        }

        if (lookUpIP != null) {
            return lookUpIP;
        }
        return ipV6Ip;
    }

    public static String getStackTrack(Throwable throwable) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(byteArrayOutputStream));
        throwable.printStackTrace(printWriter);
        return byteArrayOutputStream.toString(Charsets.UTF_8);
    }

    public static String translateSimpleExceptionMessage(Exception exception) {
        String message = exception.getMessage();
        if (StringUtils.isBlank(message)) {
            message = exception.getClass().getName();
        }
        return message;
    }

    public static void sendJSON(AsyncHttpServerResponse response, CommonRes commonRes) {
        response.send(Constant.jsonContentType, JSONObject.toJSONString(commonRes));
    }

    public static boolean pingServer() {
        String url = "http://" + CommonUtils.getLocalIp() + "：" + Constant.httpServerPort + Constant.httpServerPingPath;
        return StringUtils.equalsIgnoreCase(HttpClientUtils.getRequest(url), "true");
    }

    public static void restartAndroidSystem() {

        JadbConnection jadb = new JadbConnection();
        List<JadbDevice> devices;
        try {
            devices = jadb.getDevices();
        } catch (Exception e) {
            Log.e(TAG, "failed to find adb server", e);
            return;
        }
        if (devices.size() == 0) {
            Log.e(TAG, "failed to find adb server");
            return;
        }
        for (JadbDevice jadbDevice : devices) {
            Log.i(TAG, "reboot device:" + jadbDevice.getSerial());
            try {
                jadbDevice.execute("reboot");
            } catch (Exception e) {
                Log.i(TAG, "device reboot failed");
            }
        }
    }

    public static String execCmd(String cmd) {

        Runtime runtime = Runtime.getRuntime();
        java.lang.Process proc;
        OutputStreamWriter osw = null;
        StringBuilder stdOut = new StringBuilder();
        StringBuilder stdErr = new StringBuilder();

        try {
            proc = runtime.exec("su");
            osw = new OutputStreamWriter(proc.getOutputStream());
            osw.write(cmd);
            osw.flush();
            osw.close();
            proc.waitFor();
        } catch (Exception ex) {
            Log.e(TAG, "exec cmd error", ex);
            return CommonUtils.getStackTrack(ex);
        } finally {
            IOUtils.closeQuietly(osw);
        }
        stdOut.append((new InputStreamReader(proc.getInputStream())));
        stdErr.append(new InputStreamReader(proc.getErrorStream()));
        return StringUtils.join(new String[]{stdOut.toString(), stdErr.toString()}, "--------------");
    }

    public static Multimap parseUrlEncoded(String query) {
        return Multimap.parse(query, "&", false, new Multimap.StringDecoder() {
            @Override
            public String decode(String s) {
                return URLEncodeUtil.unescape(s);
            }
        });
    }
}
