package com.virjar.hermes.hermesagent.plugin;

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
                    fixXposedInstallerAppUpdate((Context) param.args[0], lpparam);
                }
            });

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

    private String MODULES_LIST_FILE = "/data/data/de.robv.android.xposed.installer/conf/modules.list";

    private void fixXposedInstallerAppUpdate(final Context context, final XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook forceUpdateHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
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
            XposedBridge.log("create a new classloader for plugin with new apk path: " + packageInfo.applicationInfo.sourceDir);
            PathClassLoader hotClassLoader = new PathClassLoader(packageInfo.applicationInfo.sourceDir, parent);
            classLoaderCache.putIfAbsent(packageInfo.applicationInfo.sourceDir, hotClassLoader);
            return hotClassLoader;
        }
    }
}
