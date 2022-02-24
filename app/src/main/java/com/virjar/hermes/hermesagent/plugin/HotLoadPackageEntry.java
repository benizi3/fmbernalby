package com.virjar.hermes.hermesagent.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Process;
import android.util.Log;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.virjar.hermes.hermesagent.BuildConfig;
import com.virjar.hermes.hermesagent.hermes_api.AgentCallback;
import com.virjar.hermes.hermesagent.hermes_api.SharedObject;
import com.virjar.hermes.hermesagent.host.manager.AgentDaemonTask;
import com.virjar.hermes.hermesagent.util.ClassScanner;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;

import net.dongliu.apk.parser.bean.ApkMeta;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.Timer;

import javax.annotation.Nullable;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2017/12/21.<br/>插件热加载器
 */
@SuppressWarnings("unused")
public class HotLoadPackageEntry {
    private static final String TAG = "HotPluginLoader";

    @SuppressWarnings("unused")
    public static void entry(Context context, XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (StringUtils.equalsIgnoreCase(loadPackageParam.packageName, BuildConfig.APPLICATION_ID)) {
            //CommonUtils.xposedStartSuccess = true;
            Class commonUtilClass = XposedHelpers.findClass("com.virjar.hermes.hermesagent.util.CommonUtils", loadPackageParam.classLoader);
            XposedHelpers.setStaticBooleanField(commonUtilClass, "xposedStartSuccess", true);
            return;
        }
        SharedObject.context = context;
        SharedObject.loadPackageParam = loadPackageParam;
        Timer heartbeatTimer = new Timer(TAG, true);

        Map<String, AgentCallback> callbackMap = Maps.newHashMap();
        //执行所有自定义的回调钩子函数
        Set<AgentCallback> allCallBack = findEmbedCallBack();
        for (AgentCallback agentCallback : allCallBack) {
            callbackMap.put(agentCallback.targetPackageName(), agentCallback);
        }
        //安装在容器中的扩展代码，优先级比内嵌的模块高
        allCallBack = findExternalCallBack();
        for (AgentCallback agentCallback : allCallBack) {
            AgentCallback old = callbackMap.put(agentCallback.targetPackageName(), agentCallback);
            if (old != null) {
                Log.w(TAG, "duplicate hermes wrapper found , hermes agent only load single one hermes wrapper");
            }
        }

        boolean hint = false;
        for (AgentCallback agentCallback : callbackMap.values()) {
            if (agentCallback == null) {
                continue;
            }
            try {
                XposedBridge.log("执行回调: " + agentCallback.getClass());
                //挂载钩子函数
                agentCallback.onXposedHotLoad();
                //将agent注册到server端，让server可以rpc到agent
                AgentRegister.registerToServer(agentCallback, context);
                //启动timer，保持和server的心跳，发现server死掉的话，拉起server
                heartbeatTimer.scheduleAtFixedRate(new AgentDaemonTask(context, agentCallback), 1000, 4000);
                hint = true;
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        }
        if (!hint) {
            heartbeatTimer.cancel();
        } else {
            exitIfMasterReInstall(context, loadPackageParam.packageName);
        }
    }

    private static void exitIfMasterReInstall(Context context, final String slavePackageName) {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (StringUtils.isBlank(action)) {
                    return;
                }
                String packageName = CommonUtils.getPackageName(intent);
                if (packageName == null)
                    return;
                if (!StringUtils.equalsIgnoreCase(packageName, BuildConfig.APPLICATION_ID)) {
                    return;
                }
                Log.i(TAG, "master  重新安装，重启slave 进程");

                new Thread("kill-self-thread") {
                    @Override
                    public void run() {
                        //不能马上自杀，这可能会触发slave进程去更新master的代码dex-cache。但是由于权限问题无法remove历史版本的apk代码缓存，进而热加载失败
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            return;
                        }
                        //自杀后，自然有其他守护进程拉起，无需考虑死后重启问题
                        //重启自身的原因，是因为目前挂钩代码寄生在master的apk包里面的，未来将挂钩代码迁移到slave之后，便不需要重启自身了
                        Process.killProcess(Process.myPid());
                        System.exit(0);
                    }
                }.start();

            }


        }, intentFilter);
    }

    @SuppressWarnings("unchecked")
    private synchronized static Set<AgentCallback> findExternalCallBack() {
        File modulesDir = new File(Constant.HERMES_WRAPPER_DIR);
        if (!modulesDir.exists() || !modulesDir.canRead()) {
            //Log.w("weijia", "hermesModules 文件为空，无外置HermesWrapper");
            return Collections.emptySet();
        }

        Set<AgentCallback> ret = Sets.newHashSet();
        for (File apkFile : modulesDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return StringUtils.endsWithIgnoreCase(name, ".apk");
            }
        })) {
            //Log.i("weijia", "扫描插件文件:" + apkFile.getAbsolutePath());
            try {
                ApkMeta apkMeta = CommonUtils.parseApk(apkFile);
                String packageName = apkMeta.getPackageName();
                ClassScanner.SubClassVisitor<AgentCallback> subClassVisitor = new ClassScanner.SubClassVisitor(true, AgentCallback.class);
                ClassScanner.scan(subClassVisitor, Sets.newHashSet(packageName), apkFile);
                Set<AgentCallback> filtered = filter(subClassVisitor);
                ret.addAll(filtered);
            } catch (Exception e) {
                Log.e("weijia", "failed to load hermes-wrapper module", e);
            }
        }
        return ret;
    }

    private static Set<AgentCallback> filter(ClassScanner.SubClassVisitor<AgentCallback> subClassVisitor) {
        return Sets.newHashSet(Iterables.filter(Lists.transform(subClassVisitor.getSubClass(), new Function<Class<? extends AgentCallback>, AgentCallback>() {
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

    /**
     * 在HermesAgent中内置的HermesWrapper实现
     */
    @SuppressWarnings("unchecked")
    private static Set<AgentCallback> findEmbedCallBack() {
        ClassScanner.SubClassVisitor<AgentCallback> subClassVisitor = new ClassScanner.SubClassVisitor(true, AgentCallback.class);
        ClassScanner.scan(subClassVisitor, Sets.newHashSet(Constant.appHookSupperPackage), null);
        return filter(subClassVisitor);
    }
}
