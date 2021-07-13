package com.virjar.hermes.hermesagent.host.http;

import android.content.Context;
import android.util.Log;

import com.google.common.collect.Lists;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.virjar.hermes.hermesagent.bean.CommonRes;
import com.virjar.hermes.hermesagent.host.manager.StartAppTask;
import com.virjar.hermes.hermesagent.host.service.FontService;
import com.virjar.hermes.hermesagent.host.thread.J2Executor;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

    public void startServer(Context context) {
        if (CommonUtils.pingServer()) {
            return;
        }
        stopServer();
        server = new AsyncHttpServer();
        mAsyncServer = new AsyncServer();
        j2Executor = new J2Executor(
                new ThreadPoolExecutor(10, 10, 0L,
                        TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10))
        );
        httpServerRequestCallback = new RCPInvokeCallback(fontService, j2Executor);

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
        } catch (Exception e) {
            Log.e(TAG, "startServer error", e);
        }
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
                response.send("true");
            }
        });
    }
}
