package com.virjar.hermes.hermesagent.hermes_api;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.virjar.xposed_extention.ReflectUtil;
import com.virjar.xposed_extention.SharedObject;

import java.util.ArrayList;
import java.util.Hashtable;
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

    public static void bootstrap(AgentCallback agentCallback) {
        bootstrap(agentCallback, Constant.httpServerPort);
    }

    public static void bootstrap(AgentCallback agentCallback, final int serverPort) {
        if (SharedObject.loadPackageParam == null || SharedObject.context == null) {
            throw new IllegalStateException("com.virjar.xposed_extention.SharedObject must be init first");
        }
        if (EmbedHermes.agentCallback != null) {
            throw new IllegalStateException("embed hermes can not start one hermes wrapper");
        }
        synchronized (EmbedHermes.class) {
            if (EmbedHermes.agentCallback != null) {
                throw new IllegalStateException("embed hermes can not start one hermes wrapper");
            }
            EmbedHermes.agentCallback = agentCallback;
        }
        EmbedHermes.serverPort = serverPort;
        final AsyncHttpServer server = new AsyncHttpServer();
        AsyncServer mAsyncServer = new AsyncServer("httpServerLooper");

        initIndexHandler(server);
        bindInvokeHandler(server, agentCallback);
        bindPingHandler(server);

        try {
            server.listen(mAsyncServer, serverPort);
            log.info("start server success...");
            log.info("server running on: " + localServerBaseURL());
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
                Hashtable<String, ArrayList<Object>> actions = ReflectUtil.getFieldValue(server, "mActions");
                StringBuilder html = new StringBuilder("<html><head><meta charset=\"UTF-8\"><title>Hermes</title></head><body><p>HermesAgent ，项目地址：<a href=\"https://gitee.com/virjar/hermesagent\">https://gitee.com/virjar/hermesagent</a></p>");
                html.append("<p>服务base地址：").append(baseURL).append("</p>");
                for (Hashtable.Entry<String, ArrayList<Object>> entry : actions.entrySet()) {
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
