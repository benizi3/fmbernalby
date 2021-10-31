package com.virjar.hermes.hermesagent.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Charsets;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.virjar.hermes.hermesagent.BuildConfig;
import com.virjar.hermes.hermesagent.bean.CommonRes;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
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
import java.util.concurrent.TimeUnit;

import okhttp3.Request;
import se.vidstige.jadb.JadbConnection;
import se.vidstige.jadb.JadbDevice;

import static android.content.Context.TELEPHONY_SERVICE;

/**
 * Created by virjar on 2018/8/22.
 */

public class CommonUtils {
    private static String TAG = "common_util";

    /**
     * 获取本机IP
     */
    public static String getLocalIp() {
        String ipV6Ip = null;
        String lookUpIP = null;
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                //这个好像是没法用的ip
                if (StringUtils.startsWithIgnoreCase(intf.getName(), "usbnet")) {
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
        printWriter.close();
        return byteArrayOutputStream.toString(Charsets.UTF_8);
    }

    public static String translateSimpleExceptionMessage(Throwable exception) {
        String message = exception.getMessage();
        if (StringUtils.isBlank(message)) {
            message = exception.getClass().getName();
        }
        return message;
    }

    public static void sendJSON(AsyncHttpServerResponse response, CommonRes commonRes) {
        response.send(Constant.jsonContentType, JSONObject.toJSONString(commonRes));
    }

    public static Request pingServerRequest() {
        String url = localServerBaseURL() + Constant.httpServerPingPath;
        return new Request.Builder()
                .get()
                .url(url)
                .build();
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

    public static String execCmd(String cmd, boolean useRoot) {
        Log.i(TAG, "execute command:{" + cmd + "} useRoot:" + useRoot);
        Runtime runtime = Runtime.getRuntime();
        Process proc;
        OutputStreamWriter osw = null;
        StringBuilder stdOut = new StringBuilder();
        StringBuilder stdErr = new StringBuilder();
        try {
            if (useRoot) {
                proc = runtime.exec("su");
                osw = new OutputStreamWriter(proc.getOutputStream());
                osw.write(cmd);
                osw.close();
            } else {
                proc = runtime.exec(cmd);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                proc.waitFor(10, TimeUnit.SECONDS);
            } else {
                proc.waitFor();
            }
            stdOut.append(IOUtils.toString(proc.getInputStream(), Charsets.UTF_8));
            stdErr.append(IOUtils.toString(proc.getErrorStream(), Charsets.UTF_8));
            String result = StringUtils.join(new String[]{stdOut.toString(), stdErr.toString()}, "\r\n");
            Log.i(TAG, "command execute result:" + result);
            return result;
        } catch (InterruptedException e) {
            return "timeOut";
        } catch (Exception ex) {
            String stackTrack = CommonUtils.getStackTrack(ex);
            Log.e(TAG, "exec cmd error" + stackTrack);
            return stackTrack;
        } finally {
            IOUtils.closeQuietly(osw);
        }


    }

    public static Multimap parseUrlEncoded(String query) {
        return Multimap.parse(query, "&", false, new Multimap.StringDecoder() {
            @Override
            public String decode(String s) {
                return URLEncodeUtil.unescape(s);
            }
        });
    }

    @SuppressLint("HardwareIds")
    public static String deviceID(Context context) {
        TelephonyManager telephonyMgr = (TelephonyManager) context.getApplicationContext().getSystemService(TELEPHONY_SERVICE);
        if (telephonyMgr != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                return telephonyMgr.getDeviceId();
            }

        }
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            String mac = wm.getConnectionInfo().getMacAddress();
            if (!StringUtils.equalsIgnoreCase("02:00:00:00:00:00", mac)) {
                return mac;
            }
        }

        BluetoothAdapter m_BluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        String m_szBTMAC = m_BluetoothAdapter.getAddress();
        if (m_szBTMAC != null && !StringUtils.equalsIgnoreCase("02:00:00:00:00:00", m_szBTMAC)) {
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

    public static String pingServer(String sourcePackage) {
        String url = localServerBaseURL() + Constant.httpServerPingPath + "?source_package=" + URLEncodeUtil.escape(sourcePackage);
        try {
            Log.i(TAG, "ping hermes server:" + url);
            String pingResponse = HttpClientUtils.getRequest(url);
            Log.i(TAG, "ping hermes server response: " + pingResponse);
            return pingResponse;
        } catch (IOException e) {
            Log.i(TAG, "ping server failed", e);
            return Constant.unknown;
        }
    }

    public static String localServerBaseURL() {
        return "http://" + CommonUtils.getLocalIp() + ":" + Constant.httpServerPort;
    }

    public static boolean isLocalTest() {
        return BuildConfig.DEBUG;
        //   return false;
    }
}
