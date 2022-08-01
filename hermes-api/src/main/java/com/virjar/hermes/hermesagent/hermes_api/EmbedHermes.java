package com.virjar.hermes.hermesagent.hermes_api;

import android.util.Log;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.virjar.xposed_extention.ClassScanner;
import com.virjar.xposed_extention.ReflectUtil;
import com.virjar.xposed_extention.SharedObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2018/10/15.<br>
 * 提供一个嵌入式的hermes模块，实现单个进程启动hermes的能力
 */
@Slf4j
public class EmbedHermes {
    private static AgentCallback agentCallback = null;
    private static int serverPort = 0;

    static {
        if (SharedObject.context != null) {
            try {
                //try to configure log component
                LogConfigurator.configure(SharedObject.context);
            } catch (Throwable throwable) {
                //ignore
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void bootstrap(String basePackage, Class loader) {
        ClassScanner.SubClassVisitor<AgentCallback> subClassVisitor = new ClassScanner.SubClassVisitor(true, AgentCallback.class);
        ClassScanner.scan(subClassVisitor, Lists.newArrayList(basePackage), MultiActionWrapperFactory.bindApkLocation(loader.getClassLoader()));
        List<Class<? extends AgentCallback>> subClass = subClassVisitor.getSubClass();
        if (subClass.size() > 1) {
            throw new RuntimeException("found duplicate wrapper implement:" + com.alibaba.fastjson.JSONObject.toJSONString(subClass));
        }
        AgentCallback agentCallback;
        if (subClass.size() == 0) {
            agentCallback = new MultiActionWrapper();
        } else {
            try {
                agentCallback = subClass.get(0).newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        if (agentCallback instanceof MultiActionWrapper) {
            ArrayList<ActionRequestHandler> actionRequestHandlers = MultiActionWrapperFactory.scanActionWrappers(basePackage, MultiActionWrapperFactory.bindApkLocation(loader.getClassLoader()), agentCallback.getClass().getClassLoader());
            if (actionRequestHandlers.size() == 0) {
                throw new IllegalStateException("can not find any ActionRequestHandler");
            }
            MultiActionWrapper multiActionWrapper = (MultiActionWrapper) agentCallback;
            for (ActionRequestHandler actionRequestHandler : actionRequestHandlers) {
                try {
                    multiActionWrapper.registryHandler(MultiActionWrapperFactory.resolveAction(actionRequestHandler), actionRequestHandler);
                } catch (Exception e) {
                    //ignore
                }
            }
        }
        bootstrap(agentCallback);
    }

    public static void bootstrap(AgentCallback agentCallback) {
        bootstrap(agentCallback, Constant.httpServerPort);
    }

    public static void bootstrap(final AgentCallback agentCallback, final int serverPort) {
        if (agentCallback instanceof AgentRegisterAware) {
            ((AgentRegisterAware) agentCallback).setOnAgentReadyListener(new WrapperRegister() {
                @Override
                public void regist() {
                    bootstrapInternal(agentCallback, serverPort);
                }
            });
        } else {
            bootstrapInternal(agentCallback, serverPort);
        }
    }


    private static void bootstrapInternal(AgentCallback agentCallback, final int serverPort) {
        if (SharedObject.loadPackageParam == null || SharedObject.context == null) {
            throw new IllegalStateException("com.virjar.xposed_extention.SharedObject must be init first");
        }
        Preconditions.checkNotNull(agentCallback, "agent callback can not be empty");
        if (EmbedHermes.agentCallback != null) {
            throw new IllegalStateException("embed hermes can only start one hermes wrapper");
        }
        synchronized (EmbedHermes.class) {
            if (EmbedHermes.agentCallback != null) {
                throw new IllegalStateException("embed hermes can not start one hermes wrapper");
            }
            EmbedHermes.agentCallback = agentCallback;
        }
        if (!agentCallback.needHook(SharedObject.loadPackageParam)) {
            log.info("this wrapper not suitable for process:{}", SharedObject.loadPackageParam.processName);
            return;
        }
        EmbedHermes.serverPort = serverPort;
        final AsyncHttpServer server = new AsyncHttpServer();
        AsyncServer mAsyncServer = new AsyncServer("httpServerLooper");

        initIndexHandler(server);
        bindInvokeHandler(server, agentCallback);
        bindPingHandler(server);

        try {
            server.listen(mAsyncServer, serverPort);
            Log.i("weijia", "start server success...");
            Log.i("weijia", "server running on: " + localServerBaseURL());
        } catch (Exception e) {
            log.error("startServer error", e);
        }
        try {
            agentCallback.onXposedHotLoad();
        } catch (Exception e) {
            log.error("start up agent callback failed", e);
            EmbedHermes.agentCallback = null;
            throw new RuntimeException(e);
        }

    }

    private static void bindPingHandler(final AsyncHttpServer server) {
        server.get(Constant.httpServerPingPath, new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                response.send("true");
            }
        });
    }

    private static void bindInvokeHandler(AsyncHttpServer server, AgentCallback agentCallback) {
        EmbedRPCInvokeCallback embedRPCInvokeCallback = new EmbedRPCInvokeCallback(agentCallback);
        server.get(Constant.invokePath, embedRPCInvokeCallback);
        server.post(Constant.invokePath, embedRPCInvokeCallback);
    }

    private static void initIndexHandler(final AsyncHttpServer server) {
        server.get("/", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                String baseURL = localServerBaseURL();
                ArrayList<Object> routes = ReflectUtil.getFieldValue(server, "routes");
                Map<String, ArrayList<Object>> actions = Maps.newHashMap();
                for (Object route : routes) {
                    String method = ReflectUtil.getFieldValue(route, "method");
                    ArrayList<Object> urlList = actions.get(method);
                    if (urlList == null) {
                        urlList = Lists.newArrayList();
                        actions.put(method, urlList);
                    }
                    urlList.add(route);
                }
                StringBuilder html = new StringBuilder("<html><head><meta charset=\"UTF-8\"><title>Hermes</title></head><body><p>HermesAgent ，项目地址：<a href=\"https://gitee.com/virjar/hermesagent\">https://gitee.com/virjar/hermesagent</a></p>");
                html.append("<p>服务base地址：").append(baseURL).append("</p>");
                for (Map.Entry<String, ArrayList<Object>> entry : actions.entrySet()) {
                    html.append("<p>httpMethod:").append(entry.getKey()).append("</p>");
                    html.append("<ul>");
                    for (Object object : entry.getValue()) {
                        Pattern pattern = ReflectUtil.getFieldValue(object, "regex");
                        html.append("<li>");
                        if ("get".equalsIgnoreCase(entry.getKey())) {
                            html.append("<a href=\"").append(baseURL).append(pattern.pattern().substring(1)).append("\">")
                                    .append(baseURL).append(pattern.pattern().substring(1)).append("</a>");
                        } else {
                            html.append(baseURL).append(pattern.pattern().substring(1));
                        }
                        html.append("</li>");
                    }
                    html.append("</ul>");
                }
                html.append("<p>").append("这是embed hermes，仅为测试设计，功能有限，若要保证服务长期在线，请使用hermesAgent").append("</p>");
                html.append("</body></html>");
                response.send("text/html", html.toString());
            }
        });

    }

    private static String localServerBaseURL() {
        return "http://" + APICommonUtils.getLocalIp() + ":" + serverPort;
    }
}
