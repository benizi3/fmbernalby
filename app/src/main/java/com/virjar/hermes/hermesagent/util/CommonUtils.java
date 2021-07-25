package com.virjar.hermes.hermesagent.util;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.List;

import se.vidstige.jadb.JadbConnection;
import se.vidstige.jadb.JadbDevice;

import static android.content.Context.TELEPHONY_SERVICE;

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

    @SuppressLint({"HardwareIds", "MissingPermission"})
    public static String deviceID(Context context) {
        TelephonyManager telephonyMgr = (TelephonyManager) context.getApplicationContext().getSystemService(TELEPHONY_SERVICE);
        if (telephonyMgr != null) {
            return telephonyMgr.getDeviceId();
        }
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            return wm.getConnectionInfo().getMacAddress();
        }

        BluetoothAdapter m_BluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        String m_szBTMAC = m_BluetoothAdapter.getAddress();
        if (m_szBTMAC != null) {
            return m_szBTMAC;
        }

        String m_szAndroidID = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        if (StringUtils.isNotBlank(m_szAndroidID)) {
            return m_szAndroidID;
        }

        return getPesudoUniqueID();
    }

    public static String MD5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(s.getBytes(Charsets.UTF_8));
            return toHex(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    public static String toHex(byte[] bytes) {
        StringBuilder ret = new StringBuilder(bytes.length * 2);
        for (byte aByte : bytes) {
            ret.append(HEX_DIGITS[(aByte >> 4) & 0x0f]);
            ret.append(HEX_DIGITS[aByte & 0x0f]);
        }
        return ret.toString();
    }

    private static String getPesudoUniqueID() {
        Field[] declaredFields = Build.class.getDeclaredFields();
        StringBuilder stringBuilder = new StringBuilder();
        for (Field field : declaredFields) {
            Class<?> type = field.getType();
            if (type == String.class && Modifier.isStatic(field.getModifiers())) {
                try {
                    Object o = field.get(null);
                    stringBuilder.append(o.toString());
                } catch (IllegalAccessException e) {
                    //ignore
                }
            }
        }
        String data = stringBuilder.toString();
        if (StringUtils.isBlank(data)) {
            return "unknown";
        }
        return MD5(data);
    }

}
