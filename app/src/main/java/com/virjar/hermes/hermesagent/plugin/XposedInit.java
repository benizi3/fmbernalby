package com.virjar.hermes.hermesagent.plugin;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.virjar.hermes.hermesagent.BuildConfig;
import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.SingletonXC_MethodHook;
import com.virjar.hermes.hermesagent.hermes_api.XposedReflectUtil;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;

import org.apache.commons.lang3.StringUtils;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import dalvik.system.PathClassLoader;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.callbacks.XCallback;

/**
 * Created by virjar on 2018/8/22.<br>xposed加载入口
 */

public class XposedInit implements IXposedHookLoadPackage {
    private static volatile boolean hooked = false;
    private static final String TAG = "XposedInit";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
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
     * 小米系统拦截日志：<br>
     * activity拉起拦截<br>
     * D/com.android.server.am.ExtraActivityManagerService(  757): MIUILOG- Permission Denied Activity KeyguardLocked: Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] flg=0x10000000 pkg=com.tencent.weishi cmp=com.tencent.weishi/com.tencent.oscar.module.splash.SplashActivity } pkg : com.virjar.hermes.hermesagent uid : 10129<br>
     * 定位发生作用的代码为com.android.server.am.ExtraActivityManagerService，这个代码是小米自己的，Android原生不存在
     * <br><br>
     * 自启动广播拦截<br>
     * W/BroadcastQueueInjector(  764): Unable to launch app de.robv.android.xposed.installer/10127 for broadcast Intent { act=android.intent.action.PACKAGE_ADDED dat=package:com.virjar.hermes.hermesagent flg=0x4000010 (has extras) }: process is not permitted to  auto start
     */
    private void fixMiUIStartPermissionFilter(final XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> extraActivityManagerServiceClass = XposedHelpers.findClassIfExists("com.android.server.am.ExtraActivityManagerService", lpparam.classLoader);
        if (extraActivityManagerServiceClass != null) {
            try {
                XposedReflectUtil.findAndHookMethodOnlyByMethodName(extraActivityManagerServiceClass, "isAllowedStartActivity", new SingletonXC_MethodHook() {

                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (StringUtils.equalsIgnoreCase(APICommonUtils.safeToString(param.args[3]), BuildConfig.APPLICATION_ID)) {
                            Log.i("weijia", "hermes 拉起apk的权限，强行打开");
                            param.setResult(true);
                        }
                        Object intentObject = param.args[2];
                        if (intentObject instanceof Intent && StringUtils.equalsIgnoreCase(CommonUtils.getPackageName((Intent) intentObject), BuildConfig.APPLICATION_ID)) {
                            Log.i("weijia", "其他app拉起hermes的权限，强行打开");
                            param.setResult(true);
                        }
                    }

                });
            } catch (NoSuchMethodError error) {
                //ignore
            }
        }

        //处理广播拦截  com.android.server.am.BroadcastQueueInjector
        // static  bool checkApplicationAutoStart (com.android.server.am.BroadcastQueue, com.android.server.am.ActivityManagerService, com.android.server.am.BroadcastRecord, android.content.pm.ResolveInfo);
        Class<?> broadcastQueueInjectorClass = XposedHelpers.findClassIfExists("com.android.server.am.BroadcastQueueInjector", lpparam.classLoader);
        if (broadcastQueueInjectorClass != null) {
            XposedReflectUtil.findAndHookMethodOnlyByMethodName(broadcastQueueInjectorClass, "checkApplicationAutoStart", new SingletonXC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length < 2) {
                        return;
                    }
                    Object info = param.args[param.args.length - 1];
                    if (!(info instanceof ResolveInfo)) {
                        return;
                    }
                    ResolveInfo resolveInfo = (ResolveInfo) info;
                    if (resolveInfo.activityInfo == null) {
                        return;
                    }
                    String packageName = resolveInfo.activityInfo.applicationInfo.packageName;
                    if (autoStartWhiteList.contains(packageName)) {
                        //xposedInstaller和HermesAgent，直接放开系统限制
                        Log.i("weijia", " xposedInstaller和HermesAgent，直接放开系统限制,当前开启的package：" + packageName);
                        param.setResult(true);
                        return;
                    }
                    Object r = param.args[param.args.length - 2];
                    if (r == null || StringUtils.equalsIgnoreCase(r.getClass().getName(), "com.android.server.am.BroadcastRecord")) {
                        return;
                    }
                    try {
                        Intent intent = (Intent) XposedHelpers.getObjectField(r, "intent");
                        if (intent != null && StringUtils.equalsIgnoreCase(CommonUtils.getPackageName(intent), BuildConfig.APPLICATION_ID)) {
                            //HermesAgent触发的广播，均不拦截
                            Log.i("weijia", "HermesAgent触发的广播，均不拦截");
                            param.setResult(true);
                            return;
                        }
                    } catch (Throwable throwable) {
                        //ignore
                    }
                    try {
                        if (StringUtils.equalsIgnoreCase(APICommonUtils.safeToString(XposedHelpers.getObjectField(r, "callerPackage")), BuildConfig.APPLICATION_ID)) {
                            Log.i("weijia", "HermesAgent触发的广播，均不拦截");
                            param.setResult(true);
                        }
                    } catch (Throwable throwable) {
                        //ignore
                    }
                }
            });
        }
    }

    private static Set<String> autoStartWhiteList = Sets.newHashSet(BuildConfig.APPLICATION_ID, Constant.xposedInstallerPackage);

    private static boolean hotLoadFailedDialog = false;

    private void alertHotLoadFailedWarning(Context context) {
        XposedBridge.hookAllConstructors(Activity.class, new SingletonXC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (hotLoadFailedDialog) {
                    return;
                }
                hotLoadFailedDialog = true;
                new AlertDialog.Builder((Context) param.thisObject)
                        .setTitle("HermesAgent热发代码加载失败")
                        .setMessage("Xposed模块热加载失败，热发代码可能不生效，\n" +
                                "有两个常见原因可能引发这个问题，请check:\n" +
                                "1.您的Android studio开启了Instant Run，这会导致Xposed框架无法记载到正确的回调class" +
                                "2.您安装了新代码之后，需要先打开一次HermesAgent的App，才能重启Android系统，否则Xposed会在init进程为HermesAgent的apk创建odex缓存。" +
                                "  这会导致该文件创建者为root，进而再次热发代码的时候，普通进程没有remove老的odex文件缓存的权限，导致apk代码刷新失败 "
                        )
                        .setNeutralButton("我已知晓！", new DialogInterface.OnClickListener() {//添加普通按钮
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {

                            }
                        })
                        .create().show();
            }
        });

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
            packageInfo = packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            //ignore
        }
        if (packageInfo == null) {
            XposedBridge.log("can not find plugin install location for plugin: " + BuildConfig.APPLICATION_ID);
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
