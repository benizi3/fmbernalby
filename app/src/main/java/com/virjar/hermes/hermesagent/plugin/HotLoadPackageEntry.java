package com.virjar.hermes.hermesagent.plugin;

import android.content.Context;
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
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        }
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
