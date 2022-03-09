package com.virjar.hermes.hermesagent.hookagent;

import android.app.Activity;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.AgentCallback;
import com.virjar.hermes.hermesagent.hermes_api.ClassLoadMonitor;
import com.virjar.hermes.hermesagent.hermes_api.SharedObject;
import com.virjar.hermes.hermesagent.hermes_api.XposedReflectUtil;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;
import com.virjar.hermes.hermesagent.util.CommonUtils;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
    private static ClassLoader frameworkClassLoader = null;
    private static final String TAG = "WeiShiHook";

    @Override
    public String targetPackageName() {
        return "com.tencent.weishi";
    }

    @Override
    public boolean needHook(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        //微视大约有三个子进程，这里只拦截主进程
        // return StringUtils.equalsIgnoreCase(loadPackageParam.processName, "com.tencent.weishi");
        return false;
    }


    private interface SearchBeanHandler {
        Object createSearchBean(InvokeRequest invokeRequest) throws Throwable;
    }

    private static Map<String, SearchBeanHandler> handlers = Maps.newHashMap();

    static {
        handlers.put("search", new SearchBeanHandler() {
            @Override
            public Object createSearchBean(InvokeRequest invokeRequest) {
                String key = invokeRequest.getString("key");
                if (StringUtils.isBlank(key)) {
                    return InvokeResult.failed("the param {key} not presented");
                }
                String attach_info = invokeRequest.getString("attach_info");
                Long uniqueID = nextUniueID();
                return createSearchBeanForSearch(key.trim(), uniqueID, attach_info);
            }
        });

        handlers.put("GetPersonalPage".toLowerCase(), new SearchBeanHandler() {
            @Override
            public Object createSearchBean(InvokeRequest invokeRequest) {
                String userID = invokeRequest.getString("userID");
                if (StringUtils.isBlank(userID)) {
                    return InvokeResult.failed("the param {userID} not presented");
                }
                String attach_info = invokeRequest.getString("attach_info");
                return createSearchBeanForPersonInfo(userID, attach_info);
            }
        });

        handlers.put("GetUsers".toLowerCase(), new SearchBeanHandler() {
            private Object invokeVersion1(InvokeRequest invokeRequest) throws Exception {
                String userID = invokeRequest.getString("userID");
                if (StringUtils.isBlank(userID)) {
                    return InvokeResult.failed("the param {userID} not presented");
                }
                String attach_info = invokeRequest.getString("attach_info");
                if (attach_info == null) {
                    attach_info = "";
                }
                String type = invokeRequest.getString("type");
                if (!StringUtils.equalsIgnoreCase(type, "follower")
                        && !StringUtils.equalsIgnoreCase(type, "interester")) {
                    type = "follower";
                }
                Long uniqueID = nextUniueID();
                Object getUsers = XposedHelpers.findConstructorExact("com.tencent.oscar.module.e.a.g$21", frameworkClassLoader, long.class, String.class)
                        .newInstance(uniqueID, type);

                Object stGetUsersReq = XposedHelpers.findConstructorExact("NS_KING_INTERFACE.stGetUsersReq", frameworkClassLoader, String.class, String.class, String.class, ArrayList.class)
                        .newInstance(attach_info, userID, type, null);
                XposedHelpers.setObjectField(getUsers, "req", stGetUsersReq);
                Object senderManager = XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.tencent.oscar.app.LifePlayApplication", frameworkClassLoader), "getSenderManager");

                Object callback = XposedHelpers.findConstructorExact("com.tencent.oscar.module.e.a.g$22", frameworkClassLoader, long.class, boolean.class).newInstance(nextUniueID(), true);

                if (!registerFanceCallBackFilter1) {
                    synchronized (WeiShiAgent.class) {
                        if (!registerFanceCallBackFilter1) {
                            XposedReflectUtil.findAndHookMethodOnlyByMethodName(XposedHelpers.findClass("com.tencent.oscar.module.e.a.g$22", frameworkClassLoader), "onReply", new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    Object requestBean = param.args[0];
                                    Object requestResponse = param.args[1];
                                    Object jceStruct = XposedHelpers.callMethod(requestResponse, "d");
                                    param.setResult(true);
                                    queryResult.put(requestBean, jceStruct);
                                    Object lock = lockes.remove(requestBean);
                                    if (lock == null) {
                                        return;
                                    }
                                    synchronized (lock) {
                                        lock.notify();
                                    }
                                }
                            });
                            XposedReflectUtil.findAndHookMethodOnlyByMethodName(XposedHelpers.findClass("com.tencent.oscar.module.e.a.g$22", frameworkClassLoader), "onError", new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    Object requestBean = param.args[0];
                                    String errorMessage = (String) param.args[2];
                                    Log.i(TAG, "请求error:" + errorMessage);
                                    param.setResult(false);
                                    queryResult.put(requestBean, errorMessage);
                                    lockes.remove(requestBean).notify();
                                }
                            });
                        }
                        registerFanceCallBackFilter1 = true;
                    }
                }

                XposedHelpers.callMethod(senderManager, "a", getUsers, callback);


                Object lock = new Object();
                lockes.put(getUsers, lock);
                try {
                    synchronized (lock) {
                        //微视本身是一个异步请求，这里等待5s，等待异步的结果，异步转同步
                        lock.wait(5000);
                        Object remove = queryResult.remove(getUsers);
                        return InvokeResult.success(remove, SharedObject.context);
                    }
                } catch (InterruptedException e) {
                    APICommonUtils.requestLogW(invokeRequest, "等待微视响应超时", e);
                    return InvokeResult.failed("timeOut");
                }

            }


            @Override
            public Object createSearchBean(InvokeRequest invokeRequest) throws Exception {
                if (frameworkClassLoader != null) {
                    return invokeVersion1(invokeRequest);
                }
                String userID = invokeRequest.getString("userID");
                if (StringUtils.isBlank(userID)) {
                    return InvokeResult.failed("the param {userID} not presented");
                }
                String attach_info = invokeRequest.getString("attach_info");
                if (attach_info == null) {
                    attach_info = "";
                }
                String type = invokeRequest.getString("type");
                if (!StringUtils.equalsIgnoreCase(type, "follower")
                        && !StringUtils.equalsIgnoreCase(type, "interester")) {
                    type = "follower";
                }

                Long uniqueID = nextUniueID();
                Object getUsers = XposedHelpers.findConstructorExact("com.tencent.oscar.module.f.a.f$18", hostClassLoader, long.class, String.class)
                        .newInstance(uniqueID, "GetUsers");

                Object stGetUsersReq = XposedHelpers.findConstructorExact("NS_KING_INTERFACE.stGetUsersReq", hostClassLoader, String.class, String.class, String.class, ArrayList.class)
                        .newInstance(attach_info, userID, type, null);
                XposedHelpers.setObjectField(getUsers, "req", stGetUsersReq);
                Object senderManager = XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.tencent.oscar.base.app.App", hostClassLoader)
                        , "getSenderManager");

                Object callback = XposedHelpers.findConstructorExact("com.tencent.oscar.module.f.a.f$19", hostClassLoader, long.class, boolean.class).newInstance(nextUniueID(), true);

                if (!registerFanceCallBackFilter2) {
                    synchronized (WeiShiAgent.class) {
                        if (!registerFanceCallBackFilter2) {
                            XposedReflectUtil.findAndHookMethodOnlyByMethodName(XposedHelpers.findClass("com.tencent.oscar.module.f.a.f$19", hostClassLoader), "onReply", new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    Object requestBean = param.args[0];
                                    Object requestResponse = param.args[1];
                                    Object jceStruct = XposedHelpers.callMethod(requestResponse, "d");
                                    param.setResult(true);
                                    queryResult.put(requestBean, jceStruct);
                                    Object lock = lockes.remove(requestBean);
                                    if (lock == null) {
                                        return;
                                    }
                                    synchronized (lock) {
                                        lock.notify();
                                    }
                                }
                            });
                            XposedReflectUtil.findAndHookMethodOnlyByMethodName(XposedHelpers.findClass("com.tencent.oscar.module.f.a.f$19", hostClassLoader), "onError", new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    Object requestBean = param.args[0];
                                    String errorMessage = (String) param.args[2];
                                    Log.i(TAG, "请求error:" + errorMessage);
                                    param.setResult(false);
                                    queryResult.put(requestBean, errorMessage);
                                    lockes.remove(requestBean).notify();
                                }
                            });
                        }
                        registerFanceCallBackFilter2 = true;
                    }
                }

                XposedHelpers.callMethod(senderManager, "a", getUsers, callback);


                Object lock = new Object();
                lockes.put(getUsers, lock);
                try {
                    synchronized (lock) {
                        //微视本身是一个异步请求，这里等待5s，等待异步的结果，异步转同步
                        lock.wait(5000);
                        Object remove = queryResult.remove(getUsers);
                        return InvokeResult.success(remove, SharedObject.context);
                    }
                } catch (InterruptedException e) {
                    APICommonUtils.requestLogW(invokeRequest, "等待微视响应超时", e);
                    return InvokeResult.failed("timeOut");
                }
            }
        });
    }

    private static volatile boolean registerFanceCallBackFilter1 = false;
    private static volatile boolean registerFanceCallBackFilter2 = false;

    @Override
    public InvokeResult invoke(InvokeRequest invokeRequest) {

        String action = invokeRequest.getString("action");
        if (StringUtils.isBlank(action)) {
            action = "search";
        }


        SearchBeanHandler searchBeanHandler = handlers.get(action.toLowerCase());
        if (searchBeanHandler == null) {
            return InvokeResult.failed("unknown action:" + action);
        }

        Object searchBean;
        try {
            searchBean = searchBeanHandler.createSearchBean(invokeRequest);
        } catch (Throwable throwable) {
            APICommonUtils.requestLogW(invokeRequest, "请求异常", throwable);
            return InvokeResult.failed(CommonUtils.translateSimpleExceptionMessage(throwable));
        }
        if (searchBean instanceof InvokeResult) {
            return (InvokeResult) searchBean;
        }
        APICommonUtils.requestLogI(invokeRequest, "构造查询请求:" + com.alibaba.fastjson.JSONObject.toJSONString(searchBean));
        if (!sendQueryRequest(searchBean)) {
            APICommonUtils.requestLogI(invokeRequest, "请求发送失败");
            return InvokeResult.failed("请求发送失败");
        }
        Object lock = new Object();
        lockes.put(searchBean, lock);
        try {
            synchronized (lock) {
                //微视本身是一个异步请求，这里等待5s，等待异步的结果，异步转同步
                lock.wait(5000);
                Object remove = queryResult.remove(searchBean);
                if (remove == null) {
                    return InvokeResult.failed("timeOut");
                }
                return InvokeResult.success(remove, SharedObject.context);
            }
        } catch (InterruptedException e) {
            APICommonUtils.requestLogW(invokeRequest, "等待微视响应超时", e);
            return InvokeResult.failed("timeOut");
        }
    }

    @Override
    public void onXposedHotLoad() {
        hostClassLoader = SharedObject.loadPackageParam.classLoader;
        XposedHelpers.findAndHookConstructor(Activity.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                hostClassLoader = param.thisObject.getClass().getClassLoader();
            }
        });
        ClassLoadMonitor.addClassLoadMonitor("com.tencent.oscar.module.e.a.g$21", new ClassLoadMonitor.OnClassLoader() {
            @Override
            public void onClassLoad(Class clazz) {
                frameworkClassLoader = clazz.getClassLoader();
            }
        });
    }

    private static Constructor<?> searchBeanConstructor = null;

    private static Object createSearchBeanForSearch(String param, Long uniqueID, String attachInfo) {
        if (searchBeanConstructor == null) {
            synchronized (WeiShiAgent.class) {
                if (searchBeanConstructor == null) {
                    Class<?> aClassH = XposedHelpers.findClass("com.tencent.oscar.module.discovery.ui.adapter.h", hostClassLoader);
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


    private static Constructor<?> personInfoConstructor = null;

    private static Object createSearchBeanForPersonInfo(String userID, String attachInfo) {
        if (personInfoConstructor == null) {
            synchronized (WeiShiAgent.class) {
                if (personInfoConstructor == null) {
                    Class<?> personInfoClass = XposedHelpers.findClass("com.tencent.oscar.module.f.b.a.b", hostClassLoader);
                    personInfoConstructor = XposedHelpers.findConstructorBestMatch(personInfoClass, String.class, int.class, String.class);
                }
            }
        }
        if (StringUtils.isBlank(attachInfo)) {
            attachInfo = "";
        }
        try {
            return personInfoConstructor.newInstance(userID, 0, attachInfo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }


    private static Method generateUniqueIdMethod = null;

    private static long nextUniueID() {
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
                    tinListServiceSendMethod = XposedHelpers.findMethodBestMatch(tingListServiceClass, "a",
                            XposedHelpers.findClass("com.tencent.oscar.utils.network.d", hostClassLoader),
                            XposedHelpers.findClass("com.tencent.oscar.base.service.TinListService.ERefreshPolicy", hostClassLoader)
                            , String.class);
                    Object[] enumConstants = XposedHelpers.findClass("com.tencent.oscar.base.service.TinListService.ERefreshPolicy", hostClassLoader).getEnumConstants();
                    for (Object ec : enumConstants) {
                        if (((Enum<?>) ec).name().equals("ERefreshPolicy")) {
                            EnumGetNetworkOnlyEnum = ec;
                            break;
                        }
                    }
                    XposedReflectUtil.findAndHookMethodOnlyByMethodName(tingListServiceClass, "onReply", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object requestBean = param.args[0];
                            Object requestResponse = param.args[1];
                            String requestCmd = (String) XposedHelpers.callMethod(requestBean, "getRequestCmd");
                            Log.i(TAG, "command: " + requestCmd);

                            Object jceStruct = XposedHelpers.callMethod(requestResponse, "d");
                            String responseJson = JSONObject.toJSONString(jceStruct);
                            Log.i(TAG, "get data body:" + responseJson);

                            param.setResult(true);
                            queryResult.put(requestBean, jceStruct);
                            Object lock = lockes.remove(requestBean);
                            if (lock == null) {
                                return;
                            }
                            synchronized (lock) {
                                lock.notify();
                            }
                        }
                    });

                    XposedReflectUtil.findAndHookMethodOnlyByMethodName(tingListServiceClass, "onError", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object requestBean = param.args[0];
                            String errorMessage = (String) param.args[2];
                            Log.i(TAG, "请求error:" + errorMessage);
                            param.setResult(false);
                            queryResult.put(requestBean, errorMessage);
                            Object lock = lockes.remove(requestBean);
                            if (lock == null) {
                                return;
                            }
                            synchronized (lock) {
                                lock.notify();
                            }
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


    private static Map<Object, Object> queryResult = Maps.newConcurrentMap();
    private static Map<Object, Object> lockes = Maps.newConcurrentMap();

}
