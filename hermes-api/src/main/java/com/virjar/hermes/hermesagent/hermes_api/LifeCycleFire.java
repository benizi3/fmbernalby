package com.virjar.hermes.hermesagent.hermes_api;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.common.collect.Sets;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XCallback;

/**
 * Created by virjar on 2018/9/26.
 */

public class LifeCycleFire {
    private static final String TAG = "LifeCycleFire";

    public interface OnFire<T> {
        void fire(T o);
    }

    private static Set<OnFire<Context>> contextReady = Sets.newConcurrentHashSet();
    private static Set<OnFire<Application>> applicationReady = Sets.newConcurrentHashSet();
    private static Set<OnFire<Activity>> firstPageReady = Sets.newConcurrentHashSet();

    private static volatile boolean contextReadySetUp = false;
    private static volatile boolean applicationReadySetUp = false;
    private static volatile boolean firstPageReadySetUp = false;
    private static volatile boolean findFirstActivity = false;

    public static void onFirstPageReady(OnFire<Activity> onFire) {
        firstPageReady.add(onFire);
        if (firstPageReadySetUp) {
            return;
        }
        synchronized (LifeCycleFire.class) {
            if (firstPageReadySetUp) {
                return;
            }
            XposedBridge.hookAllConstructors(Activity.class, new SingletonXC_MethodHook() {
                @Override
                protected synchronized void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (findFirstActivity) {
                        return;
                    }
                    findFirstActivity = true;
                    Class<?> activityClass = param.thisObject.getClass();
                    XposedReflectUtil.findAndHookMethodWithSupperClass(activityClass, "onCreate", Bundle.class, new SingletonXC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            for (OnFire<Activity> fire : firstPageReady) {
                                try {
                                    fire.fire((Activity) param.thisObject);
                                } catch (Exception e) {
                                    Log.i(TAG, "handle application ready fire failed ", e);
                                }
                            }
                        }
                    });
                }
            });
            firstPageReadySetUp = true;
        }
    }

    public static void onApplicationReady(OnFire<Application> onFire) {
        applicationReady.add(onFire);
        if (applicationReadySetUp) {
            return;
        }
        synchronized (LifeCycleFire.class) {
            if (applicationReadySetUp) {
                return;
            }
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook(XCallback.PRIORITY_LOWEST * 2) {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    for (OnFire<Application> fire : applicationReady) {
                        try {
                            fire.fire((Application) param.thisObject);
                        } catch (Exception e) {
                            Log.i(TAG, "handle application ready fire failed ", e);
                        }
                    }
                }
            });
            applicationReadySetUp = true;
        }
    }

    public static void onContextReady(OnFire<Context> onFire) {
        contextReady.add(onFire);
        if (SharedObject.context != null) {
            onFire.fire(SharedObject.context);
        }
        if (contextReadySetUp) {
            return;
        }
        synchronized (LifeCycleFire.class) {
            if (contextReadySetUp) {
                return;
            }
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook(XCallback.PRIORITY_LOWEST * 2) {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    for (OnFire<Context> fire : contextReady) {
                        try {
                            fire.fire((Context) param.args[0]);
                        } catch (Exception e) {
                            Log.i(TAG, "handle context ready fire failed ", e);
                        }
                    }
                }
            });
            contextReadySetUp = true;
        }
    }

}
