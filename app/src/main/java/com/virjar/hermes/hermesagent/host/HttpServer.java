package com.virjar.hermes.hermesagent.host;

import android.content.Context;
import android.util.Log;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.virjar.hermes.hermesagent.aidl.IHookAgentService;
import com.virjar.hermes.hermesagent.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.bean.CommonRes;
import com.virjar.hermes.hermesagent.host.manager.StartAppTask;
import com.virjar.hermes.hermesagent.host.service.FontService;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;
import com.virjar.hermes.hermesagent.util.URLEncodeUtil;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.util.Map;

import javax.annotation.Nullable;

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

        bindPingCommand(server);
        bindStartAppCommand(server, context);
        bindInvokeCommand(server, context);
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
    }

    private void bindInvokeCommand(AsyncHttpServer server, final Context context) {

        HttpServerRequestCallback httpServerRequestCallback = new HttpServerRequestCallback() {

            private String determineInvokeTarget(AsyncHttpServerRequest request) {
                Multimap query = request.getQuery();
                String invokePackage = query.getString(Constant.invokePackage);
                if (StringUtils.isBlank(invokePackage)) {
                    Object o = request.getBody().get();
                    if (o instanceof JSONObject) {
                        invokePackage = ((JSONObject) o).optString(Constant.invokePackage);
                    } else if (o instanceof String) {
                        try {
                            com.alibaba.fastjson.JSONObject jsonObject = com.alibaba.fastjson.JSONObject.parseObject((String) o);
                            invokePackage = jsonObject.getString(Constant.invokePackage);
                        } catch (com.alibaba.fastjson.JSONException e) {
                            //ignore
                        }
                    }
                }
                return invokePackage;
            }

            private String joinParam(Map<String, String> params) {
                if (params == null || params.isEmpty()) {
                    return null;
                }
                return Joiner.on('&').withKeyValueSeparator('=').join(Maps.transformValues(params, new Function<String, String>() {
                    @Override
                    public String apply(@Nullable String input) {
                        return URLEncodeUtil.escape(input);
                    }
                }));
            }

            private InvokeRequest buildInvokeRequest(AsyncHttpServerRequest request) {
                if ("get".equalsIgnoreCase(request.getMethod())) {

                }

                return null;
            }

            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                String invokePackage = determineInvokeTarget(request);
                if (StringUtils.isBlank(invokePackage)) {
                    CommonUtils.sendJSON(response, CommonRes.failed(Constant.status_need_invoke_package_param, Constant.needInvokePackageParamMessage));
                    return;
                }
                IHookAgentService hookAgent = fontService.findHookAgent(invokePackage);
                if (hookAgent == null) {
                    CommonUtils.sendJSON(response, CommonRes.failed(Constant.status_service_not_available, Constant.serviceNotAvailableMessage));
                    return;
                }


            }
        };

        server.get(Constant.invokePath, httpServerRequestCallback);
        server.post(Constant.invokePath, httpServerRequestCallback);
    }

    private void bindStartAppCommand(AsyncHttpServer server, final Context context) {
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


    private void bindPingCommand(AsyncHttpServer server) {
        server.get(Constant.httpServerPingPath, new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                response.send("true");
            }
        });
    }
}
