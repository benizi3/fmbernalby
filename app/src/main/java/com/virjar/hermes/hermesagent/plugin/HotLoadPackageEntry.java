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
import com.virjar.hermes.hermesagent.hermes_api.ClassLoadMonitor;
import com.virjar.hermes.hermesagent.hermes_api.LifeCycleFire;
import com.virjar.hermes.hermesagent.hermes_api.LogConfigurator;
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

import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2017/12/21.<br/>插件热加载器
 */
@SuppressWarnings("unused")
@Slf4j
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
        //初始的上下文数据，有了她就可以访问很多系统功能了，这个需要第一步完成
        SharedObject.context = context;
        SharedObject.loadPackageParam = loadPackageParam;

        LogConfigurator.confifure(context);
        log.info("plugin startup");

        //收集所有存在的classloader
        log.info("setup classloader monitor");
        ClassLoadMonitor.setUp();

        //拦截几个app关键声明周期，插件可以挂在在特定声明周期上面完成特定逻辑
        //比如有些有些apk的api，在Application中进行初始化，然后才能使用功能。我们需要让自己的hook代码在Application的oncreate执行之后才能植入
        log.info("setup lifecycle monitor");
        LifeCycleFire.init();

        /*
         * 拦截器初始化，这些一般是状态还原，比如我们设置了代理，那么app重启之后，将会还原代理配置
         */
        log.info("setup interceptor");
        InvokeInterceptorManager.setUp();

        /**
         * 插件中，维护一个全局的timer，用来执行一些简单的调度任务
         */
        SharedObject.agentTimer = new Timer("hermesAgentTimer", true);

        Map<String, AgentCallback> callbackMap = Maps.newHashMap();
        //执行所有自定义的回调钩子函数
        log.info("scan embed wrapper implementation");
        Set<AgentCallback> allCallBack = findEmbedCallBack();
        log.info("find :{} embed wrapper", allCallBack.size());
        for (AgentCallback agentCallback : allCallBack) {
            callbackMap.put(agentCallback.targetPackageName(), agentCallback);
        }
        //安装在容器中的扩展代码，优先级比内嵌的模块高
        log.info("scan external wrapper implementation");
        allCallBack = findExternalCallBack();
        log.info("find :{} external wrapper", allCallBack.size());
        for (AgentCallback agentCallback : allCallBack) {
            AgentCallback old = callbackMap.put(agentCallback.targetPackageName(), agentCallback);
            if (old != null) {
                log.warn("duplicate hermes wrapper found , hermes agent only load single one hermes wrapper");
            }
        }

        boolean hint = false;
        for (AgentCallback agentCallback : callbackMap.values()) {
            if (agentCallback == null) {
                continue;
            }
            try {
                String wrapperName = agentCallback.getClass().getName();
                log.info("执行回调: {}", wrapperName);
                //挂载钩子函数
                agentCallback.onXposedHotLoad();
                //将agent注册到server端，让server可以rpc到agent
                log.info("注册:{} 到hermes server registry", wrapperName);
                AgentRegister.registerToServer(agentCallback, context);
                //启动timer，保持和server的心跳，发现server死掉的话，拉起server
                log.info("启动到server 心跳保持timer");
                SharedObject.agentTimer.scheduleAtFixedRate(new AgentDaemonTask(context, agentCallback), 1000, 4000);
                hint = true;
            } catch (Exception e) {
                log.error("wrapper:{} 调度挂钩失败", e);
            }
        }
        if (!hint) {
            //该app不需要控制，撤销timer，减少内存消耗
            log.info("该app没有命中任何wrapper");
            SharedObject.agentTimer.cancel();
        } else {
            //在hermesAgent（master重新安装的时候，程序自身自杀，这是因为hermesAgent作为框架代码注入到本程序，
            //hermesAgent重新安装可能意味着框架逻辑发生了更新，新版的交互协议可能和当前app中植入的框架代码协议不一致）
            exitIfMasterReInstall(context);
        }
    }

    private static void exitIfMasterReInstall(Context context) {
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
                log.info("master  重新安装，重启slave 进程");

                new Thread("kill-self-thread") {
                    @Override
                    public void run() {
                        //不能马上自杀，这可能会触发slave进程去更新master的代码dex-cache。但是由于权限问题无法remove历史版本的apk代码缓存，进而热加载失败
                        //sleep似乎无效，继续排查原因
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
                log.error("failed to load hermes-wrapper module", e);
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
                    log.error("failed to load create plugin", e);
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
