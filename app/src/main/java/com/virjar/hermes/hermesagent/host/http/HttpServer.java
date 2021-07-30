package com.virjar.hermes.hermesagent.host.http;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.collect.Lists;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.virjar.hermes.hermesagent.aidl.IHookAgentService;
import com.virjar.hermes.hermesagent.bean.CommonRes;
import com.virjar.hermes.hermesagent.host.manager.StartAppTask;
import com.virjar.hermes.hermesagent.host.service.FontService;
import com.virjar.hermes.hermesagent.host.thread.J2Executor;
import com.virjar.hermes.hermesagent.plugin.ReflectUtil;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;
import com.virjar.hermes.hermesagent.util.HttpClientUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by virjar on 2018/8/23.
 */

public class HttpServer {

    private static final String TAG = "httpServer";
    private static AsyncHttpServer server = null;
    private static AsyncServer mAsyncServer = null;
    private static HttpServer instance = new HttpServer();
    private int httpServerPort = 0;
    private FontService fontService;
    private RCPInvokeCallback httpServerRequestCallback = null;
    private J2Executor j2Executor;
    private StartServiceHandler handler = new StartServiceHandler(Looper.getMainLooper(), this);

    private static class StartServiceHandler extends Handler {
        private HttpServer httpServer;

        StartServiceHandler(Looper looper, HttpServer httpServer) {
            super(looper);
            this.httpServer = httpServer;
        }

        @Override
        public void handleMessage(Message msg) {
            //super.handleMessage(msg);
            httpServer.startServerInternal((Context) msg.obj);
        }
    }

    public void setFontService(FontService fontService) {
        this.fontService = fontService;
    }

    private HttpServer() {
    }

    public static HttpServer getInstance() {
        return instance;
    }

    public int getHttpServerPort() {
        return httpServerPort;
    }

    private void startServerInternal(Context context) {
        stopServer();
        server = new AsyncHttpServer();
        mAsyncServer = new AsyncServer();
        j2Executor = new J2Executor(
                new ThreadPoolExecutor(10, 10, 0L,
                        TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10))
        );
        httpServerRequestCallback = new RCPInvokeCallback(fontService, j2Executor);

        bindRootCommand();
        bindPingCommand();
        bindStartAppCommand(context);
        bindInvokeCommand();
        bindAliveServiceCommand();
        bindRestartDeviceCommand();
        bindExecuteShellCommand();
        //TODO adb命令，需要维持会话

        try {
            httpServerPort = Constant.httpServerPort;
            server.listen(mAsyncServer, httpServerPort);
            Log.i(TAG, "start server success...");
            Log.i(TAG, "server running on: " + CommonUtils.localServerBaseURL());
        } catch (Exception e) {
            Log.e(TAG, "startServer error", e);
        }
    }

    public void startServer(final Context context) {

        HttpClientUtils.getClient().newCall(CommonUtils.pingServerRequest()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Message obtain = Message.obtain();
                obtain.obj = context;
                handler.sendMessage(obtain);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body != null) {
                        if (body.string().equalsIgnoreCase("true")) {
                            Log.i(TAG, "ping http server success");
                            return;
                        }
                    }
                }
                Message obtain = Message.obtain();
                obtain.obj = context;
                handler.sendMessage(obtain);
            }
        });


    }

    public synchronized void stopServer() {
        if (server == null) {
            return;
        }
        server.stop();
        mAsyncServer.stop();
        server = null;
        mAsyncServer = null;
        j2Executor.shutdownAll();
        j2Executor = null;
    }

    private void bindRootCommand() {
        server.get("/", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                String baseURL = CommonUtils.localServerBaseURL();
                Hashtable<String, ArrayList<Object>> actions = ReflectUtil.getFieldValue(server, "mActions");
                StringBuilder html = new StringBuilder("<html><head><meta charset=\"UTF-8\"><title>Hermes</title></head><body><p>HermesAgent ，项目地址：<a href=\"https://gitee.com/virjar/hermesagent\">https://gitee.com/virjar/hermesagent</a></p>");
                html.append("<p>服务base地址：").append(baseURL).append("</p>");
                for (Hashtable.Entry<String, ArrayList<Object>> entry : actions.entrySet()) {
                    html.append("<p>httpMethod:").append(entry.getKey()).append("</p>");
                    html.append("<ul>");
                    for (Object object : entry.getValue()) {
                        Pattern pattern = ReflectUtil.getFieldValue(object, "regex");
                        html.append("<li>");
                        if (StringUtils.equalsIgnoreCase(entry.getKey(), "get")) {
                            html.append("<a href=\"").append(baseURL).append(pattern.pattern().substring(1)).append("\">")
                                    .append(baseURL).append(pattern.pattern().substring(1)).append("</a>");
                        } else {
                            html.append(baseURL).append(pattern.pattern().substring(1));
                        }
                        html.append("</li>");
                    }
                    html.append("</ul>");
                }
                html.append("</body></html>");
                response.send("text/html", html.toString());
            }
        });
    }

    private void bindExecuteShellCommand() {
        server.get(Constant.executeShellCommandPath, new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                final String cmd = request.getQuery().getString("cmd");
                if (StringUtils.isBlank(cmd)) {
                    CommonUtils.sendJSON(response, CommonRes.failed("parameter {cmd} not present!!"));
                    return;
                }
                new J2ExecutorWrapper(j2Executor.getOrCreate("shell", 1, 2), new Runnable() {
                    @Override
                    public void run() {
                        CommonUtils.sendJSON(response, CommonRes.success(CommonUtils.execCmd(cmd)));
                    }
                }, response).run();
            }
        });
    }

    private void bindRestartDeviceCommand() {
        server.get(Constant.restartDevicePath, new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                new J2ExecutorWrapper(j2Executor.getOrCreate("shell", 1, 2), new Runnable() {
                    @Override
                    public void run() {
                        CommonUtils.sendJSON(response, CommonRes.success("command accepted"));
                        CommonUtils.restartAndroidSystem();
                    }
                }, response).run();
            }
        });
    }

    private void bindAliveServiceCommand() {
        server.get(Constant.aliveServicePath, new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                ArrayList<String> strings = Lists.newArrayList(fontService.onlineAgentServices());
                Collections.sort(strings);
                CommonUtils.sendJSON(response, CommonRes.success(strings));
            }
        });
    }

    private void bindInvokeCommand() {
        server.get(Constant.invokePath, httpServerRequestCallback);
        server.post(Constant.invokePath, httpServerRequestCallback);
    }

    private void bindStartAppCommand(final Context context) {
        server.get(Constant.startAppPath, new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                String app = request.getQuery().getString("app");
                if (StringUtils.isBlank(app)) {
                    CommonUtils.sendJSON(response, CommonRes.failed("the param {app} must exist"));
                    return;
                }
                new StartAppTask(app, context, response).execute();
            }
        });
    }


    private void bindPingCommand() {
        server.get(Constant.httpServerPingPath, new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                String sourcePackage = request.getQuery().getString("source_package");
                if (StringUtils.isBlank(sourcePackage)) {
                    response.send("true");
                    return;
                }
                IHookAgentService hookAgent = fontService.findHookAgent(sourcePackage);
                if (hookAgent == null) {
                    response.send(Constant.rebind);
                    return;
                }
                response.send("true");
            }
        });
    }
}
