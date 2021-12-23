package com.virjar.hermes.hermesagent.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.virjar.hermes.hermesagent.BuildConfig;
import com.virjar.hermes.hermesagent.bean.CommonRes;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.List;

import javax.annotation.Nullable;

import eu.chainfire.libsuperuser.Shell;
import okhttp3.Request;

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


    public static String execCmd(String cmd, boolean useRoot) {
        Log.i(TAG, "execute command:{" + cmd + "} useRoot:" + useRoot);
        List<String> strings = useRoot ? Shell.SU.run(cmd) : Shell.SH.run(cmd);
        String result = StringUtils.join(strings, "\r\n");
        Log.i(TAG, "command execute result:" + result);
        return result;
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
        String url = localServerBaseURL() + Constant.httpServerPingPath + "?source_package=" + EscapeUtil.escape(sourcePackage);
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

    public static ApkMeta parseApk(File file) {
        //now parse the file
        try (ApkFile apkFile = new ApkFile(file)) {
            return apkFile.getApkMeta();
        } catch (IOException e) {
            throw new IllegalStateException("the filed not a apk filed format", e);
        }
    }

    public static boolean xposedStartSuccess = false;

    private static boolean checkTcpAdbRunning() {
        Socket socket = new Socket();
        String localIp = getLocalIp();
        boolean localBindSuccess = false;
        try {
            socket.bind(new InetSocketAddress(localIp, 0));
            localBindSuccess = true;
            InetSocketAddress endpointSocketAddr = new InetSocketAddress(localIp, Constant.ADBD_PORT);
            socket.connect(endpointSocketAddr, 1000);
            return true;
        } catch (IOException e) {
            //ignore
            if (!localBindSuccess) {
                throw new IllegalStateException(e);
            }
            return false;
        } finally {
            IOUtils.closeQuietly(socket);
        }
    }

    private static volatile boolean isSettingADB = false;

    /**
     * 将adb daemon进程设置为tcp的模式，这样就可以通过远程的方案使用adb，adbd是在zygote之前启动的一个进程，权限高于普通system进程
     */
    public static synchronized void enableADBTCPProtocol(Context context) throws IOException, InterruptedException {
        if (isSettingADB) {
            return;
        }
        isSettingADB = true;
        String adbTag = "tcpADB";
        try {
            //check if adb running on 4555 port
            if (checkTcpAdbRunning()) {
                Log.i(adbTag, "the adb service already running on " + Constant.ADBD_PORT);
                return;
            }
            if (!Shell.SU.available()) {
                Log.w(adbTag, "acquire root permission failed,can not enable adbd service with tcp protocol mode");
                return;
            }


            List<String> result = Shell.run("su", new String[]{
                    "getprop service.adb.tcp.port"
            }, null, true);
            for (String str : result) {
                if (StringUtils.isBlank(str)) {
                    continue;
                }
                if (!StringUtils.equalsIgnoreCase(str, String.valueOf(Constant.ADBD_PORT))) {
                    Log.w(adbTag, "adbd daemon server need running on :" + Constant.ADBD_PORT + " now is: " + str + "  we will switch it");
                    break;
                } else {
                    List<String> executeOutput =
                            Shell.SU.run(Lists.newArrayList("stop adbd", "start adbd"));
                    Log.i(adbTag, "adb tcp port settings already , just restart adbd: " + Joiner.on("\n").skipNulls().join(executeOutput));
                    return;
                }
            }

            //将文件系统挂载为可读写
            List<String> executeOutput = Shell.SU.run("mount -o remount,rw /system");
            Log.i(adbTag, "remount file system: " + Joiner.on("\n").skipNulls().join(executeOutput));

            Log.i(adbTag, "edit file /system/build.prop");
            List<String> buildProperties = Shell.SU.run("cat /system/build.prop");
            List<String> newProperties = Lists.newArrayListWithCapacity(buildProperties.size());
            for (String property : buildProperties) {
                if (StringUtils.startsWithIgnoreCase(property, "ro.sys.usb.storage.type=")
                        || StringUtils.startsWithIgnoreCase(property, "persist.sys.usb.config=")) {
                    int i = property.indexOf("=");
                    newProperties.add(property.substring(0, i) + "=" + Joiner.on(",").join(Iterables.filter(Splitter.on(",").splitToList(property.substring(i + 1))
                            , new Predicate<String>() {
                                @Override
                                public boolean apply(@Nullable String input) {
                                    return !StringUtils.equalsIgnoreCase(input, "adb");
                                }
                            })));
                    continue;
                }
                if (StringUtils.startsWithIgnoreCase(property, "service.adb.tcp.port=")) {
                    continue;
                }
                newProperties.add(property);
            }
            newProperties.add("service.adb.tcp.port=" + Constant.ADBD_PORT);

            //覆盖文件到配置文件

            File file = new File(context.getCacheDir(), "build.prop");
            BufferedWriter bufferedWriter = Files.newWriter(file, Charsets.UTF_8);
            for (String property : newProperties) {
                bufferedWriter.write(property);
                bufferedWriter.newLine();
            }
            IOUtils.closeQuietly(bufferedWriter);
            String mvCommand = "mv " + file.getAbsolutePath() + " /system/";
            executeOutput = Shell.SU.run(mvCommand);
            Log.i(adbTag, "write content to /system/build.prop  " + mvCommand + "  " + Joiner.on("\n").skipNulls().join(executeOutput));
            Shell.SU.run("chmod 644 /system/build.prop");

            executeOutput = Shell.SU.run("mount -o remount ro /system");
            Log.i(adbTag, "re mount file system to read only" + Joiner.on("\n").skipNulls().join(executeOutput));

            Shell.SU.run(Lists.newArrayList("setprop service.adb.tcp.port  " + Constant.ADBD_PORT, "stop adbd", "start adbd"));
        } finally {
            isSettingADB = false;
        }
    }

    public static String safeToString(Object input) {
        if (input == null) {
            return null;
        }
        return input.toString();
    }

    public static String getPackageName(Intent intent) {
        Uri uri = intent.getData();
        return (uri != null) ? uri.getSchemeSpecificPart() : null;
    }

    public static String getRequestID() {
        return "request_session_" + Thread.currentThread().getId() + "_" + System.currentTimeMillis();
    }
}
