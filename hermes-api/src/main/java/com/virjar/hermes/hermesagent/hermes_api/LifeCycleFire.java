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

    private static Set<OnFire<Context>> contextReady = Sets.newCopyOnWriteArraySet();
    private static Set<OnFire<Application>> applicationReady = Sets.newCopyOnWriteArraySet();
    private static Set<OnFire<Activity>> firstPageReady = Sets.newCopyOnWriteArraySet();
    private static Set<OnFire<Activity>> firstPageCreate = Sets.newCopyOnWriteArraySet();


    private static Activity firstActivity;
    private static Activity firstCreatedActivity;
    private static Application application;

    public static void init() {
        Ones.hookOnes(Activity.class, "monitor_first_activity", new Ones.DoOnce() {
            @Override
            public void doOne(Class<?> clazz) {
                XposedBridge.hookAllConstructors(Activity.class, new SingletonXC_MethodHook() {
                    @Override
                    protected synchronized void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (firstActivity != null) {
                            return;
                        }
                        for (OnFire<Activity> fire : firstPageCreate) {
                            try {
                                fire.fire((Activity) param.thisObject);
                                firstPageCreate.remove(fire);
                            } catch (Exception e) {
                                Log.i(TAG, "handle application ready fire failed ", e);
                            }
                        }
                        firstCreatedActivity = (Activity) param.thisObject;
                        Class<?> activityClass = param.thisObject.getClass();
                        XposedReflectUtil.findAndHookMethodWithSupperClass(activityClass, "onCreate", Bundle.class, new SingletonXC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                for (OnFire<Activity> fire : firstPageReady) {
                                    try {
                                        fire.fire((Activity) param.thisObject);
                                        firstPageReady.remove(fire);
                                    } catch (Exception e) {
                                        Log.i(TAG, "handle application ready fire failed ", e);
                                    }
                                }
                                firstActivity = (Activity) param.thisObject;
                            }
                        });
                    }
                });
            }
        });

        Ones.hookOnes(Application.class, "monitor_first_application_set_up", new Ones.DoOnce() {
            @Override
            public void doOne(Class<?> clazz) {
                XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook(XCallback.PRIORITY_LOWEST * 2) {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        for (OnFire<Application> fire : applicationReady) {
                            try {
                                fire.fire((Application) param.thisObject);
                                applicationReady.remove(fire);
                            } catch (Exception e) {
                                Log.i(TAG, "handle application ready fire failed ", e);
                            }
                        }
                        application = (Application) param.thisObject;
                    }
                });
            }
        });

        Ones.hookOnes(Application.class, "monitor_first_content_available", new Ones.DoOnce() {
            @Override
            public void doOne(Class<?> clazz) {
                XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook(XCallback.PRIORITY_LOWEST * 2) {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        for (OnFire<Context> fire : contextReady) {
                            try {
                                fire.fire((Context) param.args[0]);
                                contextReady.remove(fire);
                            } catch (Exception e) {
                                Log.i(TAG, "handle context ready fire failed ", e);
                            }
                        }
                    }
                });
            }
        });
    }

    public static void onFirstPageReady(OnFire<Activity> onFire) {
        if (firstActivity != null) {
            onFire.fire(firstActivity);
            return;
        }
        firstPageReady.add(onFire);

    }

    public static void onFirstPageCreate(OnFire<Activity> onFire) {
        if (firstCreatedActivity != null) {
            onFire.fire(firstCreatedActivity);
            return;
        }
        firstPageCreate.add(onFire);

    }

    public static void onApplicationReady(OnFire<Application> onFire) {
        if (application != null) {
            onFire.fire(application);
            return;
        }
        applicationReady.add(onFire);
    }

    public static void onContextReady(OnFire<Context> onFire) {
        contextReady.add(onFire);
        if (SharedObject.context != null) {
            onFire.fire(SharedObject.context);
        }
    }

}
