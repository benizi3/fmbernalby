package com.virjar.hermes.hermesagent.hookagent;

import android.app.Activity;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.koushikdutta.async.http.Multimap;
import com.virjar.hermes.hermesagent.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.aidl.InvokeResult;
import com.virjar.hermes.hermesagent.plugin.AgentCallback;
import com.virjar.hermes.hermesagent.plugin.ReflectUtil;
import com.virjar.hermes.hermesagent.plugin.SharedObject;
import com.virjar.hermes.hermesagent.util.CommonUtils;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2018/8/25.<br>
 * 一个agent的测试，他可以通过传入的关键字，搜索微视的小姐姐页面数据
 */

public class WeiShiAgent implements AgentCallback {
    private static ClassLoader hostClassLoader = null;
    private static final String TAG = "WeiShiHook";

    @Override
    public String targetPackageName() {
        return "com.tencent.weishi";
    }

    @Override
    public boolean needHook(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        //微视大约有三个子进程，这里只拦截主进程
        return StringUtils.equalsIgnoreCase(loadPackageParam.processName, "com.tencent.weishi");
    }

    @Override
    public InvokeResult invoke(InvokeRequest invokeRequest) {
        String paramContent = invokeRequest.getParamContent();
        Multimap nameValuePairs = CommonUtils.parseUrlEncoded(paramContent);
        String key = nameValuePairs.getString("key");
        if (StringUtils.isBlank(key)) {
            return InvokeResult.failed("the param {key} not presented");
        }
        String attach_info = nameValuePairs.getString("attach_info");
        Long uniqueID = nextUniueID();
        Object searchBean = createSearchBean(key.trim(), uniqueID, attach_info);
        Log.i(TAG, "构造查询请求:" + com.alibaba.fastjson.JSONObject.toJSONString(searchBean));
        if (!sendQueryRequest(searchBean)) {
            return InvokeResult.failed("请求发送失败");
        }
        Object lock = new Object();
        lockes.put(uniqueID, lock);
        try {
            synchronized (lock) {
                //微视本身是一个异步请求，这里等待5s，等待异步的结果，异步转同步
                lock.wait(5000);
                Object remove = queryResult.remove(uniqueID);
                return InvokeResult.success(remove, SharedObject.context);
            }
        } catch (InterruptedException e) {
            return InvokeResult.failed("timeOut");
        }
    }

    @Override
    public void onXposedHotLoad() {
        hostClassLoader = SharedObject.loadPackageParam.classLoader;
        XposedHelpers.findAndHookConstructor(Activity.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!param.thisObject.getClass().getName().equals("com.tencent.oscar.module.discovery.ui.GlobalSearchActivity")) {
                    return;
                }
                hostClassLoader = param.thisObject.getClass().getClassLoader();
            }
        });
    }

    private static Constructor<?> searchBeanConstructor = null;
    private static Class<?> hClass = null;

    private static Object createSearchBean(String param, Long uniqueID, String attachInfo) {
        if (searchBeanConstructor == null) {
            synchronized (WeiShiAgent.class) {
                if (searchBeanConstructor == null) {
                    Class<?> aClassH = XposedHelpers.findClass("com.tencent.oscar.module.discovery.ui.adapter.h", hostClassLoader);
                    hClass = aClassH;
                    searchBeanConstructor = XposedHelpers.findConstructorBestMatch(aClassH, long.class, String.class, int.class, int.class, String.class);
                }
            }
        }
        if (StringUtils.isBlank(attachInfo)) {
            attachInfo = "";
        }

        try {
            return searchBeanConstructor.newInstance(uniqueID, param, 0, 0, attachInfo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private static Method generateUniqueIdMethod = null;

    private long nextUniueID() {
        if (generateUniqueIdMethod == null) {
            synchronized (WeiShiAgent.class) {
                if (generateUniqueIdMethod == null) {
                    Class<?> aClassUtils = XposedHelpers.findClass("com.tencent.ttpic.util.Utils", hostClassLoader);
                    generateUniqueIdMethod = XposedHelpers.findMethodBestMatch(aClassUtils, "generateUniqueId");
                }
            }
        }
        try {
            return (long) generateUniqueIdMethod.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object tinListService = null;
    private static Method tinListServiceSendMethod = null;
    private static Object EnumGetNetworkOnlyEnum = null;

    private boolean sendQueryRequest(Object searchBean) {
        if (tinListService == null) {
            synchronized (WeiShiAgent.class) {
                if (tinListService == null) {
                    Class<?> tingListServiceClass = XposedHelpers.findClass("com.tencent.oscar.base.service.TinListService", hostClassLoader);
                    tinListService = XposedHelpers.callStaticMethod(tingListServiceClass, "a");
                    tinListServiceSendMethod = XposedHelpers.findMethodBestMatch(tingListServiceClass, "a", hClass,
                            XposedHelpers.findClass("com.tencent.oscar.base.service.TinListService.ERefreshPolicy", hostClassLoader)
                            , String.class);
                    Object[] enumConstants = XposedHelpers.findClass("com.tencent.oscar.base.service.TinListService.ERefreshPolicy", hostClassLoader).getEnumConstants();
                    for (Object ec : enumConstants) {
                        if (((Enum<?>) ec).name().equals("ERefreshPolicy")) {
                            EnumGetNetworkOnlyEnum = ec;
                            break;
                        }
                    }
                    ReflectUtil.findAndHookMethodOnlyByMethodName(tingListServiceClass, "onReply", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object requestBean = param.args[0];
                            Object requestResponse = param.args[1];
                            String requestCmd = (String) XposedHelpers.callMethod(requestBean, "getRequestCmd");
                            Log.i(TAG, "command: " + requestCmd);

                            Object jceStruct = XposedHelpers.callMethod(requestResponse, "d");
                            String responseJson = JSONObject.toJSONString(jceStruct);
                            Log.i(TAG, "get data body:" + responseJson);
                            Long uniqueID = getUniqueID(requestBean);
                            if (uniqueID == null) {
                                return;
                            }
                            param.setResult(true);
                            queryResult.put(uniqueID, jceStruct);
                            Object lock = lockes.remove(uniqueID);
                            if (lock == null) {
                                return;
                            }
                            synchronized (lock) {
                                lock.notify();
                            }
                        }
                    });

                    ReflectUtil.findAndHookMethodOnlyByMethodName(tingListServiceClass, "onError", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object requestBean = param.args[0];
                            String errorMessage = (String) param.args[2];
                            Log.i(TAG, "请求error:" + errorMessage);
                            Long uniqueID = getUniqueID(requestBean);
                            if (uniqueID == null) {
                                return;
                            }
                            param.setResult(false);
                            queryResult.put(uniqueID, errorMessage);
                            lockes.remove(uniqueID).notify();
                        }
                    });

                }
            }
        }
        try {
            tinListServiceSendMethod.invoke(tinListService, searchBean, EnumGetNetworkOnlyEnum, "GlobalSearchActivity_global_search_all");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private Long getUniqueID(Object requestBean) {
        return (Long) XposedHelpers.getObjectField(requestBean, "uniqueId");
    }


    private Map<Long, Object> queryResult = Maps.newConcurrentMap();
    private Map<Long, Object> lockes = Maps.newConcurrentMap();

}
