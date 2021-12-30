package com.virjar.hermes.hermesagent.hermes_api;

import android.util.Log;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import de.robv.android.xposed.XposedHelpers;

/**
 * Created by virjar on 2018/4/11.<br>
 * 方便的代码植入封装
 */
public class ClassLoadMonitor {
    public interface OnClassLoader {
        void onClassLoad(Class clazz);
    }

    private static ConcurrentMap<String, Set<OnClassLoader>> callBacks = Maps.newConcurrentMap();
    private static Set<ClassLoader> hookedClassLoader = Sets.newConcurrentHashSet();

    static {
        enableClassMonitor();
    }

    private static void enableClassMonitor() {

        //要hook所有子类的方法实现
        XposedHelpers.findAndHookConstructor(ClassLoader.class, new SingletonXC_MethodHook() {

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                ClassLoader newClassLoader = (ClassLoader) param.thisObject;
                if (hookedClassLoader.contains(newClassLoader)) {
                    return;
                }
                hookedClassLoader.add(newClassLoader);
                fireCallBack();
            }

        });

        //隐式加载入口
        XposedHelpers.findAndHookMethod(Class.class, "forName", String.class, boolean.class, ClassLoader.class, new SingletonXC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                ClassLoader newClassLoader = (ClassLoader) param.args[2];
                if (newClassLoader == null) {
                    return;
                }
                if (hookedClassLoader.contains(newClassLoader)) {
                    return;
                }
                hookedClassLoader.add(newClassLoader);
                fireCallBack();
            }
        });

        hookedClassLoader.add(SharedObject.loadPackageParam.classLoader);
        //do not need to fire call back for masterClassLoader
    }

    private static ThreadLocal<Boolean> isCallBackRunning = new ThreadLocal<>();

    private static void fireCallBack() {
        if (callBacks.size() == 0 || hookedClassLoader.size() == 0) {
            return;
        }
        Boolean aBoolean = isCallBackRunning.get();
        if (aBoolean != null && aBoolean) {
            return;
        }
        isCallBackRunning.set(true);
        //监听函数，不允许重入，否则会有类加载不完整触发回调的可能
        try {

            Set<String> succeedCallBack = Sets.newHashSet();
            for (String monitorClassName : callBacks.keySet()) {
                for (ClassLoader classLoader : hookedClassLoader) {
                    try {
                        Class<?> aClass = classLoader.loadClass(monitorClassName);
                        Collection<OnClassLoader> onClassLoaders = callBacks.get(monitorClassName);
                        for (OnClassLoader onClassLoader : onClassLoaders) {
                            try {
                                onClassLoader.onClassLoad(aClass);
                            } catch (Throwable throwable) {
                                Log.e("weijia", "error when callback for class load monitor", throwable);
                            }
                        }
                        succeedCallBack.add(monitorClassName);
                        break;
                    } catch (Throwable throwable) {
                        //ignore
                    }
                }
            }
            for (String className : succeedCallBack) {
                callBacks.get(className).clear();
            }
        } finally {
            isCallBackRunning.remove();
        }
    }

    /**
     * 增加某个class的加载监听，注意该方法不做重入消重工作，需要调用方自己实现回调消重逻辑。<br>
     * 该函数将会尽可能早的的回调到业务方，常常用来注册挂钩函数（这样可以实现挂钩函数注册过晚导致感兴趣的逻辑拦截失败）
     *
     * @param className     将要监听的className，如果存在多个class name相同的类，存在于不同的classloader，可能会导致监听失败
     * @param onClassLoader 监听的回调
     */
    public static void addClassLoadMonitor(String className, OnClassLoader onClassLoader) {

        Set<OnClassLoader> onClassLoaders = callBacks.get(className);
        if (onClassLoaders == null) {
            onClassLoaders = Sets.newConcurrentHashSet();
            //putIfAbsent maybe null
            callBacks.putIfAbsent(className, onClassLoaders);
            onClassLoaders = callBacks.get(className);
        }
        onClassLoaders.add(onClassLoader);
        fireCallBack();
    }
}