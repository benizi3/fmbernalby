package com.virjar.hermes.hermesagent.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
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
import com.jaredrummler.android.processes.AndroidProcesses;
import com.jaredrummler.android.processes.models.AndroidAppProcess;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.virjar.hermes.hermesagent.BuildConfig;
import com.virjar.hermes.hermesagent.hermes_api.CommonRes;
import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.Constant;
import com.virjar.hermes.hermesagent.hermes_api.EscapeUtil;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.xposed_extention.CommonConfig;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.List;

import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import dalvik.system.PathClassLoader;
import eu.chainfire.libsuperuser.Shell;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

import static android.content.Context.TELEPHONY_SERVICE;

/**
 * Created by virjar on 2018/8/22.<br>
 */
@Slf4j
public class CommonUtils {

    public static boolean isLocalTest() {
        return BuildConfig.DEBUG;
        //return false;
    }


    public static String getStackTrack(Throwable throwable) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(byteArrayOutputStream));
        throwable.printStackTrace(printWriter);
        printWriter.close();
        return byteArrayOutputStream.toString(Charsets.UTF_8);
    }



    public static void sendJSON(AsyncHttpServerResponse response, CommonRes commonRes) {
        response.send(Constant.jsonContentType, JSONObject.toJSONString(commonRes));
    }

    public static void sendPlainText(AsyncHttpServerResponse response, String text) {
        response.send(Constant.plainTextContentType, text);
    }

    public static Request pingServerRequest() {
        String url = localServerBaseURL() + Constant.httpServerPingPath;
        return new Request.Builder()
                .get()
                .url(url)
                .build();
    }


    public static String execCmd(String cmd, boolean useRoot) {
        log.info("execute command:{" + cmd + "} useRoot:" + useRoot);
        List<String> strings = useRoot ? SUShell.run(cmd) : Shell.SH.run(cmd);
        String result = StringUtils.join(strings, "\r\n");
        log.info("command execute result:" + result);
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
        String url = localServerBaseURL() + Constant.httpServerPingPath;
        if (StringUtils.isNotBlank(sourcePackage)) {
            url += "?source_package=" + EscapeUtil.escape(sourcePackage);
        }
        try {
            log.info("ping hermes server:" + url);
            String pingResponse = HttpClientUtils.getRequest(url);
            log.info("ping hermes server response: " + pingResponse);
            return pingResponse;
        } catch (IOException e) {
            log.error("ping server failed", e);
            return Constant.unknown;
        }
    }

    public static String localServerBaseURL() {
        return "http://" + APICommonUtils.getLocalIp() + ":" + Constant.httpServerPort;
    }

    private static final String ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD";
    private static final String ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema";
    private static final String FEATURE_LOAD_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    private static final String FEATURE_DISABLE_DOCTYPE_DECL = "http://apache.org/xml/features/disallow-doctype-decl";

    /**
     * @param data File to load into Document
     * @return Document
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    public static Document loadDocument(String data)
            throws IOException, SAXException, ParserConfigurationException {

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setFeature(FEATURE_DISABLE_DOCTYPE_DECL, true);
        docFactory.setFeature(FEATURE_LOAD_DTD, false);

        try {
            docFactory.setAttribute(ACCESS_EXTERNAL_DTD, " ");
            docFactory.setAttribute(ACCESS_EXTERNAL_SCHEMA, " ");
        } catch (IllegalArgumentException ex) {
            log.warn("JAXP 1.5 Support is required to validate XML");
        }

        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        // Not using the parse(File) method on purpose, so that we can control when
        // to close it. Somehow parse(File) does not seem to close the file in all cases.
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data.getBytes())) {
            return docBuilder.parse(inputStream);
        }
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
        String localIp = APICommonUtils.getLocalIp();
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
        // Debug.setDebug(true);
        isSettingADB = true;
        try {
            //check if adb running on 4555 port
            if (checkTcpAdbRunning()) {
                log.info("the adb service already running on " + Constant.ADBD_PORT);
                return;
            }
            if (!CommonUtils.isSuAvailable()) {
                log.warn("acquire root permission failed,can not enable adbd service with tcp protocol mode");
                return;
            }


            List<String> result = SUShell.run("getprop service.adb.tcp.port");
            for (String str : result) {
                if (StringUtils.isBlank(str)) {
                    continue;
                }
                if (!StringUtils.equalsIgnoreCase(str, String.valueOf(Constant.ADBD_PORT))) {
                    log.info("adbd daemon server need running on :" + Constant.ADBD_PORT + " now is: " + str + "  we will switch it");
                    break;
                } else {
                    List<String> executeOutput =
                            SUShell.run(Lists.newArrayList("stop adbd", "start adbd"));
                    log.info("adb tcp port settings already , just restart adbd: " + Joiner.on("\n").skipNulls().join(executeOutput));
                    return;
                }
            }

            //将文件系统挂载为可读写
            List<String> executeOutput = SUShell.run("mount -o remount,rw /system");
            log.info("remount file system: " + Joiner.on("\n").skipNulls().join(executeOutput));

            log.info("edit file /system/build.prop");
            List<String> buildProperties = SUShell.run("cat /system/build.prop");
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
            //failed on '/data/data/com.virjar.hermes.hermesagent/cache/build.prop' - Cross-device link
            //do not use the mv command , maybe some things will wrong
            String mvCommand = "cat " + file.getAbsolutePath() + " >  /system/build.prop";
            executeOutput = SUShell.run(mvCommand);
            log.info("write content to /system/build.prop  " + mvCommand + "  " + Joiner.on("\n").skipNulls().join(executeOutput));
            SUShell.run("chmod 644 /system/build.prop");
            SUShell.run("rm -f " + file.getAbsolutePath());

            executeOutput = SUShell.run("mount -o remount ro /system");
            log.info("re mount file system to read only" + Joiner.on("\n").skipNulls().join(executeOutput));

            SUShell.run(Lists.newArrayList("setprop service.adb.tcp.port  " + Constant.ADBD_PORT, "stop adbd", "start adbd"));
            log.info("restart adbd service on port " + Constant.ADBD_PORT + " ,service will auto startup on this port when android device startup next time");
        } finally {
            isSettingADB = false;
        }
    }


    public static String getPackageName(Intent intent) {
        Uri uri = intent.getData();
        return (uri != null) ? uri.getSchemeSpecificPart() : null;
    }

    public static String genRequestID() {
        return "request_session_" + Thread.currentThread().getId() + "_" + System.currentTimeMillis();
    }

    private static boolean suAvailable = Shell.SU.available();

    public static boolean killService(String packageName) {
        //注意不能通过kill的rpc过去，需要强杀
        Log.e("pingWatchTask", "app: " + packageName + " 假死，强杀该app");
        if (!isSuAvailable()) {
            Log.w("pingWatchTask", "无法杀死targetApp，请给HermesAgent分配root权限");
            return false;
        }
        boolean hinted = false;
        List<AndroidAppProcess> runningAppProcesses = AndroidProcesses.getRunningAppProcesses();
        for (AndroidAppProcess androidAppProcess : runningAppProcesses) {
            if (androidAppProcess.getPackageName().equalsIgnoreCase(packageName)) {
                Shell.SU.run("kill -9 " + androidAppProcess.pid);
                hinted = true;
            }
        }
        return hinted;
    }

    public static boolean isSuAvailable() {
        return suAvailable;
    }

    public static boolean requestSuPermission() {
        return suAvailable = Shell.SU.available();
    }


    private static ClassLoader xposedBridgeClassLoader = null;

    public static ClassLoader createXposedClassLoadBridgeClassLoader(Context context) {
        if (xposedBridgeClassLoader != null) {
            return xposedBridgeClassLoader;
        }
        synchronized (CommonUtils.class) {
            if (xposedBridgeClassLoader != null) {
                return xposedBridgeClassLoader;
            }
            File xposedBridgeApkFile = new File(context.getFilesDir(), Constant.xposedBridgeApkFileName);
            if (!xposedBridgeApkFile.exists()) {
                releaseXposedBridgeApkFile(context, xposedBridgeApkFile);
            }
            xposedBridgeClassLoader = new PathClassLoader(xposedBridgeApkFile.getAbsolutePath(), CommonUtils.class.getClassLoader());
            return xposedBridgeClassLoader;
        }
    }

    private static void releaseXposedBridgeApkFile(Context context, File xposedBridgeApkFile) {
        AssetManager assets = context.getAssets();
        try (InputStream inputStream = assets.open(Constant.xposedBridgeApkFileName)) {
            IOUtils.copy(inputStream, new FileOutputStream(xposedBridgeApkFile));
        } catch (IOException e) {
            Log.e("weijia", "release xposed bridge apk file failed", e);
            throw new IllegalStateException(e);
        }
    }

    public static void sleep(long duration) {
        if (duration <= 0) {
            return;
        }
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            //ignore
        }
    }

    public static boolean configChange(String key, InvokeRequest invokeRequest) {
        if (!invokeRequest.hasParam(key)) {
            return false;
        }
        String requestValue = invokeRequest.getString(key);
        String configValue = CommonConfig.getString(key);
        return !StringUtils.equals(requestValue, configValue);
    }

    @SuppressLint("SdCardPath")
    public static String BASE_DIR = Build.VERSION.SDK_INT >= 24
            ? "/data/user_zh/0/" + BuildConfig.APPLICATION_ID + "/"
            : "/data/data/" + BuildConfig.APPLICATION_ID + "/";
    public static String HERMES_WRAPPER_DIR = BASE_DIR + "hermesModules/";
}
