package com.virjar.hermes.hermesagent.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Process;
import android.util.Log;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.virjar.hermes.hermesagent.host.manager.AgentDaemonTask;
import com.virjar.hermes.hermesagent.util.ClassScanner;
import com.virjar.hermes.hermesagent.util.Constant;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Timer;

import javax.annotation.Nullable;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2017/12/21.<br/>插件热加载器
 */

public class HotLoadPackageEntry {
    private static final String TAG = "HotPluginLoader";

    private static Timer heartbeatTimer = new Timer(TAG, true);

    @SuppressWarnings("unused")
    public static void entry(Context context, XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (StringUtils.equalsIgnoreCase(loadPackageParam.packageName, Constant.packageName)) {
            return;
        }
        SharedObject.context = context;
        SharedObject.loadPackageParam = loadPackageParam;


        // context.get
        //执行所有自定义的回调钩子函数
        List<AgentCallback> allCallBack = findAllCallBackV2();
        for (AgentCallback xposedHotLoadCallBack : allCallBack) {
            if (xposedHotLoadCallBack == null) {
                continue;
            }
            try {
                XposedBridge.log("执行回调: " + xposedHotLoadCallBack.getClass());
                //挂载钩子函数
                xposedHotLoadCallBack.onXposedHotLoad();
                //将agent注册到server端，让server可以rpc到agent
                AgentRegister.registerToServer(xposedHotLoadCallBack, context);

                //启动timer，保持和server的心跳，发现server死掉的话，拉起server
                heartbeatTimer.scheduleAtFixedRate(new AgentDaemonTask(context, xposedHotLoadCallBack), 1000, 4000);

                exitIfMasterReInstall(context, loadPackageParam.packageName);
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        }
    }

    private static void exitIfMasterReInstall(Context context, final String slavePackageName) {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        // intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");
        final Context finalContext = context;
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (StringUtils.isBlank(action)) {
                    return;
                }
                String packageName = getPackageName(intent);
                if (packageName == null)
                    return;
                if (!StringUtils.equalsIgnoreCase(packageName, Constant.packageName)) {
                    return;
                }
                Log.i(TAG, "master 重新安装，重启slave 进程");

                finalContext.unregisterReceiver(this);

                //自杀后，自然有其他守护进程拉起，无需考虑死后重启问题
                Process.killProcess(Process.myPid());
                System.exit(0);
            }

            private String getPackageName(Intent intent) {
                Uri uri = intent.getData();
                return (uri != null) ? uri.getSchemeSpecificPart() : null;
            }

        }, intentFilter);
    }

    @SuppressWarnings("unchecked")
    private static List<AgentCallback> findAllCallBackV2() {
        ClassScanner.SubClassVisitor<AgentCallback> subClassVisitor = new ClassScanner.SubClassVisitor(true, AgentCallback.class);
        ClassScanner.scan(subClassVisitor, Sets.newHashSet(Constant.appHookSupperPackage));
        return Lists.newArrayList(Iterables.filter(Lists.transform(subClassVisitor.getSubClass(), new Function<Class<? extends AgentCallback>, AgentCallback>() {
            @Nullable
            @Override
            public AgentCallback apply(Class<? extends AgentCallback> input) {
                try {
                    return input.newInstance();
                } catch (InstantiationException | IllegalAccessException e) {
                    Log.e("weijia", "failed to load create plugin", e);
                }
                return null;
            }
        }), new Predicate<AgentCallback>() {
            @Override
            public boolean apply(@Nullable AgentCallback input) {
                return input != null
                        && input.needHook(SharedObject.loadPackageParam)
                        && StringUtils.equalsIgnoreCase(input.targetPackageName(), SharedObject.loadPackageParam.packageName);
            }
        }));
    }
}
