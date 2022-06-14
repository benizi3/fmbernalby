package com.virjar.hermes.hermesagent.plugin;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Application;
import android.content.ContentProvider;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jaredrummler.android.processes.models.AndroidProcess;
import com.virjar.hermes.hermesagent.BuildConfig;
import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.Constant;
import com.virjar.hermes.hermesagent.util.ReflectUtil;
import com.virjar.xposed_extention.LifeCycleFire;
import com.virjar.xposed_extention.Ones;
import com.virjar.xposed_extention.SingletonXC_MethodHook;
import com.virjar.xposed_extention.XposedReflectUtil;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import dalvik.system.PathClassLoader;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.callbacks.XCallback;

/**
 * Created by virjar on 2018/8/22.<br>xposed加载入口
 */

public class XposedInit implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static final String TAG = "XposedInit";

//    public static String getTrack(Throwable e) {
//        StringBuilder msg = new StringBuilder("\n=============>\n");
//        while (e != null) {
//            StackTraceElement[] ste = e.getStackTrace();
//            for (StackTraceElement stackTraceElement : ste) {
//                msg.append(stackTraceElement.getClassName()).append(".").append(stackTraceElement.getMethodName()).append(":").append(stackTraceElement.getLineNumber()).append("\n");
//            }
//            e = e.getCause();
//            if (e != null) {
//                msg.append("cause:").append(e.getMessage()).append("\n\n");
//            }
//        }
//        msg.append("<================\n");
//        return msg.toString();
//    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        //这个进程是传说中的system_server,拥有system权限
        if (StringUtils.equalsIgnoreCase(lpparam.processName, "android")) {
            fixMiUIStartPermissionFilter(lpparam);
            toSystemAndPrivilegedApp();
            grantAllContentProviderPermission();
            grantAllPackagePermission(lpparam);

//            XposedHelpers.findAndHookMethod(Log.class, "w", String.class, String.class, new SingletonXC_MethodHook() {
//                @Override
//                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                    super.afterHookedMethod(param);
//                    if (StringUtils.equalsAnyIgnoreCase((CharSequence) param.args[0], "ActivityManager")
//                            && StringUtils.contains((CharSequence) param.args[1], "ConfirmStartActivity")) {
//                        Log.i("weijia", "start securitycenter:" + getTrack(new Throwable()));
//                    }
//                }
//            });

//            Class slogClazz = Thread.currentThread().getContextClassLoader().loadClass("android.util.Slog");
//
//            XposedHelpers.findAndHookMethod(slogClazz, "w", String.class, String.class, new SingletonXC_MethodHook() {
//                @Override
//                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                    super.afterHookedMethod(param);
//                    if (StringUtils.equalsAnyIgnoreCase((CharSequence) param.args[0], "ActivityManager")
//                            && StringUtils.contains((CharSequence) param.args[1], "ConfirmStartActivity")) {
//                        Log.i("weijia", "start securitycenter:" + getTrack(new Throwable()));
//                    }
//                }
//            });

        }
//
//        if (lpparam.packageName.equals("com.miui.securitycenter")) {
//            Log.i("weijia", "start securitycenter:" + lpparam.classLoader);
//            Class<?> aClass = lpparam.classLoader.loadClass("com.miui.permcenter.r");
//            Log.i("weijia", "app拉起设置：" + XposedHelpers.getStaticObjectField(aClass, "RI") + "     "
//                    + XposedHelpers.getStaticObjectField(aClass, "RH"));
//        }


//        ClassLoadMonitor.addClassLoadMonitor("com.miui.powerkeeper.provider.PowerKeeperConfigureProvider", new ClassLoadMonitor.OnClassLoader() {
//            @Override
//            public void onClassLoad(Class clazz) {
//                XposedReflectUtil.findAndHookMethodOnlyByMethodName(clazz, "query", new SingletonXC_MethodHook() {
//                    @Override
//                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                        Log.i("weijia", "查询神隐模式数据：" + JSONObject.toJSONString(param.args));
//                    }
//                });
//            }
//        });

        // skip if system app
        //对应系统app来说，就不要加载插件逻辑了，否则大量app都会进行文件扫描
        if (Process.myUid() < Process.FIRST_APPLICATION_UID) {
            return;
        }

        Ones.hookOnes(Application.class, "hermes_application_attach_entry", new Ones.DoOnce() {
            @Override
            public void doOne(Class<?> clazz) {
                XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook(XCallback.PRIORITY_HIGHEST * 2) {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        hotLoadPlugin((Context) param.args[0], lpparam);
                    }
                });
            }
        });

    }


    private void grantAllPackagePermission(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> contentImplClass = ReflectUtil.findClassIfExists("android.app.ContextImpl", lpparam.classLoader);
        if (contentImplClass == null) {
            return;
        }
        XposedHelpers.findAndHookMethod(contentImplClass, "checkPermission", String.class, int.class, int.class, new SingletonXC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                int pid = (int) param.args[1];
                if (pid <= 0) {
                    return;
                }
                AndroidProcess androidProcess = new AndroidProcess(pid);
                if (StringUtils.equalsIgnoreCase(androidProcess.name, BuildConfig.APPLICATION_ID)) {
                    param.setResult(PackageManager.PERMISSION_GRANTED);
                }
            }
        });
    }

    @SuppressLint("PrivateApi")
    private void grantAllContentProviderPermission() {
        Class<?> activityManagerServiceClass = ReflectUtil.findClassIfExists("com.android.server.am.ActivityManagerService", Thread.currentThread().getContextClassLoader());
        if (activityManagerServiceClass == null) {
            try {
                activityManagerServiceClass = Context.class.getClassLoader().loadClass("com.android.server.am.ActivityManagerService");
            } catch (ClassNotFoundException e) {
                Log.e("weijia", "failed to load ActivityManagerService class", e);
            }
        }
        if (activityManagerServiceClass == null) {
            try {
                activityManagerServiceClass = Class.forName("com.android.server.am.ActivityManagerService");
            } catch (ClassNotFoundException e) {
                Log.e("weijia", "failed to load ActivityManagerService class", e);
            }
        }
        if (activityManagerServiceClass != null) {
            XposedReflectUtil.findAndHookOneMethod(activityManagerServiceClass, "checkContentProviderPermissionLocked", new SingletonXC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length < 2) {
                        return;
                    }
                    Object processRecord = param.args[1];
                    if (processRecord == null) {
                        return;
                    }
                    Object processName = XposedHelpers.getObjectField(processRecord, "processName");
                    if (processName == null) {
                        return;
                    }
                    if (BuildConfig.APPLICATION_ID.equalsIgnoreCase(processName.toString())) {
                        Log.i("weijia", "hermes 调用任何content provider的权限，强行打开");
                        param.setResult(null);
                    }
                }
            });

        } else {
            Log.i("weijia", "grant contentProviderPermission failed,can not find class:com.android.server.am.ActivityManagerService for process system_server");
        }

    }

    //public static final int FLAG_PRIVILEGED = 1 << 30;

    /**
     * 讲hermes设置为PrivilegedApp的权限
     */
    private void toSystemAndPrivilegedApp() {
        Class<?> packageManagerServiceClass = ReflectUtil.findClassIfExists("com.android.server.pm.PackageManagerService", Thread.currentThread().getContextClassLoader());
        if (packageManagerServiceClass == null) {
            Log.i("weijia", "grant hermes agent system permission failed");
            return;
        }
        XposedReflectUtil.findAndHookOneMethod(packageManagerServiceClass, "isPrivilegedApp", new SingletonXC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length == 0) {
                    return;
                }
                if (param.args[0] == null) {
                    return;
                }

                Object packageName = XposedHelpers.getObjectField(param.args[0], "packageName");
                if (packageName != null && BuildConfig.APPLICATION_ID.equals(packageName)) {
                    param.setResult(true);
                }
            }
        });
    }

    //这个class比较敏感，尽量不要调用其他工具类，否则可能连锁的触发各种class初始化反应。但是很多class环境无法在系统代码上面运行，所以一些工具类在XposedInit中单独实现
    private static String getPackageName(Intent intent) {
        Uri uri = intent.getData();
        return (uri != null) ? uri.getSchemeSpecificPart() : null;
    }

    private static String safeToString(Object input) {
        if (input == null) {
            return null;
        }
        return input.toString();
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
        Class<?> extraActivityManagerServiceClass = ReflectUtil.findClassIfExists("com.android.server.am.ExtraActivityManagerService", lpparam.classLoader);
        if (extraActivityManagerServiceClass == null) {
            extraActivityManagerServiceClass = ReflectUtil.findClassIfExists("com.android.server.am.ExtraActivityManagerService", Thread.currentThread().getContextClassLoader());
        }
        if (extraActivityManagerServiceClass != null) {
            try {
                XposedReflectUtil.findAndHookOneMethod(extraActivityManagerServiceClass, "isAllowedStartActivity", new SingletonXC_MethodHook() {

                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (StringUtils.equalsIgnoreCase(safeToString(param.args[3]), BuildConfig.APPLICATION_ID)) {
                            Log.i("weijia", "hermes 拉起apk的权限，强行打开");
                            param.setResult(true);
                        }
                        Object intentObject = param.args[2];
                        if (intentObject instanceof Intent && StringUtils.equalsIgnoreCase(getPackageName((Intent) intentObject), BuildConfig.APPLICATION_ID)) {
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
        Class<?> broadcastQueueInjectorClass = ReflectUtil.findClassIfExists("com.android.server.am.BroadcastQueueInjector", lpparam.classLoader);
        if (broadcastQueueInjectorClass == null) {
            broadcastQueueInjectorClass = ReflectUtil.findClassIfExists("com.android.server.am.BroadcastQueueInjector", Thread.currentThread().getContextClassLoader());
        }
        if (broadcastQueueInjectorClass != null) {
            XposedReflectUtil.findAndHookOneMethod(broadcastQueueInjectorClass, "checkApplicationAutoStart", new SingletonXC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
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
                        if (intent != null && StringUtils.equalsIgnoreCase(getPackageName(intent), BuildConfig.APPLICATION_ID)) {
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


    private void alertHotLoadFailedWarning() {
        LifeCycleFire.onFirstPageReady(new LifeCycleFire.OnFire<Activity>() {
            @Override
            public void fire(final Activity o) {

                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (o.isDestroyed()) {
                            return;
                        }
                        new AlertDialog.Builder(o)
                                .setTitle("HermesAgent热发代码加载失败")
                                .setMessage("Xposed模块热加载失败，热发代码可能不生效，\n" +
                                        "有两个常见原因可能引发这个问题，请check:\n" +
                                        "1.您的Android studio开启了Instant Run，这会导致Xposed框架无法记载到正确的回调class\n" +
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
                }, 2000);

            }
        });
    }

    private void hotLoadPlugin(Context context, XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader hotClassLoader = replaceClassloader(context);

        Class<?> aClass;
        try {
            aClass = hotClassLoader.loadClass(Constant.xposedHotloadEntry);
        } catch (ClassNotFoundException e) {
            alertHotLoadFailedWarning();
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

    private static ClassLoader replaceClassloader(Context context) {
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

    @Override
    public void initZygote(StartupParam startupParam) {

        Method enforceReadPermissionInnerMethod = null;
        Method enforceWritePermissionInnerMethod = null;
        for (Method method : ContentProvider.class.getDeclaredMethods()) {
            if (method.getName().equals("enforceReadPermissionInner")
                    && method.getParameterTypes().length == 3) {
                enforceReadPermissionInnerMethod = method;
                continue;
            }
            if (method.getName().equals("enforceWritePermissionInner")
                    && method.getParameterTypes().length == 3) {
                enforceWritePermissionInnerMethod = method;
            }
        }

        if (enforceReadPermissionInnerMethod != null) {
            XposedBridge.hookMethod(enforceReadPermissionInnerMethod, new SingletonXC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String callingPkg = null;
                    Uri uri = null;
                    for (Object p : param.args) {
                        if (p instanceof String) {
                            callingPkg = (String) p;
                            continue;
                        }
                        if (p instanceof Uri) {
                            uri = (Uri) p;
                        }
                    }
                    if (callingPkg == null) {
                        //系统在申请权限，会直接过
                        return;
                    }
                    if (StringUtils.startsWithIgnoreCase(callingPkg, BuildConfig.APPLICATION_ID)) {
                        Log.i("weijia", "hermes  申请" + uri + " 的读取权限，直接放过");
                        if (((Method) param.method).getReturnType() == Void.class) {
                            param.setResult(null);
                        } else {
                            param.setResult(AppOpsManager.MODE_ALLOWED);
                        }
                    }
                }
            });

        }

        if (enforceWritePermissionInnerMethod != null) {
            XposedBridge.hookMethod(enforceWritePermissionInnerMethod, new SingletonXC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String callingPkg = null;
                    Uri uri = null;
                    for (Object p : param.args) {
                        if (p instanceof String) {
                            callingPkg = (String) p;
                            continue;
                        }
                        if (p instanceof Uri) {
                            uri = (Uri) p;
                        }
                    }
                    if (callingPkg == null) {
                        //系统在申请权限，会直接过
                        return;
                    }
                    if (StringUtils.startsWithIgnoreCase(callingPkg, BuildConfig.APPLICATION_ID)) {
                        Log.i("weijia", "hermes  申请" + uri + " 的写入权限，直接放过");
                        if (((Method) param.method).getReturnType() == Void.class) {
                            param.setResult(null);
                        } else {
                            param.setResult(AppOpsManager.MODE_ALLOWED);
                        }
                    }
                }
            });

        }
    }
}
