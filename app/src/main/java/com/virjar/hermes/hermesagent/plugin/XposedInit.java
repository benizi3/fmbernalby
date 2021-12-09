package com.virjar.hermes.hermesagent.plugin;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.virjar.hermes.hermesagent.hermes_api.SingletonXC_MethodHook;
import com.virjar.hermes.hermesagent.hermes_api.XposedReflectUtil;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import dalvik.system.PathClassLoader;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.callbacks.XCallback;

/**
 * Created by virjar on 2018/8/22.
 */

public class XposedInit implements IXposedHookLoadPackage {
    private static volatile boolean hooked = false;
    private static final String TAG = "XposedInit";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (StringUtils.equalsIgnoreCase(lpparam.packageName, "de.robv.android.xposed.installer")) {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook(XCallback.PRIORITY_HIGHEST * 2) {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    //经过验证，应该是某种原因导致xposed收不到广播消息
                    fixXposedInstallerAppUpdate((Context) param.args[0], lpparam);
                }
            });
        }
        if (StringUtils.equalsIgnoreCase(lpparam.processName, "android")) {
            fixMiUIStartPermissionFilter(lpparam);
        }

        if (hooked) {
            return;
        }
        hooked = true;
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook(XCallback.PRIORITY_HIGHEST * 2) {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                hotLoadPlugin((Context) param.args[0], lpparam);
            }
        });
    }

    /**
     * 小米系统的各种权限拦截，统一拆解掉<br>
     * 小米系统拦截日志：
     * D/com.android.server.am.ExtraActivityManagerService(  757): MIUILOG- Permission Denied Activity KeyguardLocked: Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] flg=0x10000000 pkg=com.tencent.weishi cmp=com.tencent.weishi/com.tencent.oscar.module.splash.SplashActivity } pkg : com.virjar.hermes.hermesagent uid : 10129<br>
     * 定位发生作用的代码为com.android.server.am.ExtraActivityManagerService，这个代码是小米自己的，Android原生不存在
     */
    private void fixMiUIStartPermissionFilter(final XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> classIfExists = XposedHelpers.findClassIfExists("com.android.server.am.ExtraActivityManagerService", lpparam.classLoader);
        if (classIfExists == null) {
            return;
        }
        //Log.i("weijia", "classLoaderType :" + classIfExists.getClassLoader().getClass());
        //Log.i("weijia", "classLoader" + JSONObject.toJSONString(classIfExists.getClassLoader()));
        //classLoaderType :class dalvik.system.PathClassLoader  根据pathClassLoader 去解析jar包或者apk的文件地址
        //dalvik.system.DexPathList
        //Object pathList = XposedHelpers.getObjectField(classIfExists.getClassLoader(), "pathList");
        //dalvik.system.DexPathList.Element
        // Object[] dexElements = (Object[]) XposedHelpers.getObjectField(pathList, "dexElements");
        //Log.i("weijia", "resource location:" + pathList);
        //resource location:DexPathList[[zip file "/system/framework/services.jar", zip file "/system/framework/ethernet-service.jar", zip file "/system/framework/wifi-service.jar"],nativeLibraryDirectories=[/vendor/lib64, /system/lib64]]

        //最终定位，这个次拦截class的jar包路径，位于/system/framework/services.jar
        //相关博客 http://www.miui.com/thread-5762572-1-1.html，该文件是一个空头文件，因为代码被odex了。
        //我拆开小米note的包，找到了services.odex的位置，就在system/framework/ota/arm里，其它miui手机应该也在这个路径！

        //逆向表明，该方法为isAllowedStartActivity，我们需要拦截这个方法

        // static android.content.Intent com.android.server.am.ExtraActivityManagerService.checkStartActivityPermission(
        // android.content.Context,com.android.server.am.ActivityManagerService,android.app.IApplicationThread,
        // android.content.pm.ActivityInfo,android.content.Intent,java.lang.String,
        // boolean,int,boolean,int,int,java.lang.String)
//        XposedReflectUtil.findAndHookMethodOnlyByMethodName(classIfExists, "checkStartActivityPermission", new SingletonXC_MethodHook() {
//            @Override
//            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                Log.i("weijia", "checkStartActivityPermission called");
//            }
//
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//
//            }
//        });

        //这个也比较可疑  static boolean com.android.server.am.ExtraActivityManagerService.isAllowedStartActivity(
        // com.android.server.am.ActivityManagerService,com.android.server.am.ActivityStackSupervisor
        // ,android.content.Intent,
        // java.lang.String,int,android.content.pm.ActivityInfo)
        XposedReflectUtil.findAndHookMethodOnlyByMethodName(classIfExists, "isAllowedStartActivity", new SingletonXC_MethodHook() {

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (StringUtils.equalsIgnoreCase(CommonUtils.safeToString(param.args[3]), Constant.packageName)) {
                    Log.i("weijia", "hermes 拉起apk的权限，强行打开");
                    param.setResult(true);
                }
            }

        });
        //        String sourceDir = "unknown";
//        if (lpparam.appInfo != null) {
//            sourceDir = lpparam.appInfo.sourceDir;
//        }
//        Log.i("weijia", "找到 activity manager,processName is:" + lpparam.processName + "  packageName is:" + lpparam.packageName + " appLocation is:" + sourceDir);
//        //Method[] declaredMethods = classIfExists.getDeclaredMethods();
//        XposedReflectUtil.printAllMethod(classIfExists);
    }

    @SuppressLint("SdCardPath")
    private String MODULES_LIST_FILE = "/data/data/de.robv.android.xposed.installer/conf/modules.list";

    private void fixXposedInstallerAppUpdate(final Context context, final XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook forceUpdateHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Log.i("weijia", "收到app安装广播");
                List<String> strings = Files.readLines(new File(MODULES_LIST_FILE), Charsets.UTF_8);
                if (strings.size() == 0) {
                    return;
                }
                PackageInfo packageInfo = context.getPackageManager().getPackageInfo(Constant.packageName, PackageManager.GET_META_DATA);
                if (packageInfo == null) {
                    //被卸载了
                    return;
                }
                String sourceDir = packageInfo.applicationInfo.sourceDir;
                Log.i("weijia", "hermesAgent的安装路径为:" + sourceDir);
                List<String> editedConfig = Lists.newArrayListWithExpectedSize(strings.size());
                boolean edited = false;
                for (String config : strings) {
                    if (StringUtils.containsIgnoreCase(config, Constant.packageName)) {
                        if (!StringUtils.equals(sourceDir, config)) {
                            editedConfig.add(sourceDir);
                            edited = true;
                        }
                        break;
                    } else {
                        editedConfig.add(config);
                    }
                }
                if (edited) {
                    if (editedConfig.size() > 0 && StringUtils.startsWithIgnoreCase(editedConfig.get(editedConfig.size() - 1), "#hermes_auto")) {
                        editedConfig.add("#hermes_auto edit");
                    }
                    Log.i("weijia", "重刷xposed配置文件");
                    PrintWriter modulesList = new PrintWriter(MODULES_LIST_FILE);
                    for (String config : editedConfig) {
                        modulesList.println(config);
                    }
                    modulesList.close();
                    Class<?> aClass = XposedHelpers.findClass("android.os.FileUtils", lpparam.classLoader);
                    XposedHelpers.callStaticMethod(aClass, "setPermissions", MODULES_LIST_FILE, 00664, -1, -1);
                }
            }
        };

        Class<?> packageChangeReceiverClass = XposedHelpers.findClassIfExists("de.robv.android.xposed.installer.receivers.PackageChangeReceiver", lpparam.classLoader);

        if (packageChangeReceiverClass != null) {
            XposedHelpers.findAndHookMethod(packageChangeReceiverClass, "onReceive", Context.class, Intent.class, forceUpdateHook);
        }
        packageChangeReceiverClass = XposedHelpers.findClassIfExists("de.robv.android.xposed.installer.PackageChangeReceiver", lpparam.classLoader);
        if (packageChangeReceiverClass != null) {
            XposedHelpers.findAndHookMethod(packageChangeReceiverClass, "onReceive", Context.class, Intent.class, forceUpdateHook);
        }
        //TDOO

    }

    private void alertHotLoadFailedWarning(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("HermesAgent热发代码加载失败")
                .setMessage("Xposed模块热加载失败，热发代码可能不生效，\n" +
                        "有两个常见原因可能引发这个问题，请check:\n" +
                        "1.您的Android studio开启了Instant Run，这会导致Xposed框架无法记载到正确的回调class" +
                        "2.您安装了新代码之后，需要先打开一次HermesAgent的App，才能重启Android系统，否则Xposed会在init进程为HermesAgent的apk创建odex缓存。" +
                        "  这会导致该文件创建者为root，进而再次热发代码的时候，普通进程没有remove老的odex文件缓存的权限，导致apk代码刷新失败 "
                )
                //.setIcon(R.mipmap.ic_launcher)
                .setNeutralButton("我已知晓！", new DialogInterface.OnClickListener() {//添加普通按钮
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .create().show();
    }

    private void hotLoadPlugin(Context context, XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader hotClassLoader = replaceClassloader(context, lpparam);

        Class<?> aClass;
        try {
            aClass = hotClassLoader.loadClass(Constant.xposedHotloadEntry);
        } catch (ClassNotFoundException e) {
            alertHotLoadFailedWarning(context);
            Log.e(TAG, "hot load failed", e);
            try {
                aClass = XposedInit.class.getClassLoader().loadClass(Constant.xposedHotloadEntry);
            } catch (ClassNotFoundException e1) {
                throw new IllegalStateException(e1);
            }
        }
        try {
            aClass.getMethod("entry", Context.class, XC_LoadPackage.LoadPackageParam.class)
                    .invoke(null, context, lpparam);
        } catch (Exception e) {
            Log.e(TAG, "invoke hotload class failed", e);
        }

    }

    private static ClassLoader replaceClassloader(Context context, XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader classLoader = XposedInit.class.getClassLoader();

        //find real apk location by package name
        PackageManager packageManager = context.getPackageManager();
        if (packageManager == null) {
            XposedBridge.log("can not find packageManager");
            return classLoader;
        }

        PackageInfo packageInfo = null;
        try {
            packageInfo = packageManager.getPackageInfo(Constant.packageName, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            //ignore
        }
        if (packageInfo == null) {
            XposedBridge.log("can not find plugin install location for plugin: " + Constant.packageName);
            return classLoader;
        }

        return createClassLoader(classLoader.getParent(), packageInfo);
    }

    private static ConcurrentMap<String, PathClassLoader> classLoaderCache = Maps.newConcurrentMap();

    private static PathClassLoader createClassLoader(ClassLoader parent, PackageInfo packageInfo) {
        if (classLoaderCache.containsKey(packageInfo.applicationInfo.sourceDir)) {
            return classLoaderCache.get(packageInfo.applicationInfo.sourceDir);
        }
        synchronized (XposedInit.class) {
            if (classLoaderCache.containsKey(packageInfo.applicationInfo.sourceDir)) {
                return classLoaderCache.get(packageInfo.applicationInfo.sourceDir);
            }
            XposedBridge.log("create a new   classloader for plugin with new apk path: " + packageInfo.applicationInfo.sourceDir);
            PathClassLoader hotClassLoader = new PathClassLoader(packageInfo.applicationInfo.sourceDir, parent);
            classLoaderCache.putIfAbsent(packageInfo.applicationInfo.sourceDir, hotClassLoader);
            return hotClassLoader;
        }
    }
}
