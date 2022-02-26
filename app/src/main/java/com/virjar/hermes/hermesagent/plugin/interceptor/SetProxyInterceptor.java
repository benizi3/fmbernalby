package com.virjar.hermes.hermesagent.plugin.interceptor;

import android.app.Activity;
import android.util.Log;

import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.ClassLoadMonitor;
import com.virjar.hermes.hermesagent.hermes_api.HermesCommonConfig;
import com.virjar.hermes.hermesagent.hermes_api.LifeCycleFire;
import com.virjar.hermes.hermesagent.hermes_api.Ones;
import com.virjar.hermes.hermesagent.hermes_api.SingletonXC_MethodHook;
import com.virjar.hermes.hermesagent.hermes_api.XposedReflectUtil;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;
import com.virjar.hermes.hermesagent.plugin.InvokeInterceptor;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.net.ProxySelector;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by virjar on 2018/9/27.<br>
 * 设置代理,该设置仅针对于单个app生效
 */
@SuppressWarnings("unused")
public class SetProxyInterceptor implements InvokeInterceptor {
    private static final String hermesProxyIPSettingKey = "__hermes_proxy_ip";
    private static final String hermesProxyPortSettingKey = "__hermes_proxy_port";
    private static final String forceProxyFlag = "__hermes_force_use_proxy";


    @Override
    public InvokeResult intercept(InvokeRequest invokeRequest) {
        String invokeProxy = invokeRequest.getString("__hermes_invoke_proxy");
        if (StringUtils.isBlank(invokeProxy)) {
            return null;
        }
        APICommonUtils.requestLogI(invokeRequest, "setting proxy info:" + invokeProxy);
        String[] ipAndPort = invokeProxy.split(":");
        if (ipAndPort.length != 2) {
            APICommonUtils.requestLogW(invokeRequest, "proxy param format error");
            return null;
        }
        String ip = ipAndPort[0];
        String portStr = ipAndPort[1];
        int port = NumberUtils.toInt(portStr, -1);
        if (port <= 0 || port > 65534) {
            return InvokeResult.failed("setting proxy failed,the proxy port must be a positive number，your config is：" + invokeProxy + " ,please check it!!");
        }
        if (StringUtils.startsWithIgnoreCase(ip, "http:")
                || StringUtils.startsWithIgnoreCase(ip, "https:")) {
            return InvokeResult.failed("setting proxy failed,the proxy  must be a host name,domain or ip number，your config is：" + invokeProxy + " ,please check it!!");
        }

        String hermesProxyIp = HermesCommonConfig.getString(hermesProxyIPSettingKey);
        String hermesProxyPort = HermesCommonConfig.getString(hermesProxyPortSettingKey);

        if (!StringUtils.equals(ip, hermesProxyIp)
                || !StringUtils.equals(portStr, hermesProxyPort)) {
            HermesCommonConfig.putString(hermesProxyIPSettingKey, ip);
            HermesCommonConfig.putString(hermesProxyPortSettingKey, hermesProxyPort);
            HermesCommonConfig.putBoolean(forceProxyFlag, StringUtils.equalsIgnoreCase(invokeRequest.getString(forceProxyFlag), "true"));
            setup();
        }
        return null;
    }

    @Override
    public void setup() {
        final String hermesProxyIp = HermesCommonConfig.getString(hermesProxyIPSettingKey);
        final String hermesProxyPort = HermesCommonConfig.getString(hermesProxyPortSettingKey);

        if (StringUtils.isBlank(hermesProxyIp)
                || StringUtils.isBlank(hermesProxyPort)) {
            return;
        }
        LifeCycleFire.onFirstPageReady(new LifeCycleFire.OnFire<Activity>() {
            @Override
            public void fire(Activity o) {
                setProxy(hermesProxyIp, hermesProxyPort);
            }
        });
        setProxy(hermesProxyIp, hermesProxyPort);
        //强制使用我们提供的代理
        if (HermesCommonConfig.getBoolean(forceProxyFlag)) {
            Ones.hookOnes(SetProxyInterceptor.class, "forceUseSystemProxySetUp", new Ones.DoOnce() {
                @Override
                public void doOne(Class<?> clazz) {
                    setUpForceUseSystemProxy();
                }
            });
        }
    }

    private void setUpForceUseSystemProxy() {

        final ProxySelector proxySelector = ProxySelector.getDefault();

        ClassLoadMonitor.addClassLoadMonitor("okhttp3.OkHttpClient", new ClassLoadMonitor.OnClassLoader() {
            @Override
            public void onClassLoad(Class clazz) {
                XposedHelpers.findAndHookMethod(clazz, "proxySelector", XC_MethodReplacement.returnConstant(proxySelector));
            }
        });

        ClassLoadMonitor.addClassLoadMonitor("com.squareup.okhttp.OkHttpClient", new ClassLoadMonitor.OnClassLoader() {
            @Override
            public void onClassLoad(Class clazz) {
                XposedHelpers.findAndHookMethod(clazz, "getProxySelector", XC_MethodReplacement.returnConstant(proxySelector));
            }
        });

        //TODO 还有一个Android internal的版本，现在OkHttpClient已经成为内置的网络库，所以Android源码构建的时候，有一个编译hook，会将okhttp3的代码路由到com.android.x

        //httpclient的设置
        ClassLoadMonitor.addClassLoadMonitor("org.apache.http.impl.client.InternalHttpClient", new ClassLoadMonitor.OnClassLoader() {
            @Override
            public void onClassLoad(Class clazz) {
                Class<?> systemDefaultRoutPlannerClass = XposedHelpers.findClassIfExists("org.apache.http.impl.conn.SystemDefaultRoutePlanner", clazz.getClassLoader());
                if (systemDefaultRoutPlannerClass == null) {
                    return;
                }
                Class<?> defaultSchemaPortResolver = XposedHelpers.findClass("org.apache.http.impl.conn.DefaultSchemePortResolver", clazz.getClassLoader());
                final Object systemDefaultRoutePlanner = XposedHelpers.newInstance(systemDefaultRoutPlannerClass, XposedHelpers.getStaticObjectField(defaultSchemaPortResolver, "INSTANCE"), proxySelector);

                XposedHelpers.findAndHookMethod(clazz, "determineRoute", "org.apache.http.HttpHost", "org.apache.http.HttpRequest", "org.apache.http.protocol.HttpContext", new SingletonXC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object host = param.args[0];
                        if (host == null) {
                            host = XposedHelpers.callMethod(XposedHelpers.callMethod(param.args[1], "getParams"), "getParameter", "http.default-host");
                        }
                        param.setResult(XposedHelpers.callMethod(systemDefaultRoutePlanner, "determineRoute", host, param.args[1], param.args[2]));
                    }
                });
            }
        });


        ClassLoadMonitor.addClassLoadMonitor("org.apache.http.impl.client.AbstractHttpClient", new ClassLoadMonitor.OnClassLoader() {

            @Override
            public void onClassLoad(final Class clazz) {
                Class<?> systemDefaultRoutPlannerClass = XposedHelpers.findClassIfExists("org.apache.http.impl.conn.SystemDefaultRoutePlanner", clazz.getClassLoader());
                if (systemDefaultRoutPlannerClass == null) {
                    return;
                }
                Class<?> defaultSchemaPortResolver = XposedHelpers.findClass("org.apache.http.impl.conn.DefaultSchemePortResolver", clazz.getClassLoader());
                final Object systemDefaultRoutePlanner = XposedHelpers.newInstance(systemDefaultRoutPlannerClass, XposedHelpers.getStaticObjectField(defaultSchemaPortResolver, "INSTANCE"), proxySelector);
                XposedBridge.hookAllConstructors(clazz, new SingletonXC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Ones.hookOnes(XposedReflectUtil.findMethodWithSupperClass(param.thisObject.getClass(), "getRoutePlanner").getDeclaringClass(), "hook_getRoutePlanner", new Ones.DoOnce() {
                            @Override
                            public void doOne(Class<?> clazz) {
                                XposedHelpers.findAndHookMethod(clazz, "getRoutePlanner", XC_MethodReplacement.returnConstant(systemDefaultRoutePlanner));
                            }
                        });
                    }
                });

            }
        });

    }

    private void setProxy(String proxyIP, String proxyPort) {
        System.setProperty("http.proxyHost", proxyIP);
        System.setProperty("https.proxyHost", proxyIP);

        System.setProperty("http.proxyPort", proxyPort);
        System.setProperty("https.proxyPort", proxyPort);

        System.setProperty("hermes.http.proxyHost", proxyIP);
        System.setProperty("hermes.https.proxyHost", proxyIP);

        System.setProperty("hermes.http.proxyPort", proxyPort);
        System.setProperty("hermes.https.proxyPort", proxyPort);
        preventInternal();
    }


    /**
     * 阻止app自己通过api，再次设置系统代理
     */
    private void preventInternal() {
        Ones.hookOnes(System.class, "hook_setProperty", new Ones.DoOnce() {
            @Override
            public void doOne(Class<?> clazz) {
                XposedHelpers.findAndHookMethod(System.class, "setProperty",
                        String.class, String.class, new SingletonXC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                                String key = (String) param.args[0];
                                if (!StringUtils.startsWith(key, "http")) {
                                    return;
                                }
                                if (StringUtils.equals(key, "http.proxyHost")
                                        || StringUtils.equals(key, "http.proxyPort")
                                        || StringUtils.equals(key, "https.proxyHost")
                                        || StringUtils.equals(key, "https.proxyPort")) {
                                    Log.i("CustomProxyPrevent", "key:" + key + "  value:" + param.args[1]);
                                    param.setResult(null);
                                } else if (StringUtils.startsWithIgnoreCase(key, "hermes.")) {
                                    param.args[0] = key.substring("hermes.".length());
                                }
                            }
                        });
            }
        });
    }
}
