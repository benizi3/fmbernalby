package com.virjar.hermes.hermesagent.plugin;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.google.common.collect.Maps;
import com.virjar.hermes.hermesagent.util.Constant;

import org.apache.commons.lang3.StringUtils;

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
        if (StringUtils.equalsIgnoreCase(lpparam.packageName, Constant.packageName)) {
            return;
        }
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

    private void fixXposedInstallerAppUpdate(final Context context, XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook forceUpdateHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Intent intent = (Intent) param.args[1];
                String action = intent.getAction();
                XposedBridge.log("收到模块更新消息:" + action);
                if (action != null && action.equals(Intent.ACTION_PACKAGE_REMOVED) && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    // Ignore existing packages being removed in order to be updated
                    // package update ,the apk file location maybe changed,so can not ignore this message
                    Intent newIntent = new Intent(intent);
                    newIntent.putExtra(Intent.EXTRA_REPLACING, false);
                    param.args[1] = newIntent;

                    XposedBridge.log("xposed installer 强刷配置");
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

    }

    private void hotLoadPlugin(Context context, XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader hotClassLoader = replaceClassloader(context, lpparam);

        Class<?> aClass = null;
        try {
            aClass = hotClassLoader.loadClass(Constant.xposedHotloadEntry);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "do you not disable Instant Runt for Android studio?");
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
